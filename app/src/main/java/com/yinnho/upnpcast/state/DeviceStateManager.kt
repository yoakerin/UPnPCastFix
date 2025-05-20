package com.yinnho.upnpcast.state

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yinnho.upnpcast.model.RemoteDevice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 设备状态管理器
 * 
 * 负责跟踪和管理所有设备的状态，提供状态变更通知和查询能力。
 * 支持设备状态转换规则验证，避免非法状态转换。
 */
class DeviceStateManager private constructor() {
    private val TAG = "DeviceStateManager"
    
    // 设备状态枚举
    enum class DeviceState {
        UNKNOWN,        // 未知状态
        DISCOVERED,     // 已发现但未验证
        VALIDATED,      // 已验证可用
        CONNECTED,      // 已连接
        PLAYING,        // 播放中
        PAUSED,         // 已暂停
        STOPPED,        // 已停止
        BUFFERING,      // 缓冲中
        TRANSITIONING,  // 状态转换中
        ERROR,          // 错误状态
        LOST,           // 设备已丢失
        REMOVED         // 设备已移除
    }
    
    // 设备状态信息类
    data class DeviceStateInfo(
        var device: RemoteDevice? = null,
        var state: DeviceState = DeviceState.UNKNOWN,
        var lastStateChangeTime: Long = System.currentTimeMillis(),
        var lastSeenTimestamp: Long = System.currentTimeMillis(),
        var mediaUrl: String? = null,
        var positionMs: Long = 0,
        var durationMs: Long = 0,
        var volume: Int = 50,
        var isMute: Boolean = false,
        var errorMessage: String? = null,
        var connectionAttempts: Int = 0,
        var additionalInfo: Map<String, Any> = mapOf()
    )
    
    // 状态变化监听器接口
    interface StateChangeListener {
        fun onDeviceStateChanged(
            deviceId: String,
            oldState: DeviceState,
            newState: DeviceState,
            info: DeviceStateInfo
        )
        
        fun onDeviceListChanged(activeDevices: List<DeviceStateInfo>)
    }
    
    // 状态转换规则
    private val validStateTransitions = mapOf(
        DeviceState.UNKNOWN to setOf(
            DeviceState.DISCOVERED,
            DeviceState.VALIDATED,
            DeviceState.REMOVED
        ),
        DeviceState.DISCOVERED to setOf(
            DeviceState.VALIDATED,
            DeviceState.LOST,
            DeviceState.ERROR,
            DeviceState.REMOVED
        ),
        DeviceState.VALIDATED to setOf(
            DeviceState.CONNECTED,
            DeviceState.LOST,
            DeviceState.ERROR,
            DeviceState.REMOVED
        ),
        DeviceState.CONNECTED to setOf(
            DeviceState.PLAYING,
            DeviceState.STOPPED,
            DeviceState.BUFFERING,
            DeviceState.TRANSITIONING,
            DeviceState.LOST,
            DeviceState.ERROR,
            DeviceState.REMOVED,
            DeviceState.CONNECTED // 特殊允许：连接状态刷新
        ),
        DeviceState.PLAYING to setOf(
            DeviceState.PAUSED,
            DeviceState.STOPPED,
            DeviceState.BUFFERING,
            DeviceState.TRANSITIONING,
            DeviceState.LOST,
            DeviceState.ERROR,
            DeviceState.REMOVED
        ),
        DeviceState.PAUSED to setOf(
            DeviceState.PLAYING,
            DeviceState.STOPPED,
            DeviceState.BUFFERING,
            DeviceState.TRANSITIONING,
            DeviceState.LOST,
            DeviceState.ERROR,
            DeviceState.REMOVED
        ),
        DeviceState.STOPPED to setOf(
            DeviceState.PLAYING,
            DeviceState.BUFFERING,
            DeviceState.TRANSITIONING,
            DeviceState.CONNECTED,
            DeviceState.LOST,
            DeviceState.ERROR,
            DeviceState.REMOVED
        ),
        DeviceState.BUFFERING to setOf(
            DeviceState.PLAYING,
            DeviceState.PAUSED,
            DeviceState.STOPPED,
            DeviceState.TRANSITIONING,
            DeviceState.LOST,
            DeviceState.ERROR,
            DeviceState.REMOVED
        ),
        DeviceState.TRANSITIONING to setOf(
            DeviceState.PLAYING,
            DeviceState.PAUSED,
            DeviceState.STOPPED,
            DeviceState.BUFFERING,
            DeviceState.LOST,
            DeviceState.ERROR,
            DeviceState.REMOVED
        ),
        DeviceState.ERROR to setOf(
            DeviceState.CONNECTED,
            DeviceState.DISCOVERED,
            DeviceState.VALIDATED,
            DeviceState.LOST,
            DeviceState.REMOVED
        ),
        DeviceState.LOST to setOf(
            DeviceState.DISCOVERED,
            DeviceState.VALIDATED,
            DeviceState.CONNECTED,
            DeviceState.REMOVED
        ),
        DeviceState.REMOVED to setOf(
            DeviceState.DISCOVERED, 
            DeviceState.VALIDATED
        )
    )
    
