package com.yinnho.upnpcast.device

import android.util.Log
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.model.ServiceType
import com.yinnho.upnpcast.utils.DeviceUtils as UtilsDeviceUtils
import com.yinnho.upnpcast.utils.locationKey as utilsLocationKey
import com.yinnho.upnpcast.utils.getDisplayString as utilsGetDisplayString
import com.yinnho.upnpcast.utils.getDisplayName as utilsGetDisplayName
import com.yinnho.upnpcast.utils.getIpAddress as utilsGetIpAddress
import com.yinnho.upnpcast.utils.getPort as utilsGetPort
import com.yinnho.upnpcast.utils.deviceId as utilsDeviceId
import com.yinnho.upnpcast.utils.address as utilsAddress
import com.yinnho.upnpcast.utils.isSameDevice as utilsIsSameDevice

/**
 * 设备工具类
 * 此类现在是utils.DeviceUtils的代理
 * @deprecated 请直接使用com.yinnho.upnpcast.utils.DeviceUtils
 */
@Deprecated("请直接使用com.yinnho.upnpcast.utils.DeviceUtils", ReplaceWith("com.yinnho.upnpcast.utils.DeviceUtils"))
object DeviceUtils {
    private const val TAG = "DeviceUtils"

    // 获取设备UDN
    fun getDeviceUDN(device: RemoteDevice): String = UtilsDeviceUtils.getDeviceUDN(device)

    // 验证设备有效性
    fun isValidDevice(device: RemoteDevice): Boolean = UtilsDeviceUtils.isValidDevice(device)

    // 检查必需服务
    fun hasRequiredServices(device: RemoteDevice): Boolean = UtilsDeviceUtils.hasRequiredServices(device)
}

/**
 * 设备扩展属性和函数的代理
 * @deprecated 请使用com.yinnho.upnpcast.utils包中的扩展函数
 */

/**
 * 设备location键扩展属性
 * @deprecated 请使用com.yinnho.upnpcast.utils.locationKey
 */
@Deprecated("请使用com.yinnho.upnpcast.utils.locationKey", ReplaceWith("com.yinnho.upnpcast.utils.locationKey"))
val RemoteDevice.locationKey: String
    get() = this.utilsLocationKey

/**
 * 获取设备显示名称
 * @deprecated 请使用com.yinnho.upnpcast.utils.getDisplayName
 */
@Deprecated("请使用com.yinnho.upnpcast.utils.getDisplayName", ReplaceWith("com.yinnho.upnpcast.utils.getDisplayName()"))
fun RemoteDevice.getDisplayName(): String = this.utilsGetDisplayName()

/**
 * 获取设备显示字符串
 * @deprecated 请使用com.yinnho.upnpcast.utils.getDisplayString
 */
@Deprecated("请使用com.yinnho.upnpcast.utils.getDisplayString", ReplaceWith("com.yinnho.upnpcast.utils.getDisplayString()"))
fun RemoteDevice.getDisplayString(): String = this.utilsGetDisplayString()

/**
 * 安全获取设备IP地址
 * @deprecated 请使用com.yinnho.upnpcast.utils.getIpAddress
 */
@Deprecated("请使用com.yinnho.upnpcast.utils.getIpAddress", ReplaceWith("com.yinnho.upnpcast.utils.getIpAddress()"))
fun RemoteDevice.getIpAddress(): String = this.utilsGetIpAddress()

/**
 * 安全获取设备端口
 * @deprecated 请使用com.yinnho.upnpcast.utils.getPort
 */
@Deprecated("请使用com.yinnho.upnpcast.utils.getPort", ReplaceWith("com.yinnho.upnpcast.utils.getPort()"))
fun RemoteDevice.getPort(): Int = this.utilsGetPort()

/**
 * 设备ID扩展属性
 * @deprecated 请使用com.yinnho.upnpcast.utils.deviceId
 */
@Deprecated("请使用com.yinnho.upnpcast.utils.deviceId", ReplaceWith("com.yinnho.upnpcast.utils.deviceId"))
val RemoteDevice.deviceId: String
    get() = this.utilsDeviceId

/**
 * 设备地址扩展属性
 * @deprecated 请使用com.yinnho.upnpcast.utils.address
 */
@Deprecated("请使用com.yinnho.upnpcast.utils.address", ReplaceWith("com.yinnho.upnpcast.utils.address"))
val RemoteDevice.address: String
    get() = this.utilsAddress

/**
 * 是否为相同设备的判断
 * @deprecated 请使用com.yinnho.upnpcast.utils.isSameDevice
 */
@Deprecated("请使用com.yinnho.upnpcast.utils.isSameDevice", ReplaceWith("com.yinnho.upnpcast.utils.isSameDevice(other)"))
fun RemoteDevice.isSameDevice(other: RemoteDevice): Boolean = this.utilsIsSameDevice(other) 