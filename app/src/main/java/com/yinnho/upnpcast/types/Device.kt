package com.yinnho.upnpcast.types

/**
 * DLNA设备信息
 */
data class Device(
    val id: String,
    val name: String,
    val address: String,
    val isTV: Boolean
) 