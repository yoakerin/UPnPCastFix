package com.yinnho.upnpcast.network

import android.util.Log
import com.yinnho.upnpcast.device.DeviceParser
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.model.DeviceIdentity
import com.yinnho.upnpcast.model.DeviceDetails
import com.yinnho.upnpcast.model.ManufacturerInfo
import com.yinnho.upnpcast.model.ModelInfo
import com.yinnho.upnpcast.model.DeviceType
import java.net.InetAddress
import java.net.URL

/**
 * SSDP消息处理器 - 负责解析SSDP消息并提取设备信息
 * 这是一个简化版，将在第三阶段完全实现
 */
class SSDPMessageProcessor {
    private val TAG = "SSDPMessageProcessor"
    
    // 预创建通用设备类型，避免重复创建对象
    private val GENERIC_DEVICE_TYPE = DeviceType(
        namespace = "schemas-upnp-org",
        type = "MediaRenderer",
        version = 1
    )
    
    companion object {
        /**
         * 解析消息头部
         * @param message SSDP消息
         * @return 解析后的消息头
         */
        fun parseHeaders(message: String): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            
            try {
                val lines = message.split("\r\n").filter { it.isNotEmpty() }
                
                for (line in lines) {
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val key = line.substring(0, colonIndex).trim().uppercase()
                        val value = line.substring(colonIndex + 1).trim()
                        headers[key] = value
                    }
                }
            } catch (e: Exception) {
                Log.e("SSDPMessageProcessor", "解析消息头异常", e)
            }
            
            return headers
        }
        
        /**
         * 从USN中提取UUID
         * @param usn USN字符串
         * @return UUID字符串
         */
        fun extractUUID(usn: String): String {
            // 提取UUID
            val uuidRegex = "uuid:([^:]+)".toRegex()
            val matchResult = uuidRegex.find(usn)
            
            return matchResult?.groupValues?.get(1) ?: usn
        }
    }
    
    /**
     * 处理搜索响应
     * @param message 搜索响应消息
     * @param address 发送者地址
     * @return 解析出的设备信息，如果解析失败则返回null
     */
    fun processSearchResponse(message: String, address: InetAddress): DeviceInfo? {
        try {
            val headers = parseHeaders(message)
            
            // 提取必要字段
            val location = headers["LOCATION"] ?: return null
            val server = headers["SERVER"] ?: ""
            val usn = headers["USN"] ?: headers["NT"] ?: ""
            val uuid = extractUUID(usn)
            
            // 构建设备信息
            return DeviceInfo(
                location = location,
                server = server,
                usn = usn,
                uuid = uuid,
                address = address
            )
        } catch (e: Exception) {
            Log.e(TAG, "处理搜索响应异常", e)
            return null
        }
    }
    
    /**
     * 处理NOTIFY消息
     * @param message NOTIFY消息
     * @param address 发送者地址
     * @return 解析出的设备信息和通知类型，如果解析失败则返回null
     */
    fun processNotify(message: String, address: InetAddress): Pair<DeviceInfo, NotifyType>? {
        try {
            val headers = parseHeaders(message)
            
            // 提取必要字段
            val location = headers["LOCATION"] ?: return null
            val server = headers["SERVER"] ?: ""
            val usn = headers["USN"] ?: headers["NT"] ?: ""
            val uuid = extractUUID(usn)
            val nts = headers["NTS"] ?: ""
            
            // 确定通知类型
            val notifyType = when (nts) {
                "ssdp:alive" -> NotifyType.ALIVE
                "ssdp:byebye" -> NotifyType.BYEBYE
                else -> NotifyType.UNKNOWN
            }
            
            // 构建设备信息
            val deviceInfo = DeviceInfo(
                location = location,
                server = server,
                usn = usn,
                uuid = uuid,
                address = address
            )
            
            return deviceInfo to notifyType
        } catch (e: Exception) {
            Log.e(TAG, "处理NOTIFY消息异常", e)
            return null
        }
    }
    
    /**
     * 创建简单的RemoteDevice对象
     * 注意：这仅用于测试，完整实现需要从设备描述XML获取更多信息
     */
    fun createSimpleRemoteDevice(deviceInfo: DeviceInfo): RemoteDevice? {
        try {
            // 创建一个简单的设备标识
            val identity = DeviceIdentity(
                udn = deviceInfo.uuid,
                descriptorURL = URL(deviceInfo.location)
            )
            
            // 解析服务器信息以提取更有意义的制造商信息
            val serverParts = deviceInfo.server.split("/", limit = 2)
            val manufacturerName = serverParts.firstOrNull()?.trim() ?: "Unknown"
            
            // 创建制造商信息
            val manufacturerInfo = ManufacturerInfo(
                name = manufacturerName
            )
            
            // 创建更有意义的设备友好名称
            val friendlyName = if (deviceInfo.usn.contains("MediaRenderer", ignoreCase = true)) {
                "媒体播放器 (${deviceInfo.uuid.takeLast(6)})"
            } else {
                "UPNP设备 ${deviceInfo.uuid.takeLast(6)}"
            }
            
            // 创建模型信息
            val modelInfo = ModelInfo(
                name = "未知型号",
                description = "通过SSDP发现的设备"
            )
            
            // 创建设备详情
            val details = DeviceDetails(
                friendlyName = friendlyName,
                manufacturerInfo = manufacturerInfo,
                modelInfo = modelInfo
            )
            
            // 使用预创建的通用设备类型，避免重复创建
            return RemoteDevice(
                type = GENERIC_DEVICE_TYPE,
                identity = identity,
                details = details,
                services = emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "创建RemoteDevice失败", e)
            return null
        }
    }
    
    /**
     * 设备信息数据类
     */
    data class DeviceInfo(
        val location: String,
        val server: String,
        val usn: String,
        val uuid: String,
        val address: InetAddress
    )
    
    /**
     * 通知类型枚举
     */
    enum class NotifyType {
        ALIVE,      // 设备在线
        BYEBYE,     // 设备离线
        UNKNOWN     // 未知类型
    }
} 