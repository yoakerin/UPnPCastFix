package com.yinnho.upnpcast.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yinnho.upnpcast.core.EnhancedThreadManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
/**
 * 设备状态管理器
 * 用于跟踪和管理DLNA设备的状态
 */
object DeviceStatusManager {
    private const val TAG = "DeviceStatusManager"
    
    // 设备状态枚举
    enum class DeviceState {
        UNKNOWN,        // 未知状态
        DISCOVERED,     // 已发现
        CONNECTED,      // 已连接
        PREPARING,      // 准备中
        PLAYING,        // 播放中
        PAUSED,         // 已暂停
        STOPPED,        // 已停止
        TRANSITIONING,  // 状态转换中
        ERROR,          // 错误状态
        LOST            // 设备已丢失
    }
    
    // 设备状态信息
    data class DeviceStatus(
        var state: DeviceState = DeviceState.UNKNOWN,
        var lastUpdateTime: Long = System.currentTimeMillis(),
        var mediaUrl: String? = null,
        var errorMessage: String? = null,
        var positionMs: Long = 0,
        var volume: Int = 50,
        var lastHeartbeatTime: Long = 0,
        var connectionAttempts: Int = 0
    )
    
    // 设备状态变化监听器
    interface StatusChangeListener {
        fun onDeviceStatusChanged(udn: String, status: DeviceStatus)
    }
    
    // 使用线程安全的Map存储设备状态
    private val deviceStatusMap = ConcurrentHashMap<String, DeviceStatus>()
    
    // 状态变化监听器列表
    private val listeners = mutableListOf<StatusChangeListener>()
    
    // 主线程Handler用于通知状态变化
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 心跳检查间隔
    private const val HEARTBEAT_INTERVAL_MS = 10000L // 10秒
    
    // 注册状态变化监听器
    @Synchronized
    fun registerListener(listener: StatusChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            EnhancedThreadManager.d(TAG, "注册状态变化监听器: $listener")
        }
    }
    
    // 注销状态变化监听器
    @Synchronized
    fun unregisterListener(listener: StatusChangeListener) {
        listeners.remove(listener)
        EnhancedThreadManager.d(TAG, "注销状态变化监听器: $listener")
    }
    
    // 获取设备状态
    fun getDeviceStatus(udn: String): DeviceStatus {
        return deviceStatusMap.getOrPut(udn) { DeviceStatus() }
    }
    
    // 更新设备状态
    fun updateDeviceState(udn: String, state: DeviceState) {
        val status = getDeviceStatus(udn)
        val previousState = status.state
        
        if (previousState != state) {
            status.state = state
            status.lastUpdateTime = System.currentTimeMillis()
            
            // 记录状态变化
            EnhancedThreadManager.d(TAG, "设备状态变化: $udn, $previousState -> $state")
            
            // 通知监听器
            notifyStatusChanged(udn, status)
        }
    }
    
    // 更新媒体URL
    fun updateMediaUrl(udn: String, mediaUrl: String?) {
        val status = getDeviceStatus(udn)
        status.mediaUrl = mediaUrl
        status.lastUpdateTime = System.currentTimeMillis()
        
        // 通知监听器
        notifyStatusChanged(udn, status)
    }
    
    // 更新播放位置
    fun updatePosition(udn: String, positionMs: Long) {
        val status = getDeviceStatus(udn)
        status.positionMs = positionMs
        status.lastUpdateTime = System.currentTimeMillis()
        
        // 通知监听器
        notifyStatusChanged(udn, status)
    }
    
    // 更新音量
    fun updateVolume(udn: String, volume: Int) {
        val status = getDeviceStatus(udn)
        status.volume = volume
        status.lastUpdateTime = System.currentTimeMillis()
        
        // 通知监听器
        notifyStatusChanged(udn, status)
    }
    
    // 更新心跳时间
    fun updateHeartbeat(udn: String) {
        val status = getDeviceStatus(udn)
        status.lastHeartbeatTime = System.currentTimeMillis()
        
        // 如果设备之前是错误状态，且现在有心跳，则恢复为已连接状态
        if (status.state == DeviceState.ERROR) {
            updateDeviceState(udn, DeviceState.CONNECTED)
        }
    }
    
    // 记录错误状态
    fun recordError(udn: String, errorMessage: String) {
        val status = getDeviceStatus(udn)
        status.state = DeviceState.ERROR
        status.errorMessage = errorMessage
        status.lastUpdateTime = System.currentTimeMillis()
        
        EnhancedThreadManager.e(TAG, "设备错误: $udn, $errorMessage")
        
        // 通知监听器
        notifyStatusChanged(udn, status)
    }
    
    // 移除设备状态
    fun removeDevice(udn: String) {
        deviceStatusMap.remove(udn)
        EnhancedThreadManager.d(TAG, "移除设备状态: $udn")
    }
    
    // 检查设备是否活跃
    fun isDeviceActive(udn: String): Boolean {
        val status = deviceStatusMap[udn] ?: return false
        
        // 如果最后更新时间超过一定阈值，则认为设备不活跃
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - status.lastUpdateTime
        
        return timeSinceLastUpdate < HEARTBEAT_INTERVAL_MS * 3 // 允许最多3个心跳周期无响应
    }
    
    // 检查设备状态
    fun checkDevicesStatus() {
        val currentTime = System.currentTimeMillis()
        
        // 遍历所有设备状态
        deviceStatusMap.forEach { (udn, status) ->
            val timeSinceLastHeartbeat = currentTime - status.lastHeartbeatTime
            
            // 如果超过心跳间隔且状态不是LOST或ERROR，则标记为可能丢失
            if (timeSinceLastHeartbeat > HEARTBEAT_INTERVAL_MS * 2 && 
                status.state != DeviceState.LOST && 
                status.state != DeviceState.ERROR) {
                
                EnhancedThreadManager.w(TAG, "设备可能丢失: $udn, 最后心跳: ${timeSinceLastHeartbeat}ms前")
                updateDeviceState(udn, DeviceState.LOST)
            }
        }
    }
    
    // 获取所有活跃设备
    fun getActiveDevices(): List<String> {
        val currentTime = System.currentTimeMillis()
        
        return deviceStatusMap.entries
            .filter { (_, status) -> 
                currentTime - status.lastUpdateTime < HEARTBEAT_INTERVAL_MS * 3 &&
                status.state != DeviceState.LOST &&
                status.state != DeviceState.ERROR
            }
            .map { it.key }
    }
    
    // 通知状态变化
    private fun notifyStatusChanged(udn: String, status: DeviceStatus) {
        // 在主线程通知监听器
        mainHandler.post {
            listeners.forEach { listener ->
                try {
                    listener.onDeviceStatusChanged(udn, status)
                } catch (e: Exception) {
                    EnhancedThreadManager.e(TAG, "通知状态变化失败", e)
                }
            }
        }
    }
    
    // 清除所有状态数据
    fun clear() {
        deviceStatusMap.clear()
        EnhancedThreadManager.d(TAG, "清除所有设备状态数据")
    }
} 