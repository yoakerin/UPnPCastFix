package com.yinnho.upnpcast.manager.callback

import com.yinnho.upnpcast.CastListener
import com.yinnho.upnpcast.DLNAException
import com.yinnho.upnpcast.RemoteDevice
import com.yinnho.upnpcast.api.PlaybackState
import com.yinnho.upnpcast.core.EnhancedThreadManager

/**
 * 回调管理器
 * 负责管理各类监听器和事件转发
 */
class CallbackManager {
    companion object {
        private const val TAG = "CallbackManager"
    }
    
    // 投屏监听器
    private var castListener: CastListener? = null
    
    // 播放状态监听器
    interface PlaybackStateListener {
        /**
         * 播放状态变化回调
         */
        fun onPlaybackStateChanged(state: String)
        
        /**
         * 播放位置变化回调
         */
        fun onPositionChanged(positionMs: Long, durationMs: Long)
    }
    
    // 播放状态监听器
    private var playbackStateListener: PlaybackStateListener? = null
    
    // 设备发现监听器
    interface DeviceFoundListener {
        fun onDeviceFound(device: com.yinnho.upnpcast.model.RemoteDevice)
    }
    
    // 设备发现监听器
    private var deviceFoundListener: DeviceFoundListener? = null
    
    // 设备连接监听器
    interface DeviceConnectedListener {
        fun onDeviceConnected(device: com.yinnho.upnpcast.model.RemoteDevice)
    }
    
    // 设备连接监听器
    private var deviceConnectedListener: DeviceConnectedListener? = null
    
    // 设备断开监听器
    interface DeviceDisconnectedListener {
        fun onDeviceDisconnected()
    }
    
    // 设备断开监听器
    private var deviceDisconnectedListener: DeviceDisconnectedListener? = null
    
    // 错误监听器
    interface ErrorListener {
        fun onError(error: DLNAException)
    }
    
    // 错误监听器
    private var errorListener: ErrorListener? = null
    
    /**
     * 设置投屏监听器
     */
    fun setCastListener(listener: CastListener?) {
        this.castListener = listener
        EnhancedThreadManager.d(TAG, "设置投屏监听器: ${listener != null}")
    }
    
    /**
     * 设置播放状态监听器
     */
    fun setPlaybackStateListener(listener: PlaybackStateListener?) {
        this.playbackStateListener = listener
        EnhancedThreadManager.d(TAG, "设置播放状态监听器: ${listener != null}")
    }
    
    /**
     * 设置设备发现监听器
     */
    fun setDeviceFoundListener(listener: DeviceFoundListener?) {
        this.deviceFoundListener = listener
        EnhancedThreadManager.d(TAG, "设置设备发现监听器: ${listener != null}")
    }
    
    /**
     * 设置设备连接监听器
     */
    fun setDeviceConnectedListener(listener: DeviceConnectedListener?) {
        this.deviceConnectedListener = listener
        EnhancedThreadManager.d(TAG, "设置设备连接监听器: ${listener != null}")
    }
    
    /**
     * 设置设备断开监听器
     */
    fun setDeviceDisconnectedListener(listener: DeviceDisconnectedListener?) {
        this.deviceDisconnectedListener = listener
        EnhancedThreadManager.d(TAG, "设置设备断开监听器: ${listener != null}")
    }
    
    /**
     * 设置错误监听器
     */
    fun setErrorListener(listener: ErrorListener?) {
        this.errorListener = listener
        EnhancedThreadManager.d(TAG, "设置错误监听器: ${listener != null}")
    }
    
    /**
     * 通知设备列表更新
     */
    fun notifyDeviceListUpdated(devices: List<RemoteDevice>) {
        try {
            castListener?.onDeviceListUpdated(devices)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "通知设备列表更新出错: ${e.message}", e)
        }
    }
    
    /**
     * 通知设备已连接
     */
    fun notifyDeviceConnected(device: RemoteDevice) {
        try {
            castListener?.onConnected(device)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "通知设备已连接出错: ${e.message}", e)
        }
    }
    
    /**
     * 通知设备已断开
     */
    fun notifyDeviceDisconnected() {
        try {
            castListener?.onDisconnected()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "通知设备已断开出错: ${e.message}", e)
        }
    }
    
    /**
     * 通知错误
     */
    fun notifyError(error: DLNAException) {
        try {
            castListener?.onError(error)
            errorListener?.onError(error)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "通知错误出错: ${e.message}", e)
        }
    }
    
    /**
     * 通知播放状态变化
     */
    fun notifyPlaybackStateChanged(state: String) {
        try {
            playbackStateListener?.onPlaybackStateChanged(state)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "通知播放状态变化出错: ${e.message}", e)
        }
    }
    
    /**
     * 通知播放位置变化
     */
    fun notifyPositionChanged(positionMs: Long, durationMs: Long) {
        try {
            playbackStateListener?.onPositionChanged(positionMs, durationMs)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "通知播放位置变化出错: ${e.message}", e)
        }
    }
    
    /**
     * 转发播放状态
     */
    fun forwardPlaybackState(state: PlaybackState) {
        try {
            notifyPlaybackStateChanged(state.toString())
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "转发播放状态出错: ${e.message}", e)
        }
    }
    
    /**
     * 内部通知设备发现
     */
    fun notifyInternalDeviceFound(device: com.yinnho.upnpcast.model.RemoteDevice) {
        try {
            deviceFoundListener?.onDeviceFound(device)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "内部通知设备发现出错: ${e.message}", e)
        }
    }
    
    /**
     * 内部通知设备连接
     */
    fun notifyInternalDeviceConnected(device: com.yinnho.upnpcast.model.RemoteDevice) {
        try {
            deviceConnectedListener?.onDeviceConnected(device)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "内部通知设备连接出错: ${e.message}", e)
        }
    }
    
    /**
     * 内部通知设备断开
     */
    fun notifyInternalDeviceDisconnected() {
        try {
            deviceDisconnectedListener?.onDeviceDisconnected()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "内部通知设备断开出错: ${e.message}", e)
        }
    }
    
    /**
     * 清除所有监听器
     */
    fun clearAllListeners() {
        castListener = null
        playbackStateListener = null
        deviceFoundListener = null
        deviceConnectedListener = null
        deviceDisconnectedListener = null
        errorListener = null
        
        EnhancedThreadManager.d(TAG, "清除所有监听器")
    }
} 