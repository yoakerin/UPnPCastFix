package com.yinnho.upnpcast.utils

import android.util.Log
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.model.ServiceType

/**
 * 设备工具类
 * 提供设备类型识别和相关工具方法
 */
object DeviceUtils {
    private const val TAG = "DeviceUtils"
    
    /**
     * 判断是否为小米设备
     * @param device 远程设备
     * @return 是否为小米设备
     */
    fun isXiaomiDevice(device: RemoteDevice): Boolean {
        val manufacturer = device.details.manufacturerInfo?.name ?: ""
        val friendlyName = device.details.friendlyName ?: ""
        val modelName = device.details.modelInfo?.name ?: ""
        
        return manufacturer.contains("xiaomi", ignoreCase = true) || 
               manufacturer.contains("mi", ignoreCase = true) ||
               friendlyName.contains("小米", ignoreCase = true) ||
               modelName.contains("xiaomi", ignoreCase = true)
    }
    
    /**
     * 判断是否为三星设备
     * @param device 远程设备
     * @return 是否为三星设备
     */
    fun isSamsungDevice(device: RemoteDevice): Boolean {
        val manufacturer = device.details.manufacturerInfo?.name ?: ""
        val friendlyName = device.details.friendlyName ?: ""
        
        return manufacturer.contains("samsung", ignoreCase = true) ||
               friendlyName.lowercase().contains("samsung")
    }
    
    /**
     * 判断是否为LG设备
     * @param device 远程设备
     * @return 是否为LG设备
     */
    fun isLGDevice(device: RemoteDevice): Boolean {
        val manufacturer = device.details.manufacturerInfo?.name ?: ""
        val friendlyName = device.details.friendlyName ?: ""
        
        return manufacturer.contains("lg", ignoreCase = true) ||
               friendlyName.lowercase().contains("lg")
    }
    
    /**
     * 获取设备的制造商名称
     * @param device 远程设备
     * @return 制造商名称，如果不可用则返回"未知厂商"
     */
    fun getManufacturer(device: RemoteDevice): String {
        return device.details.manufacturerInfo?.name ?: "未知厂商"
    }
    
    /**
     * 获取设备的型号名称
     * @param device 远程设备
     * @return 型号名称，如果不可用则返回"未知型号"
     */
    fun getModelName(device: RemoteDevice): String {
        return device.details.modelInfo?.name ?: "未知型号"
    }
    
    /**
     * 获取设备的友好名称
     * @param device 远程设备
     * @return 友好名称，如果不可用则返回设备ID
     */
    fun getFriendlyName(device: RemoteDevice): String {
        return device.details.friendlyName ?: device.identity.udn
    }
    
    /**
     * 获取设备UDN
     */
    fun getDeviceUDN(device: RemoteDevice): String {
        return device.identity.udn
    }
    
    /**
     * 验证设备有效性
     */
    fun isValidDevice(device: RemoteDevice): Boolean {
        Log.d(TAG, "检查设备是否有效: ${device.getDisplayString()}")

        if (device.services.isEmpty()) {
            Log.d(TAG, "设备服务为空，但可能是模拟设备，视为有效")
            return true
        }

        // 检查是否为媒体渲染器设备
        val isMediaRenderer = device.type.toString().contains("MediaRenderer")
        Log.d(TAG, "设备类型是否为MediaRenderer: $isMediaRenderer")

        // 检查是否有必要的服务
        val hasAVTransport = device.services.any {
            it.serviceType.toString() == ServiceType.AV_TRANSPORT.toString()
        }

        return isMediaRenderer || hasAVTransport
    }
    
    /**
     * 检查必需服务
     */
    fun hasRequiredServices(device: RemoteDevice): Boolean {
        Log.d(TAG, "检查设备是否有必要的服务: ${device.getDisplayString()}")

        if (device.services.isEmpty()) {
            Log.d(TAG, "设备服务为空，但可能是模拟设备，视为有必要的服务")
            return true
        }

        val avTransportType = ServiceType.AV_TRANSPORT
        val renderingControlType = ServiceType.RENDERING_CONTROL

        val hasAVTransport = device.services.any {
            it.serviceType.toString() == avTransportType.toString()
        }
        val hasRenderingControl = device.services.any {
            it.serviceType.toString() == renderingControlType.toString()
        }

        Log.d(TAG, "设备是否有AVTransport服务: $hasAVTransport")
        Log.d(TAG, "设备是否有RenderingControl服务: $hasRenderingControl")

        return hasAVTransport && hasRenderingControl
    }
}

/**
 * 设备扩展属性和函数
 */

/**
 * 设备location键扩展属性
 * 用于设备唯一性判断、缓存、去重等
 */
val RemoteDevice.locationKey: String
    get() = identity.descriptorURL.toString()

/**
 * 获取设备显示名称
 * 优先使用友好名称，其次使用型号+厂商，最后使用UDN
 */
fun RemoteDevice.getDisplayName(): String {
    val friendlyName = details.friendlyName ?: ""
    val manufacturer = details.manufacturerInfo?.name ?: ""
    val modelName = details.modelInfo?.name ?: ""

    return when {
        friendlyName.isNotEmpty() -> friendlyName
        modelName.isNotEmpty() -> "$modelName (${manufacturer.takeIf { it.isNotEmpty() } ?: "未知厂商"})"
        else -> identity.udn
    }
}

/**
 * 获取设备显示字符串
 * 默认返回显示名称
 */
fun RemoteDevice.getDisplayString(): String = getDisplayName()

/**
 * 安全获取设备IP地址
 * 从设备中提取IP地址，失败时返回默认值
 */
fun RemoteDevice.getIpAddress(): String {
    return try {
        this.identity.descriptorURL.host ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}

/**
 * 安全获取设备端口
 * 从设备中提取端口，失败时返回默认值
 */
fun RemoteDevice.getPort(): Int {
    return try {
        val url = this.identity.descriptorURL
        if (url.port == -1) 80 
        else url.port
    } catch (e: Exception) {
        80
    }
}

/**
 * 设备ID扩展属性
 * 返回设备的唯一标识符，优先使用locationKey
 */
val RemoteDevice.deviceId: String
    get() = this.locationKey

/**
 * 设备地址扩展属性
 */
val RemoteDevice.address: String
    get() {
        return this.identity.descriptorURL.host ?: ""
    }

/**
 * 是否为相同设备的判断
 * 基于locationKey比较两个设备是否相同
 */
fun RemoteDevice.isSameDevice(other: RemoteDevice): Boolean {
    return this.locationKey == other.locationKey
} 