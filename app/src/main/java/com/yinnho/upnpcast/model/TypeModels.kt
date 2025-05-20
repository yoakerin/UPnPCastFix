package com.yinnho.upnpcast.model

import java.io.Serializable

/**
 * DLNA服务类型
 */
enum class ServiceType(val namespace: String, val type: String, val version: Int) {
    AV_TRANSPORT("schemas-upnp-org", "AVTransport", 1),
    RENDERING_CONTROL("schemas-upnp-org", "RenderingControl", 1),
    CONNECTION_MANAGER("schemas-upnp-org", "ConnectionManager", 1);

    val urn: String
        get() = "urn:$namespace:service:$type:$version"

    override fun toString(): String = urn

    companion object {
        fun fromString(typeString: String): ServiceType? {
            val parts = typeString.split(":").map { it.trim() }
            if (parts.size < 5) return null
            
            return when {
                parts[3].equals("AVTransport", ignoreCase = true) -> AV_TRANSPORT
                parts[3].equals("RenderingControl", ignoreCase = true) -> RENDERING_CONTROL
                parts[3].equals("ConnectionManager", ignoreCase = true) -> CONNECTION_MANAGER
                else -> null
            }
        }
    }
}

/**
 * 服务ID
 */
data class ServiceId(
    val namespace: String = "schemas-upnp-org",
    val type: String,
    val version: Int = 1
) {
    val urn: String
        get() = "urn:$namespace:serviceId:$type:$version"

    override fun toString(): String = urn

    companion object {
        val AVTransport = ServiceId(type = "AVTransport")
        val RenderingControl = ServiceId(type = "RenderingControl")
        val ConnectionManager = ServiceId(type = "ConnectionManager")

        fun fromString(idString: String): ServiceId? {
            val parts = idString.split(":").map { it.trim() }
            if (parts.size < 5) return null
            return try {
                ServiceId(
                    namespace = parts[1],
                    type = parts[3],
                    version = parts[4].toInt()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 设备类型
 */
data class DeviceType(
    val namespace: String,
    val type: String,
    val version: Int = 1
) : Serializable {

    val urn: String
        get() = "urn:$namespace:device:$type:$version"

    override fun toString(): String = urn

    companion object {
        fun fromString(typeString: String): DeviceType {
            val parts = typeString.split(":").map { it.trim() }
            require(parts.size == 3) { "Invalid device type format: $typeString" }
            return DeviceType(
                namespace = parts[0],
                type = parts[1],
                version = parts[2].toIntOrNull() ?: 1
            )
        }
    }
}

/**
 * 设备状态枚举
 * 表示DLNA设备的当前状态
 */
enum class DeviceStatus(val value: Int) {
    UNKNOWN(0),        // 未知状态
    CONNECTING(1),     // 连接中
    CONNECTED(2),      // 已连接
    PLAYING(3),        // 播放中
    PAUSED(4),         // 已暂停
    STOPPED(5),        // 已停止
    TRANSITIONING(6),  // 状态转换中
    ERROR(7);          // 错误状态
    
    companion object {
        /**
         * 从整数值获取状态枚举
         */
        fun fromValue(value: Int): DeviceStatus {
            return values().find { it.value == value } ?: UNKNOWN
        }
    }
} 