package com.yinnho.upnpcast.manager

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.interfaces.RegistryListener
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.device.locationKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 注册表实现类
 * 管理所有发现的远程设备和监听器
 */
class RegistryImpl private constructor() : Registry {
    private val TAG = "RegistryImpl"
    
    // 设备注册表，使用ConcurrentHashMap保证线程安全，key为设备的locationKey
    private val remoteDevices = ConcurrentHashMap<String, RemoteDevice>()
    
    // 设备添加时间戳，用于计算设备存活时间和节流通知
    private val deviceAddTimes = ConcurrentHashMap<String, Long>()
    
    // 注册表监听器列表
    private val registryListeners = CopyOnWriteArrayList<RegistryListener>()
    
    // 主线程Handler，用于在主线程上发送通知
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 通知节流时间（300毫秒内相同设备的添加/移除只通知一次）
    private val NOTIFICATION_THROTTLE_MS = 300L
    
    // 是否已关闭
    private var isShutdown = false
    
    companion object {
        @Volatile
        private var instance: RegistryImpl? = null
        
        fun getInstance(): RegistryImpl {
            return instance ?: synchronized(this) {
                instance ?: RegistryImpl().also { instance = it }
            }
        }
    }
    
    /**
     * 添加设备到注册表
     */
    override fun addDevice(device: RemoteDevice) {
        if (isShutdown) return
        
        // 使用location作为唯一键
        val deviceKey = device.locationKey
        val deviceName = device.details.friendlyName ?: "未知设备"
        val threadId = Thread.currentThread().name
        
        // 检查是否需要节流通知
        val now = System.currentTimeMillis()
        val lastUpdate = deviceAddTimes[deviceKey]
        if (lastUpdate != null && now - lastUpdate < NOTIFICATION_THROTTLE_MS) {
            // 如果该设备最近刚更新过，则不重复通知，但仍然更新设备
            remoteDevices[deviceKey] = device
            return
        }
        
        remoteDevices[deviceKey] = device
        
        // 设置设备添加时间，用于计算存活时间
        deviceAddTimes[deviceKey] = now
        
        // 通知所有监听器
        EnhancedThreadManager.d(TAG, "注册表添加设备 | UDN=${device.identity.udn} | 名称=$deviceName | 线程=$threadId")
        notifyDeviceAdded(device)
    }
    
    /**
     * 移除设备
     */
    override fun removeDevice(device: RemoteDevice) {
        if (isShutdown) return
        
        // 使用location作为唯一键
        val deviceKey = device.locationKey
        val deviceName = device.details.friendlyName ?: "未知设备"
        
        val removed = remoteDevices.remove(deviceKey)
        if (removed != null) {
            // 移除设备添加时间
            deviceAddTimes.remove(deviceKey)
            
            // 通知所有监听器
            EnhancedThreadManager.d(TAG, "注册表移除设备 | UDN=${device.identity.udn} | 名称=$deviceName")
            notifyDeviceRemoved(device)
        }
    }
    
    /**
     * 移除所有远程设备
     */
    override fun removeAllRemoteDevices() {
        if (isShutdown) return
        
        // 保存当前设备列表用于通知
        val devicesToRemove = remoteDevices.values.toList()
        
        // 清空设备集合
        remoteDevices.clear()
        deviceAddTimes.clear()
        
        EnhancedThreadManager.d(TAG, "已移除所有远程设备，共${devicesToRemove.size}个")
        
        // 通知所有监听器
        devicesToRemove.forEach { device ->
            notifyDeviceRemoved(device)
        }
    }
    
