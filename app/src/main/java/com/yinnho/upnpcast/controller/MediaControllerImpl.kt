package com.yinnho.upnpcast.controller

import com.yinnho.upnpcast.interfaces.MediaController
import com.yinnho.upnpcast.interfaces.PlaybackState as IPlaybackState
import com.yinnho.upnpcast.api.PlaybackState
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * MediaController接口的实现类
 * 直接使用DlnaController，减少适配层级
 */
class MediaControllerImpl(
    private val controller: DlnaController
) : MediaController {
    
    private val TAG = "MediaControllerImpl"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var errorListener: ((Int, String) -> Unit)? = null
    private var positionChangeListener: ((position: Long, duration: Long) -> Unit)? = null
    
    override val device: RemoteDevice
        get() = controller.device
    
    override fun playMedia(url: String, metadata: String): Boolean = runBlocking {
        try {
            val result = controller.setAVTransportURI(url, metadata)
            if (result) {
                controller.play()
            } else {
                false
            }
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "播放媒体失败", e)
            errorListener?.invoke(ERROR_PLAYBACK, "播放媒体失败: ${e.message}")
            false
        }
    }
    
    override fun pause(): Boolean = runBlocking {
        try {
            controller.pause()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "暂停失败", e)
            errorListener?.invoke(ERROR_CONTROL, "暂停失败: ${e.message}")
            false
        }
    }
    
    override fun resume(): Boolean = runBlocking {
        try {
            controller.play()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "恢复播放失败", e)
            errorListener?.invoke(ERROR_CONTROL, "恢复播放失败: ${e.message}")
            false
        }
    }
    
    override fun stop(): Boolean = runBlocking {
        try {
            controller.stop()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "停止失败", e)
            errorListener?.invoke(ERROR_CONTROL, "停止失败: ${e.message}")
            false
        }
    }
    
    override fun seekTo(positionMs: Long): Boolean = runBlocking {
        try {
            controller.seekTo(positionMs)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "跳转失败", e)
            errorListener?.invoke(ERROR_CONTROL, "跳转失败: ${e.message}")
            false
        }
    }
    
    override fun setVolume(volume: Int): Boolean = runBlocking {
        try {
            controller.setVolume(volume.toUInt())
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置音量失败", e)
            errorListener?.invoke(ERROR_CONTROL, "设置音量失败: ${e.message}")
            false
        }
    }
    
    override fun setMute(mute: Boolean): Boolean = runBlocking {
        try {
            controller.setMute(mute)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置静音失败", e)
            errorListener?.invoke(ERROR_CONTROL, "设置静音失败: ${e.message}")
            false
        }
    }
    
    override fun getCurrentPosition(): Long = runBlocking {
        try {
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
    
    override fun getDuration(): Long = runBlocking {
        try {
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
    
    override fun getVolume(): Int = runBlocking {
        try {
            controller.getVolume()?.toInt() ?: 50
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取音量失败", e)
            50 // 默认音量
        }
    }
    
    override fun getMute(): Boolean = runBlocking {
        try {
            controller.getMute() ?: false
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取静音状态失败", e)
            false
        }
    }
    
    override fun getPlaybackState(): IPlaybackState = runBlocking {
        try {
            // 将内部PlaybackState转换为接口PlaybackState
            val transportInfo = controller.getTransportInfo()
            val state = when ((transportInfo as? Map<*, *>)?.get("currentTransportState")?.toString()?.lowercase()) {
                "playing" -> PlaybackState.PLAYING
                "paused_playback" -> PlaybackState.PAUSED
                "stopped" -> PlaybackState.STOPPED
                "transitioning" -> PlaybackState.TRANSITIONING
                "no_media_present" -> PlaybackState.IDLE
                else -> PlaybackState.IDLE
            }
            
            // 转换为接口PlaybackState
            when (state) {
                PlaybackState.PLAYING -> IPlaybackState.PLAYING
                PlaybackState.PAUSED -> IPlaybackState.PAUSED
                PlaybackState.STOPPED -> IPlaybackState.STOPPED
                PlaybackState.TRANSITIONING -> IPlaybackState.TRANSITIONING
                PlaybackState.BUFFERING -> IPlaybackState.BUFFERING
                PlaybackState.COMPLETED -> IPlaybackState.COMPLETED
                PlaybackState.ERROR -> IPlaybackState.ERROR
                else -> IPlaybackState.IDLE
            }
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取播放状态失败", e)
            IPlaybackState.IDLE
        }
    }
    
    override fun setPlaybackStateListener(listener: (IPlaybackState) -> Unit) {
        controller.setPlaybackStateListener { dlnaState ->
            // 将内部PlaybackState转换为接口PlaybackState
            val interfaceState = when (dlnaState) {
                PlaybackState.PLAYING -> IPlaybackState.PLAYING
                PlaybackState.PAUSED -> IPlaybackState.PAUSED
                PlaybackState.STOPPED -> IPlaybackState.STOPPED
                PlaybackState.TRANSITIONING -> IPlaybackState.TRANSITIONING
                PlaybackState.BUFFERING -> IPlaybackState.BUFFERING
                PlaybackState.COMPLETED -> IPlaybackState.COMPLETED
                PlaybackState.ERROR -> IPlaybackState.ERROR
                else -> IPlaybackState.IDLE
            }
            listener(interfaceState)
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
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        positionChangeListener?.invoke(position, duration)
                    }
                    kotlinx.coroutines.delay(1000) // 1秒更新一次
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "位置监听错误", e)
            }
        }
    }
    
    override fun setErrorListener(listener: (errorCode: Int, errorMessage: String) -> Unit) {
        this.errorListener = listener
    }
    
    override fun release() {
        positionChangeListener = null
    }
    
    companion object {
        // 错误代码
        const val ERROR_CONNECTION = 1001
        const val ERROR_PLAYBACK = 2001
        const val ERROR_CONTROL = 3001
        const val ERROR_NETWORK = 4001
        const val ERROR_UNKNOWN = 9999
    }
} 