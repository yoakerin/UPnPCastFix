package com.yinnho.upnpcast.utils

import com.yinnho.upnpcast.api.DLNAErrorType
import com.yinnho.upnpcast.api.DLNAException
import com.yinnho.upnpcast.core.EnhancedThreadManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 协程错误处理工具
 * 提供与协程配合使用的统一错误处理方法
 */
object CoroutineErrorHandler {
    private const val TAG = "CoroutineErrorHandler"

    /**
     * 创建协程异常处理器
     * @param operation 操作名称，用于日志记录
     * @param onError 错误处理回调（可选）
     * @return CoroutineExceptionHandler实例
     */
    fun createHandler(
        operation: String,
        onError: (DLNAException) -> Unit = {}
    ): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            val exception = when (throwable) {
                is DLNAException -> throwable
                is Exception -> ErrorService.handleException(throwable, operation)
                else -> DLNAException(
                    DLNAErrorType.UNKNOWN_ERROR,
                    "操作「$operation」发生未知错误: ${throwable.message}"
                )
            }
            
            // 调用错误回调
            onError(exception)
        }
    }

    /**
     * 安全地执行协程代码
     * @param operation 操作名称
     * @param context 协程上下文（可选）
     * @param onError 错误处理回调（可选）
     * @param block 要执行的代码块
     * @return 操作结果，如果出错则为null
     */
    suspend fun <T> runSafely(
        operation: String,
        context: CoroutineContext = EmptyCoroutineContext,
        onError: (DLNAException) -> Unit = {},
        block: suspend CoroutineScope.() -> T
    ): T? = withContext(context + createHandler(operation, onError)) {
        try {
            block()
        } catch (e: Exception) {
            val exception = ErrorService.handleException(e, operation)
            onError(exception)
            null
        }
    }

    /**
     * 带默认值的安全协程执行
     * @param operation 操作名称
     * @param defaultValue 发生错误时返回的默认值
     * @param context 协程上下文（可选）
     * @param block 要执行的代码块
     * @return 操作结果，如果出错则为默认值
     */
    suspend fun <T> runWithDefault(
        operation: String,
        defaultValue: T,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> T
    ): T = withContext(context + createHandler(operation)) {
        try {
            block()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "操作「$operation」失败: ${e.message}", e)
            defaultValue
        }
    }

    /**
     * 带重试机制的协程安全执行
     * @param operation 操作名称
     * @param maxRetries 最大重试次数
     * @param retryDelay 重试间隔（毫秒）
     * @param context 协程上下文（可选）
     * @param onError 错误处理回调（可选）
     * @param block 要执行的代码块
     * @return 操作结果，如果所有重试都失败则为null
     */
    suspend fun <T> runWithRetry(
        operation: String,
        maxRetries: Int = 3,
        retryDelay: Long = 1000,
        context: CoroutineContext = EmptyCoroutineContext,
        onError: (DLNAException) -> Unit = {},
        block: suspend CoroutineScope.() -> T
    ): T? = withContext(context + createHandler(operation, onError)) {
        var lastException: Exception? = null
        
        for (attempt in 0..maxRetries) {
            try {
                // 尝试执行代码块
                return@withContext block()
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxRetries) {
                    // 记录重试信息
                    EnhancedThreadManager.w(TAG, "操作「$operation」失败，将在${retryDelay}ms后进行第${attempt+1}次重试: ${e.message}")
                    
                    // 等待一段时间后重试
                    kotlinx.coroutines.delay(retryDelay)
                }
            }
        }
        
        // 所有重试都失败
        if (lastException != null) {
            val exception = ErrorService.handleException(
                lastException, 
                "$operation (已重试${maxRetries}次)"
            )
            onError(exception)
        }
        
        null
    }

    /**
     * 执行多个协程任务并处理错误
     * @param operation 操作名称
     * @param context 协程上下文（可选）
     * @param onError 错误处理回调（可选）
     * @param tasks 要执行的任务列表
     * @return 成功完成的任务结果列表
     */
    suspend fun <T> executeAll(
        operation: String,
        context: CoroutineContext = Dispatchers.IO,
        onError: (DLNAException) -> Unit = {},
        vararg tasks: suspend CoroutineScope.() -> T
    ): List<T> = withContext(context) {
        val deferreds = tasks.map { task ->
            async(createHandler("$operation-子任务", onError)) {
                task()
            }
        }
        
        val results = mutableListOf<T>()
        for (deferred in deferreds) {
            try {
                results.add(deferred.await())
            } catch (e: Exception) {
                // 已由异常处理器处理，不需要额外处理
            }
        }
        
        results
    }
} 