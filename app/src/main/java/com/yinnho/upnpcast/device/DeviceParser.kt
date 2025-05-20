package com.yinnho.upnpcast.device

import android.util.Log
import com.yinnho.upnpcast.api.DLNAException
import com.yinnho.upnpcast.api.DLNAErrorType
import com.yinnho.upnpcast.cache.UPnPCacheManager
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.model.DLNAUrl
import com.yinnho.upnpcast.model.DeviceDetails
import com.yinnho.upnpcast.model.DeviceIdentity
import com.yinnho.upnpcast.model.ManufacturerInfo
import com.yinnho.upnpcast.model.ModelInfo
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.model.RemoteServiceInfo
import com.yinnho.upnpcast.model.DeviceType
import com.yinnho.upnpcast.core.XmlParser
import com.yinnho.upnpcast.network.CachedHttpClient
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 设备解析器 - 统一处理设备信息解析
 * 整合了DeviceDescriptionRetriever和SsdpDatagramProcessor中的解析逻辑
 */
object DeviceParser {
    private const val TAG = "DeviceParser"
    
    /**
     * 服务描述数据类
     */
    data class ServiceDescription(
        val serviceType: String,
        val serviceId: String,
        
        /**
         * 控制URL - 用于发送SOAP控制命令
         */
        val controlURL: String,
        
        /**
         * 事件订阅URL - 用于事件订阅和通知
         */
        val eventSubscriptionURL: String,
        
        /**
         * 服务描述文档URL - 用于获取服务的SCPD文档
         */
        val scpdURL: String
    )

    /**
     * 设备模型信息数据类
     */
    data class DeviceModelInfo(
        val name: String?,
        val description: String?
    )

    /**
     * 设备描述数据类
     */
    data class DeviceDescription(
        val manufacturer: String?,
        val model: DeviceModelInfo?,
        val services: List<ServiceDescription>,
        val friendlyName: String?
    )
    
    /**
     * 设备回调接口
     */
    interface DeviceCallback {
        fun onSuccess(description: DeviceDescription)
        fun onError(exception: Exception)
    }
    
    // 使用缓存HTTP客户端
    private val httpClient = CachedHttpClient.getInstance()
    
    // 缓存管理器
    private val cacheManager = UPnPCacheManager.getInstance()
    
    // 最大重试次数
    private const val MAX_RETRIES = 3
    
    // 正在处理的设备描述请求缓存，键为location
    private val pendingRequests = ConcurrentHashMap<String, MutableList<(Result<DeviceDescription>) -> Unit>>()
    
