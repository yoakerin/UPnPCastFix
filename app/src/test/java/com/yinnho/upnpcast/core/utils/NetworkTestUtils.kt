package com.yinnho.upnpcast.utils

import java.net.URL

/**
 * 网络测试工具类
 * 包含网络相关测试的辅助函数
 */
object NetworkTestUtils {
    
    /**
     * 从URL字符串中提取主机和端口
     * @param urlString URL字符串
     * @return 主机和端口的Pair对象
     */
    fun extractHostAndPort(urlString: String): Pair<String, Int> {
        val url = URL(urlString)
        val host = url.host
        val port = if (url.port == -1) {
            if (url.protocol == "https") 443 else 80
        } else {
            url.port
        }
        return Pair(host, port)
    }
    
    /**
     * 判断是否是安全URL
     * @param urlString URL字符串
     * @return 是否是安全URL
     */
    fun isSecureUrl(urlString: String): Boolean {
        return urlString.lowercase().startsWith("https")
    }
    
    /**
     * 判断URL是否是本地地址
     * @param urlString URL字符串
     * @return 是否是本地地址
     */
    fun isLocalAddress(urlString: String): Boolean {
        val host = extractHostAndPort(urlString).first
        return host == "localhost" || 
               host == "127.0.0.1" || 
               host.startsWith("192.168.") || 
               host.startsWith("10.") || 
               host.startsWith("172.16.")
    }
} 