package com.yinnho.upnpcast.interfaces

import java.net.NetworkInterface
import java.util.concurrent.ExecutorService

/**
 * UPnP服务配置接口
 * 提供UPnP服务的配置参数
 */
interface UpnpServiceConfiguration {
    val networkAddress: String
    val streamListenPort: Int
    val multicastPort: Int
    val executorService: ExecutorService
    val multicastInterface: NetworkInterface?
    val multicastAddress: String
    val searchTimeout: Long
    val maxRetries: Int
    val retryInterval: Long
    val datagramProcessor: DatagramProcessor

    fun shutdown()

    /**
     * 设置搜索超时时间
     * @param timeoutMs 超时时间（毫秒）
     */
    fun setSearchTimeout(timeoutMs: Long)
}

/**
 * UPnP服务接口
 * 提供UPnP核心服务功能
 */
interface UpnpService {
    val configuration: UpnpServiceConfiguration
    val router: Router
    val registry: Registry
    val controlPoint: ControlPoint

    fun shutdown()
}

/**
 * Android UPnP服务接口
 * 为Android平台提供的UPnP服务接口
 */
interface AndroidUpnpService {
    val registry: Registry
    val controlPoint: ControlPoint
    val router: Router
    val configuration: UpnpServiceConfiguration
} 