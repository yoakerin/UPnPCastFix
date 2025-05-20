package com.yinnho.upnpcast.utils

import android.content.Context
import android.content.Intent
import com.yinnho.upnpcast.core.EnhancedThreadManager

/**
 * 通知工具类
 * 负责处理DLNA事件通知和广播
 */
object NotificationUtils {
    private const val TAG = "NotificationUtils"
    
    // 广播Action常量
    const val ACTION_DLNA_STATUS = "com.yinnho.upnpcast.ACTION_DLNA_STATUS"
    const val ACTION_DLNA_ERROR = "com.yinnho.upnpcast.ACTION_DLNA_ERROR"
    
    // 广播Extra常量
    const val EXTRA_DEVICE_ID = "device_id"
    const val EXTRA_STATUS = "status"
    const val EXTRA_ERROR_MESSAGE = "error_message"
    
    // 应用Context引用
    private var appContext: Context? = null
    
    /**
     * 初始化工具类
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }
    
    /**
     * 通知DLNA操作状态
     */
    fun notifyDLNAActionStatus(deviceId: String, status: String) {
        EnhancedThreadManager.d(TAG, "通知DLNA操作状态: deviceId=$deviceId, status=$status")
        
        val context = appContext ?: return
        
        val intent = Intent(ACTION_DLNA_STATUS).apply {
            putExtra(EXTRA_DEVICE_ID, deviceId)
            putExtra(EXTRA_STATUS, status)
        }
        
        context.sendBroadcast(intent)
    }
    
    /**
     * 发送错误广播
     */
    fun sendErrorBroadcast(errorMessage: String) {
        EnhancedThreadManager.d(TAG, "发送错误广播: $errorMessage")
        
        val context = appContext ?: return
        
        val intent = Intent(ACTION_DLNA_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        
        context.sendBroadcast(intent)
    }
    
    /**
     * 释放资源
     */
    fun release() {
        appContext = null
    }
} 