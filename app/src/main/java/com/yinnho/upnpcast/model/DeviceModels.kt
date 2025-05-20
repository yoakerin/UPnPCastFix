package com.yinnho.upnpcast.model

import android.util.Log
import com.yinnho.upnpcast.device.Device
import com.yinnho.upnpcast.interfaces.IDevice
import com.yinnho.upnpcast.device.getDisplayName
import com.yinnho.upnpcast.device.getDisplayString
import com.yinnho.upnpcast.device.locationKey
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 设备标识
 */
data class DeviceIdentity(
    val udn: String, // 唯一设备标识符
    val descriptorURL: URL
)

/**
 * 设备详情
 */
data class DeviceDetails(
    val friendlyName: String? = null,
    val manufacturerInfo: ManufacturerInfo? = null,
    val modelInfo: ModelInfo? = null,
    val serialNumber: String? = null,
    val presentationURL: URL? = null
)

/**
 * 制造商信息
 */
data class ManufacturerInfo(
    val name: String? = null,
    val url: URL? = null
)

/**
 * 模型信息
 */
data class ModelInfo(
    val name: String? = null,
    val description: String? = null,
    val number: String? = null,
    val url: URL? = null
)

/**
 * 远程服务接口
 * 表示DLNA设备提供的服务的数据结构
 */
interface RemoteServiceData {
    /**
     * 服务类型
     */
    val serviceType: String

    /**
     * 服务ID
     */
    val serviceId: String

    /**
     * 控制URL
     */
    val controlURL: URL

    /**
     * 事件订阅URL
     */
    val eventSubURL: URL

    /**
     * 服务描述URL
     */
    val descriptorURL: URL
}

/**
 * 远程服务信息
 * 表示DLNA设备提供的服务数据
 */
data class RemoteServiceInfo(
    override val serviceType: String,
    override val serviceId: String,
    override val controlURL: URL,
    override val eventSubURL: URL,
    override val descriptorURL: URL
) : RemoteServiceData

/**
 * 远程设备类
 * 表示网络上发现的DLNA设备
 */
data class RemoteDevice(
    override val type: DeviceType,
    override val identity: DeviceIdentity,
    override val details: DeviceDetails,
    override val services: List<RemoteServiceInfo>,
    override val embeddedDevices: List<IDevice> = emptyList(),
    override val deviceId: String? = null
) : Device() {
        
    override val displayName: String
        get() = getDisplayName()
        
    override val displayString: String
        get() = getDisplayString()

    /**
     * 重写equals方法，使用location作为唯一标识符比较设备
     * 不再使用UDN，避免设备刷UDN导致重复
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteDevice) return false

        // 使用描述文件URL作为唯一标识符比较设备
        return this.locationKey == other.locationKey
    }

    /**
     * 重写hashCode方法，与equals保持一致
     * 使用location的hashCode
     */
    override fun hashCode(): Int {
        return locationKey.hashCode()
    }
}

/**
 * 设备注册表
 * 用于管理设备列表
 */
class DeviceRegistry {
    private val TAG = "DeviceRegistry"
    private val deviceMap = ConcurrentHashMap<String, RemoteDevice>()

    fun addDevice(device: RemoteDevice) {
        val usn = device.identity.udn.toString()
        deviceMap[usn] = device
        Log.d(TAG, "添加设备: $usn, ${device.details.friendlyName}")
    }

    fun removeDevice(device: RemoteDevice) {
        val usn = device.identity.udn.toString()
        deviceMap.remove(usn)
        Log.d(TAG, "移除设备: $usn")
    }

    fun getDevice(usn: String): RemoteDevice? {
        return deviceMap[usn]
    }

    fun getAllDevices(): List<RemoteDevice> {
        return deviceMap.values.toList()
    }

    fun clear() {
        deviceMap.clear()
        Log.d(TAG, "清空设备列表")
    }
} 