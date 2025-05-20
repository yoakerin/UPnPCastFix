package com.yinnho.upnpcast.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.yinnho.upnpcast.DLNAErrorType
import com.yinnho.upnpcast.DLNAException
import com.yinnho.upnpcast.core.LogManager

/**
 * 简化的错误服务
 * 统一错误处理、日志记录和回调通知
 */
object ErrorService {
    private const val TAG = "ErrorService"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 错误回调接口
    interface ErrorCallback {
        fun onError(errorType: DLNAErrorType, message: String, exception: Throwable?)
    }
    
    // 默认错误处理回调
    private var defaultCallback: ErrorCallback? = null
    
    /**
     * 设置默认错误回调
     */
    fun setDefaultCallback(callback: ErrorCallback) {
        this.defaultCallback = callback
    }
    
    /**
     * 处理异常
     * @param e 异常对象
     * @param operation 操作描述
     * @param callback 错误回调（可选）
     * @return 创建的DLNAException对象
     */
    fun handleException(
        e: Exception,
        operation: String,
        callback: ErrorCallback? = null
    ): DLNAException {
        // 分类错误
        val errorType = if (e is DLNAException) e.errorType else classifyError(e)
        
        // 格式化错误消息
        val errorMessage = formatErrorMessage(operation, e)
        
        // 记录日志
        LogManager.e(TAG, "$errorMessage (${e.javaClass.simpleName})", e)
        
        // 通知错误
        notifyError(errorType, errorMessage, e, callback)
        
        // 创建并返回包装后的异常
        return DLNAException(errorType, errorMessage, e)
    }
    
    /**
     * 简化的错误分类方法
     */
    private fun classifyError(e: Exception): DLNAErrorType {
        return when {
            // 网络相关错误
            e.javaClass.name.contains("java.net.") || 
            e.javaClass.name.contains("javax.net.") ||
            e.message?.contains("network") == true ||
            e.message?.contains("connect") == true ->
                DLNAErrorType.NETWORK_ERROR
                
            // 连接超时错误
            e.javaClass.name.contains("SocketTimeoutException") ||
            e.javaClass.name.contains("ConnectTimeoutException") ||
            e.message?.contains("timeout") == true ->
                DLNAErrorType.NETWORK_TIMEOUT
            
            // IO相关错误
            e.javaClass.name.contains("java.io.") ->
                DLNAErrorType.CONNECTION_ERROR
                
            // 解析错误
            e.javaClass.name.contains("parse") || 
            e.javaClass.name.contains("xml") ||
            e.javaClass.name.contains("json") ||
            e.message?.contains("parse") == true ->
                DLNAErrorType.PARSING_ERROR
            
            // 设备错误
            e.message?.contains("device") == true ->
                DLNAErrorType.DEVICE_ERROR
                
            // 播放相关错误
            e.message?.contains("play") == true ||
            e.message?.contains("media") == true ||
            e.message?.contains("audio") == true ||
            e.message?.contains("video") == true ->
                DLNAErrorType.PLAYBACK_ERROR
                
            // 发现设备相关错误
            e.message?.contains("discovery") == true ||
            e.message?.contains("find") == true ||
            e.message?.contains("search") == true ->
                DLNAErrorType.DISCOVERY_ERROR
            
            // 无效参数错误
            e.javaClass.name.contains("IllegalArgument") ||
            e.message?.contains("invalid") == true ||
            e.message?.contains("parameter") == true ->
                DLNAErrorType.INVALID_PARAMETER
                
            // 默认为未知错误
            else -> DLNAErrorType.UNKNOWN_ERROR
        }
    }
    
    /**
     * 格式化错误消息
     */
    private fun formatErrorMessage(operation: String, exception: Exception): String {
        val baseMessage = "操作「$operation」失败"
        val errorMessage = exception.message?.takeIf { it.isNotEmpty() } 
            ?: exception.javaClass.simpleName
        
        return "$baseMessage: $errorMessage"
    }
    
    /**
     * 通知错误
     */
    private fun notifyError(errorType: DLNAErrorType, message: String, exception: Throwable?, callback: ErrorCallback?) {
        val actualCallback = callback ?: defaultCallback
        actualCallback?.let {
            mainHandler.post {
                try {
                    it.onError(errorType, message, exception)
                } catch (e: Exception) {
                    LogManager.e(TAG, "错误回调执行异常", e)
                }
            }
        }
    }
    
    /**
     * 处理错误
     * @param errorType 错误类型
     * @param message 错误消息
     * @param callback 错误回调（可选）
     * @return 创建的DLNAException对象
     */
    fun handleError(
        errorType: DLNAErrorType,
        message: String,
        callback: ErrorCallback? = null
    ): DLNAException {
        // 记录日志
        LogManager.e(TAG, message)
        
        // 通知错误
        notifyError(errorType, message, null, callback)
        
        // 创建并返回异常
        return DLNAException(errorType, message)
    }
    
