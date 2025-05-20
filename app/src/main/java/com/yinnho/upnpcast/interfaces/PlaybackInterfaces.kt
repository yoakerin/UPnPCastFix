package com.yinnho.upnpcast.interfaces

import com.yinnho.upnpcast.model.MediaInfo
import com.yinnho.upnpcast.model.RemoteDevice

/**
 * 播放器回调接口
 * 用于监听设备发现和基本播放状态
 */
interface PlayerCallback {
    fun onDeviceFound(device: RemoteDevice)
    fun onDeviceLost(device: RemoteDevice)
    fun onDeviceConnected(device: RemoteDevice)
    fun onDeviceDisconnected(device: RemoteDevice)
    fun onPlaybackStateChanged(state: String)
    fun onError(message: String)
}

/**
 * 播放回调接口
 * 用于通知播放器状态变化
 */
interface PlaybackCallback {
    /**
     * 播放状态变化回调
     */
    fun onPlaybackStateChanged(state: PlaybackState)
    
    /**
     * 播放进度变化回调
     * @param position 当前位置（毫秒）
     * @param duration 总时长（毫秒）
     */
    fun onPlaybackPositionChanged(position: Long, duration: Long)
    
    /**
     * 媒体信息更新回调
     */
    fun onMediaInfoUpdated(mediaInfo: MediaInfo)
    
    /**
     * 错误回调
     */
    fun onError(error: Exception)
}

/**
 * DLNA播放监听器接口
 * 用于监听DLNA设备的播放事件
 */
interface DLNAPlaybackListener {
    /**
     * 播放开始
     */
    fun onPlaybackStarted()
    
    /**
     * 播放暂停
     */
    fun onPlaybackPaused()
    
    /**
     * 播放停止
     */
    fun onPlaybackStopped()
    
    /**
     * 播放进度更新
     * @param position 当前播放位置（毫秒）
     * @param duration 总时长（毫秒）
     */
    fun onPlaybackProgress(position: Long, duration: Long)
    
    /**
     * 播放完成
     */
    fun onPlaybackCompleted()
    
    /**
     * 播放发生错误
     * @param error 错误信息
     */
    fun onPlaybackError(error: String)
} 