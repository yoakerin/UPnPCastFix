package com.yinnho.upnpcast.interfaces

/**
 * 传输信息接口
 */
interface TransportInfo {
    val currentTransportState: String
    val currentTransportStatus: String
    val currentSpeed: String
}

/**
 * 播放位置信息接口
 */
interface PositionInfo {
    val track: UInt
    val trackDuration: String
    val trackMetaData: String
    val trackURI: String
    val relTime: String
    val absTime: String
    val relCount: Int
    val absCount: Int
} 