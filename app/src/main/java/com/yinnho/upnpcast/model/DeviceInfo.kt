package com.yinnho.upnpcast.model

/**
 * 设备信息数据类
 * 用于向上层应用提供简化的设备信息
 */
data class DeviceInfo(
    val id: String,              // 设备唯一标识符
    val name: String,            // 设备友好名称
    val isEmpty: Boolean = false // 设备是否为空
) 