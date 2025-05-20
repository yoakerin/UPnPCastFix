package com.yinnho.upnpcast.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 增强线程管理器
 * 统一管理和复用线程资源，提供线程池功能
 * 集成了日志管理功能，作为项目的唯一日志入口点
 */
object EnhancedThreadManager {
    private const val TAG = "ThreadManager"
    
    // 日志标签最大长度
    private const val MAX_TAG_LENGTH = 23
    
    // 网络操作线程池
    private val networkExecutor: ExecutorService = Executors.newFixedThreadPool(
        3, // 并发线程数
        NamedThreadFactory("DLNA-Network")
    )
    
    // 任务处理线程池
    private val taskExecutor: ExecutorService = Executors.newCachedThreadPool(
        NamedThreadFactory("DLNA-Task")
    )
    
    // 工作线程池（优先级更低）
    private val workExecutor: ExecutorService = Executors.newSingleThreadExecutor(
        NamedThreadFactory("DLNA-Work")
    )
    
    // 定时任务线程池
    private val scheduledExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(
        1,
        NamedThreadFactory("DLNA-Schedule")
    )
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 调试模式标志，在开发期间设置为true
    private var debugMode = false
    
    /**
     * 启用或禁用调试模式
     * 在调试模式下，会输出更详细的日志信息
     */
    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }
    
    /**
     * 在网络线程池中执行任务
     */
    fun executeNetworkTask(task: Runnable) {
        if (!networkExecutor.isShutdown) {
            networkExecutor.execute(task)
        }
    }
    
    /**
     * 在网络线程池中执行任务
     */
    fun executeNetworkTask(task: () -> Unit) {
        if (!networkExecutor.isShutdown) {
            networkExecutor.execute(task)
        }
    }
    
    /**
     * 在任务线程池中执行任务
     */
    fun executeTask(task: Runnable) {
        if (!taskExecutor.isShutdown) {
            taskExecutor.execute(task)
        }
    }
    
    /**
     * 在任务线程池中执行任务
     */
    fun executeTask(task: () -> Unit) {
        if (!taskExecutor.isShutdown) {
            taskExecutor.execute(task)
        }
    }
    
    /**
     * 在主线程中执行任务
     */
    fun executeOnMainThread(task: Runnable) {
        mainHandler.post(task)
    }
    
    /**
     * 在主线程中执行任务
     */
    fun executeOnMainThread(task: () -> Unit) {
        mainHandler.post(task)
    }
    
    /**
     * 在主线程中延迟执行任务
     */
    fun executeOnMainThreadDelayed(task: Runnable, delayMillis: Long) {
        mainHandler.postDelayed(task, delayMillis)
    }
    
    /**
     * 在主线程中延迟执行任务
     */
    fun executeOnMainThreadDelayed(task: () -> Unit, delayMillis: Long) {
        mainHandler.postDelayed(task, delayMillis)
    }
    
    /**
     * 安排定时任务
     * @return 返回ScheduledFuture，可用于取消任务
     */
    fun scheduleTask(task: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*> {
        return scheduledExecutor.scheduleAtFixedRate(task, initialDelay, period, unit)
    }
    
    /**
     * 安排定时任务
     * @return 返回ScheduledFuture，可用于取消任务
     */
    fun scheduleTask(task: () -> Unit, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*> {
        return scheduledExecutor.scheduleAtFixedRate(task, initialDelay, period, unit)
    }
    
    /**
     * 安排定时任务，固定延迟（scheduleAtFixedRate替代方法）
     * @return 返回ScheduledFuture，可用于取消任务
     */
    fun scheduleWithFixedDelay(task: Runnable, initialDelay: Long, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        return scheduledExecutor.scheduleWithFixedDelay(task, initialDelay, delay, unit)
    }
    
    /**
     * 安排定时任务，固定延迟（lambada版本）
     * @return 返回ScheduledFuture，可用于取消任务
     */
    fun scheduleWithFixedDelay(task: () -> Unit, initialDelay: Long, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        return scheduledExecutor.scheduleWithFixedDelay(task, initialDelay, delay, unit)
    }
    
    /**
     * 安排单次延迟任务
     * @return 返回ScheduledFuture，可用于取消任务
     */
    fun scheduleDelayedTask(task: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        return scheduledExecutor.schedule(task, delay, unit)
    }
    
    /**
     * 关闭线程池，释放资源
     */
    fun shutdown() {
        networkExecutor.shutdown()
        taskExecutor.shutdown()
        workExecutor.shutdown()
        scheduledExecutor.shutdown()
    }
    
    /**
     * 测量代码块执行时间（毫秒）
     * 常用于性能分析或耗时操作的监控
     * @param tag 日志标签
     * @param block 要执行的代码块
     * @return 代码块的返回值
     */
    inline fun <T> measureTimeMillis(tag: String, message: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val end = System.currentTimeMillis()
        d(tag, "$message - 耗时: ${end - start}ms")
        return result
    }
    
    /**
     * 使用条件执行日志打印
     * 只有满足条件时才会输出日志
     * @param condition 条件
     * @param logBlock 日志打印代码块
     */
    inline fun logIf(condition: Boolean, logBlock: () -> Unit) {
        if (condition) {
            logBlock()
        }
    }
    
    /**
     * 记录DEBUG级别日志
     * 
     * 使用场景:
     * - 开发调试信息
     * - 详细的流程跟踪
     * - 不会在生产环境中造成性能问题的信息
     * - 组件初始化和释放
     * - 设备发现和连接状态变化
     */
    fun d(tag: String, message: String) {
        if (debugMode || Log.isLoggable(normalizeTag(tag), Log.DEBUG)) {
            Log.d(normalizeTag(tag), message)
        }
    }
    
    /**
     * 记录INFO级别日志
     * 
     * 使用场景:
     * - 重要的状态变更
     * - 正常业务流程的重点信息
     * - 操作的开始和完成事件
     * - 用户行为和关键业务数据
     * - 设备连接成功
     * - 媒体播放状态重要变化（开始播放、暂停、停止）
     */
    fun i(tag: String, message: String) {
        Log.i(normalizeTag(tag), message)
    }
    
    /**
     * 记录WARNING级别日志
     * 
     * 使用场景:
     * - 潜在的问题但不是错误
     * - 性能警告或异常情况
     * - 可能导致将来错误的情况
     * - 资源使用异常
     * - 重试操作
     * - 网络连接不稳定
     * - 设备响应延迟
     */
    fun w(tag: String, message: String) {
        Log.w(normalizeTag(tag), message)
    }
    
    /**
     * 记录WARNING级别日志，附带异常信息
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(normalizeTag(tag), message, throwable)
    }
    
    /**
     * 记录ERROR级别日志
     * 
     * 使用场景:
     * - 功能无法正常运行的错误
     * - 程序逻辑错误
     * - 不可恢复的资源问题
     * - 需要立即关注的问题
     * - 连接失败
     * - 媒体播放错误
     * - API调用异常
     */
    fun e(tag: String, message: String) {
        Log.e(normalizeTag(tag), message)
    }
    
    /**
     * 记录ERROR级别日志，附带异常信息
     * 提供完整的异常信息包括类型、消息和堆栈跟踪
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        val formattedMessage = "$message - ${formatExceptionBrief(throwable)}"
        Log.e(normalizeTag(tag), formattedMessage, throwable)
    }
    
    /**
     * 记录错误，但带有自定义堆栈跟踪深度
     * 
     * @param tag 日志标签
     * @param message 错误消息
     * @param throwable 异常对象
     * @param stackTraceDepth 堆栈跟踪行数（0表示不显示堆栈）
     * @param includeCause 是否包含引起异常的原因
     */
    fun e(tag: String, message: String, throwable: Throwable, stackTraceDepth: Int, includeCause: Boolean = true) {
        val exceptionInfo = formatException(throwable, stackTraceDepth > 0, stackTraceDepth, includeCause)
        Log.e(normalizeTag(tag), "$message - $exceptionInfo", throwable)
    }
    
    /**
     * 简要格式化异常信息
     * 只包含异常类型和消息，不包含堆栈
     */
    fun formatExceptionBrief(throwable: Throwable): String {
        return "${throwable.javaClass.simpleName}: ${throwable.message ?: "无详细信息"}"
    }
    
    /**
     * 格式化异常为字符串，包含异常类型、消息和堆栈
     * 
     * @param throwable 需要格式化的异常
     * @param includeStackTrace 是否包含堆栈跟踪
     * @param maxStackTraceLines 最大堆栈跟踪行数（默认5）
     * @param includeCause 是否包含引起异常的原因
     * @return 格式化后的异常信息
     */
    fun formatException(
        throwable: Throwable, 
        includeStackTrace: Boolean = true,
        maxStackTraceLines: Int = 5,
        includeCause: Boolean = true
    ): String {
        return buildString {
            append("${throwable.javaClass.simpleName}: ${throwable.message ?: "无详细信息"}")
            
            if (includeStackTrace && throwable.stackTrace.isNotEmpty()) {
                val stackLines = throwable.stackTrace.take(maxStackTraceLines)
                stackLines.forEach { element ->
                    append("\n    at $element")
                }
                
                if (throwable.stackTrace.size > maxStackTraceLines) {
                    append("\n    ... ${throwable.stackTrace.size - maxStackTraceLines} more")
                }
            }
            
            // 包含嵌套异常
            if (includeCause) {
                var cause = throwable.cause
                var level = 0
                while (cause != null && cause !== throwable && level < 3) {
                    append("\nCaused by: ${cause.javaClass.simpleName}: ${cause.message ?: "无详细信息"}")
                    
                    if (includeStackTrace && cause.stackTrace.isNotEmpty()) {
                        val causeStackLines = cause.stackTrace.take(3)
                        causeStackLines.forEach { element ->
                            append("\n    at $element")
                        }
                        
                        if (cause.stackTrace.size > 3) {
                            append("\n    ... ${cause.stackTrace.size - 3} more")
                        }
                    }
                    
                    cause = cause.cause
                    level++
                }
                
                if (level >= 3 && cause != null) {
                    append("\n... (more causes omitted)")
                }
            }
        }
    }
    
    /**
     * 记录VERBOSE级别日志（仅调试模式）
     * 
     * 使用场景:
     * - 最详细的调试信息
     * - 临时调试代码
     * - 不应该在生产环境中保留
     * - 频繁的状态更新（例如刷新活跃状态）
     * - 详细的网络通信内容
     * - 性能跟踪点
     */
    fun v(tag: String, message: String) {
        if (debugMode || Log.isLoggable(normalizeTag(tag), Log.VERBOSE)) {
            Log.v(normalizeTag(tag), message)
        }
    }
    
    /**
     * 标准化TAG，确保长度符合Android限制
     */
    private fun normalizeTag(tag: String): String {
        return if (tag.length <= MAX_TAG_LENGTH) tag else tag.substring(0, MAX_TAG_LENGTH)
    }
    
    /**
     * 线程工厂，创建带命名的线程
     */
    private class NamedThreadFactory(
        private val prefix: String
    ) : ThreadFactory {
        // 使用懒加载初始化线程组，确保空安全
        private val group: ThreadGroup? by lazy {
            System.getSecurityManager()?.threadGroup ?: Thread.currentThread().threadGroup
        }
        
        private val threadNumber = AtomicInteger(1)
        
        override fun newThread(r: Runnable): Thread {
            // 创建新线程时处理可能的空线程组
            val threadGroup = group ?: Thread.currentThread().threadGroup
            val t = Thread(
                threadGroup,
                r,
                "$prefix-${threadNumber.getAndIncrement()}",
                0
            )
            
            // 设置为非守护线程
            if (t.isDaemon) {
                t.isDaemon = false
            }
            
            // 使用普通优先级
            if (t.priority != Thread.NORM_PRIORITY) {
                t.priority = Thread.NORM_PRIORITY
            }
            
            return t
        }
    }
} 