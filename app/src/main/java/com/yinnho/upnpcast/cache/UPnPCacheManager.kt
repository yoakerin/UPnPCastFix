package com.yinnho.upnpcast.cache

import android.content.Context
import android.util.LruCache
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.model.RemoteDevice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * UPnP缓存管理器
 * 负责管理设备、控制器、服务描述和HTTP响应的缓存
 */
object UPnPCacheManager {
    private const val TAG = "UPnPCacheManager"
    
    // 缓存类型枚举
    enum class CacheType {
        DEVICE,          // 设备缓存
        CONTROLLER,      // 控制器缓存
        DESCRIPTION,     // 服务描述缓存
        HTTP_RESPONSE    // HTTP响应缓存
    }
    
    // 缓存配置常量
    private const val DEFAULT_DEVICE_CACHE_SIZE = 20
    private const val DEFAULT_CONTROLLER_CACHE_SIZE = 10
    private const val DEFAULT_SERVICE_DESC_CACHE_SIZE = 50
    private const val DEFAULT_HTTP_CACHE_SIZE = 100
    
    // 表示内存使用级别
    enum class MemoryLevel {
        LOW,       // 低内存设备，最小缓存
        MEDIUM,    // 中等内存设备，标准缓存
        HIGH       // 高内存设备，大缓存
    }
    
    // 缓存对象
    private lateinit var deviceCache: LruCache<String, RemoteDevice>
    private lateinit var controllerCache: LruCache<String, Any>
    private lateinit var serviceDescriptionCache: LruCache<String, String>
    private lateinit var httpResponseCache: LruCache<String, ByteArray>
    
    // URL计数和过期时间跟踪
    private val urlAccessCount = ConcurrentHashMap<String, AtomicInteger>()
    private val urlExpiryTimes = ConcurrentHashMap<String, Long>()
    
    // 缓存命中率统计
    private val cacheStats = ConcurrentHashMap<CacheType, CacheStatistics>()
    
    // 低内存处理
    private var isLowMemory = false
    
    // 清理任务
    private var cleanupExecutor: ScheduledExecutorService? = null
    private const val CLEANUP_INTERVAL_MINUTES = 30L
    private const val URL_EXPIRY_HOURS = 12L
    
    // 内存级别
    private var memoryLevel = MemoryLevel.MEDIUM
    
    /**
     * 缓存统计信息类
     */
    class CacheStatistics {
        private val hits = AtomicLong(0)
        private val misses = AtomicLong(0)
        private val evictions = AtomicLong(0)
        
        fun recordHit() = hits.incrementAndGet()
        fun recordMiss() = misses.incrementAndGet()
        fun recordEviction() = evictions.incrementAndGet()
        
        fun getHits(): Long = hits.get()
        fun getMisses(): Long = misses.get()
        fun getEvictions(): Long = evictions.get()
        
        fun getHitRate(): Float {
            val totalRequests = hits.get() + misses.get()
            return if (totalRequests > 0) hits.get().toFloat() / totalRequests else 0f
        }
        
        fun reset() {
            hits.set(0)
            misses.set(0)
            evictions.set(0)
        }
    }
    
    /**
     * 获取UPnPCacheManager实例
     * 单例实现，确保只有一个缓存管理器实例
     */
    @JvmStatic
    fun getInstance(): UPnPCacheManager = this
    
    /**
     * 初始化缓存管理器
     * @param context 应用上下文
     */
    fun initialize(context: Context) {
        // 初始化缓存统计
        initCacheStatistics()
        
        // 确定内存级别
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryClass = activityManager.memoryClass
        
        memoryLevel = when {
            memoryClass < 64 -> MemoryLevel.LOW
            memoryClass < 128 -> MemoryLevel.MEDIUM
            else -> MemoryLevel.HIGH
        }
        
        // 根据内存级别配置缓存大小
        configureCaches(memoryLevel)
        
        // 启动清理任务
        scheduleCleanupTask()
        
        // 注册内存监控
        registerMemoryMonitor(context)
        
        EnhancedThreadManager.d(TAG, "UPnP缓存管理器已初始化，内存级别: ${memoryClass}MB，配置: $memoryLevel")
    }
    
    /**
     * 初始化缓存统计
     */
    private fun initCacheStatistics() {
        CacheType.values().forEach { cacheType ->
            cacheStats[cacheType] = CacheStatistics()
        }
    }
    