    /**
     * 发送错误广播
     */
    fun sendErrorBroadcast(context: Context, errorMessage: String, errorType: DLNAErrorType? = null) {
        try {
            val intent = Intent("com.yinnho.upnpcast_ERROR")
            intent.putExtra("error", errorMessage)
            errorType?.let { intent.putExtra("error_type", it.name) }
            
            context.sendBroadcast(intent)
            LogManager.d(TAG, "发送错误广播: $errorMessage")
        } catch (e: Exception) {
            LogManager.e(TAG, "发送错误广播失败", e)
        }
    }
    
    /**
     * 创建错误广播接收器
     */
    fun createErrorReceiver(errorCallback: (String, DLNAErrorType?) -> Unit): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.yinnho.upnpcast_ERROR") {
                    val errorMessage = intent.getStringExtra("error") ?: "未知错误"
                    val errorTypeName = intent.getStringExtra("error_type")
                    val errorType = errorTypeName?.let { 
                        try {
                            DLNAErrorType.valueOf(it)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    errorCallback(errorMessage, errorType)
                }
            }
        }
    }
    
    /**
     * 运行代码并安全处理异常
     */
    fun <T> runSafely(
        operation: String,
        callback: ErrorCallback? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            handleException(e, operation, callback)
            null
        }
    }
    
    /**
     * 带默认值的安全执行操作
     */
    fun <T> runWithDefault(operation: String, defaultValue: T, action: () -> T): T {
        return try {
            action()
        } catch (e: Exception) {
            LogManager.e(TAG, "操作 '$operation' 失败", e)
            defaultValue
        }
    }
    
    /**
     * 带重试机制和指数退避的安全运行
     */
    fun <T> runWithRetry(
        operation: String,
        maxRetries: Int = 3,
        initialDelay: Long = 1000,
        callback: ErrorCallback? = null,
        shouldRetry: (Exception) -> Boolean = { it !is DLNAException || it.errorType != DLNAErrorType.INVALID_PARAMETER },
        block: () -> T
    ): T? {
        var lastException: Exception? = null
        var currentDelay = initialDelay
        
        for (attempt in 0..maxRetries) {
            try {
                // 尝试执行代码块
                return block()
            } catch (e: Exception) {
                lastException = e
                
                // 检查是否应该重试
                if (attempt < maxRetries && shouldRetry(e)) {
                    // 记录重试信息
                    LogManager.w(TAG, "操作「$operation」失败，将在${currentDelay}ms后进行第${attempt+1}次重试: ${e.message}")
                    
                    // 等待一段时间后重试
                    try {
                        Thread.sleep(currentDelay)
                        // 指数退避策略，每次重试延迟翻倍
                        currentDelay = (currentDelay * 1.5).toLong().coerceAtMost(30000) // 最大30秒
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                } else {
                    // 不应该重试或已达到最大重试次数
                    break
                }
            }
        }
        
        // 所有重试都失败
        if (lastException != null) {
            handleException(lastException, "$operation (已重试${maxRetries}次)", callback)
        }
        
        return null
    }
    
    /**
     * 创建稳健的网络请求执行器
     */
    fun <T> executeNetworkRequest(
        operationName: String,
        maxRetries: Int = 3,
        callback: ErrorCallback? = null,
        request: () -> T
    ): T? {
        return runWithRetry(
            operation = operationName,
            maxRetries = maxRetries,
            initialDelay = 2000,
            callback = callback,
            // 只对网络和连接错误进行重试
            shouldRetry = { e ->
                when {
                    e is DLNAException -> {
                        e.errorType == DLNAErrorType.NETWORK_ERROR ||
                        e.errorType == DLNAErrorType.NETWORK_TIMEOUT ||
                        e.errorType == DLNAErrorType.CONNECTION_ERROR
                    }
                    e.javaClass.name.contains("java.net.") ||
                    e.javaClass.name.contains("java.io.") ||
                    e.message?.contains("timeout") == true ||
                    e.message?.contains("connect") == true -> true
                    else -> false
                }
            },
            block = request
        )
    }
    
    /**
     * 自动重连设备
     */
    fun autoReconnectDevice(
        deviceId: String, 
        maxAttempts: Int = 3,
        reconnectAction: (String) -> Boolean
    ): Boolean {
        var success = false
        var attempts = 0
        
        while (!success && attempts < maxAttempts) {
            attempts++
            
            try {
                LogManager.d(TAG, "尝试重新连接设备 $deviceId (第${attempts}次)")
                success = reconnectAction(deviceId)
                
                if (!success && attempts < maxAttempts) {
                    // 等待时间随着尝试次数增加
                    val waitTime = 1000L * attempts
                    Thread.sleep(waitTime)
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "重连尝试失败: ${e.message}", e)
                
                // 如果不是最后一次尝试，继续
                if (attempts < maxAttempts) {
                    val waitTime = 1000L * attempts
                    try {
                        Thread.sleep(waitTime)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
        
        return success
    }
    
    /**
     * 创建安全的Runnable对象
     */
    fun createSafeRunnable(operationName: String, action: () -> Unit): Runnable {
        return Runnable {
            runSafely(operationName) {
                action()
            }
        }
    }
} 