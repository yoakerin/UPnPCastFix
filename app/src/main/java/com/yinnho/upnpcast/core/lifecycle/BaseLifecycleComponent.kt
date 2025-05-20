package com.yinnho.upnpcast.core.lifecycle

import android.util.Log
import java.io.Closeable
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基础生命周期组件
 * 实现LifecycleAwareComponent接口，作为其他组件的基类
 */
abstract class BaseLifecycleComponent : LifecycleAwareComponent {
    
    companion object {
        private const val TAG = "BaseLifecycleComponent"
    }
    
    /**
     * 组件名称，便于调试和日志
     */
    protected abstract val componentName: String
    
    /**
     * 状态标志
     */
    private val isInitialized = AtomicBoolean(false)
    private val isStarted = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val isDestroyed = AtomicBoolean(false)
    
    /**
     * 资源管理器
     */
    protected val resources = mutableListOf<Pair<Int, String>>()
    
    /**
     * 定时器管理
     */
    protected var timerId: Int = -1
    protected var timer: Timer? = null
    
    /**
     * 初始化组件
     */
    override fun onInit() {
        if (isInitialized.compareAndSet(false, true)) {
            logLifecycle("初始化")
        } else {
            Log.w(TAG, "$componentName 已经初始化过")
        }
    }
    
    /**
     * 启动组件
     */
    override fun onStart() {
        if (!isInitialized.get()) {
            onInit()
        }
        
        if (isStarted.compareAndSet(false, true)) {
            isPaused.set(false)
            logLifecycle("启动")
        } else {
            Log.w(TAG, "$componentName 已经启动")
        }
    }
    
    /**
     * 暂停组件
     */
    override fun onPause() {
        if (isStarted.get() && !isPaused.get()) {
            isPaused.set(true)
            logLifecycle("暂停")
        }
    }
    
    /**
     * 恢复组件
     */
    override fun onResume() {
        if (isStarted.get() && isPaused.get()) {
            isPaused.set(false)
            logLifecycle("恢复")
        } else if (!isStarted.get()) {
            onStart()
        }
    }
    
    /**
     * 停止组件
     */
    override fun onStop() {
        if (isStarted.compareAndSet(true, false)) {
            isPaused.set(false)
            logLifecycle("停止")
        }
    }
    
    /**
     * 销毁组件
     */
    override fun onDestroy() {
        if (isDestroyed.compareAndSet(false, true)) {
            // 确保先停止
            if (isStarted.get()) {
                onStop()
            }
            
            // 释放所有注册的资源
            releaseAllResources()
            
            // 取消定时器
            cancelTimer()
            
            logLifecycle("销毁")
        }
    }
    
    /**
     * 注册资源
     * @param resource 资源
     * @param name 资源名称
     * @return 资源ID
     */
    protected fun <T : Any> registerResource(resource: T, name: String = ""): Int {
        val resourceName = name.ifEmpty { "${componentName}_${resource.javaClass.simpleName}" }
        val resourceId = ResourceManager.registerResource(resource, resourceName)
        
        resources.add(resourceId to resourceName)
        return resourceId
    }
    
    /**
     * 释放指定资源
     * @param resourceId 资源ID
     */
    protected fun releaseResource(resourceId: Int) {
        ResourceManager.releaseResource(resourceId)
        resources.removeIf { it.first == resourceId }
    }
    
    /**
     * 释放所有注册的资源
     */
    protected fun releaseAllResources() {
        logLifecycle("释放 ${resources.size} 个资源")
        
        resources.forEach { (id, name) ->
            try {
                ResourceManager.releaseResource(id)
                Log.d(TAG, "$componentName 释放资源: $name [ID:$id]")
            } catch (e: Exception) {
                Log.e(TAG, "$componentName 释放资源 $name 失败: ${e.message}", e)
            }
        }
        
        resources.clear()
    }
    
    /**
     * 创建定时器
     * @param isDaemon 是否为守护线程
     */
    protected fun createTimer(isDaemon: Boolean = true) {
        if (timer != null) {
            cancelTimer()
        }
        
        val (id, newTimer) = TimerManager.createTimer(componentName, isDaemon)
        timerId = id
        timer = newTimer
        
        Log.d(TAG, "$componentName 创建定时器 [ID:$id]")
    }
    
    /**
     * 调度定时任务
     * @param task 任务
     * @param delay 延迟（毫秒）
     * @param period 周期（毫秒），0表示只执行一次
     * @return 是否成功
     */
    protected fun scheduleTask(task: TimerTask, delay: Long, period: Long = 0): Boolean {
        if (timer == null) {
            createTimer()
        }
        
        return TimerManager.scheduleTask(timerId, task, delay, period)
    }
    
    /**
     * 取消定时器
     */
    protected fun cancelTimer() {
        if (timerId >= 0) {
            TimerManager.cancelTimer(timerId)
            timer = null
            timerId = -1
            Log.d(TAG, "$componentName 取消定时器")
        }
    }
    
    /**
     * 注册Closeable资源并在销毁时自动关闭
     * @param resource Closeable资源
     * @param name 资源名称
     */
    protected fun <T : Closeable> registerCloseable(resource: T, name: String = ""): T {
        registerResource(resource, name)
        return resource
    }
    
    /**
     * 记录生命周期事件日志
     * @param event 事件名称
     */
    private fun logLifecycle(event: String) {
        Log.d(TAG, "$componentName $event")
    }
} 