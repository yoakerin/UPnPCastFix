package com.yinnho.upnpcast.service

import com.yinnho.upnpcast.interfaces.DLNAService

/**
 * 连接服务接口
 * 提供设备连接管理功能
 * 整合了旧版ConnectionManager的所有功能
 */
interface ConnectionService : DLNAService {
    /**
     * 获取支持的协议信息
     */
    fun getProtocolInfo(): ProtocolInfo
    
    /**
     * 获取当前连接ID列表
     */
    fun getCurrentConnectionIDs(): List<Int>
    
    /**
     * 获取当前连接信息
     * @param connectionID 连接ID
     */
    fun getCurrentConnectionInfo(connectionID: Int): ConnectionInfo
    
    /**
     * 准备连接
     * @param remoteProtocolInfo 远程协议信息
     * @param peerConnectionManager 对等连接管理器
     * @param peerConnectionID 对等连接ID
     */
    fun prepareForConnection(
        remoteProtocolInfo: String,
        peerConnectionManager: String,
        peerConnectionID: Int
    ): PrepareForConnectionResult

    /**
     * 完成连接
     * @param connectionID 连接ID
     */
    fun connectionComplete(connectionID: Int)
    
    /**
     * 检查是否支持特定媒体格式
     * @param mimeType 媒体MIME类型
     * @return 是否支持该媒体类型
     */
    fun supportsMediaFormat(mimeType: String): Boolean
}

/**
 * 协议信息数据类
 */
data class ProtocolInfo(
    val source: List<String> = listOf(),
    val sink: List<String> = listOf()
)

/**
 * 连接信息数据类
 */
data class ConnectionInfo(
    val rcsID: Int = -1,
    val avTransportID: Int = -1,
    val protocolInfo: String = "",
    val peerConnectionManager: String = "",
    val peerConnectionID: Int = -1,
    val direction: ConnectionDirection = ConnectionDirection.INPUT,
    val status: ConnectionStatus = ConnectionStatus.UNKNOWN
)

/**
 * 连接准备结果数据类
 */
data class PrepareForConnectionResult(
    val connectionID: Int = -1,
    val avTransportID: Int = -1,
    val rcsID: Int = -1
)

/**
 * 连接方向枚举
 */
enum class ConnectionDirection {
    INPUT,
    OUTPUT
}

/**
 * 连接状态枚举
 */
enum class ConnectionStatus {
    OK,
    UNKNOWN,
    CONTENT_FORMAT_MISMATCH,
    INSUFFICIENT_BANDWIDTH,
    UNRELIABLE_CHANNEL,
    ERROR_OCCURRED
}

/**
 * 连接管理器错误枚举
 */
enum class ConnectionManagerError {
    INVALID_PROTOCOL_INFO,
    INCOMPATIBLE_PROTOCOL_INFO,
    INSUFFICIENT_NETWORK_RESOURCES,
    LOCAL_RESTRICTIONS,
    ACCESS_DENIED,
    INVALID_CONNECTION_REFERENCE,
    NOT_IN_NETWORK
} 