package com.yinnho.upnpcast.core.lifecycle

import android.util.Log
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 计时器管理器
 * 用于统一管理定时器和线程，防止内存泄漏
 */
object TimerManager {
    private const val TAG = "TimerManager"
    
    // 定时器ID计数器
    private val timerIdCounter = AtomicInteger(0)
    
    // 定时器映射表
    private val timers = ConcurrentHashMap<Int, ManagedTimer>()
    
    // 线程ID计数器
    private val threadIdCounter = AtomicInteger(0)
    
    // 线程映射表
    private val threads = ConcurrentHashMap<Int, ManagedThread>()
    
    // 默认的调度执行器
    private val scheduler = Executors.newScheduledThreadPool(2)
    
    init {
        // 注册默认调度器到资源管理器
        ResourceManager.registerResource(scheduler, "DefaultScheduler")
    }
    
    /**
     * 创建并启动一个Timer
     * @param name 定时器名称
     * @param isDaemon 是否为守护线程
     * @return 定时器ID和Timer实例
     */
    fun createTimer(name: String, isDaemon: Boolean = true): Pair<Int, Timer> {
        val id = timerIdCounter.incrementAndGet()
        val timer = Timer("${name}_$id", isDaemon)
        
        val managedTimer = ManagedTimer(id, timer, name)
        timers[id] = managedTimer
        
        Log.d(TAG, "已创建定时器 $name [ID:$id]")
        return id to timer
    }
    
    /**
     * 启动定时任务
     * @param timerId 定时器ID
     * @param task 要执行的任务
     * @param delay 延迟时间（毫秒）
     * @param period 周期时间（毫秒），为0表示只执行一次
     * @return 是否成功启动
     */
    fun scheduleTask(timerId: Int, task: TimerTask, delay: Long, period: Long = 0): Boolean {
        val managedTimer = timers[timerId] ?: return false
        
        try {
            if (period > 0) {
                managedTimer.timer.schedule(task, delay, period)
                Log.d(TAG, "已在定时器 ${managedTimer.name} 上启动周期任务，延迟: ${delay}ms，周期: ${period}ms")
            } else {
                managedTimer.timer.schedule(task, delay)
                Log.d(TAG, "已在定时器 ${managedTimer.name} 上启动单次任务，延迟: ${delay}ms")
            }
            
            managedTimer.taskCount.incrementAndGet()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "启动定时任务失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 使用ScheduledExecutorService执行定时任务
     * @param task 要执行的任务
     * @param delay 延迟时间
     * @param period 周期时间，为0表示只执行一次
     * @param unit 时间单位
     * @return 任务的Future
     */
    fun scheduleWithExecutor(
        task: Runnable,
        delay: Long,
        period: Long = 0,
        unit: TimeUnit = TimeUnit.MILLISECONDS
    ): ScheduledFuture<*> {
        val future = if (period > 0) {
            scheduler.scheduleAtFixedRate(task, delay, period, unit)
        } else {
            scheduler.schedule(task, delay, unit)
        }
        
        // 注册到资源管理器
        ResourceManager.registerResource(future, "ScheduledTask")
        
        Log.d(TAG, "已使用调度器启动任务，延迟: $delay，周期: $period")
        return future
    }
    
    /**
     * 创建并启动一个线程
     * @param name 线程名称
     * @param isDaemon 是否为守护线程
     * @param runnable 要执行的任务
     * @return 线程ID和Thread实例
     */
    fun createThread(name: String, isDaemon: Boolean = false, runnable: Runnable): Pair<Int, Thread> {
        val id = threadIdCounter.incrementAndGet()
        val thread = Thread(runnable, "${name}_$id")
        thread.isDaemon = isDaemon
        
        val managedThread = ManagedThread(id, thread, name)
        threads[id] = managedThread
        
        Log.d(TAG, "已创建线程 $name [ID:$id, 守护线程:$isDaemon]")
        
        // 启动线程
        thread.start()
        
        return id to thread
    }
    
    /**
     * 取消定时器
     * @param timerId 定时器ID
     * @return 是否成功取消
     */
    fun cancelTimer(timerId: Int): Boolean {
        val managedTimer = timers.remove(timerId) ?: return false
        
        try {
            managedTimer.timer.cancel()
            managedTimer.timer.purge()
            Log.d(TAG, "已取消定时器 ${managedTimer.name} [ID:$timerId]")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "取消定时器失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 中断线程
     * @param threadId 线程ID
     * @return 是否成功中断
     */
    fun interruptThread(threadId: Int): Boolean {
        val managedThread = threads.remove(threadId) ?: return false
        
        try {
            if (managedThread.thread.isAlive) {
                managedThread.thread.interrupt()
                Log.d(TAG, "已中断线程 ${managedThread.name} [ID:$threadId]")
            } else {
                Log.d(TAG, "线程 ${managedThread.name} [ID:$threadId] 已经不在运行")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "中断线程失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 关闭所有定时器和线程
     */
    fun shutdownAll() {
        Log.d(TAG, "正在关闭所有定时器和线程")
        
        // 关闭所有定时器
        timers.keys.forEach { cancelTimer(it) }
        timers.clear()
        
        // 中断所有线程
        threads.keys.forEach { interruptThread(it) }
        threads.clear()
        
        // 关闭调度器
        try {
            scheduler.shutdownNow()
            scheduler.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "关闭调度器失败: ${e.message}", e)
        }
        
        Log.d(TAG, "所有定时器和线程已关闭")
    }
    
    /**
     * 获取当前管理的定时器数量
     */
    fun getTimerCount(): Int = timers.size
    
    /**
     * 获取当前管理的线程数量
     */
    fun getThreadCount(): Int = threads.size
    
    /**
     * 打印当前管理的所有定时器和线程信息
     */
    fun dumpStatus() {
        Log.d(TAG, "===== 计时器管理器状态 =====")
        Log.d(TAG, "当前管理定时器数量: ${timers.size}")
        
        timers.values.forEachIndexed { index, timer ->
            Log.d(TAG, "$index. 定时器ID:${timer.id}, 名称:${timer.name}, 任务数:${timer.taskCount.get()}")
        }
        
        Log.d(TAG, "当前管理线程数量: ${threads.size}")
        
        threads.values.forEachIndexed { index, thread ->
            Log.d(TAG, "$index. 线程ID:${thread.id}, 名称:${thread.name}, 存活:${thread.thread.isAlive}, 状态:${thread.thread.state}")
        }
        
        Log.d(TAG, "===========================")
    }
    
    /**
     * 内部类，表示一个被管理的定时器
     */
    private data class ManagedTimer(
        val id: Int,
        val timer: Timer,
        val name: String,
        val taskCount: AtomicInteger = AtomicInteger(0),
        val creationTime: Long = System.currentTimeMillis()
    )
    
    /**
     * 内部类，表示一个被管理的线程
     */
    private data class ManagedThread(
        val id: Int,
        val thread: Thread,
        val name: String,
        val creationTime: Long = System.currentTimeMillis()
    )
} 