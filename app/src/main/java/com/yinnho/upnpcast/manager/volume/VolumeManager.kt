package com.yinnho.upnpcast.manager.volume

import com.yinnho.upnpcast.DLNAErrorType
import com.yinnho.upnpcast.DLNAException
import com.yinnho.upnpcast.controller.DlnaController
import com.yinnho.upnpcast.core.EnhancedThreadManager
import kotlinx.coroutines.*

/**
 * 音量管理器
 * 负责处理音量和静音控制
 */
class VolumeManager {
    companion object {
        private const val TAG = "VolumeManager"
        
        // 音量限制
        private const val MIN_VOLUME = 0
        private const val MAX_VOLUME = 100
    }
    
    // 协程范围
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 错误监听器
    private var errorListener: ((DLNAException) -> Unit)? = null
    
    /**
     * 设置错误监听器
     */
    fun setErrorListener(listener: ((DLNAException) -> Unit)?) {
        this.errorListener = listener
    }
    
    /**
     * 设置音量
     */
    fun setVolume(controller: DlnaController, volume: Int) {
        coroutineScope.launch {
            try {
                // 检查音量范围
                val validVolume = validateVolume(volume)
                
                EnhancedThreadManager.d(TAG, "执行设置音量: $validVolume")
                val result = controller.setVolumeSync(validVolume.toUInt())
                
                if (result) {
                    EnhancedThreadManager.d(TAG, "设置音量成功: $validVolume")
                } else {
                    EnhancedThreadManager.e(TAG, "设置音量失败")
                    withContext(Dispatchers.Main) {
                        errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "设置音量失败"))
                    }
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "设置音量异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "设置音量异常: ${e.message}", e))
                }
            }
        }
    }
    
    /**
     * 设置静音状态
     */
    fun setMute(controller: DlnaController, mute: Boolean) {
        coroutineScope.launch {
            try {
                EnhancedThreadManager.d(TAG, "执行设置静音: $mute")
                val result = controller.setMuteSync(mute)
                
                if (result) {
                    EnhancedThreadManager.d(TAG, "设置静音成功: $mute")
                } else {
                    EnhancedThreadManager.e(TAG, "设置静音失败")
                    withContext(Dispatchers.Main) {
                        errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "设置静音失败"))
                    }
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "设置静音异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "设置静音异常: ${e.message}", e))
                }
            }
        }
    }
    
    /**
     * 增加音量
     */
    fun increaseVolume(controller: DlnaController, stepSize: Int = 5) {
        coroutineScope.launch {
            try {
                // 获取当前音量
                val currentVolume = getCurrentVolume(controller)
                
                if (currentVolume != null) {
                    // 计算新音量
                    val newVolume = validateVolume(currentVolume + stepSize)
                    
                    // 设置新音量
                    EnhancedThreadManager.d(TAG, "增加音量: $currentVolume -> $newVolume")
                    val result = controller.setVolumeSync(newVolume.toUInt())
                    
                    if (!result) {
                        EnhancedThreadManager.e(TAG, "增加音量失败")
                        withContext(Dispatchers.Main) {
                            errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "增加音量失败"))
                        }
                    }
                } else {
                    EnhancedThreadManager.e(TAG, "获取当前音量失败，无法增加音量")
                    withContext(Dispatchers.Main) {
                        errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "获取当前音量失败"))
                    }
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "增加音量异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "增加音量异常: ${e.message}", e))
                }
            }
        }
    }
    
    /**
     * 减小音量
     */
    fun decreaseVolume(controller: DlnaController, stepSize: Int = 5) {
        coroutineScope.launch {
            try {
                // 获取当前音量
                val currentVolume = getCurrentVolume(controller)
                
                if (currentVolume != null) {
                    // 计算新音量
                    val newVolume = validateVolume(currentVolume - stepSize)
                    
                    // 设置新音量
                    EnhancedThreadManager.d(TAG, "减小音量: $currentVolume -> $newVolume")
                    val result = controller.setVolumeSync(newVolume.toUInt())
                    
                    if (!result) {
                        EnhancedThreadManager.e(TAG, "减小音量失败")
                        withContext(Dispatchers.Main) {
                            errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "减小音量失败"))
                        }
                    }
                } else {
                    EnhancedThreadManager.e(TAG, "获取当前音量失败，无法减小音量")
                    withContext(Dispatchers.Main) {
                        errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "获取当前音量失败"))
                    }
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "减小音量异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "减小音量异常: ${e.message}", e))
                }
            }
        }
    }
    
    /**
     * 切换静音状态
     */
    fun toggleMute(controller: DlnaController) {
        coroutineScope.launch {
            try {
                // 获取当前静音状态
                val currentMute = getMuteState(controller)
                
                if (currentMute != null) {
                    // 切换静音状态
                    val newMute = !currentMute
                    EnhancedThreadManager.d(TAG, "切换静音状态: $currentMute -> $newMute")
                    val result = controller.setMuteSync(newMute)
                    
                    if (!result) {
                        EnhancedThreadManager.e(TAG, "切换静音状态失败")
                        withContext(Dispatchers.Main) {
                            errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "切换静音状态失败"))
                        }
                    }
                } else {
                    EnhancedThreadManager.e(TAG, "获取当前静音状态失败，无法切换")
                    withContext(Dispatchers.Main) {
                        errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "获取当前静音状态失败"))
                    }
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "切换静音状态异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    errorListener?.invoke(DLNAException(DLNAErrorType.PLAYBACK_ERROR, "切换静音状态异常: ${e.message}", e))
                }
            }
        }
    }
    
    /**
     * 验证并修正音量值
     * @return 修正后的音量
     */
    private fun validateVolume(volume: Int): Int {
        return when {
            volume < MIN_VOLUME -> MIN_VOLUME
            volume > MAX_VOLUME -> MAX_VOLUME
            else -> volume
        }
    }
    
    /**
     * 获取当前音量
     * @return 当前音量，如果获取失败则返回null
     */
    private suspend fun getCurrentVolume(controller: DlnaController): Int? {
        return try {
            // 使用DlnaController的getVolume方法获取当前音量
            val volume = controller.getVolume()
            volume?.toInt()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取当前音量异常: ${e.message}", e)
            null
        }
    }
    
    /**
     * 获取当前静音状态
     * @return 当前静音状态，如果获取失败则返回null
     */
    private suspend fun getMuteState(controller: DlnaController): Boolean? {
        return try {
            // 使用DlnaController的getMute方法获取当前静音状态
            controller.getMute()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取当前静音状态异常: ${e.message}", e)
            null
        }
    }
    
    /**
     * 取消所有正在进行的操作
     */
    fun cancelAllOperations() {
        coroutineScope.coroutineContext.cancelChildren()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        coroutineScope.cancel()
    }
} 