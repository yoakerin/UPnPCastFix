package com.yinnho.upnpcast.service

import com.yinnho.upnpcast.interfaces.DLNAPlaybackListener
import com.yinnho.upnpcast.interfaces.DLNAService
import com.yinnho.upnpcast.interfaces.PlaybackCallback
import com.yinnho.upnpcast.interfaces.PlaybackState
import com.yinnho.upnpcast.interfaces.TransportInfo
import com.yinnho.upnpcast.model.MediaInfo
import com.yinnho.upnpcast.model.DeviceType

/**
 * AV传输服务接口
 * 继承DLNAService接口实现媒体传输控制功能
 */
interface AVTransportService : DLNAService {
    /**
     * 获取播放状态信息
     * @param instanceId 实例ID，通常为"0"
     * @return 传输状态信息
     */
    fun getTransportInfo(instanceId: String): TransportInfo
}

/**
 * 播放服务接口
 * 用于控制媒体播放
 */
interface PlaybackService {
    /**
     * 设置播放监听器
     * @param listener 监听器
     */
    fun setPlaybackListener(listener: DLNAPlaybackListener)
    
    /**
     * 设置播放回调
     * @param callback 回调
     */
    fun setPlaybackCallback(callback: PlaybackCallback)
    
    /**
     * 更新播放状态
     * @param state 新状态
     */
    fun updatePlaybackState(state: PlaybackState)
    
    /**
     * 获取当前播放状态
     * @return 播放状态
     */
    fun getPlaybackState(): PlaybackState
    
    /**
     * 设置媒体时长
     * @param durationMs 时长（毫秒）
     */
    fun setDuration(durationMs: Long)
    
    /**
     * 更新播放位置
     * @param positionMs 位置（毫秒）
     */
    fun updatePlaybackPosition(positionMs: Long)
    
    /**
     * 设置媒体信息
     * @param mediaInfo 媒体信息
     */
    fun setMediaInfo(mediaInfo: MediaInfo)
    
    /**
     * 处理错误
     * @param error 错误
     */
    fun handleError(error: Exception)
    
    /**
     * 设置设备类型
     * @param deviceType 设备类型
     */
    fun setDeviceType(deviceType: DeviceType)
    
    /**
     * 检查设备类型是否支持
     * @param deviceType 设备类型
     * @return 是否支持
     */
    fun isDeviceTypeSupported(deviceType: DeviceType): Boolean
    
    /**
     * 释放资源
     */
    fun release()
} 