    /**
     * 注册内存监控
     */
    private fun registerMemoryMonitor(context: Context) {
        cleanupExecutor?.scheduleAtFixedRate({
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                
                val wasLowMemory = isLowMemory
                isLowMemory = memoryInfo.lowMemory
                
                // 如果内存状态变为低内存，触发缓存收缩
                if (!wasLowMemory && isLowMemory) {
                    EnhancedThreadManager.w(TAG, "检测到低内存状态，收缩缓存")
                    shrinkCaches()
                }
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "内存监控异常", e)
            }
        }, 1, 5, TimeUnit.MINUTES)
    }
    
    /**
     * 在低内存情况下收缩缓存
     */
    private fun shrinkCaches() {
        if (!::httpResponseCache.isInitialized) return
        
        // 清除低价值的HTTP缓存项
        val now = System.currentTimeMillis()
        val urlsToRemove = mutableListOf<String>()
        
        // 查找超过一半过期时间的URL
        urlExpiryTimes.forEach { (url, expiryTime) ->
            val remainingTime = expiryTime - now
            val totalTtl = expiryTime - (urlExpiryTimes[url] ?: now)
            
            // 如果剩余时间小于总TTL的一半，可以移除
            if (remainingTime < totalTtl / 2) {
                urlsToRemove.add(url)
            }
        }
        
        // 移除找到的项
        urlsToRemove.forEach { url ->
            httpResponseCache.remove(url)
            urlExpiryTimes.remove(url)
            urlAccessCount.remove(url)
        }
        
        EnhancedThreadManager.d(TAG, "低内存处理：移除了${urlsToRemove.size}个HTTP缓存项")
    }
    
    /**
     * 更新缓存管理器配置
     * @param level 新的内存级别
     */
    fun updateConfiguration(level: MemoryLevel = memoryLevel) {
        // 应用新的配置
        memoryLevel = level
        configureCaches(level)
        
        val memoryClass = when(level) {
            MemoryLevel.LOW -> "<64MB"
            MemoryLevel.MEDIUM -> "64-128MB"
            MemoryLevel.HIGH -> ">128MB"
        }
        
        EnhancedThreadManager.d(TAG, "缓存管理器配置已更新，内存等级: ${memoryClass}，缓存配置: 设备=${deviceCache.maxSize()}, 控制器=${controllerCache.maxSize()}, 服务=${serviceDescriptionCache.maxSize()}, HTTP=${httpResponseCache.maxSize()}")
    }
    
    /**
     * 配置所有缓存
     * @param level 内存级别
     */
    private fun configureCaches(level: MemoryLevel) {
        // 根据内存级别调整缓存大小
        val factor = when(level) {
            MemoryLevel.LOW -> 0.5
            MemoryLevel.MEDIUM -> 1.0
            MemoryLevel.HIGH -> 2.0
        }
        
        // 创建设备缓存，添加eviction监听
        deviceCache = object : LruCache<String, RemoteDevice>((DEFAULT_DEVICE_CACHE_SIZE * factor).toInt()) {
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: RemoteDevice, newValue: RemoteDevice?) {
                super.entryRemoved(evicted, key, oldValue, newValue)
                if (evicted) {
                    cacheStats[CacheType.DEVICE]?.recordEviction()
                }
            }
        }
        
        // 创建控制器缓存，添加eviction监听
        controllerCache = object : LruCache<String, Any>((DEFAULT_CONTROLLER_CACHE_SIZE * factor).toInt()) {
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: Any, newValue: Any?) {
                super.entryRemoved(evicted, key, oldValue, newValue)
                if (evicted) {
                    cacheStats[CacheType.CONTROLLER]?.recordEviction()
                    // 尝试释放控制器资源
                    if (oldValue != newValue) {
                        try {
                            oldValue.javaClass.getMethod("release").invoke(oldValue)
                        } catch (e: Exception) {
                            // 忽略异常
                        }
                    }
                }
            }
        }
        
        // 创建服务描述缓存，添加eviction监听
        serviceDescriptionCache = object : LruCache<String, String>((DEFAULT_SERVICE_DESC_CACHE_SIZE * factor).toInt()) {
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: String, newValue: String?) {
                super.entryRemoved(evicted, key, oldValue, newValue)
                if (evicted) {
                    cacheStats[CacheType.DESCRIPTION]?.recordEviction()
                }
            }
        }
        
        // 创建HTTP响应缓存，添加eviction监听
        httpResponseCache = object : LruCache<String, ByteArray>((DEFAULT_HTTP_CACHE_SIZE * factor).toInt()) {
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: ByteArray, newValue: ByteArray?) {
                super.entryRemoved(evicted, key, oldValue, newValue)
                if (evicted) {
                    cacheStats[CacheType.HTTP_RESPONSE]?.recordEviction()
                    // 同时清理URL相关数据
                    urlExpiryTimes.remove(key)
                    urlAccessCount.remove(key)
                }
            }
            
            // 自定义size计算，更准确地衡量内存使用
            override fun sizeOf(key: String, value: ByteArray): Int {
                // 返回KB为单位的大小，而不是默认的1
                return value.size / 1024 + 1
            }
        }
        
        EnhancedThreadManager.d(TAG, "缓存大小已配置 - 设备:${deviceCache.maxSize()}, 控制器:${controllerCache.maxSize()}, 服务:${serviceDescriptionCache.maxSize()}, HTTP:${httpResponseCache.maxSize()}")
    }
    
    /**
     * 安排定期清理任务
     */
    private fun scheduleCleanupTask() {
        cleanupExecutor?.shutdown()
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleAtFixedRate(
                { performCleanup() },
                CLEANUP_INTERVAL_MINUTES,
                CLEANUP_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
        }
    }
    
    /**
     * 缓存设备
     * @param deviceId 设备ID
     * @param device 设备对象
     */
    fun cacheDevice(deviceId: String, device: RemoteDevice) {
        if (!::deviceCache.isInitialized) return
        
        deviceCache.put(deviceId, device)
        EnhancedThreadManager.d(TAG, "设备已缓存: ${device.details.friendlyName} (${deviceId})")
    }
    
    /**
     * 获取缓存的设备
     * @param deviceId 设备ID
     * @return 缓存的设备对象，如果不存在则返回null
     */
    fun getDevice(deviceId: String): RemoteDevice? {
        if (!::deviceCache.isInitialized) return null
        
        val result = deviceCache.get(deviceId)
        if (result != null) {
            cacheStats[CacheType.DEVICE]?.recordHit()
        } else {
            cacheStats[CacheType.DEVICE]?.recordMiss()
        }
        
        return result
    }
    
    /**
     * 缓存控制器
     * @param deviceId 设备ID
     * @param controller 控制器对象
     */
    fun cacheController(deviceId: String, controller: Any) {
        if (!::controllerCache.isInitialized) return
        
        // 如果已有对象，先安全释放
        val oldController = controllerCache.get(deviceId)
        if (oldController != null && oldController !== controller) {
            try {
                // 尝试调用release方法如果存在
                oldController.javaClass.getMethod("release").invoke(oldController)
            } catch (e: Exception) {
                EnhancedThreadManager.w(TAG, "释放控制器资源失败: ${e.message}")
            }
        }
        
        controllerCache.put(deviceId, controller)
        EnhancedThreadManager.d(TAG, "控制器已缓存: $deviceId (${controller.javaClass.simpleName})")
    }
    
    /**
     * 获取缓存的控制器
     * @param deviceId 设备ID
     * @param type 控制器类型
     * @return 缓存的控制器对象，如果不存在或类型不匹配则返回null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getController(deviceId: String, type: Class<T>): T? {
        if (!::controllerCache.isInitialized) return null
        
        val controller = controllerCache.get(deviceId)
        if (controller != null) {
            if (type.isInstance(controller)) {
                cacheStats[CacheType.CONTROLLER]?.recordHit()
                return controller as T
            }
        } 
        
        cacheStats[CacheType.CONTROLLER]?.recordMiss()
        return null
    }
    
    /**
     * 缓存HTTP响应
     * @param url 请求URL
     * @param data 响应数据
     * @param expiration 过期时间（毫秒），默认为12小时
     */
    fun cacheHttpResponse(url: String, data: ByteArray, expiration: Long = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(URL_EXPIRY_HOURS)) {
        if (!::httpResponseCache.isInitialized) return
        
        // 在低内存情况下，减少缓存时间
        val actualExpiration = if (isLowMemory) {
            System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1) // 低内存时只缓存1小时
        } else {
            expiration
        }
        
        httpResponseCache.put(url, data)
        urlExpiryTimes[url] = actualExpiration
        urlAccessCount.getOrPut(url) { AtomicInteger(0) }.incrementAndGet()
        
        EnhancedThreadManager.d(TAG, "HTTP响应已缓存: $url (${data.size}字节，过期时间: ${java.text.SimpleDateFormat("MM-dd HH:mm").format(actualExpiration)})")
    }
    
    /**
     * 获取缓存的HTTP响应
     * @param url 请求URL
     * @return 缓存的响应数据，如果不存在或已过期则返回null
     */
    fun getHttpResponse(url: String): ByteArray? {
        if (!::httpResponseCache.isInitialized) return null
        
        val currentTime = System.currentTimeMillis()
        val expiryTime = urlExpiryTimes[url] ?: 0
        
        if (currentTime > expiryTime) {
            // 已过期，移除缓存
            httpResponseCache.remove(url)
            urlExpiryTimes.remove(url)
            cacheStats[CacheType.HTTP_RESPONSE]?.recordMiss()
            return null
        }
        
        // 增加访问计数
        urlAccessCount.getOrPut(url) { AtomicInteger(0) }.incrementAndGet()
        
        val result = httpResponseCache.get(url)
        if (result != null) {
            cacheStats[CacheType.HTTP_RESPONSE]?.recordHit()
        } else {
            cacheStats[CacheType.HTTP_RESPONSE]?.recordMiss()
        }
        
        return result
    }
    
    /**
     * 缓存服务描述
     * @param serviceId 服务ID
     * @param serviceType 服务类型
     * @param description 描述内容
     */
    fun cacheServiceDescription(serviceId: String, serviceType: String, description: String) {
        if (!::serviceDescriptionCache.isInitialized) return
        
        val key = "$serviceId:$serviceType"
        serviceDescriptionCache.put(key, description)
        EnhancedThreadManager.d(TAG, "服务描述已缓存: $serviceId ($serviceType)")
    }
    
    /**
     * 获取缓存的服务描述
     * @param serviceId 服务ID
     * @param serviceType 服务类型
     * @return 缓存的服务描述，如果不存在则返回null
     */
    fun getServiceDescription(serviceId: String, serviceType: String): String? {
        if (!::serviceDescriptionCache.isInitialized) return null
        
        val key = "$serviceId:$serviceType"
        val result = serviceDescriptionCache.get(key)
        
        if (result != null) {
            cacheStats[CacheType.DESCRIPTION]?.recordHit()
        } else {
            cacheStats[CacheType.DESCRIPTION]?.recordMiss()
        }
        
        return result
    }
    
    /**
     * 清除指定类型的缓存
     * @param type 缓存类型
     */
    fun clearCache(type: CacheType) {
        when (type) {
            CacheType.DEVICE -> clearDeviceCache()
            CacheType.CONTROLLER -> clearControllerCache()
            CacheType.DESCRIPTION -> clearServiceDescriptionCache() 
            CacheType.HTTP_RESPONSE -> clearHttpResponseCache()
        }
    }
    
    /**
     * 清除设备缓存
     */
    fun clearDeviceCache() {
        if (::deviceCache.isInitialized) {
            deviceCache.evictAll()
            EnhancedThreadManager.d(TAG, "设备缓存已清除")
        }
    }
    
    /**
     * 清除控制器缓存
     */
    fun clearControllerCache() {
        if (::controllerCache.isInitialized) {
            controllerCache.evictAll()
            EnhancedThreadManager.d(TAG, "控制器缓存已清除")
        }
    }
    
    /**
     * 清除服务描述缓存
     */
    fun clearServiceDescriptionCache() {
        if (::serviceDescriptionCache.isInitialized) {
            serviceDescriptionCache.evictAll()
            EnhancedThreadManager.d(TAG, "服务描述缓存已清除")
        }
    }
    
    /**
     * 清除HTTP响应缓存
     */
    fun clearHttpResponseCache() {
        if (::httpResponseCache.isInitialized) {
            httpResponseCache.evictAll()
            urlExpiryTimes.clear()
            urlAccessCount.clear()
            EnhancedThreadManager.d(TAG, "HTTP响应缓存已清除")
        }
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCaches() {
        EnhancedThreadManager.d(TAG, "清除所有缓存")
        clearDeviceCache()
        clearControllerCache()
        clearServiceDescriptionCache()
        clearHttpResponseCache()
        
        // 重置统计信息
        cacheStats.values.forEach { it.reset() }
    }
    
    /**
     * 执行缓存清理
     * 移除过期的HTTP缓存和低访问频率的项
     */
    private fun performCleanup() {
        if (!::httpResponseCache.isInitialized) return
        
        try {
            val currentTime = System.currentTimeMillis()
            val expiredUrls = mutableListOf<String>()
            
            // 查找过期URL
            urlExpiryTimes.forEach { (url, expiryTime) ->
                if (currentTime > expiryTime) {
                    expiredUrls.add(url)
                }
            }
            
            // 移除过期项
            var removedCount = 0
            expiredUrls.forEach { url ->
                httpResponseCache.remove(url)
                urlExpiryTimes.remove(url)
                urlAccessCount.remove(url)
                removedCount++
            }
            
            // 内存压力管理：如果HTTP缓存接近满，则移除最少访问的项
            if (httpResponseCache.size() > httpResponseCache.maxSize() * 0.8) {
                val leastUsedItems = urlAccessCount.entries
                    .sortedBy { it.value.get() }
                    .take(httpResponseCache.size() / 5) // 移除20%的最少使用项
                    .map { it.key }
                
                leastUsedItems.forEach { url ->
                    httpResponseCache.remove(url)
                    urlExpiryTimes.remove(url)
                    urlAccessCount.remove(url)
                    removedCount++
                }
            }
            
            EnhancedThreadManager.d(TAG, "缓存清理完成，移除了${removedCount}个过期缓存项和${expiredUrls.size}个过期URL计数")
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "缓存清理过程中发生错误", e)
        }
    }
    
    /**
     * 获取当前缓存的摘要状态
     * @return 包含各种缓存状态的字符串
     */
    fun getCacheSummary(): String {
        if (!::deviceCache.isInitialized) return "缓存未初始化"
        
        return buildString {
            append("缓存状态摘要:\n")
            append("- 设备缓存: ${deviceCache.size()}/${deviceCache.maxSize()} (命中率: ${formatPercent(cacheStats[CacheType.DEVICE]?.getHitRate() ?: 0f)})\n")
            append("- 控制器缓存: ${controllerCache.size()}/${controllerCache.maxSize()} (命中率: ${formatPercent(cacheStats[CacheType.CONTROLLER]?.getHitRate() ?: 0f)})\n")
            append("- 服务描述缓存: ${serviceDescriptionCache.size()}/${serviceDescriptionCache.maxSize()} (命中率: ${formatPercent(cacheStats[CacheType.DESCRIPTION]?.getHitRate() ?: 0f)})\n")
            append("- HTTP响应缓存: ${httpResponseCache.size()}/${httpResponseCache.maxSize()} (命中率: ${formatPercent(cacheStats[CacheType.HTTP_RESPONSE]?.getHitRate() ?: 0f)})\n")
            append("- URL跟踪项: ${urlExpiryTimes.size}\n")
            append("- 内存状态: ${if (isLowMemory) "低内存" else "正常"}")
        }
    }
    
    /**
     * 格式化百分比
     */
    private fun formatPercent(value: Float): String {
        return String.format("%.1f%%", value * 100)
    }
    
    /**
     * 获取特定类型的缓存统计信息
     */
    fun getCacheStatistics(type: CacheType): CacheStatistics? {
        return cacheStats[type]
    }
    
    /**
     * 检查特定设备是否在缓存中
     * @param deviceId 设备ID
     * @return 是否存在于缓存中
     */
    fun hasDeviceInCache(deviceId: String): Boolean {
        if (!::deviceCache.isInitialized) return false
        return deviceCache.get(deviceId) != null
    }
    
    /**
     * 检查控制器是否在缓存中
     * @param deviceId 设备ID
     * @return 是否有关联的控制器
     */
    fun isControllerCached(deviceId: String): Boolean {
        if (!::controllerCache.isInitialized) return false
        return controllerCache.get(deviceId) != null
    }
    
    /**
     * 释放缓存管理器资源
     */
    fun release() {
        EnhancedThreadManager.d(TAG, "释放缓存管理器资源")
        
        // 关闭清理任务执行器
        cleanupExecutor?.let {
            try {
                it.shutdownNow()
                cleanupExecutor = null
            } catch (e: Exception) {
                EnhancedThreadManager.w(TAG, "关闭执行器异常", e)
            }
        }
        
        // 清除所有缓存
        if (::deviceCache.isInitialized) deviceCache.evictAll()
        if (::controllerCache.isInitialized) controllerCache.evictAll()
        if (::serviceDescriptionCache.isInitialized) serviceDescriptionCache.evictAll()
        if (::httpResponseCache.isInitialized) httpResponseCache.evictAll()
        
        urlExpiryTimes.clear()
        urlAccessCount.clear()
        
        // 记录最终统计信息
        logFinalStatistics()
    }
    
    /**
     * 记录最终缓存统计信息
     */
    private fun logFinalStatistics() {
        if (cacheStats.isEmpty()) return
        
        EnhancedThreadManager.d(TAG, "缓存最终统计:")
        CacheType.values().forEach { type ->
            val stats = cacheStats[type] ?: return@forEach
            EnhancedThreadManager.d(TAG, "- $type: 命中=${stats.getHits()}, 未命中=${stats.getMisses()}, " +
                                   "淘汰=${stats.getEvictions()}, 命中率=${formatPercent(stats.getHitRate())}")
        }
    }
} 