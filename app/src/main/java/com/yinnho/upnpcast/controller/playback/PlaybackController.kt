package com.yinnho.upnpcast.controller.playback

import com.yinnho.upnpcast.api.PlaybackState
import com.yinnho.upnpcast.controller.device.DeviceInfoManager
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.utils.MediaMetadataUtils
import com.yinnho.upnpcast.utils.TimeUtils
import com.yinnho.upnpcast.utils.UrlUtils
import com.yinnho.upnpcast.utils.CoroutineErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 播放控制器
 * 负责媒体播放控制功能
 */
class PlaybackController(
    private val deviceInfoManager: DeviceInfoManager
) {
    companion object {
        private const val TAG = "PlaybackController"
        private const val DEFAULT_INSTANCE_ID = "0"
    }

    // 播放状态回调
    private var playbackStateListener: ((PlaybackState) -> Unit)? = null
    
    // 播放位置变化回调
    private var positionChangeListener: ((Long, Long) -> Unit)? = null

    /**
     * 设置播放状态监听器
     */
    fun setPlaybackStateListener(listener: (PlaybackState) -> Unit) {
        this.playbackStateListener = listener
    }
    
    /**
     * 设置播放位置变化监听器
     */
    fun setPositionChangeListener(listener: (position: Long, duration: Long) -> Unit) {
        this.positionChangeListener = listener
    }
    
    /**
     * 设置媒体URI
     */
    suspend fun setAVTransportURI(uri: String, metadata: String = ""): Boolean = 
        CoroutineErrorHandler.runWithDefault("设置媒体URI", false, Dispatchers.IO) {
            EnhancedThreadManager.d(TAG, "设置媒体URI: $uri")
            EnhancedThreadManager.d(TAG, "元数据: $metadata")
            
            val service = deviceInfoManager.avTransportService 
                ?: throw IllegalStateException("AVTransport服务不可用")
                
            service.setAVTransportURI(DEFAULT_INSTANCE_ID, uri, metadata)
            true
        }
    
    /**
     * 开始播放
     */
    suspend fun play(): Boolean = 
        CoroutineErrorHandler.runWithDefault("开始播放", false, Dispatchers.IO) {
            EnhancedThreadManager.d(TAG, "开始播放")
            
            val service = deviceInfoManager.avTransportService 
                ?: throw IllegalStateException("AVTransport服务不可用")
                
            service.play(DEFAULT_INSTANCE_ID, "1")
            
            // 通知状态改变
            playbackStateListener?.invoke(PlaybackState.PLAYING)
            
            true
        }
    
    /**
     * 暂停播放
     */
    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        try {
            EnhancedThreadManager.d(TAG, "暂停播放")
            
            val service = deviceInfoManager.avTransportService 
                ?: throw IllegalStateException("AVTransport服务不可用")
                
            service.pause(DEFAULT_INSTANCE_ID)
            
            // 通知状态改变
            playbackStateListener?.invoke(PlaybackState.PAUSED)
            
            true
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "暂停播放失败", e)
            false
        }
    }
    
    /**
     * 停止播放
     */
    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        try {
            EnhancedThreadManager.d(TAG, "停止播放")
            
            val service = deviceInfoManager.avTransportService 
                ?: throw IllegalStateException("AVTransport服务不可用")
                
            service.stop(DEFAULT_INSTANCE_ID)
            
            // 通知状态改变
            playbackStateListener?.invoke(PlaybackState.STOPPED)
            
            true
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "停止播放失败", e)
            false
        }
    }
    
    /**
     * 跳转到指定时间
     * @param position 格式为 "HH:MM:SS"
     */
    suspend fun seek(position: String): Boolean = withContext(Dispatchers.IO) {
        try {
            EnhancedThreadManager.d(TAG, "跳转到时间: $position")
            
            val service = deviceInfoManager.avTransportService 
                ?: throw IllegalStateException("AVTransport服务不可用")
                
            service.seek(DEFAULT_INSTANCE_ID, position)
            true
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "跳转失败", e)
            false
        }
    }
    
    /**
     * 跳转到指定毫秒位置
     * @param positionMs 毫秒
     */
    suspend fun seekTo(positionMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            if (positionMs <= 0) {
                EnhancedThreadManager.d(TAG, "播放位置为0或负数，不设置位置")
                return@withContext true
            }
            
            // 使用TimeUtils转换位置为hh:mm:ss格式
            val timeString = TimeUtils.millisecondsToTimeString(positionMs)
            
            EnhancedThreadManager.d(TAG, "跳转到毫秒位置: $positionMs (格式化为: $timeString)")
            
            seek(timeString)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "跳转到毫秒位置失败", e)
            false
        }
    }
    
    /**
     * 获取当前播放位置信息
     */
    suspend fun getPositionInfo(): Any? = withContext(Dispatchers.IO) {
        try {
            val service = deviceInfoManager.avTransportService 
                ?: throw IllegalStateException("AVTransport服务不可用")
                
            service.getPositionInfo(DEFAULT_INSTANCE_ID)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取播放位置失败", e)
            null
        }
    }
    
    /**
     * 获取传输状态信息
     */
    suspend fun getTransportInfo(): Any? = withContext(Dispatchers.IO) {
        try {
            val service = deviceInfoManager.avTransportService 
                ?: throw IllegalStateException("AVTransport服务不可用")
                
            service.getTransportInfo(DEFAULT_INSTANCE_ID)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取传输状态失败", e)
            null
        }
    }
    
    /**
     * 播放媒体
     * 完整流程：停止当前播放 -> 设置媒体URI -> 开始播放 -> 设置播放位置
     */
    suspend fun playMedia(mediaUrl: String, title: String, episodeLabel: String = "", positionMs: Long = 0): Boolean = 
        CoroutineErrorHandler.runWithDefault("播放媒体", false, Dispatchers.IO) {
            EnhancedThreadManager.d(TAG, "开始播放媒体: $mediaUrl")
            EnhancedThreadManager.d(TAG, "标题: $title, 集数: $episodeLabel, 初始位置: ${positionMs}ms")
            
            // 使用UrlUtils处理URL
            val processedUrl = UrlUtils.preprocessMediaUrl(
                mediaUrl,
                deviceInfoManager.isXiaomiDevice
            )
            
            // 使用MediaMetadataUtils构建元数据
            val metadata = MediaMetadataUtils.buildMetadata(
                processedUrl,
                title,
                episodeLabel,
                deviceInfoManager.isXiaomiDevice
            )
            
            // 1. 先停止当前播放
            val stopSuccess = stop()
            if (!stopSuccess) {
                EnhancedThreadManager.w(TAG, "停止当前播放失败，但将继续尝试设置媒体")
            }
            
            // 短暂延迟，确保Stop命令被处理
            kotlinx.coroutines.delay(100)
            
            // 2. 设置媒体URI
            val setURISuccess = setAVTransportURI(processedUrl, metadata)
            if (!setURISuccess) {
                EnhancedThreadManager.e(TAG, "设置媒体URI失败")
                return@runWithDefault false
            }
            
            // 短暂延迟，确保SetAVTransportURI命令被处理
            kotlinx.coroutines.delay(100)
            
            // 3. 开始播放
            val playSuccess = play()
            if (!playSuccess) {
                EnhancedThreadManager.e(TAG, "开始播放失败")
                return@runWithDefault false
            }
            
            // 4. 如果需要，设置播放位置
            if (positionMs > 0) {
                EnhancedThreadManager.d(TAG, "需要设置播放位置: ${positionMs}ms，将在3秒后执行")
                // 延迟几秒再设置位置，确保播放开始
                kotlinx.coroutines.delay(3000)
                
                CoroutineErrorHandler.runSafely("设置播放位置") {
                    seekTo(positionMs)
                }
            }
            
            true
        }

    /**
     * 释放资源
     */
    fun release() {
        try {
            EnhancedThreadManager.d(TAG, "释放PlaybackController资源")
            
            // 移除监听器
            playbackStateListener = null
            positionChangeListener = null
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "释放PlaybackController资源失败", e)
        }
    }
} 