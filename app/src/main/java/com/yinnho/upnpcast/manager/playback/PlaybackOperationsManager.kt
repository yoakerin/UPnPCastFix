package com.yinnho.upnpcast.manager.playback

import com.yinnho.upnpcast.DLNAErrorType
import com.yinnho.upnpcast.DLNAException
import com.yinnho.upnpcast.controller.DlnaController
import com.yinnho.upnpcast.core.EnhancedThreadManager

/**
 * 播放操作管理器
 * 负责处理播放相关的操作
 */
class PlaybackOperationsManager {
    companion object {
        private const val TAG = "PlaybackOperationsManager"
    }
    
    // 错误监听器
    private var errorListener: ((DLNAException) -> Unit)? = null
    
    /**
     * 设置错误监听器
     */
    fun setErrorListener(listener: ((DLNAException) -> Unit)?) {
        this.errorListener = listener
    }
    
    /**
     * 播放媒体
     */
    fun playMedia(
        controller: DlnaController, 
        mediaUrl: String, 
        title: String, 
        episodeLabel: String = "", 
        positionMs: Long = 0
    ) {
        EnhancedThreadManager.executeTask {
            try {
                EnhancedThreadManager.d(TAG, "执行播放媒体: $mediaUrl")
                val result = controller.playMediaSync(mediaUrl, title, episodeLabel, positionMs)
                
                if (result) {
                    EnhancedThreadManager.d(TAG, "媒体播放成功")
                } else {
                    EnhancedThreadManager.e(TAG, "媒体播放失败")
                    errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "媒体播放失败"))
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "播放媒体异常: ${e.message}", e)
                errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "播放异常: ${e.message}", e))
            }
        }
    }
    
    /**
     * 暂停播放
     */
    fun pausePlayback(controller: DlnaController) {
        EnhancedThreadManager.executeTask {
            try {
                EnhancedThreadManager.d(TAG, "执行暂停播放")
                val result = controller.pauseSync()
                
                if (result) {
                    EnhancedThreadManager.d(TAG, "暂停成功")
                } else {
                    EnhancedThreadManager.e(TAG, "暂停失败")
                    errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "暂停失败"))
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "暂停异常: ${e.message}", e)
                errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "暂停异常: ${e.message}", e))
            }
        }
    }
    
    /**
     * 恢复播放
     */
    fun resumePlayback(controller: DlnaController) {
        EnhancedThreadManager.executeTask {
            try {
                EnhancedThreadManager.d(TAG, "执行恢复播放")
                val result = controller.playSync()
                
                if (result) {
                    EnhancedThreadManager.d(TAG, "恢复播放成功")
                } else {
                    EnhancedThreadManager.e(TAG, "恢复播放失败")
                    errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "恢复播放失败"))
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "恢复播放异常: ${e.message}", e)
                errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "恢复播放异常: ${e.message}", e))
            }
        }
    }
    
    /**
     * 停止播放
     */
    fun stopPlayback(controller: DlnaController) {
        EnhancedThreadManager.executeTask {
            try {
                EnhancedThreadManager.d(TAG, "执行停止播放")
                val result = controller.stopSync()
                
                if (result) {
                    EnhancedThreadManager.d(TAG, "停止播放成功")
                } else {
                    EnhancedThreadManager.e(TAG, "停止播放失败")
                    errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "停止播放失败"))
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "停止播放异常: ${e.message}", e)
                errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "停止播放异常: ${e.message}", e))
            }
        }
    }
    
    /**
     * 跳转到指定位置
     */
    fun seekTo(controller: DlnaController, positionMs: Long) {
        EnhancedThreadManager.executeTask {
            try {
                EnhancedThreadManager.d(TAG, "执行跳转到位置: ${positionMs}ms")
                val result = controller.seekToSync(positionMs)
                
                if (result) {
                    EnhancedThreadManager.d(TAG, "跳转成功")
                } else {
                    EnhancedThreadManager.e(TAG, "跳转失败")
                    errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "跳转失败"))
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "跳转异常: ${e.message}", e)
                errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "跳转异常: ${e.message}", e))
            }
        }
    }
    
    /**
     * 尝试停止播放但忽略错误
     */
    fun safeStopPlayback(controller: DlnaController) {
        EnhancedThreadManager.executeTask {
            try {
                controller.stopSync()
            } catch (e: Exception) {
                // 忽略异常
                EnhancedThreadManager.d(TAG, "安全停止播放时忽略异常: ${e.message}")
            }
        }
    }
} 