    // 设备状态表
    private val deviceStateMap = ConcurrentHashMap<String, DeviceStateInfo>()
    
    // 监听器列表
    private val listeners = mutableListOf<StateChangeListener>()
    
    // 主线程Handler用于监听器回调
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 设备活跃检查定时器
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    // 设备超时时间
    private val DEVICE_TIMEOUT_MS = 60 * 1000L // 1分钟无更新视为丢失
    
    init {
        // 启动设备活跃状态检查
        startDeviceCheckTimer()
        Log.d(TAG, "设备状态管理器已初始化")
    }
    
    /**
     * 添加状态变化监听器
     */
    @Synchronized
    fun addListener(listener: StateChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            Log.d(TAG, "已添加状态监听器: ${listener.javaClass.simpleName}")
            
            // 立即通知当前活跃设备列表
            val activeDevices = getActiveDevices()
            if (activeDevices.isNotEmpty()) {
                notifyDeviceListChanged(activeDevices)
            }
        }
    }
    
    /**
     * 移除状态变化监听器
     */
    @Synchronized
    fun removeListener(listener: StateChangeListener) {
        listeners.remove(listener)
        Log.d(TAG, "已移除状态监听器: ${listener.javaClass.simpleName}")
    }
    
    /**
     * 注册新设备
     * @return 如果是新设备返回true，否则返回false
     */
    fun registerDevice(deviceId: String, device: RemoteDevice): Boolean {
        val info = deviceStateMap[deviceId]
        val isNewDevice = info == null
        
        if (isNewDevice) {
            // 新设备，创建状态信息
            val stateInfo = DeviceStateInfo(
                device = device,
                state = DeviceState.DISCOVERED,
                lastStateChangeTime = System.currentTimeMillis(),
                lastSeenTimestamp = System.currentTimeMillis()
            )
            deviceStateMap[deviceId] = stateInfo
            
            Log.d(TAG, "注册新设备: ${device.details.friendlyName}")
            
            // 通知监听器
            notifyStateChanged(deviceId, DeviceState.UNKNOWN, DeviceState.DISCOVERED, stateInfo)
            notifyDeviceListChanged(getActiveDevices())
        } else {
            // 更新设备
            val stateInfo = deviceStateMap[deviceId]!!
            stateInfo.device = device
            stateInfo.lastSeenTimestamp = System.currentTimeMillis()
            
            if (stateInfo.state == DeviceState.LOST) {
                // 如果设备之前标记为丢失，则恢复为已发现状态
                updateDeviceState(deviceId, DeviceState.DISCOVERED)
            }
        }
        
        return isNewDevice
    }
    
    /**
     * 更新设备状态
     * @return 如果状态变化返回true，否则返回false
     */
    fun updateDeviceState(deviceId: String, newState: DeviceState): Boolean {
        val info = deviceStateMap[deviceId]
        
        if (info == null) {
            Log.w(TAG, "尝试更新未知设备的状态: $deviceId")
            return false
        }
        
        val oldState = info.state
        
        // 检查状态转换是否有效
        if (oldState != newState && !isValidStateTransition(oldState, newState)) {
            Log.w(TAG, "无效的状态转换: $deviceId, $oldState -> $newState")
            
            // 如果出现特殊情况，可以在这里处理例外
            if (oldState == DeviceState.CONNECTED && newState == DeviceState.CONNECTED) {
                // 连接状态刷新，更新最后活跃时间但不触发状态变更
                info.lastSeenTimestamp = System.currentTimeMillis()
                return false
            }
            
            return false
        }
        
        if (oldState != newState) {
            // 更新状态
            info.state = newState
            info.lastStateChangeTime = System.currentTimeMillis()
            info.lastSeenTimestamp = System.currentTimeMillis()
            
            Log.d(TAG, "设备状态变化: $deviceId, $oldState -> $newState")
            
            // 通知监听器
            notifyStateChanged(deviceId, oldState, newState, info)
            
            // 如果连接状态发生重大变化，通知设备列表变更
            if (isConnectionStateChange(oldState, newState)) {
                notifyDeviceListChanged(getActiveDevices())
            }
            
            return true
        } else {
            // 如果状态未变化，仍然更新活跃时间
            info.lastSeenTimestamp = System.currentTimeMillis()
            return false
        }
    }
    
    /**
     * 更新设备活跃状态
     */
    fun updateDeviceActivity(deviceId: String) {
        val info = deviceStateMap[deviceId] ?: return
        info.lastSeenTimestamp = System.currentTimeMillis()
    }
    
    /**
     * 更新媒体信息
     */
    fun updateMediaInfo(deviceId: String, url: String?, positionMs: Long, durationMs: Long) {
        val info = deviceStateMap[deviceId] ?: return
        
        info.mediaUrl = url
        info.positionMs = positionMs
        info.durationMs = durationMs
        info.lastSeenTimestamp = System.currentTimeMillis()
    }
    
    /**
     * 更新播放位置
     */
    fun updatePosition(deviceId: String, positionMs: Long) {
        val info = deviceStateMap[deviceId] ?: return
        
        info.positionMs = positionMs
        info.lastSeenTimestamp = System.currentTimeMillis()
    }
    
    /**
     * 更新音量
     */
    fun updateVolume(deviceId: String, volume: Int, isMute: Boolean) {
        val info = deviceStateMap[deviceId] ?: return
        
        info.volume = volume
        info.isMute = isMute
        info.lastSeenTimestamp = System.currentTimeMillis()
    }
    
    /**
     * 记录错误
     */
    fun recordError(deviceId: String, errorMessage: String) {
        val info = deviceStateMap[deviceId] ?: return
        
        info.errorMessage = errorMessage
        updateDeviceState(deviceId, DeviceState.ERROR)
    }
    
    /**
     * 增加连接尝试次数
     */
    fun incrementConnectionAttempts(deviceId: String) {
        val info = deviceStateMap[deviceId] ?: return
        info.connectionAttempts++
    }
    
    /**
     * 重置连接尝试次数
     */
    fun resetConnectionAttempts(deviceId: String) {
        val info = deviceStateMap[deviceId] ?: return
        info.connectionAttempts = 0
    }
    
    /**
     * 移除设备
     */
    fun removeDevice(deviceId: String) {
        val info = deviceStateMap[deviceId] ?: return
        
        updateDeviceState(deviceId, DeviceState.REMOVED)
        
        // 清理后从Map中删除
        deviceStateMap.remove(deviceId)
        
        Log.d(TAG, "设备已移除: $deviceId")
        
        // 通知设备列表变更
        notifyDeviceListChanged(getActiveDevices())
    }
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(deviceId: String): DeviceState {
        return deviceStateMap[deviceId]?.state ?: DeviceState.UNKNOWN
    }
    
    /**
     * 获取状态信息
     */
    fun getStateInfo(deviceId: String): DeviceStateInfo? {
        return deviceStateMap[deviceId]
    }
    
    /**
     * 检查设备是否存在
     */
    fun hasDevice(deviceId: String): Boolean {
        return deviceStateMap.containsKey(deviceId)
    }
    
    /**
     * 获取活跃设备列表
     */
    fun getActiveDevices(): List<DeviceStateInfo> {
        val currentTime = System.currentTimeMillis()
        
        return deviceStateMap.values
            .filter { info -> 
                currentTime - info.lastSeenTimestamp < DEVICE_TIMEOUT_MS &&
                info.state != DeviceState.REMOVED &&
                info.state != DeviceState.LOST
            }
            .toList()
    }
    
    /**
     * 获取所有设备列表
     */
    fun getAllDevices(): List<DeviceStateInfo> {
        return deviceStateMap.values.toList()
    }
    
    /**
     * 检查设备状态
     */
    private fun checkDeviceStatus() {
        val currentTime = System.currentTimeMillis()
        val deadDevices = mutableListOf<String>()
        
        // 检查所有设备的活跃状态
        deviceStateMap.forEach { (deviceId, info) ->
            val timeSinceLastSeen = currentTime - info.lastSeenTimestamp
            
            // 超时且不是已移除的设备标记为丢失
            if (timeSinceLastSeen > DEVICE_TIMEOUT_MS && 
                info.state != DeviceState.LOST && 
                info.state != DeviceState.REMOVED) {
                
                Log.w(TAG, "设备已超时: $deviceId, 最后活跃: ${timeSinceLastSeen/1000}秒前")
                updateDeviceState(deviceId, DeviceState.LOST)
                
                // 超过5分钟的设备，从列表中删除
                if (timeSinceLastSeen > 5 * 60 * 1000) {
                    deadDevices.add(deviceId)
                }
            }
        }
        
        // 移除长时间不活跃的设备
        deadDevices.forEach { deviceId ->
            Log.d(TAG, "移除长期不活跃设备: $deviceId")
            deviceStateMap.remove(deviceId)
        }
        
        // 如果有设备被移除，通知列表变更
        if (deadDevices.isNotEmpty()) {
            notifyDeviceListChanged(getActiveDevices())
        }
    }
    
    /**
     * 启动设备检查定时器
     */
    private fun startDeviceCheckTimer() {
        executor.scheduleAtFixedRate(
            { checkDeviceStatus() },
            30, 30, TimeUnit.SECONDS
        )
    }
    
    /**
     * 检查状态转换是否有效
     */
    private fun isValidStateTransition(oldState: DeviceState, newState: DeviceState): Boolean {
        if (oldState == newState) return true
        return validStateTransitions[oldState]?.contains(newState) ?: false
    }
    
    /**
     * 判断是否为连接状态变化
     * 连接状态变化会触发设备列表更新
     */
    private fun isConnectionStateChange(oldState: DeviceState, newState: DeviceState): Boolean {
        val connectionStates = setOf(
            DeviceState.CONNECTED, 
            DeviceState.PLAYING,
            DeviceState.DISCOVERED,
            DeviceState.VALIDATED,
            DeviceState.LOST,
            DeviceState.REMOVED
        )
        
        return (oldState in connectionStates || newState in connectionStates) &&
               oldState != newState
    }
    
    /**
     * 通知状态变化
     */
    private fun notifyStateChanged(
        deviceId: String,
        oldState: DeviceState,
        newState: DeviceState,
        info: DeviceStateInfo
    ) {
        if (listeners.isEmpty()) return
        
        mainHandler.post {
            listeners.forEach { listener ->
                try {
                    listener.onDeviceStateChanged(deviceId, oldState, newState, info)
                } catch (e: Exception) {
                    Log.e(TAG, "通知状态变化异常", e)
                }
            }
        }
    }
    
    /**
     * 通知设备列表变化
     */
    private fun notifyDeviceListChanged(devices: List<DeviceStateInfo>) {
        if (listeners.isEmpty()) return
        
        mainHandler.post {
            listeners.forEach { listener ->
                try {
                    listener.onDeviceListChanged(devices)
                } catch (e: Exception) {
                    Log.e(TAG, "通知设备列表变化异常", e)
                }
            }
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        // 停止定时任务
        executor.shutdown()
        
        // 清空设备状态表
        deviceStateMap.clear()
        
        // 清空监听器
        listeners.clear()
        
        Log.d(TAG, "设备状态管理器已释放")
    }
    
    companion object {
        @Volatile
        private var INSTANCE: DeviceStateManager? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(): DeviceStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceStateManager().also { INSTANCE = it }
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