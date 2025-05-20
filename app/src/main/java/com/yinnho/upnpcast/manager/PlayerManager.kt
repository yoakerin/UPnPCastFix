package com.yinnho.upnpcast.manager

import com.yinnho.upnpcast.DLNAException
import com.yinnho.upnpcast.DLNAErrorType
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.interfaces.PlaybackState
import com.yinnho.upnpcast.manager.controller.ControllerManager
import com.yinnho.upnpcast.model.RemoteDevice

/**
 * 播放管理器
 * 负责处理DLNA媒体播放和控制功能
 */
class PlayerManager {
    private val TAG = "PlayerManager"
    
    // 控制器管理器
    private val controllerManager = ControllerManager()
    
    // 监听器
    private var playbackStateListener: ((String) -> Unit)? = null
    private var positionChangeListener: ((Long, Long) -> Unit)? = null
    private var errorListener: ((DLNAException) -> Unit)? = null
    
    init {
        controllerManager.setErrorListener { error -> errorListener?.invoke(error) }
    }
    
    /**
     * 设置播放状态监听器
     */
    fun setPlaybackStateListener(listener: ((String) -> Unit)?) {
        this.playbackStateListener = listener
    }
    
    /**
     * 设置位置变化监听器
     */
    fun setPositionChangeListener(listener: ((Long, Long) -> Unit)?) {
        this.positionChangeListener = listener
    }
    
    /**
     * 设置错误监听器
     */
    fun setErrorListener(listener: ((DLNAException) -> Unit)?) {
        this.errorListener = listener
    }
    
    /**
     * 添加设备控制器
     */
    fun addDeviceController(deviceId: String, device: RemoteDevice) {
        try {
            controllerManager.addController(deviceId, device)
        } catch (e: Exception) {
            handleError(e, "添加设备控制器失败")
        }
    }
    
    /**
     * 播放媒体
     */
    fun playMedia(deviceId: String, mediaUrl: String, title: String, episodeLabel: String = "", positionMs: Long = 0) {
        try {
            val controller = getControllerOrThrow(deviceId)
            
            // 设置监听器
            controller.setPlaybackStateListener { state ->
                val stateString = when(state) {
                    PlaybackState.PLAYING -> "PLAYING"
                    PlaybackState.PAUSED -> "PAUSED"
                    PlaybackState.STOPPED -> "STOPPED"
                    PlaybackState.TRANSITIONING -> "TRANSITIONING"
                    else -> "UNKNOWN"
                }
                playbackStateListener?.invoke(stateString)
            }
            
            controller.setPositionChangeListener { position, duration ->
                positionChangeListener?.invoke(position, duration)
            }
            
            // 执行播放
            controller.playMediaSync(mediaUrl, title, episodeLabel, positionMs)
        } catch (e: Exception) {
            handleError(e, "播放媒体失败")
        }
    }
    
    /**
     * 暂停播放
     */
    fun pausePlayback(deviceId: String) {
        try {
            getControllerOrThrow(deviceId).pauseSync()
        } catch (e: Exception) {
            handleError(e, "暂停播放失败")
        }
    }
    
    /**
     * 恢复播放
     */
    fun resumePlayback(deviceId: String) {
        try {
            getControllerOrThrow(deviceId).playSync()
        } catch (e: Exception) {
            handleError(e, "恢复播放失败")
        }
    }
    
    /**
     * 停止播放
     */
    fun stopPlayback(deviceId: String) {
        try {
            getControllerOrThrow(deviceId).stopSync()
        } catch (e: Exception) {
            handleError(e, "停止播放失败")
        }
    }
    
    /**
     * 跳转到指定位置
     */
    fun seekTo(deviceId: String, positionMs: Long) {
        try {
            getControllerOrThrow(deviceId).seekToSync(positionMs)
        } catch (e: Exception) {
            handleError(e, "跳转失败")
        }
    }
    
    /**
     * 设置音量
     */
    fun setVolume(deviceId: String, volume: Int) {
        try {
            getControllerOrThrow(deviceId).setVolumeSync(volume.toUInt())
        } catch (e: Exception) {
            handleError(e, "设置音量失败")
        }
    }
    
    /**
     * 设置静音状态
     */
    fun setMute(deviceId: String, mute: Boolean) {
        try {
            getControllerOrThrow(deviceId).setMuteSync(mute)
        } catch (e: Exception) {
            handleError(e, "设置静音状态失败")
        }
    }
    
    /**
     * 清除控制器缓存
     */
    fun clearControllerCache() {
        controllerManager.clearAllControllers()
    }
    
    /**
     * 获取控制器或抛出异常
     */
    private fun getControllerOrThrow(deviceId: String) = 
        controllerManager.getController(deviceId, true)
            ?: throw DLNAException(DLNAErrorType.DEVICE_ERROR, "设备不存在: $deviceId")
    
    /**
     * 统一错误处理
     */
    private fun handleError(e: Exception, message: String) {
        EnhancedThreadManager.e(TAG, "$message: ${e.message}", e)
        val error = if (e is DLNAException) e else 
            DLNAException(DLNAErrorType.PLAYBACK_ERROR, "$message: ${e.message}", e)
        errorListener?.invoke(error)
        throw error
    }
    
    /**
     * 释放资源
     */
    fun release() {
        playbackStateListener = null
        positionChangeListener = null
        errorListener = null
        controllerManager.release()
    }
} 