package com.yinnho.upnpcast.registry

import android.util.Log
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.device.locationKey
import com.yinnho.upnpcast.model.RemoteDevice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 标准设备注册表实现
 * 
 * 基于ConcurrentHashMap提供高效的线程安全实现，支持设备的添加、查询和移除。
 * 实现了设备过期清理和通知节流机制，提高系统稳定性和效率。
 */
class StandardDeviceRegistry : DeviceRegistry {
    private val TAG = "StandardDeviceRegistry"
    
    // 设备缓存条目 - 用于跟踪设备状态和元数据
    private data class DeviceCacheEntry(
        val device: RemoteDevice,
        val creationTime: Long = System.currentTimeMillis(),
        var lastUpdatedTime: Long = System.currentTimeMillis(),
        var accessCount: Int = 0
    )
    
    // 配置参数
    private val MAX_CACHE_SIZE = 100
    private val DEVICE_EXPIRE_TIME_MS = 30 * 60 * 1000L // 30分钟
    private val CLEANUP_INTERVAL_MS = 5 * 60 * 1000L // 5分钟
    private val NOTIFICATION_THROTTLE_MS = 500L // 节流通知的最小间隔
    
    // 存储设备映射，键为设备的locationKey (描述文件URL)
    private val deviceMap = ConcurrentHashMap<String, DeviceCacheEntry>()
    
    // 设备更新时间记录，用于通知节流
    private val deviceUpdateTimes = ConcurrentHashMap<String, Long>()
    
    // 注册表监听器
    private val listeners = CopyOnWriteArrayList<DeviceRegistry.Listener>()
    
    // 上次通知的设备位置键集合，用于检测设备列表变化
    private var lastNotifiedLocationKeys = emptySet<String>()
    
    // 关闭标识
    private val isShutdown = AtomicBoolean(false)
    
    // 同步锁，用于批量操作和通知
    private val notifyLock = Any()
    
    // 清理任务调度器
    private val cleanupTask = EnhancedThreadManager.scheduleWithFixedDelay(
        { cleanupExpiredDevices() },
        CLEANUP_INTERVAL_MS,
        CLEANUP_INTERVAL_MS,
        TimeUnit.MILLISECONDS
    )
    
    /**
     * 添加监听器
     */
    override fun addListener(listener: DeviceRegistry.Listener) {
        if (!isShutdown.get() && !listeners.contains(listener)) {
            listeners.add(listener)
            Log.d(TAG, "添加设备注册表监听器, 当前共${listeners.size}个")
        }
    }
    
    /**
     * 移除监听器
     */
    override fun removeListener(listener: DeviceRegistry.Listener) {
        if (listeners.remove(listener)) {
            Log.d(TAG, "移除设备注册表监听器, 剩余${listeners.size}个")
        }
    }
    
    /**
     * 添加设备
     * 
     * @return 如果设备是新添加的，返回true；如果是更新已有设备，返回false
     */
    override fun addDevice(device: RemoteDevice): Boolean {
        if (isShutdown.get()) return false
        
        val deviceKey = device.locationKey
        val now = System.currentTimeMillis()
        
        // 如果缓存已满且是新设备，清理空间
        if (deviceMap.size >= MAX_CACHE_SIZE && !deviceMap.containsKey(deviceKey)) {
            evictLeastRecentlyUsedDevices(1)
        }
        
        val existingEntry = deviceMap[deviceKey]
        val isNewDevice = existingEntry == null
        
        // 检查是否需要节流通知
        val lastUpdateTime = deviceUpdateTimes[deviceKey]
        val shouldNotify = lastUpdateTime == null || 
                now - lastUpdateTime > NOTIFICATION_THROTTLE_MS
        
        if (isNewDevice) {
            // 添加新设备
            deviceMap[deviceKey] = DeviceCacheEntry(device, now, now, 1)
            deviceUpdateTimes[deviceKey] = now
            
            // 记录日志
            Log.d(TAG, "添加新设备: ${device.details.friendlyName ?: "未命名设备"} (${device.identity.udn})")
            
            // 通知监听器
            if (shouldNotify) {
                notifyDeviceAdded(device)
            }
        } else {
            // 更新已有设备
            deviceMap[deviceKey] = existingEntry!!.copy(
                device = device,
                lastUpdatedTime = now, 
                accessCount = existingEntry.accessCount + 1
            )
            deviceUpdateTimes[deviceKey] = now
            
            // 记录详细日志
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "更新已有设备: ${device.details.friendlyName ?: "未命名设备"} (${device.identity.udn})")
            }
            
