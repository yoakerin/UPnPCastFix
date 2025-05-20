package com.yinnho.upnpcast.controller

import com.yinnho.upnpcast.api.PlaybackState
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 控制器适配器接口
 * 用于适配不同版本的控制器API
 */
interface ControllerAdapter {
    
    /**
     * 获取关联的设备
     */
    val device: RemoteDevice
    
    /**
     * 设置播放URL
     */
    suspend fun setMediaUrl(url: String, metadata: String = ""): Boolean
    
    /**
     * 开始播放
     */
    suspend fun play(): Boolean
    
    /**
     * 暂停播放
     */
    suspend fun pause(): Boolean
    
    /**
     * 停止播放
     */
    suspend fun stop(): Boolean
    
    /**
     * 跳转到指定位置
     */
    suspend fun seekTo(position: Long): Boolean
    
    /**
     * 设置音量
     */
    suspend fun setVolume(volume: UInt): Boolean
    
    /**
     * 静音或取消静音
     */
    suspend fun setMute(mute: Boolean): Boolean
    
    /**
     * 获取播放状态
     */
    suspend fun getPlaybackState(): PlaybackState
    
    /**
     * 获取当前播放位置
     */
    suspend fun getCurrentPosition(): Long
    
    /**
     * 获取媒体时长
     */
    suspend fun getDuration(): Long
    
    /**
     * 获取当前音量
     */
    suspend fun getVolume(): UInt
    
    /**
     * 获取静音状态
     */
    suspend fun getMute(): Boolean
    
    /**
     * 设置播放状态监听器
     */
    fun setPlaybackStateListener(listener: (PlaybackState) -> Unit)
    
    /**
     * 设置位置变化监听器
     */
    fun setPositionChangeListener(listener: (position: Long, duration: Long) -> Unit)
    
    /**
     * 释放资源
     */
    fun release()
}

/**
 * 控制器适配器实现类
 * 将DlnaController适配到统一的ControllerAdapter接口
 */
class ControllerAdapterImpl(
    private val controller: DlnaController
) : ControllerAdapter {
    
    private val TAG = "ControllerAdapterImpl"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var positionChangeListener: ((position: Long, duration: Long) -> Unit)? = null
    
    override val device: RemoteDevice
        get() = controller.device
    
    override suspend fun setMediaUrl(url: String, metadata: String): Boolean {
        return controller.setAVTransportURI(url, metadata)
    }
    
    override suspend fun play(): Boolean {
        return controller.play()
    }
    
    override suspend fun pause(): Boolean {
        return controller.pause()
    }
    
    override suspend fun stop(): Boolean {
        return controller.stop()
    }
    
    override suspend fun seekTo(position: Long): Boolean {
        return controller.seekTo(position)
    }
    
    override suspend fun setVolume(volume: UInt): Boolean {
        return controller.setVolume(volume)
    }
    
    override suspend fun setMute(mute: Boolean): Boolean {
        return controller.setMute(mute)
    }
    
    override suspend fun getPlaybackState(): PlaybackState {
        return try {
            val transportInfo = controller.getTransportInfo()
            when ((transportInfo as? Map<*, *>)?.get("currentTransportState")?.toString()?.lowercase()) {
                "playing" -> PlaybackState.PLAYING
                "paused_playback" -> PlaybackState.PAUSED
                "stopped" -> PlaybackState.STOPPED
                "transitioning" -> PlaybackState.TRANSITIONING
                "no_media_present" -> PlaybackState.IDLE
                else -> PlaybackState.IDLE
            }
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取播放状态失败", e)
            PlaybackState.ERROR
        }
    }
    
    override suspend fun getCurrentPosition(): Long {
        return try {
            (controller.getPositionInfo() as? Map<*, *>)?.let { info ->
                // 使用TimeUtils解析时间
                val timeString = info["relTime"]?.toString() ?: "00:00:00"
                TimeUtils.timeStringToMilliseconds(timeString)
            } ?: 0L
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取当前位置失败", e)
            0L
        }
    }
    
    override suspend fun getDuration(): Long {
        return try {
            (controller.getPositionInfo() as? Map<*, *>)?.let { info ->
                // 使用TimeUtils解析时间
                val timeString = info["trackDuration"]?.toString() ?: "00:00:00"
                TimeUtils.timeStringToMilliseconds(timeString)
            } ?: 0L
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取时长失败", e)
            0L
        }
    }
    
    override suspend fun getVolume(): UInt {
        return try {
            controller.getVolume() ?: 50u
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取音量失败", e)
            50u // 默认音量
        }
    }
    
    override suspend fun getMute(): Boolean {
        return try {
            controller.getMute() ?: false
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取静音状态失败", e)
            false
        }
    }
    
    override fun setPlaybackStateListener(listener: (PlaybackState) -> Unit) {
        // 使用字符串状态进行映射，避免不兼容的枚举比较
        controller.setPlaybackStateListener { dlnaPlaybackState ->
            // 将字符串状态映射到我们的PlaybackState枚举
            val state = when (dlnaPlaybackState.name) {
                "PLAYING" -> PlaybackState.PLAYING
                "PAUSED" -> PlaybackState.PAUSED
                "STOPPED" -> PlaybackState.STOPPED
                "TRANSITIONING" -> PlaybackState.TRANSITIONING
                "BUFFERING" -> PlaybackState.BUFFERING
                "COMPLETED" -> PlaybackState.COMPLETED
                "ERROR" -> PlaybackState.ERROR
                "IDLE" -> PlaybackState.IDLE
                else -> PlaybackState.IDLE // 默认状态
            }
            listener(state)
        }
    }
    
    override fun setPositionChangeListener(listener: (position: Long, duration: Long) -> Unit) {
        this.positionChangeListener = listener
        
        // 启动位置监听线程
        coroutineScope.launch {
            try {
                while (true) {
                    val position = getCurrentPosition()
                    val duration = getDuration()
                    withContext(Dispatchers.Main) {
                        positionChangeListener?.invoke(position, duration)
                    }
                    kotlinx.coroutines.delay(1000) // 1秒更新一次
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "位置监听错误", e)
            }
        }
    }
    
    override fun release() {
        positionChangeListener = null
    }
} 