package com.yinnho.upnpcast

/**
 * 远程设备信息 - 极简版
 */
data class RemoteDevice(
    val id: String,                    // 设备唯一标识（使用location URL）
    val displayName: String,           // 显示名称
    val address: String,              // 设备地址
    val manufacturer: String = "",     // 制造商
    val model: String = "",           // 型号
    val details: Map<String, Any> = emptyMap()  // 设备详细信息（location, usn, server等）
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteDevice) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "RemoteDevice(id='$id', name='$displayName', address='$address')"
    }
}

/**
 * 设备位置标识扩展属性
 * 与backup代码保持兼容性
 */
val RemoteDevice.locationKey: String
    get() = details["locationUrl"] as? String ?: id 