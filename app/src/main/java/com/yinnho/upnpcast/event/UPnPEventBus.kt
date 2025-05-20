package com.yinnho.upnpcast.event

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UPnP事件总线
 * 
 * 提供事件发布-订阅机制，解耦事件发送者和接收者。
 * 支持按事件类型订阅，可指定处理线程和事件优先级。
 */
class UPnPEventBus private constructor() {
    private val TAG = "UPnPEventBus"
    
    // 事件订阅映射表，key为事件类型，value为订阅者列表
    private val subscriptions = ConcurrentHashMap<Class<out UPnPEvent>, MutableList<Subscription<UPnPEvent>>>()
    
    // 线程池，用于异步事件分发
    private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2) { r ->
        Thread(r, "UPnP-EventBus-" + THREAD_COUNTER.incrementAndGet()).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 是否已初始化
    private val initialized = AtomicBoolean(false)
    
    // 是否启用事件记录
    private var eventLogging = false
    
    // 保存最近的事件，用于调试
    private val recentEvents = mutableListOf<EventRecord>()
    private val MAX_RECENT_EVENTS = 50
    
    /**
     * 初始化
     */
    fun initialize() {
        if (initialized.compareAndSet(false, true)) {
            Log.d(TAG, "UPnP事件总线已初始化")
        }
    }
    
    /**
     * 发布事件
     * @param event 要发布的事件
     */
    fun post(event: UPnPEvent) {
        if (!initialized.get()) {
            Log.w(TAG, "事件总线未初始化，忽略事件: ${event.javaClass.simpleName}")
            return
        }
        
        if (eventLogging) {
            recordEvent(event)
        }
        
        // 获取事件类型的所有订阅者
        val eventClass = event.javaClass
        val subscribers = getSubscribersForEvent(eventClass)
        
        if (subscribers.isEmpty()) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "没有订阅者处理事件: ${eventClass.simpleName}")
            }
            return
        }
        
        // 按优先级排序
        val sortedSubscribers = subscribers.sortedByDescending { it.priority }
        
        // 分发事件
        for (subscription in sortedSubscribers) {
            dispatchEvent(subscription, event)
        }
    }
    
    /**
     * 延迟发布事件
     * @param event 要发布的事件
     * @param delayMs 延迟时间（毫秒）
     */
    fun postDelayed(event: UPnPEvent, delayMs: Long) {
        if (!initialized.get()) {
            Log.w(TAG, "事件总线未初始化，忽略延迟事件: ${event.javaClass.simpleName}")
            return
        }
        
        executorService.schedule({
            post(event)
        }, delayMs, TimeUnit.MILLISECONDS)
    }
    
    /**
     * 获取指定事件类型的所有订阅者
     */
    private fun getSubscribersForEvent(eventClass: Class<out UPnPEvent>): List<Subscription<UPnPEvent>> {
        val result = mutableListOf<Subscription<UPnPEvent>>()
        
        // 添加直接匹配的订阅者
        subscriptions[eventClass]?.let { result.addAll(it) }
        
        // 添加父类或接口的订阅者
        subscriptions.forEach { (subscribedClass, subscribers) ->
            // 如果事件是已订阅类的子类，也通知这些订阅者
            if (subscribedClass != eventClass && subscribedClass.isAssignableFrom(eventClass)) {
                result.addAll(subscribers)
            }
        }
        
        return result
    }
    
    /**
     * 分发事件到指定订阅者
     */
    private fun dispatchEvent(subscription: Subscription<UPnPEvent>, event: UPnPEvent) {
        when (subscription.threadMode) {
            ThreadMode.MAIN -> {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    // 已在主线程，直接执行
                    deliverEvent(subscription, event)
                } else {
                    // 不在主线程，通过Handler切换到主线程
                    mainHandler.post {
                        deliverEvent(subscription, event)
                    }
                }
            }
            ThreadMode.BACKGROUND -> {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    // 在主线程，切换到后台线程
                    executorService.execute {
                        deliverEvent(subscription, event)
                    }
                } else {
                    // 已在后台线程，直接执行
                    deliverEvent(subscription, event)
                }
            }
            ThreadMode.POSTING -> {
                // 在调用线程直接执行
                deliverEvent(subscription, event)
            }
        }
    }
    
    /**
     * 执行事件回调
     */
    private fun deliverEvent(subscription: Subscription<UPnPEvent>, event: UPnPEvent) {
        try {
            @Suppress("UNCHECKED_CAST")
            subscription.subscriber.onEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "事件处理失败: ${event.javaClass.simpleName}", e)
        }
    }
    
    /**
     * 记录事件
     */
    private fun recordEvent(event: UPnPEvent) {
        synchronized(recentEvents) {
            recentEvents.add(EventRecord(
                event.javaClass.simpleName,
                System.currentTimeMillis()
            ))
            
            // 保持最近事件数量限制
            if (recentEvents.size > MAX_RECENT_EVENTS) {
                recentEvents.removeAt(0)
            }
        }
    }
    
    /**
     * 获取最近的事件记录
     */
    fun getRecentEvents(): List<EventRecord> {
        synchronized(recentEvents) {
            return recentEvents.toList()
        }
    }
    
    /**
     * 设置是否启用事件日志记录
     */
    fun setEventLoggingEnabled(enabled: Boolean) {
        eventLogging = enabled
        if (enabled) {
            Log.d(TAG, "事件日志记录已启用")
        } else {
            Log.d(TAG, "事件日志记录已禁用")
            synchronized(recentEvents) {
                recentEvents.clear()
            }
        }
    }
    
    /**
     * 订阅事件
     * @param subscriber 订阅者
     * @param eventType 事件类型
     * @param threadMode 线程模式
     * @param priority 优先级（0-10，值越大优先级越高）
     */
    fun <T : UPnPEvent> subscribe(
        subscriber: EventSubscriber<T>,
        eventType: Class<T>,
        threadMode: ThreadMode = ThreadMode.POSTING,
        priority: Int = DEFAULT_PRIORITY
    ) {
        if (!initialized.get()) {
            initialize()
        }
        
        val subscription = Subscription(
            subscriber = subscriber as EventSubscriber<UPnPEvent>,
            eventType = eventType as Class<UPnPEvent>,
            threadMode = threadMode,
            priority = priority.coerceIn(0, 10) // 限制优先级范围
        )
        
        // 将订阅添加到映射表
        subscriptions.computeIfAbsent(eventType) { mutableListOf() }.add(subscription)
        
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "添加订阅: ${subscriber.javaClass.simpleName} -> ${eventType.simpleName}")
        }
    }
    
    /**
     * 取消订阅
     * @param subscriber 订阅者
     */
    fun unsubscribe(subscriber: EventSubscriber<*>) {
        // 遍历所有事件类型的订阅列表，移除指定订阅者
        subscriptions.forEach { (eventType, subscribers) ->
            subscribers.removeIf { it.subscriber == subscriber }
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "移除订阅: ${subscriber.javaClass.simpleName} -> ${eventType.simpleName}")
            }
        }
        
        // 删除空的订阅列表
        subscriptions.entries.removeIf { it.value.isEmpty() }
    }
    
    /**
     * 取消指定事件类型的订阅
     * @param subscriber 订阅者
     * @param eventType 事件类型
     */
    fun <T : UPnPEvent> unsubscribe(subscriber: EventSubscriber<T>, eventType: Class<T>) {
        subscriptions[eventType]?.removeIf { it.subscriber == subscriber }
        
        // 如果该事件类型没有订阅者了，从映射表中移除
        if (subscriptions[eventType]?.isEmpty() == true) {
            subscriptions.remove(eventType)
        }
        
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "移除特定订阅: ${subscriber.javaClass.simpleName} -> ${eventType.simpleName}")
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        if (initialized.compareAndSet(true, false)) {
            // 清空订阅
            subscriptions.clear()
            
            // 关闭线程池
            executorService.shutdown()
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    executorService.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executorService.shutdownNow()
            }
            
            // 清空事件记录
            synchronized(recentEvents) {
                recentEvents.clear()
            }
            
            Log.d(TAG, "UPnP事件总线已释放")
        }
    }
    
    /**
     * 事件记录类
     */
    data class EventRecord(
        val eventType: String,
        val timestamp: Long
    )
    
    /**
     * 订阅信息类
     */
    private data class Subscription<T : UPnPEvent>(
        val subscriber: EventSubscriber<T>,
        val eventType: Class<T>,
        val threadMode: ThreadMode,
        val priority: Int
    )
    
    companion object {
        // 线程计数器
        private val THREAD_COUNTER = java.util.concurrent.atomic.AtomicInteger(0)
        
        // 默认优先级
        const val DEFAULT_PRIORITY = 5
        
        @Volatile
        private var INSTANCE: UPnPEventBus? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(): UPnPEventBus {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UPnPEventBus().also {
                    INSTANCE = it
                    it.initialize()
                }
            }
        }
        
        /**
         * 释放单例实例
         */
        fun releaseInstance() {
            synchronized(this) {
                INSTANCE?.release()
                INSTANCE = null
            }
        }
    }
}

/**
 * 事件处理线程模式
 */
enum class ThreadMode {
    /**
     * 在发布线程中处理事件
     */
    POSTING,
    
    /**
     * 在主线程中处理事件
     */
    MAIN,
    
    /**
     * 在后台线程中处理事件
     */
    BACKGROUND
}

/**
 * 事件订阅者接口
 */
interface EventSubscriber<T : UPnPEvent> {
    /**
     * 处理事件
     */
    fun onEvent(event: T)
}

/** * UPnP事件基础接口 */interface UPnPEvent {    /**     * 获取事件源     */    val source: Any?} 