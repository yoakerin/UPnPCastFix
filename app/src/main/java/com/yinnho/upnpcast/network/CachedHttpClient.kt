package com.yinnho.upnpcast.network

import android.util.Log
import com.yinnho.upnpcast.api.DLNAException
import com.yinnho.upnpcast.api.DLNAErrorType
import com.yinnho.upnpcast.cache.UPnPCacheManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.nio.charset.Charset

/**
 * 缓存HTTP客户端
 * 
 * 提供基于UPnPCacheManager的HTTP请求缓存功能，减少重复网络请求，
 * 特别针对UPnP设备描述文件和服务SCPD文件等经常请求但很少变化的资源。
 */
class CachedHttpClient private constructor() {
    private val TAG = "CachedHttpClient"
    
    // 缓存管理器
    private val cacheManager = UPnPCacheManager.getInstance()
    
    // 连接超时时间（毫秒）
    private val CONNECT_TIMEOUT_MS = 8000
    
    // 读取超时时间（毫秒）
    private val READ_TIMEOUT_MS = 15000
    
    // 默认缓存时间（毫秒）
    private val DEFAULT_CACHE_TIME_MS = 3600000L // 1小时
    
    // 需要缓存的内容类型
    private val CACHEABLE_CONTENT_TYPES = listOf(
        "text/xml", 
        "application/xml",
        "text/html",
        "text/plain"
    )
    
    /**
     * 执行GET请求
     * 
     * @param url 请求URL
     * @param useCache 是否使用缓存
     * @param cacheTimeMs 缓存时间，0表示使用默认缓存时间
     * @return 响应数据
     */
    @Throws(DLNAException::class)
    fun get(url: String, useCache: Boolean = true, cacheTimeMs: Long = 0): Response {
        // 如果启用缓存，先检查缓存
        if (useCache) {
            cacheManager.getHttpResponse(url)?.let { cacheData ->
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "使用缓存: $url")
                }
                return Response(
                    cacheData,
                    emptyMap(), // 缓存中没有保存headers
                    200,
                    true
                )
            }
        }
        
        // 缓存未命中，执行网络请求
        return executeRequest(url, useCache, cacheTimeMs)
    }
    
    /**
     * 执行网络请求
     */
    private fun executeRequest(url: String, useCache: Boolean, cacheTimeMs: Long): Response {
        var connection: HttpURLConnection? = null
        
        try {
            // 创建连接
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "*/*")
            
            // 设置请求头
            connection.setRequestProperty("Connection", "close")
            connection.setRequestProperty("User-Agent", "UPnPCast/1.0")
            
            // 获取响应
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 读取响应数据
                val contentLength = connection.contentLength
                val buffer = if (contentLength > 0) {
                    ByteArray(contentLength)
                } else {
                    ByteArray(16384) // 默认缓冲区大小
                }
                
                val inputStream = connection.inputStream
                val responseData = inputStream.use { input ->
                    if (contentLength > 0) {
                        // 已知大小，直接读取
                        var bytesRead = 0
                        var offset = 0
                        while (offset < contentLength) {
                            bytesRead = input.read(buffer, offset, contentLength - offset)
                            if (bytesRead == -1) break
                            offset += bytesRead
                        }
                        buffer
                    } else {
                        // 未知大小，使用动态缓冲区
                        input.readBytes()
                    }
                }
                
                // 获取响应头
                val headers = mutableMapOf<String, String>()
                for (headerName in connection.headerFields.keys) {
                    headerName?.let {
                        val headerValue = connection.getHeaderField(it)
                        if (headerValue != null) {
                            headers[it] = headerValue
                        }
                    }
                }
                
                // 创建响应对象
                val response = Response(
                    responseData,
                    headers,
                    responseCode,
                    false
                )
                
                // 如果启用缓存且内容类型可缓存，则缓存响应
                if (useCache && isCacheable(headers)) {
                    val expireTime = cacheTimeMs.takeIf { it > 0 } ?: DEFAULT_CACHE_TIME_MS
                    cacheManager.cacheHttpResponse(url, responseData)
                }
                
                return response
            } else {
                // 非成功响应
                Log.w(TAG, "HTTP请求失败: $url, 状态码: $responseCode")
                throw DLNAException(DLNAErrorType.NETWORK_ERROR, "HTTP请求失败，状态码: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP请求异常: $url", e)
            if (e is DLNAException) throw e
            throw DLNAException(DLNAErrorType.NETWORK_ERROR, "HTTP请求异常: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * 检查响应是否可缓存
     */
    private fun isCacheable(headers: Map<String, String>): Boolean {
        // 检查内容类型是否可缓存
        val contentType = headers["Content-Type"] ?: return false
        return CACHEABLE_CONTENT_TYPES.any { contentType.contains(it, ignoreCase = true) }
    }
    
    /**
     * 清除HTTP缓存
     */
    fun clearCache() {
        cacheManager.clearCache(UPnPCacheManager.CacheType.HTTP_RESPONSE)
    }
    
    /**
     * HTTP响应数据类
     */
    data class Response(
        val data: ByteArray,
        val headers: Map<String, String>,
        val statusCode: Int,
        val fromCache: Boolean
    ) {
        // 为兼容性添加别名属性
        val responseData: ByteArray
            get() = data
            
        /**
         * 获取响应内容为字符串
         */
        fun getContentAsString(): String {
            val charset = getCharset() ?: "UTF-8"
            return String(data, Charset.forName(charset))
        }
        
        /**
         * 获取字符集
         */
        private fun getCharset(): String? {
            val contentType = headers["Content-Type"] ?: return null
            val charsetRegex = "charset=([\\w-]+)".toRegex(RegexOption.IGNORE_CASE)
            val matchResult = charsetRegex.find(contentType)
            return matchResult?.groupValues?.getOrNull(1)
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as Response
            
            if (!data.contentEquals(other.data)) return false
            if (headers != other.headers) return false
            if (statusCode != other.statusCode) return false
            if (fromCache != other.fromCache) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + headers.hashCode()
            result = 31 * result + statusCode
            result = 31 * result + fromCache.hashCode()
            return result
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: CachedHttpClient? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(): CachedHttpClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CachedHttpClient().also { INSTANCE = it }
            }
        }
    }
} 