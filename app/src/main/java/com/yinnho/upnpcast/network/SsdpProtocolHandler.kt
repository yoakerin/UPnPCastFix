package com.yinnho.upnpcast.network

import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.device.DeviceParser
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.device.locationKey
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SSDP协议处理器 - 处理DLNA设备发现和管理
 */
class SsdpProtocolHandler(
    private val registry: Registry,
    private val executor: ExecutorService
) {
    private val TAG = "SsdpProtocolHandler"
    
    // 缓存和配置
    private val processingDevices = ConcurrentHashMap<String, MutableList<(Result<DeviceParser.DeviceDescription>) -> Unit>>()
    private val knownLocations = ConcurrentHashMap<String, Long>()
    private val devices = ConcurrentHashMap<String, RemoteDevice>()
    private val isShutdown = AtomicBoolean(false)
    private val DEBUG_MODE = false
    
    // 公共API
    fun setShutdown(shutdown: Boolean) = isShutdown.set(shutdown)
    fun isShutdown(): Boolean = isShutdown.get()
    fun getDevices(): Map<String, RemoteDevice> = HashMap(devices)
    fun clearCache() {
        knownLocations.clear()
        processingDevices.clear()
        devices.clear()
    }
    
    /**
     * 处理搜索响应消息
     */
    fun processSearchResponse(message: String, _fromAddr: InetAddress) {
        if (isShutdown.get()) return
        
        runCatching {
            val headers = DeviceParser.parseHeaders(message)
            headers["LOCATION"]?.let { location ->
                val usn = headers["USN"] ?: "uuid:virtual-${location.hashCode()}"
                processDeviceDiscovery(location, usn)
            }
        }.onFailure { e ->
            EnhancedThreadManager.e(TAG, "处理搜索响应出错", e as? Exception ?: Exception(e))
        }
    }
    
    /**
     * 处理NOTIFY消息
     */
    fun processNotify(message: String, _addr: InetAddress) {
        if (isShutdown.get()) return

        runCatching {
            val headers = DeviceParser.parseHeaders(message)
            val location = headers["LOCATION"] ?: return
            val usn = headers["USN"] ?: "uuid:virtual-${location.hashCode()}"

            when (headers["NTS"]) {
                "ssdp:alive" -> {
                    EnhancedThreadManager.v(TAG, "接收到ssdp:alive消息: $usn, 位置: $location")
                    processDeviceDiscovery(location, usn)
                }
                "ssdp:byebye" -> {
                    EnhancedThreadManager.d(TAG, "接收到ssdp:byebye消息: $usn, 准备移除设备")
                    removeDevice(location, DeviceParser.extractUUID(usn))
                }
                else -> EnhancedThreadManager.v(TAG, "接收到未知NTS类型的通知: ${headers["NTS"]}")
            }
        }.onFailure { e ->
            EnhancedThreadManager.e(TAG, "处理NOTIFY消息出错: ${_addr.hostAddress}", e as? Exception ?: Exception(e))
        }
    }
    
    /**
     * 处理设备发现
     */
    private fun processDeviceDiscovery(location: String, usn: String) {
        // 过滤重复消息和已存在设备
        if (shouldFilterDuplicate(location) || (devices[location] != null && !DEBUG_MODE)) {
            EnhancedThreadManager.v(TAG, "跳过重复设备消息: $location")
            return
        }
        
        executor.execute {
            EnhancedThreadManager.d(TAG, "开始处理新发现的设备: $usn")
            fetchDeviceDescription(location, DeviceParser.extractUUID(usn)) { result ->
                result.fold(
                    onSuccess = { description ->
                        runCatching {
                            val device = DeviceParser.createRemoteDevice(usn, description, location)
                            devices[device.locationKey] = device
                            registry.addDevice(device)
                            EnhancedThreadManager.i(TAG, "成功添加设备: ${description.friendlyName ?: "未命名设备"} (${DeviceParser.extractUUID(usn)})")
                        }.onFailure { error ->
                            EnhancedThreadManager.e(TAG, "处理设备失败: $location, ${error.message}", error as? Exception ?: Exception(error))
                        }
                    },
                    onFailure = { error ->
                        EnhancedThreadManager.e(TAG, "设备描述获取失败: $location", error as? Exception ?: Exception(error))
                    }
                )
            }
        }
    }
    
    /**
     * 是否过滤重复消息
     */
    private fun shouldFilterDuplicate(location: String): Boolean {
        if (DEBUG_MODE) return false
        
        val now = System.currentTimeMillis()
        val lastSeenTime = knownLocations.put(location, now)
        
        return lastSeenTime != null && (now - lastSeenTime < 10)
    }
    
    /**
     * 移除设备
     */
    private fun removeDevice(location: String, uuid: String) {
        runCatching {
            // 尝试找到设备并移除
            val device = registry.getDevice(uuid) ?: devices[location]
            device?.let {
                registry.removeDevice(it)
                devices.remove(it.locationKey)
                knownLocations.remove(it.locationKey)
            }
        }.onFailure { error ->
            EnhancedThreadManager.e(TAG, "移除设备失败: $uuid", error as? Exception ?: Exception(error))
        }
    }
    
    /**
     * 获取设备描述 - 使用Result类型
     */
    private fun fetchDeviceDescription(
        location: String, 
        udn: String, 
        callback: (Result<DeviceParser.DeviceDescription>) -> Unit
    ) {
        // 检查是否有正在处理的相同请求
        val callbacks = processingDevices.compute(location) { _, list -> 
            list ?: mutableListOf()
        }!!
        
        synchronized(callbacks) {
            callbacks.add(callback)
            if (callbacks.size > 1) return // 已有处理中的请求
        }
        
        // 获取设备描述 - 使用新的Result风格API
        runCatching {
            DeviceParser.retrieveDeviceDescriptionAsync(location, udn) { result ->
                notifyCallbacks(location, result)
            }
        }.onFailure { error ->
            processingDevices.remove(location)
            callback(Result.failure(error as? Exception ?: Exception("获取设备描述失败", error)))
            EnhancedThreadManager.e(TAG, "获取设备描述失败", error as? Exception ?: Exception(error))
        }
    }
    
    /**
     * 通知回调 - 使用Result类型
     */
    private fun notifyCallbacks(location: String, result: Result<DeviceParser.DeviceDescription>) {
        val callbacks = processingDevices.remove(location) ?: return
        val callbackCount = callbacks.size
        
        if (callbackCount > 1) {
            EnhancedThreadManager.d(TAG, "通知${callbackCount}个等待回调: $location")
        }
        
        var successCount = 0
        var errorCount = 0
        
        callbacks.forEach { callback ->
            try { 
                callback(result)
                successCount++
            } catch (e: Exception) { 
                errorCount++
                EnhancedThreadManager.e(TAG, "回调处理异常 ($errorCount/$callbackCount): ${e.message}", e) 
            }
        }
        
        // 只在存在失败的情况下记录信息
        if (errorCount > 0) {
            EnhancedThreadManager.w(TAG, "回调处理完成: 成功=${successCount}个, 失败=${errorCount}个")
        }
    }
} 