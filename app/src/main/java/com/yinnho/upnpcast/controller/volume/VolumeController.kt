package com.yinnho.upnpcast.controller.volume

import com.yinnho.upnpcast.controller.device.DeviceInfoManager
import com.yinnho.upnpcast.core.EnhancedThreadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 音量控制器
 * 负责处理设备音量和静音控制
 */
class VolumeController(
    private val deviceInfoManager: DeviceInfoManager
) {
    companion object {
        private const val TAG = "VolumeController"
        private const val DEFAULT_INSTANCE_ID = "0"
        private const val MASTER_CHANNEL = "Master"
    }
    
    /**
     * 设置音量
     * @param volume 音量值(0-100)
     */
    suspend fun setVolume(volume: UInt): Boolean = withContext(Dispatchers.IO) {
        try {
            EnhancedThreadManager.d(TAG, "设置音量: $volume")
            
            val service = deviceInfoManager.renderingControlService 
                ?: throw IllegalStateException("RenderingControl服务不可用")
                
            service.setVolume(DEFAULT_INSTANCE_ID, MASTER_CHANNEL, volume)
            true
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置音量失败", e)
            false
        }
    }
    
    /**
     * 获取当前音量
     */
    suspend fun getVolume(): UInt? = withContext(Dispatchers.IO) {
        try {
            val service = deviceInfoManager.renderingControlService 
                ?: throw IllegalStateException("RenderingControl服务不可用")
                
            service.getVolume(DEFAULT_INSTANCE_ID, MASTER_CHANNEL)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取音量失败", e)
            null
        }
    }
    
    /**
     * 设置静音状态
     */
    suspend fun setMute(mute: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            EnhancedThreadManager.d(TAG, "设置静音: $mute")
            
            val service = deviceInfoManager.renderingControlService 
                ?: throw IllegalStateException("RenderingControl服务不可用")
                
            service.setMute(DEFAULT_INSTANCE_ID, MASTER_CHANNEL, mute)
            true
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置静音失败", e)
            false
        }
    }
    
    /**
     * 获取当前静音状态
     */
    suspend fun getMute(): Boolean? = withContext(Dispatchers.IO) {
        try {
            val service = deviceInfoManager.renderingControlService 
                ?: throw IllegalStateException("RenderingControl服务不可用")
                
            service.getMute(DEFAULT_INSTANCE_ID, MASTER_CHANNEL)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取静音状态失败", e)
            null
        }
    }
    
    /**
     * 增加音量
     * @param step 增加步长
     */
    suspend fun increaseVolume(step: UInt = 5u): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取当前音量
            val currentVolume = getVolume() ?: return@withContext false
            
            // 计算新音量，上限为100
            val newVolume = minOf(currentVolume + step, 100u)
            
            // 设置新音量
            setVolume(newVolume)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "增加音量失败", e)
            false
        }
    }
    
    /**
     * 减少音量
     * @param step 减少步长
     */
    suspend fun decreaseVolume(step: UInt = 5u): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取当前音量
            val currentVolume = getVolume() ?: return@withContext false
            
            // 计算新音量，下限为0
            val newVolume = if (currentVolume > step) currentVolume - step else 0u
            
            // 设置新音量
            setVolume(newVolume)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "减少音量失败", e)
            false
        }
    }
    
    /**
     * 切换静音状态
     */
    suspend fun toggleMute(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取当前静音状态
            val isMuted = getMute() ?: return@withContext false
            
            // 切换静音状态
            setMute(!isMuted)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "切换静音状态失败", e)
            false
        }
    }
} 