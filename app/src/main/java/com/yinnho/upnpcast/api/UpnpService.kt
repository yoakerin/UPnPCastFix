package com.yinnho.upnpcast.api

import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.interfaces.Router
import com.yinnho.upnpcast.interfaces.UpnpServiceConfiguration

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