            // 通知监听器
            if (shouldNotify) {
                notifyDeviceUpdated(device)
            }
        }
        
        return isNewDevice
    }
    
    /**
     * 移除设备
     */
    override fun removeDevice(device: RemoteDevice): Boolean {
        if (isShutdown.get()) return false
        
        val deviceKey = device.locationKey
        
        return deviceMap.remove(deviceKey)?.let { entry ->
            // 清除更新时间记录
            deviceUpdateTimes.remove(deviceKey)
            
            // 记录日志
            Log.d(TAG, "移除设备: ${entry.device.details.friendlyName ?: "未命名设备"} (${entry.device.identity.udn})")
            
            // 通知监听器
            notifyDeviceRemoved(entry.device)
            true
        } ?: false
    }
    
    /**
     * 根据设备ID获取设备
     */
    override fun getDeviceById(deviceId: String): RemoteDevice? {
        if (isShutdown.get()) return null
        
        return getAllDevices().find { it.identity.udn == deviceId }
    }
    
    /**
     * 根据位置键获取设备
     */
    override fun getDeviceByLocation(locationKey: String): RemoteDevice? {
        if (isShutdown.get()) return null
        
        return deviceMap[locationKey]?.let { entry ->
            // 更新访问信息
            val now = System.currentTimeMillis()
            deviceMap[locationKey] = entry.copy(
                lastUpdatedTime = now,
                accessCount = entry.accessCount + 1
            )
            entry.device
        }
    }
    
    /**
     * 获取所有设备
     */
    override fun getAllDevices(): List<RemoteDevice> {
        if (isShutdown.get()) return emptyList()
        
        // 更新所有设备的访问时间
        val now = System.currentTimeMillis()
        
        return deviceMap.values.map { entry ->
            // 更新访问信息，但不重新创建对象避免内存占用
            deviceMap.compute(entry.device.locationKey) { _, e ->
                e?.copy(lastUpdatedTime = now)
            }
            entry.device
        }
    }
    
    /**
     * 批量更新设备列表
     */
    override fun updateDeviceList(devices: List<RemoteDevice>) {
        if (isShutdown.get()) return
        
        synchronized(notifyLock) {
            // 计算当前设备的位置键集合
            val currentLocationKeys = devices.map { it.locationKey }.toSet()
            
            // 只有当设备列表实际发生变化时才通知，避免重复通知
            if (currentLocationKeys != lastNotifiedLocationKeys) {
                Log.d(TAG, "设备列表已变化，通知监听器")
                lastNotifiedLocationKeys = currentLocationKeys
                notifyDeviceListUpdated(devices)
            } else if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "设备列表未变化，不重复通知")
            }
        }
    }
    
    /**
     * 清空设备列表
     */
    override fun clearDevices() {
        if (isShutdown.get()) return
        
        synchronized(notifyLock) {
            // 保存当前设备列表用于通知
            val devicesToRemove = deviceMap.values.map { it.device }
            
            // 清空所有映射
            deviceMap.clear()
            deviceUpdateTimes.clear()
            lastNotifiedLocationKeys = emptySet()
            
            Log.d(TAG, "清空设备列表，移除${devicesToRemove.size}个设备")
            
            // 逐个通知设备移除
            devicesToRemove.forEach { device ->
                notifyDeviceRemoved(device)
            }
            
            // 通知设备列表更新
            notifyDeviceListUpdated(emptyList())
        }
    }
    
    /**
     * 获取设备数量
     */
    override fun getDeviceCount(): Int = deviceMap.size
    
    /**
     * 导出注册表状态
     */
    override fun dump(detailLevel: Int): String {
        val sb = StringBuilder()
        
        sb.appendLine("========== 设备注册表状态 ==========")
        sb.appendLine("设备数量: ${deviceMap.size}")
        sb.appendLine("监听器数量: ${listeners.size}")
        sb.appendLine("注册表状态: ${if (isShutdown.get()) "已关闭" else "运行中"}")
        
        if (detailLevel >= 1 && deviceMap.isNotEmpty()) {
            sb.appendLine("\n设备列表:")
            deviceMap.values.forEachIndexed { index, entry ->
                val device = entry.device
                sb.appendLine("${index + 1}. ${device.details.friendlyName ?: "未命名设备"}")
                
                if (detailLevel >= 2) {
                    sb.appendLine("   - UUID: ${device.identity.udn}")
                    sb.appendLine("   - 位置: ${device.identity.descriptorURL}")
                    sb.appendLine("   - 创建时间: ${formatTime(entry.creationTime)}")
                    sb.appendLine("   - 最后更新: ${formatTime(entry.lastUpdatedTime)}")
                    sb.appendLine("   - 访问次数: ${entry.accessCount}")
                    
                    device.details.manufacturerInfo?.let { 
                        sb.appendLine("   - 制造商: ${it.name ?: "未知"}") 
                    }
                    
                    device.details.modelInfo?.let { 
                        sb.appendLine("   - 型号: ${it.name ?: "未知"}") 
                    }
                    
                    if (device.services.isNotEmpty()) {
                        sb.appendLine("   - 服务数量: ${device.services.size}")
                    }
                }
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 释放资源
     */
    override fun release() {
        if (isShutdown.compareAndSet(false, true)) {
            // 取消定时任务
            cleanupTask.cancel(false)
            
            // 清空设备列表
            deviceMap.clear()
            deviceUpdateTimes.clear()
            lastNotifiedLocationKeys = emptySet()
            
            // 清空监听器列表
            listeners.clear()
            
            Log.d(TAG, "设备注册表已关闭")
        }
    }
    
    /**
     * 清理过期设备
     */
    private fun cleanupExpiredDevices() {
        if (isShutdown.get()) return
        
        val now = System.currentTimeMillis()
        val expiredKeys = mutableListOf<String>()
        
        // 查找过期设备
        deviceMap.forEach { (key, entry) ->
            if (now - entry.lastUpdatedTime > DEVICE_EXPIRE_TIME_MS) {
                expiredKeys.add(key)
            }
        }
        
        if (expiredKeys.isEmpty()) return
        
        Log.d(TAG, "清理${expiredKeys.size}个过期设备")
        
        // 移除过期设备并通知
        expiredKeys.forEach { key ->
            deviceMap.remove(key)?.let { entry ->
                deviceUpdateTimes.remove(key)
                notifyDeviceRemoved(entry.device)
            }
        }
        
        // 如果有设备被移除，通知设备列表更新
        if (expiredKeys.isNotEmpty()) {
            notifyDeviceListUpdated(getAllDevices())
        }
    }
    
    /**
     * 根据最近最少使用原则移除设备
     */
    private fun evictLeastRecentlyUsedDevices(count: Int) {
        if (isShutdown.get() || deviceMap.isEmpty() || count <= 0) return
        
        // 按最后访问时间排序，移除最旧的设备
        val entriesToRemove = deviceMap.entries
            .sortedBy { it.value.lastUpdatedTime }
            .take(count)
            .map { it.key }
        
        Log.d(TAG, "缓存已满，移除${entriesToRemove.size}个最少使用的设备")
        
        entriesToRemove.forEach { key ->
            deviceMap.remove(key)?.let { entry ->
                deviceUpdateTimes.remove(key)
                notifyDeviceRemoved(entry.device)
            }
        }
    }
    
    /**
     * 通知设备添加
     */
    private fun notifyDeviceAdded(device: RemoteDevice) {
        for (listener in listeners) {
            try {
                listener.onDeviceAdded(device)
            } catch (e: Exception) {
                Log.e(TAG, "通知设备添加异常", e)
            }
        }
    }
    
    /**
     * 通知设备移除
     */
    private fun notifyDeviceRemoved(device: RemoteDevice) {
        for (listener in listeners) {
            try {
                listener.onDeviceRemoved(device)
            } catch (e: Exception) {
                Log.e(TAG, "通知设备移除异常", e)
            }
        }
    }
    
    /**
     * 通知设备更新
     */
    private fun notifyDeviceUpdated(device: RemoteDevice) {
        for (listener in listeners) {
            try {
                listener.onDeviceUpdated(device)
            } catch (e: Exception) {
                Log.e(TAG, "通知设备更新异常", e)
            }
        }
    }
    
    /**
     * 通知设备列表更新
     */
    private fun notifyDeviceListUpdated(devices: List<RemoteDevice>) {
        for (listener in listeners) {
            try {
                listener.onDeviceListUpdated(devices)
            } catch (e: Exception) {
                Log.e(TAG, "通知设备列表更新异常", e)
            }
        }
    }
    
    /**
     * 格式化时间戳为可读字符串
     */
    private fun formatTime(timeMs: Long): String {
        val diffMs = System.currentTimeMillis() - timeMs
        return when {
            diffMs < 1000 -> "刚刚"
            diffMs < 60000 -> "${diffMs / 1000}秒前"
            diffMs < 3600000 -> "${diffMs / 60000}分钟前"
            diffMs < 86400000 -> "${diffMs / 3600000}小时前"
            else -> "${diffMs / 86400000}天前"
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: StandardDeviceRegistry? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(): StandardDeviceRegistry {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StandardDeviceRegistry().also { INSTANCE = it }
            }
        }
        
        /**
         * 释放单例实例
         */
        fun releaseInstance() {
            synchronized(this) {
                INSTANCE?.release()
                INSTANCE = null
            }
        }
    }
} 