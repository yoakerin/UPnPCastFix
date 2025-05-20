package com.yinnho.upnpcast.core

import android.util.Log
import com.yinnho.upnpcast.interfaces.UpnpServiceConfiguration
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.network.SSDPManager
import com.yinnho.upnpcast.registry.StandardDeviceRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * SSDP整合适配器
 * 负责将新的SSDPManager与现有系统集成
 * 作为过渡阶段的桥接组件
 */
class SSDPIntegrationAdapter private constructor(
    private val configuration: UpnpServiceConfiguration
) {
    private val TAG = "SSDPIntegrationAdapter"
    
    // 获取SSDP管理器实例
    private val ssdpManager = SSDPManager.getInstance(configuration)
    
    // 直接使用标准设备注册表
    private val deviceRegistry = StandardDeviceRegistry.getInstance()
    
    // 设备发现回调
    private var deviceFoundCallback: ((RemoteDevice) -> Unit)? = null
    
    // 已记录日志的设备缓存
    private val loggedDevices = ConcurrentHashMap<String, Long>()
    
    // 是否使用事件总线
    private var useEventBus = true
    
    companion object {
        @Volatile
        private var INSTANCE: SSDPIntegrationAdapter? = null
        
        fun getInstance(configuration: UpnpServiceConfiguration): SSDPIntegrationAdapter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SSDPIntegrationAdapter(configuration).also { INSTANCE = it }
            }
        }
        
        fun releaseInstance() {
            synchronized(this) {
                INSTANCE?.release()
                INSTANCE = null
            }
        }
    }
    
    /**
     * 设置是否使用事件总线
     */
    fun setUseEventBus(use: Boolean) {
        this.useEventBus = use
        ssdpManager.setUseEventBus(use)
        Log.d(TAG, "设置使用事件总线: $use")
    }
    
    init {
        // 注册SSDP监听器
        ssdpManager.addListener(object : SSDPManager.DeviceDiscoveryListener {
            override fun onDeviceDiscovered(device: RemoteDevice) {
                // 添加到设备注册表
                deviceRegistry.addDevice(device)
                // 通知外部回调
                deviceFoundCallback?.invoke(device)
                // 更新设备列表
                notifyDeviceListUpdated()
                
                // 提取设备地址作为唯一标识（格式为host:port）
                val deviceHost = device.identity.descriptorURL.host ?: ""
                val devicePort = device.identity.descriptorURL.port
                val deviceKey = "$deviceHost:$devicePort"
                
                // 检查是否已经记录过该设备的日志
                val now = System.currentTimeMillis()
                val lastLogTime = loggedDevices.put(deviceKey, now)
                
                // 只有新设备或已经超过30秒的设备才记录日志
                if (lastLogTime == null || (now - lastLogTime > 30000)) {
                    // 提取详细信息
                    val deviceIp = deviceHost
                    val deviceName = device.details.friendlyName ?: "未命名设备"
                    val manufacturer = device.details.manufacturerInfo?.name ?: "未知厂商"
                    val model = device.details.modelInfo?.name ?: "未知型号"
                    
                    Log.i(TAG, "设备发现详情: {" +
                        "\n  设备名称: $deviceName" +
                        "\n  设备IP: $deviceIp" +
                        "\n  设备ID: ${device.identity.udn}" +
                        "\n  制造商: $manufacturer" +
                        "\n  型号: $model" +
                        "\n}")
                }
            }
            
            override fun onDeviceLost(device: RemoteDevice) {
                // 从设备注册表移除设备
                deviceRegistry.removeDevice(device)
                // 更新设备列表
                notifyDeviceListUpdated()
                
                // 从已记录日志的设备缓存中移除
                val deviceHost = device.identity.descriptorURL.host ?: ""
                val devicePort = device.identity.descriptorURL.port
                val deviceKey = "$deviceHost:$devicePort"
                loggedDevices.remove(deviceKey)
                
                Log.i(TAG, "设备丢失: ${device.details.friendlyName ?: "未命名设备"} (${device.identity.udn})")
            }
            
            override fun onSearchStarted() {
                Log.d(TAG, "SSDP设备搜索开始")
            }
            
            override fun onSearchFinished(devices: List<RemoteDevice>) {
                if (devices.isEmpty()) {
                    Log.d(TAG, "SSDP设备搜索完成，未发现设备")
                } else {
                    Log.i(TAG, "SSDP设备搜索完成，发现${devices.size}个设备")
                }
                
                // 只有在找到设备时才更新列表
                if (devices.isNotEmpty()) {
                    // 更新设备列表
                    notifyDeviceListUpdated()
                }
            }
        })
        
        Log.i(TAG, "SSDP集成适配器已初始化")
    }
    
    /**
     * 开始搜索设备
     */
    fun startDeviceSearch() {
        Log.d(TAG, "开始SSDP设备搜索")
        ssdpManager.startDiscovery()
        
        // 创建单线程执行器，用于延迟操作
        val executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "SSDP-Search-Scheduler").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY
            }
        }
        
        // 创建搜索策略：首次搜索后，如果没有发现设备，则进行第二次搜索
        // 如果第二次仍没有发现，再进行第三次
        // 搜索间隔为2秒，避免网络拥塞
        executor.schedule({
            // 检查是否已经发现设备
            val devicesFound = deviceRegistry.getAllDevices().isNotEmpty()
            
            // 只有在未发现设备时才进行第二次搜索
            if (!devicesFound) {
                Log.d(TAG, "未发现设备，进行第二次SSDP搜索")
                ssdpManager.startDiscovery()
                
                // 再次延迟2秒，检查是否需要第三次搜索
                executor.schedule({
                    val devicesFoundAfterSecond = deviceRegistry.getAllDevices().isNotEmpty()
                    
                    // 只有在前两次搜索都未发现设备时才进行第三次搜索
                    if (!devicesFoundAfterSecond) {
                        Log.d(TAG, "仍未发现设备，进行最后一次SSDP搜索")
                        ssdpManager.startDiscovery()
                    } else {
                        Log.d(TAG, "已发现设备，无需进行额外搜索")
                    }
                    
                    // 关闭执行器
                    executor.shutdown()
                }, 2000, TimeUnit.MILLISECONDS)
            } else {
                Log.d(TAG, "已发现设备，无需进行额外搜索")
                executor.shutdown()
            }
        }, 2000, TimeUnit.MILLISECONDS)
    }
    
    /**
     * 停止设备搜索
     */
    fun stopDeviceSearch() {
        Log.d(TAG, "停止SSDP设备搜索")
        ssdpManager.stopDiscovery()
    }
    
    /**
     * 设置设备发现回调
     */
    fun setDeviceFoundCallback(callback: (RemoteDevice) -> Unit) {
        deviceFoundCallback = callback
    }
    
    /**
     * 通知设备列表更新
     */
    private fun notifyDeviceListUpdated() {
        // 引入同步锁，防止多线程并发更新
        synchronized(this) {
            // 从SSDP管理器获取设备列表
            val devices = ssdpManager.getDiscoveredDevices()
            
            // 只有在设备列表不为空时通知更新
            if (devices.isNotEmpty()) {
                // 获取当前设备列表
                val currentDevices = deviceRegistry.getAllDevices()
                
                // 检查设备列表是否真正发生变化（数量或内容）
                val needsUpdate = devices.size != currentDevices.size ||
                        devices.any { newDevice -> 
                            !currentDevices.any { it.identity.descriptorURL == newDevice.identity.descriptorURL }
                        }
                
                // 只在设备列表真正变化时才更新并记录日志
                if (needsUpdate) {
                    // 通知设备注册表更新
                    deviceRegistry.updateDeviceList(devices)
                    Log.d(TAG, "设备列表已更新，包含${devices.size}个设备")
                }
            }
        }
    }
    
    /**
     * 获取所有设备
     */
    fun getAllDevices(): List<RemoteDevice> {
        return ssdpManager.getDiscoveredDevices()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        deviceFoundCallback = null
        // 不释放SSDPManager的实例，因为它是单例的
    }
} 