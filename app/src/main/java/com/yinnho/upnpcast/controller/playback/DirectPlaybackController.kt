package com.yinnho.upnpcast.controller.playback

import com.yinnho.upnpcast.controller.device.DeviceInfoManager
import com.yinnho.upnpcast.controller.soap.SoapTransportExecutor
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.utils.MediaMetadataUtils
import com.yinnho.upnpcast.utils.NotificationUtils
import com.yinnho.upnpcast.utils.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 直接播放控制器
 * 精简版：使用SoapTransportExecutor实现的播放控制
 */
class DirectPlaybackController(
    private val deviceInfoManager: DeviceInfoManager,
    private val soapExecutor: SoapTransportExecutor
) {
    companion object {
        private const val TAG = "DirectPlaybackController"
    }
    
    /**
     * 使用直接SOAP通信播放媒体（完整流程）
     */
    suspend fun playMediaDirect(mediaUrl: String, title: String, episodeLabel: String = "", positionMs: Long = 0): Boolean = withContext(Dispatchers.IO) {
        try {
            EnhancedThreadManager.d(TAG, "开始直接播放媒体: $mediaUrl")
            
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
            val stopSuccess = soapExecutor.stop()
            if (!stopSuccess) {
                EnhancedThreadManager.w(TAG, "停止当前播放失败，但将继续尝试设置媒体")
            }
            
            // 短暂延迟，确保Stop命令被处理
            kotlinx.coroutines.delay(100)
            
            // 2. 设置媒体URI
            val setURISuccess = soapExecutor.setAVTransportURI(processedUrl, metadata)
            if (!setURISuccess) {
                EnhancedThreadManager.e(TAG, "设置媒体URI失败")
                NotificationUtils.notifyDLNAActionStatus(deviceInfoManager.deviceId, "ERROR_SET_URI_FAILED")
                NotificationUtils.sendErrorBroadcast("设置媒体URI失败")
                return@withContext false
            }
            
            // 短暂延迟，确保SetAVTransportURI命令被处理
            kotlinx.coroutines.delay(100)
            
            // 3. 开始播放
            val playSuccess = soapExecutor.play()
            if (!playSuccess) {
                EnhancedThreadManager.e(TAG, "开始播放失败")
                NotificationUtils.notifyDLNAActionStatus(deviceInfoManager.deviceId, "ERROR_PLAY_FAILED")
                NotificationUtils.sendErrorBroadcast("播放命令失败")
                return@withContext false
            }
            
            NotificationUtils.notifyDLNAActionStatus(deviceInfoManager.deviceId, "PLAYING")
            
            // 4. 如果需要，设置播放位置
            if (positionMs > 0) {
                EnhancedThreadManager.d(TAG, "需要设置播放位置: ${positionMs}ms，将在3秒后执行")
                // 延迟几秒再设置位置，确保播放开始
                kotlinx.coroutines.delay(3000)
                
                try {
                    soapExecutor.seek(positionMs)
                } catch (e: Exception) {
                    EnhancedThreadManager.e(TAG, "设置播放位置失败: ${e.message}")
                }
            }
            
            return@withContext true
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "直接播放媒体失败: ${e.message}", e)
            NotificationUtils.notifyDLNAActionStatus(deviceInfoManager.deviceId, "ERROR")
            NotificationUtils.sendErrorBroadcast(e.message ?: "未知错误")
            return@withContext false
        }
    }
} 