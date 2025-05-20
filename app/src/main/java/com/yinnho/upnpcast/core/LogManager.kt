package com.yinnho.upnpcast.core

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 日志管理工具类
 * 统一管理日志输出，支持长消息自动分段和标签格式化
 */
object LogManager {
    
    // 是否启用调试日志
    private var isDebugEnabled = true
    
    // 最大日志长度（超过将被截断）
    private const val MAX_LOG_LENGTH = 2000
    
    // 标签前缀
    private const val TAG_PREFIX = "UPnPCast."
    
    /**
     * 设置是否启用调试日志
     */
    fun setDebugEnabled(enabled: Boolean) {
        isDebugEnabled = enabled
        d("LogManager", "调试模式已${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 格式化日志标签
     */
    fun formatTag(tag: String): String =
        if (tag.startsWith(TAG_PREFIX)) tag else "$TAG_PREFIX$tag"
    
    /**
     * 输出调试级别日志
     */
    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            logLongMessage(Log.DEBUG, formatTag(tag), message)
        }
    }
    
    /**
     * 输出信息级别日志
     */
    fun i(tag: String, message: String) {
        logLongMessage(Log.INFO, formatTag(tag), message)
    }
    
    /**
     * 输出警告级别日志
     */
    fun w(tag: String, message: String) {
        logLongMessage(Log.WARN, formatTag(tag), message)
    }
    
    /**
     * 输出警告级别日志（带异常）
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        logLongMessage(Log.WARN, formatTag(tag), "$message\n${getStackTraceString(throwable)}")
    }
    
    /**
     * 输出错误级别日志
     */
    fun e(tag: String, message: String) {
        logLongMessage(Log.ERROR, formatTag(tag), message)
    }
    
    /**
     * 输出错误级别日志（带异常）
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        logLongMessage(Log.ERROR, formatTag(tag), "$message\n${getStackTraceString(throwable)}")
    }
    
    /**
     * 处理长消息日志输出
     * Android日志有长度限制，需要分段输出
     */
    private fun logLongMessage(priority: Int, tag: String, message: String) {
        if (message.length <= MAX_LOG_LENGTH) {
            Log.println(priority, tag, message)
            return
        }
        
        // 分段输出
        var i = 0
        val length = message.length
        while (i < length) {
            val end = (i + MAX_LOG_LENGTH).coerceAtMost(length)
            val part = message.substring(i, end)
            Log.println(
                priority, 
                tag, 
                if (i == 0) part else "(续) $part"
            )
            i = end
        }
    }
    
    /**
     * 获取异常的堆栈跟踪字符串
     */
    private fun getStackTraceString(throwable: Throwable): String {
        return StringWriter().use { sw ->
            PrintWriter(sw).use { pw ->
                throwable.printStackTrace(pw)
                sw.toString()
            }
        }
    }
    
    /**
     * 测量执行时间并记录日志
     */
    inline fun <T> measureTimeMillis(tag: String, taskName: String, block: () -> T): T {
        val startTime = System.currentTimeMillis()
        return try {
            block().also {
                val duration = System.currentTimeMillis() - startTime
                d(tag, "$taskName 执行完成，耗时: $duration ms")
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            e(tag, "$taskName 执行失败，耗时: $duration ms", e)
            throw e
        }
    }
} 