    /**
     * 导出注册表信息
     * @param detailLevel 详细级别，0-基本信息，1-包含设备列表，2-包含详细设备信息
     * @return 注册表信息字符串
     */
    override fun dump(detailLevel: Int): String {
        val sb = StringBuilder()
        
        sb.appendLine("========== 注册表状态 ==========")
        sb.appendLine("设备数量: ${remoteDevices.size}")
        sb.appendLine("监听器数量: ${registryListeners.size}")
        sb.appendLine("注册表状态: ${if (isShutdown) "已关闭" else "运行中"}")
        
        // 根据详细级别添加更多信息
        if (detailLevel >= 1 && remoteDevices.isNotEmpty()) {
            sb.appendLine("\n---------- 设备列表 ----------")
            remoteDevices.values.forEachIndexed { index, device ->
                sb.appendLine("设备[$index]: ${device.details.friendlyName} (${device.identity.udn})")
                
                if (detailLevel >= 2) {
                    // 添加详细设备信息
                    sb.appendLine("  位置: ${device.locationKey}")
                    sb.appendLine("  描述URL: ${device.identity.descriptorURL}")
                    sb.appendLine("  设备类型: ${device.type.type}")
                    sb.appendLine("  制造商: ${device.details.manufacturerInfo?.name ?: "未知"}")
                    sb.appendLine("  型号: ${device.details.modelInfo?.name ?: "未知"}")
                    
                    // 添加时间戳信息
                    val addTime = deviceAddTimes[device.locationKey]
                    if (addTime != null) {
                        val ageMs = System.currentTimeMillis() - addTime
                        val ageSeconds = ageMs / 1000
                        sb.appendLine("  添加时间: ${ageSeconds}秒前")
                    }
                    
                    // 添加服务信息
                    if (device.services.isNotEmpty()) {
                        sb.appendLine("  服务列表:")
                        device.services.forEachIndexed { serviceIndex, service ->
                            sb.appendLine("    服务[$serviceIndex]: ${service.serviceType}")
                        }
                    }
                    
                    sb.appendLine()
                }
            }
        }
        
        if (detailLevel >= 2 && registryListeners.isNotEmpty()) {
            sb.appendLine("\n---------- 监听器列表 ----------")
            registryListeners.forEachIndexed { index, listener ->
                sb.appendLine("监听器[$index]: ${listener.javaClass.name}")
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 根据UDN获取设备
     * 兼容旧接口，内部使用locationKey查找
     */
    override fun getDevice(udn: String): RemoteDevice? {
        if (isShutdown) return null
        
        // 先尝试直接用udn作为locationKey查找（可能是传入的就是locationKey）
        remoteDevices[udn]?.let { return it }
        
        // 如果找不到，则遍历所有设备，查找匹配UDN的设备
        return remoteDevices.values.find { it.identity.udn == udn }
    }
    
    /**
     * 根据locationKey获取设备
     */
    fun getDeviceByLocation(locationKey: String): RemoteDevice? {
        if (isShutdown) return null
        return remoteDevices[locationKey]
    }
    
    /**
     * 获取所有设备
     */
    override fun getDevices(): List<RemoteDevice> {
        return if (isShutdown) {
            emptyList()
        } else {
            remoteDevices.values.toList()
        }
    }
    
    /**
     * 添加监听器
     */
    override fun addListener(listener: RegistryListener) {
        if (isShutdown) return
        if (!registryListeners.contains(listener)) {
            registryListeners.add(listener)
            EnhancedThreadManager.d(TAG, "添加注册表监听器: ${listener.javaClass.simpleName}")
        }
    }
    
    /**
     * 移除监听器
     */
    override fun removeListener(listener: RegistryListener) {
        if (isShutdown) return
        registryListeners.remove(listener)
        EnhancedThreadManager.d(TAG, "移除注册表监听器: ${listener.javaClass.simpleName}")
    }
    
    /**
     * 判断是否包含指定监听器
     */
    override fun hasListener(listener: RegistryListener): Boolean {
        return registryListeners.contains(listener)
    }
    
    /**
     * 获取监听器数量（用于调试）
     */
    fun getListenerCount(): Int {
        return registryListeners.size
    }
    
    /**
     * 通知设备添加
     */
    private fun notifyDeviceAdded(device: RemoteDevice) {
        if (isShutdown || registryListeners.isEmpty()) return
        
        // 在主线程中通知所有监听器
        mainHandler.post {
            for (listener in registryListeners) {
                try {
                    listener.deviceAdded(device)
                } catch (e: Exception) {
                    Log.e(TAG, "通知设备添加时异常: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 通知设备移除
     */
    private fun notifyDeviceRemoved(device: RemoteDevice) {
        if (isShutdown || registryListeners.isEmpty()) return
        
        // 在主线程中通知所有监听器
        mainHandler.post {
            for (listener in registryListeners) {
                try {
                    listener.deviceRemoved(device)
                } catch (e: Exception) {
                    Log.e(TAG, "通知设备移除时异常: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 关闭注册表
     */
    override fun shutdown() {
        if (isShutdown) return
        
        isShutdown = true
        
        // 清空设备和监听器
        remoteDevices.clear()
        deviceAddTimes.clear()
        registryListeners.clear()
        
        // 移除所有待处理的消息
        mainHandler.removeCallbacksAndMessages(null)
        
        Log.d(TAG, "注册表已关闭")
    }
} 