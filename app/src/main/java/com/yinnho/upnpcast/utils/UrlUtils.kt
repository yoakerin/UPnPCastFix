package com.yinnho.upnpcast.utils

import com.yinnho.upnpcast.core.EnhancedThreadManager
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * URL工具类
 * 提供URL处理相关的工具方法
 */
object UrlUtils {
    private const val TAG = "UrlUtils"
    
    /**
     * 从URL中提取主机名和端口
     * @param urlString URL字符串
     * @return 主机和端口的对
     */
    fun extractHostAndPort(urlString: String): Pair<String, Int> {
        try {
            val url = URL(urlString)
            val host = url.host
            val port = if (url.port == -1) {
                // 根据协议使用默认端口
                when (url.protocol.lowercase()) {
                    "https" -> 443
                    "http" -> 80
                    "ftp" -> 21
                    else -> 80
                }
            } else {
                url.port
            }
            return Pair(host, port)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "URL解析失败: $urlString", e)
            return Pair("", -1)
        }
    }
    
    /**
     * 从URL获取协议
     * @param urlString URL字符串
     * @return 协议，如http或https，解析失败返回空字符串
     */
    fun getProtocol(urlString: String): String {
        return try {
            URL(urlString).protocol
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 获取URL的路径部分
     * @param urlString URL字符串
     * @return 路径，解析失败返回空字符串
     */
    fun getPath(urlString: String): String {
        return try {
            URL(urlString).path
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 检查URL是否包含查询参数
     * @param urlString URL字符串
     * @return 是否包含查询参数
     */
    fun hasQuery(urlString: String): Boolean {
        return try {
            !URL(urlString).query.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 将相对路径和基础URL组合成完整URL
     * @param baseUrl 基础URL
     * @param relativePath 相对路径
     * @return 完整URL
     */
    fun combineUrl(baseUrl: String, relativePath: String): String {
        try {
            // 处理相对路径
            if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
                return relativePath // 已经是完整URL
            }
            
            val base = if (!baseUrl.endsWith("/")) "$baseUrl/" else baseUrl
            val path = if (relativePath.startsWith("/")) relativePath.substring(1) else relativePath
            
            return "$base$path"
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "URL组合失败", e)
            return baseUrl
        }
    }
    
    /**
     * 标准化URL，移除多余的斜杠等
     * @param urlString URL字符串
     * @return 标准化后的URL
     */
    fun normalizeUrl(urlString: String): String {
        try {
            val url = URL(urlString)
            val protocol = url.protocol
            val host = url.host
            val port = if (url.port == -1) "" else ":${url.port}"
            var path = url.path
            
            // 确保路径以/开头
            if (!path.startsWith("/") && path.isNotEmpty()) {
                path = "/$path"
            }
            
            val query = if (url.query.isNullOrEmpty()) "" else "?${url.query}"
            
            return "$protocol://$host$port$path$query"
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "URL标准化失败: $urlString", e)
            return urlString
        }
    }
    
    /**
     * 获取URL的基础部分（协议+主机+端口）
     * @param urlString URL字符串
     * @return 基础URL
     */
    fun getBaseUrl(urlString: String): String {
        return try {
            val url = URL(urlString)
            val protocol = url.protocol
            val host = url.host
            val port = if (url.port == -1) "" else ":${url.port}"
            
            "$protocol://$host$port"
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "URL基础部分提取失败: $urlString", e)
            ""
        }
    }
    
    /**
     * 重载方法：从URL对象获取基础URL
     * @param url URL对象
     * @return 基础URL
     */
    fun getBaseUrl(url: URL?): String {
        if (url == null) return ""
        
        return try {
            val protocol = url.protocol
            val host = url.host
            val port = if (url.port == -1) "" else ":${url.port}"
            
            "$protocol://$host$port"
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "URL基础部分提取失败", e)
            ""
        }
    }
    
    /**
     * 预处理媒体URL，处理不同类型媒体URL的差异
     * @param url 原始URL
     * @param isXiaomiDevice 是否为小米设备
     * @return 处理后的URL
     */
    fun preprocessMediaUrl(url: String, isXiaomiDevice: Boolean = false): String {
        // 针对不同设备处理URL
        var processedUrl = url
        
        if (isXiaomiDevice) {
            EnhancedThreadManager.d(TAG, "预处理小米设备URL: $url")
            
            if (url.contains("m3u8") && url.contains("?")) {
                // 小米设备对m3u8 URL参数处理
                processedUrl = url.split("?")[0]
                EnhancedThreadManager.d(TAG, "小米设备: 移除m3u8 URL参数: $processedUrl")
            } else if (url.contains("myqcloud.com") || url.contains("cos.ap-")) {
                // 针对腾讯云COS链接的特殊处理
                try {
                    processedUrl = formatXiaomiMediaUrl(url)
                    EnhancedThreadManager.d(TAG, "小米设备: 特殊处理腾讯云URL: $processedUrl")
                } catch (e: Exception) {
                    EnhancedThreadManager.e(TAG, "处理腾讯云URL异常，使用原始URL", e)
                }
            }
        }
        
        // 检查处理后的URL是否过长
        if (processedUrl.length > 1000) {
            EnhancedThreadManager.w(TAG, "处理后的URL仍然很长(${processedUrl.length}字符)，可能影响兼容性")
        }
        
        return processedUrl
    }
    
    /**
     * 处理小米设备的媒体URL
     * @param url 原始URL
     * @return 处理后的URL
     */
    private fun formatXiaomiMediaUrl(url: String): String {
        // 针对腾讯云URL的特殊处理
        if (url.contains("myqcloud.com") || url.contains("cos.ap-")) {
            // 移除部分参数，保留基础参数
            val baseUrl = url.split("?")[0]
            val params = extractQueryParams(url)
            
            // 保留必要参数
            val essentialParams = listOf("sign", "t")
            val filteredParams = params.filter { (key, _) -> 
                essentialParams.contains(key.lowercase()) 
            }
            
            // 重建URL
            return if (filteredParams.isEmpty()) {
                baseUrl
            } else {
                val queryString = filteredParams.entries.joinToString("&") { 
                    "${it.key}=${it.value}" 
                }
                "$baseUrl?$queryString"
            }
        }
        
        return url
    }
    
    /**
     * 预处理控制URL，处理不同设备的URL格式差异
     * @param controlURL 控制URL
     * @param friendlyName 设备名称，用于识别设备类型
     * @return 处理后的URL
     */
    fun preprocessControlURL(controlURL: String, friendlyName: String = ""): String {
        // 首先检查URL格式是否正确
        if (!controlURL.startsWith("http")) {
            EnhancedThreadManager.w(TAG, "非HTTP协议的控制URL: $controlURL")
            
            // 可能是相对路径，进行基本的检查和清理
            val cleanPath = controlURL.trim().replace("\\", "/")
            EnhancedThreadManager.w(TAG, "清理后的相对路径: $cleanPath")
            return cleanPath
        }
        
        try {
            // 解析URL，确保格式正确
            val url = URL(controlURL)
            val host = url.host
            val urlPort = if (url.port == -1) 80 else url.port
            val path = url.path.trim()
            
            // 规范化路径，确保以/开头
            val normalizedPath = if (path.startsWith("/")) path else "/$path"
            
            // 小米设备特殊处理
            if (isHostXiaomiDevice(host, friendlyName)) {
                EnhancedThreadManager.d(TAG, "检测到小米设备，应用专用URL格式")
                return "http://$host:$urlPort/upnp/control/AVTransport1"
            }
            
            // 重建URL
            val processedURL = "http://$host:$urlPort$normalizedPath"
            
            EnhancedThreadManager.d(TAG, "规范化URL: $processedURL")
            return processedURL
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "预处理URL失败: ${e.message}")
            
            // 如果URL解析失败，尝试基本格式修复
            if (controlURL.contains(":") && !controlURL.contains("://")) {
                // 可能缺少协议
                return "http://$controlURL"
            }
            
            return controlURL
        }
    }
    
    /**
     * 判断主机是否为小米设备
     * @param host 主机名
     * @param friendlyName 设备友好名称
     * @return 是否为小米设备
     */
    private fun isHostXiaomiDevice(host: String, friendlyName: String): Boolean {
        return host.contains("mi", ignoreCase = true) || 
               host.contains("xiaomi", ignoreCase = true) ||
               friendlyName.contains("小米", ignoreCase = true)
    }
    
    /**
     * 从URL中提取查询参数
     * @param url 原始URL
     * @return 参数映射
     */
    fun extractQueryParams(url: String): Map<String, String> {
        try {
            val query = URL(url).query ?: return emptyMap()
            
            return query.split("&").associate { 
                val parts = it.split("=", limit = 2)
                val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name())
                val value = if (parts.size > 1) {
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                } else {
                    ""
                }
                key to value
            }
        } catch (e: Exception) {
            return emptyMap()
        }
    }
    
    /**
     * 判断URL是否安全
     * @param url 原始URL
     * @return 是否为HTTPS URL
     */
    fun isSecureUrl(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true)
    }
    
    /**
     * 解析URL并返回主机名和端口
     * @param url 原始URL
     * @return 主机名和端口的Pair，解析失败则返回null
     */
    fun getHostAndPort(url: String): Pair<String, Int>? {
        return try {
            val parsedUrl = URL(url)
            val host = parsedUrl.host
            val port = if (parsedUrl.port == -1) {
                if (parsedUrl.protocol.equals("https", ignoreCase = true)) 443 else 80
            } else {
                parsedUrl.port
            }
            host to port
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取URL的文件扩展名
     * @param url 原始URL
     * @return 文件扩展名，无扩展名或解析失败则返回空字符串
     */
    fun getFileExtension(url: String): String {
        return try {
            val path = URL(url).path
            val dotIndex = path.lastIndexOf('.')
            if (dotIndex != -1 && dotIndex < path.length - 1) {
                path.substring(dotIndex + 1).lowercase()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 根据文件扩展名判断MIME类型
     * @param url 媒体URL
     * @return 对应的MIME类型
     */
    fun getMimeTypeFromUrl(url: String): String {
        val extension = getFileExtension(url)
        return when (extension) {
            "mp4" -> "video/mp4"
            "m3u8" -> "application/x-mpegURL"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "flv" -> "video/x-flv"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            else -> "video/mp4" // 默认为MP4格式
        }
    }
}

/**
 * URL扩展函数
 */

/**
 * 判断URL是否有效
 */
fun URL?.isValid(): Boolean {
    return this != null && this.host.isNotEmpty()
}