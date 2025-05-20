package com.yinnho.upnpcast.device

import com.yinnho.upnpcast.interfaces.DLNAService
import com.yinnho.upnpcast.interfaces.IDevice
import com.yinnho.upnpcast.model.RemoteServiceInfo
import com.yinnho.upnpcast.model.DeviceDetails
import com.yinnho.upnpcast.model.DeviceIdentity
import com.yinnho.upnpcast.model.DeviceType
import com.yinnho.upnpcast.model.ServiceId
import com.yinnho.upnpcast.model.ServiceType
import java.util.logging.Logger

/**
 * DLNA设备基类
 * 用于统一远程与本地设备
 */
abstract class Device : IDevice {
    companion object {
        // Logger for debug
        private val logger = Logger.getLogger(Device::class.java.name)
    }

    abstract override val type: DeviceType
    abstract override val identity: DeviceIdentity
    abstract override val details: DeviceDetails
    abstract override val services: List<RemoteServiceInfo>
    abstract override val embeddedDevices: List<IDevice>
    abstract override val displayName: String
    abstract override val displayString: String

    override fun <T : DLNAService> findService(serviceType: ServiceType): T? {
        logger.info("Looking for service: $serviceType")
        val service = services.find { it.serviceType.contains(serviceType.toString()) }
        
        // 我们无法直接将RemoteServiceInfo转换为DLNAService，这里只能做类型擦除并使用as
        @Suppress("UNCHECKED_CAST")
        return service as? T
    }

    override fun findServiceById(serviceId: ServiceId): RemoteServiceInfo? {
        logger.info("Looking for service by id: $serviceId")
        val service = services.find { it.serviceId.contains(serviceId.toString()) }
        
        return service
    }
}