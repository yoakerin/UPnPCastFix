package com.yinnho.upnpcast.manager.config

import android.content.Context
import android.content.SharedPreferences
import com.yinnho.upnpcast.core.EnhancedThreadManager
import java.lang.ref.WeakReference

/**
 * 配置管理器
 * 负责处理配置和偏好设置
 */
class ConfigManager(context: Context) {
    companion object {
        private const val TAG = "ConfigManager"
        private const val PREF_NAME = "dlna_preferences"
        
        // 配置键值常量
        const val KEY_AUTO_CONNECT = "auto_connect"
        const val KEY_SEARCH_TIMEOUT = "search_timeout"
        const val KEY_DEBUG_MODE = "debug_mode"
        const val KEY_DEFAULT_DEVICE = "default_device"
        const val KEY_MAX_VOLUME = "max_volume"
        
        // 默认值
        const val DEFAULT_SEARCH_TIMEOUT = 10000L // 10秒
        const val DEFAULT_MAX_VOLUME = 100
    }
    
    // 使用弱引用存储Context，避免内存泄漏
    private val contextRef = WeakReference(context.applicationContext)
    
    // 共享偏好设置
    private val sharedPreferences: SharedPreferences by lazy {
        contextRef.get()?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            ?: throw IllegalStateException("Context已被回收")
    }
    
    /**
     * 设置自动连接模式
     */
    fun setAutoConnect(enabled: Boolean) {
        try {
            EnhancedThreadManager.d(TAG, "设置自动连接模式: $enabled")
            
            // 保存设置到偏好
            sharedPreferences.edit()
                .putBoolean(KEY_AUTO_CONNECT, enabled)
                .apply()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置自动连接模式失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取自动连接模式
     */
    fun getAutoConnect(): Boolean {
        return try {
            sharedPreferences.getBoolean(KEY_AUTO_CONNECT, false)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取自动连接模式失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 设置调试模式
     */
    fun setDebugMode(enabled: Boolean) {
        try {
            EnhancedThreadManager.d(TAG, "设置调试模式: $enabled")
            
            // 保存设置到偏好
            sharedPreferences.edit()
                .putBoolean(KEY_DEBUG_MODE, enabled)
                .apply()
                
            // 应用到日志管理器
            EnhancedThreadManager.setDebugMode(enabled)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置调试模式失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取调试模式
     */
    fun getDebugMode(): Boolean {
        return try {
            sharedPreferences.getBoolean(KEY_DEBUG_MODE, false)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取调试模式失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 设置搜索超时时间
     */
    fun setSearchTimeout(timeoutMs: Long) {
        try {
            EnhancedThreadManager.d(TAG, "设置搜索超时时间: ${timeoutMs}ms")
            
            // 保存设置到偏好
            sharedPreferences.edit()
                .putLong(KEY_SEARCH_TIMEOUT, timeoutMs)
                .apply()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置搜索超时时间失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取搜索超时时间
     */
    fun getSearchTimeout(): Long {
        return try {
            sharedPreferences.getLong(KEY_SEARCH_TIMEOUT, DEFAULT_SEARCH_TIMEOUT)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取搜索超时时间失败: ${e.message}", e)
            DEFAULT_SEARCH_TIMEOUT
        }
    }
    
    /**
     * 保存默认设备ID
     */
    fun saveDefaultDevice(deviceId: String) {
        try {
            EnhancedThreadManager.d(TAG, "保存默认设备: $deviceId")
            
            sharedPreferences.edit()
                .putString(KEY_DEFAULT_DEVICE, deviceId)
                .apply()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "保存默认设备失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取默认设备ID
     */
    fun getDefaultDevice(): String? {
        return try {
            sharedPreferences.getString(KEY_DEFAULT_DEVICE, null)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取默认设备失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 清除默认设备
     */
    fun clearDefaultDevice() {
        try {
            EnhancedThreadManager.d(TAG, "清除默认设备")
            
            sharedPreferences.edit()
                .remove(KEY_DEFAULT_DEVICE)
                .apply()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "清除默认设备失败: ${e.message}", e)
        }
    }
    
    /**
     * 设置最大音量
     */
    fun setMaxVolume(volume: Int) {
        try {
            EnhancedThreadManager.d(TAG, "设置最大音量: $volume")
            
            sharedPreferences.edit()
                .putInt(KEY_MAX_VOLUME, volume)
                .apply()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置最大音量失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取最大音量
     */
    fun getMaxVolume(): Int {
        return try {
            sharedPreferences.getInt(KEY_MAX_VOLUME, DEFAULT_MAX_VOLUME)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取最大音量失败: ${e.message}", e)
            DEFAULT_MAX_VOLUME
        }
    }
    
    /**
     * 清除所有配置
     */
    fun clearAllConfigs() {
        try {
            EnhancedThreadManager.d(TAG, "清除所有配置")
            
            sharedPreferences.edit().clear().apply()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "清除所有配置失败: ${e.message}", e)
        }
    }
} 