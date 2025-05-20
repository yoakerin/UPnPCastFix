package com.yinnho.upnpcast.model

import com.yinnho.upnpcast.interfaces.PositionInfo
import com.yinnho.upnpcast.interfaces.TransportInfo

/**
 * 媒体信息数据类
 * 存储媒体的详细信息
 */
data class MediaInfo(
    /**
     * 媒体标题
     */
    val title: String = "",
    
    /**
     * 作者/艺术家
     */
    val artist: String = "",
    
    /**
     * 专辑封面URL
     */
    val albumArt: String? = null,
    
    /**
     * 媒体时长（毫秒）
     */
    val duration: Long = 0,
    
    /**
     * MIME类型
     */
    val mimeType: String = "",
    
    /**
     * 媒体大小（字节）
     */
    val size: Long = 0,
    
    /**
     * 媒体URL
     */
    val url: String = "",
    
    /**
     * 其他元数据，键值对形式
     */
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 传输信息实现类
 */
data class TransportInfoImpl(
    override val currentTransportState: String,
    override val currentTransportStatus: String,
    override val currentSpeed: String
) : TransportInfo

/**
 * 位置信息实现类
 */
data class PositionInfoImpl(
    override val track: UInt = 0u,
    override val trackDuration: String = "",
    override val trackMetaData: String = "",
    override val trackURI: String = "",
    override val relTime: String = "",
    override val absTime: String = "",
    override val relCount: Int = -1,
    override val absCount: Int = -1
) : PositionInfo

/**
 * 网络接口统计信息
 * 跟踪特定网络接口上的数据包统计
 */
data class InterfaceStats(
    /**
     * 关联的网络接口名称
     */
    val name: String
) {
    /**
     * 成功发送消息的计数
     */
    var successCount: Int = 0

    /**
     * 发送失败的消息计数
     */
    var failureCount: Int = 0

    /**
     * 最后一次使用时间
     */
    var lastUsed: Long = System.currentTimeMillis()

    /**
     * 计算成功率
     */
    val successRate: Float
        get() = if (successCount + failureCount > 0) {
            successCount.toFloat() / (successCount + failureCount)
        } else {
            0f
        }
} 