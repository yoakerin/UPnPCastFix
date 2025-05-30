package com.yinnho.upnpcast

/**
 * DLNA异常类 - 极简版
 */
class DLNAException(
    val errorType: DLNAErrorType,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    override fun toString(): String {
        return "DLNAException(type=$errorType, message='$message')"
    }
}

/**
 * DLNA错误类型枚举
 */
enum class DLNAErrorType {
    NETWORK_ERROR,          // 网络错误
    DEVICE_NOT_FOUND,       // 设备未找到
    CONNECTION_FAILED,      // 连接失败
    PLAYBACK_ERROR,         // 播放错误
    PERMISSION_DENIED,      // 权限被拒绝
    UNKNOWN_ERROR          // 未知错误
} 