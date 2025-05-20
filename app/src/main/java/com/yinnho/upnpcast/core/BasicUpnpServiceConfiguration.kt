package com.yinnho.upnpcast.core

import android.content.Context
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.net.InetAddress

/**
 * 默认UPnP服务配置
 * 提供UPnP服务所需的基本配置参数和工厂方法
 */
class BasicUpnpServiceConfiguration(
    private val context: Context
) {
    // 存储可变的搜索超时时间
    private val searchTimeoutMs = AtomicReference<Int>(DEFAULT_SEARCH_TIMEOUT)

    // 默认超时设置
    companion object {
        // 多播响应等待时间
        const val DEFAULT_MULTICAST_RESPONSE_WAIT_TIME = 3000
        
        // 搜索超时时间
        const val DEFAULT_SEARCH_TIMEOUT = 10000
        
        // 控制点请求超时时间
        const val DEFAULT_CONTROL_POINT_REQUEST_TIMEOUT = 30000
        
        // 事件订阅超时时间
        const val DEFAULT_EVENT_SUBSCRIPTION_TIMEOUT = 1800
        
        // 网络连接超时
        const val DEFAULT_NETWORK_CONNECT_TIMEOUT = 10000
        
        // 数据读取超时
        const val DEFAULT_NETWORK_READ_TIMEOUT = 60000
        
        // 默认最大服务线程数
        const val DEFAULT_MAX_SERVICE_THREADS = 25
        
        // 默认核心线程数
        const val DEFAULT_CORE_THREADS = 5
        
        // 默认线程池队列大小
        const val DEFAULT_THREAD_QUEUE_SIZE = 1000
        
        // 线程池前缀
        const val THREAD_POOL_NAME = "upnp-pool"
    }
    
    // 线程工厂，创建自定义命名的线程
    private class UpnpThreadFactory(private val threadPoolName: String) : ThreadFactory {
        private val threadNumber = AtomicInteger(1)
        
        // 使用懒加载初始化线程组，确保空安全
        private val group: ThreadGroup? by lazy {
            val securityManager = System.getSecurityManager()
            securityManager?.threadGroup ?: Thread.currentThread().threadGroup
        }
        
        override fun newThread(r: Runnable): Thread {
            // 创建新线程时处理可能的空线程组
            val threadGroup = group ?: Thread.currentThread().threadGroup
            val thread = Thread(threadGroup, r, "$threadPoolName-${threadNumber.getAndIncrement()}", 0)
            
            if (thread.isDaemon) thread.isDaemon = false
            if (thread.priority != Thread.NORM_PRIORITY) thread.priority = Thread.NORM_PRIORITY
            return thread
        }
    }
    
    // 创建默认的Executor实例
    fun createDefaultExecutor(): ThreadPoolExecutor {
        return ThreadPoolExecutor(
            DEFAULT_CORE_THREADS, // 核心线程数
            DEFAULT_MAX_SERVICE_THREADS, // 最大线程数
            30L, // 空闲线程存活时间
            TimeUnit.SECONDS, // 时间单位
            ArrayBlockingQueue(DEFAULT_THREAD_QUEUE_SIZE), // 工作队列
            UpnpThreadFactory(THREAD_POOL_NAME) // 线程工厂
        )
    }
    
    // 获取多播监听端口
    fun getMulticastListenPort(): Int {
        return 1900
    }
    
    // 获取多播接口地址
    fun getMulticastInterface(): InetAddress? {
        return null // 返回null表示使用默认接口
    }
    
    // 获取多播响应等待时间
    fun getMulticastResponseWaitTime(): Int {
        return DEFAULT_MULTICAST_RESPONSE_WAIT_TIME
    }
    
    // 获取网络连接超时
    fun getNetworkConnectTimeout(): Int {
        return DEFAULT_NETWORK_CONNECT_TIMEOUT
    }
    
    // 获取网络数据读取超时
    fun getNetworkReadTimeout(): Int {
        return DEFAULT_NETWORK_READ_TIMEOUT
    }
    
    // 是否使用ipv6
    fun isUseIpv6(): Boolean {
        return false // 默认不使用ipv6
    }
    
    // 获取搜索超时时间
    fun getSearchTimeout(): Int {
        return searchTimeoutMs.get()
    }
    
    /**
     * 设置搜索超时时间
     * @param timeoutMs 超时时间（毫秒）
     */
    fun setSearchTimeout(timeoutMs: Long) {
        searchTimeoutMs.set(timeoutMs.toInt())
    }
    
    // 获取控制点请求超时时间
    fun getControlPointRequestTimeout(): Int {
        return DEFAULT_CONTROL_POINT_REQUEST_TIMEOUT
    }
    
    // 获取事件订阅超时时间
    fun getEventSubscriptionTimeout(): Int {
        return DEFAULT_EVENT_SUBSCRIPTION_TIMEOUT
    }
    
    // 获取应用上下文
    fun getContext(): Context {
        return context
    }
} 