    // 线程池
    private val executor = Executors.newCachedThreadPool(object : ThreadFactory {
        override fun newThread(r: Runnable): Thread {
            return Thread(r, "DeviceParser-Worker").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY
            }
        }
    })
    
    // 同步锁对象
    private val requestLock = Any()
    
    /**
     * 从USN中提取UUID
     */
    fun extractUUID(usn: String): String {
        return usn.split("::").firstOrNull()?.let { uuid ->
            if (uuid.startsWith("uuid:")) {
                uuid.substring(5)
            } else {
                uuid
            }
        } ?: usn
    }
    
    /**
     * 判断是否是媒体渲染设备
     */
    fun isMediaRendererDevice(nt: String): Boolean {
        // 尝试匹配标准的MediaRenderer类型
        val isStandardRenderer = nt.contains("MediaRenderer", ignoreCase = true)
        
        // 检查其他可能的媒体设备服务类型
        val isMediaService = nt.contains("RenderingControl", ignoreCase = true) || 
                             nt.contains("AVTransport", ignoreCase = true) ||
                             nt.contains("ConnectionManager", ignoreCase = true)
        
        // 小米设备特征
        val isXiaomiDevice = nt.contains("mi-com", ignoreCase = true) ||
                             nt.contains("RController", ignoreCase = true)
                            
        // LG设备特征
        val isLGDevice = nt.contains("lge.com", ignoreCase = true)
        
        // 三星设备特征
        val isSamsungDevice = nt.contains("samsung.com", ignoreCase = true)
        
        return isStandardRenderer || isMediaService || isXiaomiDevice || isLGDevice || isSamsungDevice
    }
    
    /**
     * 解析HTTP请求头
     */
    fun parseHeaders(message: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val lines = message.lines()
        
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().uppercase()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }
        
        return headers
    }
    
    /**
     * 同步获取设备描述 - 使用缓存HTTP客户端实现
     * location参数用于下载，udn参数作为额外标识
     * 使用Result类型返回结果
     */
    fun retrieveDeviceDescription(location: String, udn: String): Result<DeviceDescription> {
        Log.d(TAG, "同步获取设备描述: $location (UDN: $udn)")
        
        var retryCount = 0
        var lastException: Exception? = null
        
        while (retryCount < MAX_RETRIES) {
            try {
                // 使用缓存HTTP客户端获取设备描述
                val response = httpClient.get(location, true)
                
                // 获取XML内容
                val xml = response.getContentAsString()
                
                // 解析设备描述XML
                val description = parseDeviceXml(xml)
                
                // 使用缓存管理器缓存服务描述
                if (description.services.isNotEmpty()) {
                    description.services.forEach { service ->
                        cacheManager.cacheServiceDescription(
                            service.serviceId,
                            service.serviceType,
                            "服务描述缓存 - ${description.friendlyName}"
                        )
                    }
                }
                
                Log.d(TAG, "成功获取设备描述: $location (UDN: $udn)")
                return Result.success(description)
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "获取设备描述失败，尝试重试 (${retryCount + 1}/$MAX_RETRIES): $location", e)
                retryCount++
                Thread.sleep(500) // 添加短暂延迟，避免连续失败
            }
        }
        
        // 所有重试都失败
        val errorMsg = lastException?.message ?: "未知错误"
        Log.e(TAG, "获取设备描述彻底失败: $location, $errorMsg")
        return Result.failure(lastException ?: IOException("获取设备描述失败：$errorMsg"))
    }
    
    /**
     * 异步获取设备描述
     */
    fun retrieveDeviceDescriptionAsync(location: String, udn: String, callback: (Result<DeviceDescription>) -> Unit) {
        // 使用同步块确保pendingRequests更新操作是原子的
        synchronized(requestLock) {
            // 检查是否有正在处理的相同请求
            val callbacks = pendingRequests.compute(location) { _, list -> 
                (list ?: mutableListOf()).apply { add(callback) }
            }!!
            
            // 如果有多个回调，表示已经有请求正在处理中，直接返回
            if (callbacks.size > 1) {
                Log.v(TAG, "设备描述已在处理中，合并请求: $location")
                return
            }
        }
        
        // 第一个请求，执行获取操作
        Log.d(TAG, "异步获取设备描述: $location (UDN: $udn)")
        executor.execute {
            try {
                val result = retrieveDeviceDescription(location, udn)
                
                // 获取所有等待的回调并通知
                val callbacksToNotify = pendingRequests.remove(location) ?: listOf()
                callbacksToNotify.forEach { cb ->
                    try { 
                        cb(result) 
                    } catch (e: Exception) { 
                        Log.e(TAG, "回调处理异常", e) 
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取设备描述时出现异常", e)
                
                // 获取所有等待的回调并通知错误
                val callbacksToNotify = pendingRequests.remove(location) ?: listOf()
                callbacksToNotify.forEach { cb ->
                    try { 
                        cb(Result.failure(e)) 
                    } catch (e: Exception) { 
                        Log.e(TAG, "回调处理异常", e) 
                    }
                }
            }
        }
    }
    
    /**
     * 解析设备描述XML
     */
    fun parseDeviceXml(xml: String): DeviceDescription {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(xml.reader())
            
            // 设备信息
            var manufacturer: String? = null
            var friendlyName: String? = null
            var modelName: String? = null
            var modelDescription: String? = null
            
            // 服务列表
            val services = mutableListOf<ServiceDescription>()
            
            // 当前元素
            var currentTag: String? = null
            var isServiceList = false
            var currentServiceType: String? = null
            var currentServiceId: String? = null
            var currentControlURL: String? = null
            var currentEventSubURL: String? = null
            var currentSCPDURL: String? = null
            
            // 开始解析XML
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        
                        when (currentTag) {
                            "serviceList" -> isServiceList = true
                            "service" -> {
                                // 重置服务信息
                                currentServiceType = null
                                currentServiceId = null
                                currentControlURL = null
                                currentEventSubURL = null
                                currentSCPDURL = null
                            }
                        }
                    }
                    
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "friendlyName" -> friendlyName = text
                                "manufacturer" -> manufacturer = text
                                "modelName" -> modelName = text
                                "modelDescription" -> modelDescription = text
                                
                                // 服务相关信息
                                "serviceType" -> if (isServiceList) currentServiceType = text
                                "serviceId" -> if (isServiceList) currentServiceId = text
                                "controlURL" -> if (isServiceList) currentControlURL = text
                                "eventSubURL" -> if (isServiceList) currentEventSubURL = text
                                "SCPDURL" -> if (isServiceList) currentSCPDURL = text
                            }
                        }
                    }
                    
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "serviceList" -> isServiceList = false
                            "service" -> {
                                // 如果有足够的信息，创建服务描述
                                if (currentServiceType != null && currentServiceId != null &&
                                    currentControlURL != null && currentEventSubURL != null && currentSCPDURL != null) {
                                    
                                    services.add(ServiceDescription(
                                        serviceType = currentServiceType,
                                        serviceId = currentServiceId,
                                        controlURL = currentControlURL,
                                        eventSubscriptionURL = currentEventSubURL,
                                        scpdURL = currentSCPDURL
                                    ))
                                }
                            }
                        }
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }
            
            // 创建设备模型信息
            val modelInfo = DeviceModelInfo(modelName, modelDescription)
            
            return DeviceDescription(
                manufacturer = manufacturer,
                model = modelInfo,
                services = services,
                friendlyName = friendlyName
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析设备描述XML出错", e)
            throw DLNAException(DLNAErrorType.PARSING_ERROR, "解析设备描述XML出错: ${e.message}", e)
        }
    }
    
    /**
     * 创建RemoteDevice对象
     * 从DeviceDescription创建RemoteDevice对象
     */
    fun createRemoteDevice(usn: String, description: DeviceDescription, location: String): RemoteDevice {
        try {
            val baseUrl = getBaseUrl(location)
            val url = URL(location)
            
            // 创建设备标识
            val deviceIdentity = DeviceIdentity(usn, url)
            
            // 创建制造商信息
            val manufacturerInfo = description.manufacturer?.let {
                ManufacturerInfo(it, null)
            }
            
            // 创建设备模型信息
            val modelInfo = description.model?.let { model ->
                ModelInfo(
                    name = model.name,
                    description = model.description,
                    number = null,
                    url = null
                )
            }
            
            // 创建设备详情
            val deviceDetails = DeviceDetails(
                friendlyName = description.friendlyName,
                manufacturerInfo = manufacturerInfo,
                modelInfo = modelInfo
            )
            
            // 创建服务列表
            val services = description.services.map { service ->
                val controlUrl = normalizeUrl(baseUrl, service.controlURL)
                val eventSubUrl = normalizeUrl(baseUrl, service.eventSubscriptionURL)
                val descUrl = normalizeUrl(baseUrl, service.scpdURL)
                
                RemoteServiceInfo(
                    serviceType = service.serviceType,
                    serviceId = service.serviceId,
                    controlURL = URL(controlUrl),
                    eventSubURL = URL(eventSubUrl),
                    descriptorURL = URL(descUrl)
                )
            }
            
            // 确定设备类型
            val deviceType = DeviceType(
                namespace = "schemas-upnp-org",
                type = "MediaRenderer",
                version = 1
            )
            
            // 创建并返回设备对象
            val device = RemoteDevice(
                type = deviceType,
                identity = deviceIdentity,
                details = deviceDetails,
                services = services
            )
            
            // 使用缓存管理器缓存设备
            cacheManager.cacheDevice(device.identity.udn, device)
            
            return device
        } catch (e: Exception) {
            Log.e(TAG, "创建RemoteDevice失败", e)
            throw DLNAException(DLNAErrorType.DEVICE_ERROR, "创建RemoteDevice失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取基础URL
     */
    private fun getBaseUrl(url: String): String {
        return try {
            val parsedUrl = URL(url)
            val path = parsedUrl.path
            val lastSlashIndex = path.lastIndexOf('/')
            val basePath = if (lastSlashIndex > 0) {
                path.substring(0, lastSlashIndex + 1)
            } else {
                "/"
            }
            
            "${parsedUrl.protocol}://${parsedUrl.host}:${parsedUrl.port}$basePath"
        } catch (e: Exception) {
            Log.e(TAG, "获取基础URL失败: $url", e)
            url
        }
    }
    
    /**
     * 标准化URL
     * 将相对路径URL转换为绝对路径URL
     */
    private fun normalizeUrl(baseUrl: String, url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else if (url.startsWith("/")) {
            // 处理绝对路径
            try {
                val base = URL(baseUrl)
                "http://${base.host}:${base.port}$url"
            } catch (e: Exception) {
                Log.e(TAG, "处理绝对路径URL失败: $url", e)
                "$baseUrl$url"
            }
        } else {
            // 处理相对路径
            if (baseUrl.endsWith("/")) "$baseUrl$url" else "$baseUrl/$url"
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        // 清理HTTP缓存
        httpClient.clearCache()
        
        // 清理设备和服务缓存
        cacheManager.clearCache(UPnPCacheManager.CacheType.DEVICE)
        cacheManager.clearCache(UPnPCacheManager.CacheType.DESCRIPTION)
        
        Log.d(TAG, "设备描述缓存已清空")
    }
} 