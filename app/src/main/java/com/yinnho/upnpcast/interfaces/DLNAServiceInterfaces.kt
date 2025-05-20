package com.yinnho.upnpcast.interfaces

import com.yinnho.upnpcast.model.DLNAUrl

/**
 * DLNA服务基础接口
 * 定义所有DLNA服务共有的URL属性
 */
interface BaseService {
    /**
     * 控制URL - 用于发送SOAP控制命令
     */
    val controlURL: DLNAUrl
    
    /**
     * 事件订阅URL - 用于事件订阅和通知
     */
    val eventSubURL: DLNAUrl
    
    /**
     * 服务描述文档URL - 用于获取服务的SCPD文档
     */
    val SCPDURL: DLNAUrl
    
    /**
     * 设备描述文档URL - 用于访问设备描述
     */
    val descriptorURL: DLNAUrl
    
    /**
     * 服务类型 - 例如: "urn:schemas-upnp-org:service:AVTransport:1"
     */
    val serviceType: String
    
    /**
     * 服务ID - 例如: "AVTransport"
     */
    val serviceId: String
}

/**
 * DLNA服务操作接口
 * 提供基本的媒体控制方法
 */
interface DLNAService : BaseService {
    // 媒体控制基本方法
    /**
     * 播放媒体
     * @param instanceId 实例ID，通常为"0"
     * @param speed 播放速度，通常为"1"
     */
    fun play(instanceId: String, speed: String = "1")
    
    /**
     * 暂停播放
     * @param instanceId 实例ID，通常为"0"
     */
    fun pause(instanceId: String)
    
    /**
     * 停止播放
     * @param instanceId 实例ID，通常为"0"
     */
    fun stop(instanceId: String)
    
    /**
     * 播放进度跳转
     * @param instanceId 实例ID，通常为"0"
     * @param target 目标时间，格式为"HH:MM:SS"
     */
    fun seek(instanceId: String, target: String)
    
    /**
     * 设置媒体URI
     * @param instanceId 实例ID，通常为"0"
     * @param uri 媒体资源URI
     * @param metadata 媒体元数据，DIDL-Lite格式
     */
    fun setAVTransportURI(instanceId: String, uri: String, metadata: String = "")
    
    /**
     * 获取播放位置信息
     * @param instanceId 实例ID，通常为"0"
     * @return 播放位置信息
     */
    fun getPositionInfo(instanceId: String): PositionInfo
}

/**
 * AV传输服务接口
 * 提供音视频传输控制功能
 */
interface AVTransportService {
    fun setAVTransportURI(instanceId: String, uri: String, metadata: String)
    fun play(instanceId: String)
    fun pause(instanceId: String)
    fun stop(instanceId: String)
    fun seek(instanceId: String, target: String)
    fun getPositionInfo(instanceId: String): PositionInfo
}

/**
 * 媒体信息
 */
data class MediaInfo(
    val currentURI: String,
    val currentURIMetaData: String
) 