package com.yinnho.upnpcast

/**
 * UPnP服务配置接口 - 参考Cling配置设计
 */
interface UpnpServiceConfiguration {
    
    /**
     * 获取设备发现超时时间（毫秒）
     */
    fun getDiscoveryTimeoutMs(): Long
    
    /**
     * 获取连接超时时间（毫秒）
     */
    fun getConnectionTimeoutMs(): Long
    
    /**
     * 获取SSDP端口
     */
    fun getSsdpPort(): Int
    
    /**
     * 是否启用调试模式
     */
    fun isDebugEnabled(): Boolean
    
    /**
     * 获取用户代理字符串
     */
    fun getUserAgent(): String
    
    /**
     * 获取最大设备缓存数量
     */
    fun getMaxDeviceCacheSize(): Int
}

/**
 * 默认配置实现
 */
class DefaultUpnpServiceConfiguration : UpnpServiceConfiguration {
    
    override fun getDiscoveryTimeoutMs(): Long = 30000 // 30秒
    
    override fun getConnectionTimeoutMs(): Long = 10000 // 10秒
    
    override fun getSsdpPort(): Int = 1900
    
    override fun isDebugEnabled(): Boolean = false
    
    override fun getUserAgent(): String = "UPnPCast/1.0"
    
    override fun getMaxDeviceCacheSize(): Int = 100
} 