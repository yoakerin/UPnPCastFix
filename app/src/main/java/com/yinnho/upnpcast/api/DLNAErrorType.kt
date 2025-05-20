package com.yinnho.upnpcast.api

/**
 * DLNA错误类型枚举
 * 简化后的错误类型，只保留核心错误类型
 */
enum class DLNAErrorType(val code: Int, val message: String) {
    // 发现设备相关错误
    DISCOVERY_ERROR(1003, "设备发现错误"),
    
    // 连接相关错误
    CONNECTION_ERROR(2001, "连接错误"),
    
    // 设备连接错误
    DEVICE_CONNECTION_ERROR(2002, "设备连接错误"),
    
    // 设备相关错误
    DEVICE_ERROR(2004, "设备错误"),
    
    // 通讯错误
    COMMUNICATION_ERROR(2003, "设备通信错误"),
    
    // 播放控制相关错误
    PLAYBACK_ERROR(3001, "播放控制错误"),
    
    // 控制错误
    CONTROL_ERROR(3002, "控制命令错误"),
    
    // 网络相关错误
    NETWORK_ERROR(1001, "网络错误"),
    
    // 网络超时
    NETWORK_TIMEOUT(1002, "网络超时"),
    
    // 无效参数
    INVALID_PARAMETER(4001, "参数无效"),
    
    // 安全错误
    SECURITY_ERROR(5001, "安全错误"),
    
    // 解析错误
    PARSING_ERROR(4003, "解析错误"),
    
    // 资源错误
    RESOURCE_ERROR(4002, "资源错误"),
    
    // 兼容性错误
    COMPATIBILITY_ERROR(6001, "设备兼容性错误"),
    
    // 未知错误
    UNKNOWN_ERROR(9999, "未知错误")
} 