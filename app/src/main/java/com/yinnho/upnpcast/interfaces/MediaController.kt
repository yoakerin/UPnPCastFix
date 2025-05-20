package com.yinnho.upnpcast.interfaces

import com.yinnho.upnpcast.model.RemoteDevice

/**
 * 媒体控制器接口
 * 定义标准媒体控制操作
 */
interface MediaController {
    /**
     * 获取关联的设备
     */
    val device: RemoteDevice
    
    /**
     * 设置媒体URL并开始播放
     * @param url 媒体URL
     * @param metadata 媒体元数据（可选）
     * @return 操作是否成功
     */
    fun playMedia(url: String, metadata: String = ""): Boolean
    
    /**
     * 暂停播放
     * @return 操作是否成功
     */
    fun pause(): Boolean
    
    /**
     * 恢复播放
     * @return 操作是否成功
     */
    fun resume(): Boolean
    
    /**
     * 停止播放
     * @return 操作是否成功
     */
    fun stop(): Boolean
    
    /**
     * 跳转到指定位置
     * @param positionMs 位置（毫秒）
     * @return 操作是否成功
     */
    fun seekTo(positionMs: Long): Boolean
    
    /**
     * 设置音量
     * @param volume 音量（0-100）
     * @return 操作是否成功
     */
    fun setVolume(volume: Int): Boolean
    
    /**
     * 设置静音状态
     * @param mute 是否静音
     * @return 操作是否成功
     */
    fun setMute(mute: Boolean): Boolean
    
    /**
     * 获取当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Long
    
    /**
     * 获取媒体总时长（毫秒）
     */
    fun getDuration(): Long
    
    /**
     * 获取当前音量
     */
    fun getVolume(): Int
    
    /**
     * 获取当前静音状态
     */
    fun getMute(): Boolean
    
    /**
     * 获取当前播放状态
     */
    fun getPlaybackState(): PlaybackState
    
    /**
     * 设置播放状态变化监听器
     */
    fun setPlaybackStateListener(listener: (PlaybackState) -> Unit)
    
    /**
     * 设置播放位置变化监听器
     */
    fun setPositionChangeListener(listener: (position: Long, duration: Long) -> Unit)
    
    /**
     * 设置出错监听器
     */
    fun setErrorListener(listener: (errorCode: Int, errorMessage: String) -> Unit)
    
    /**
     * 释放资源
     */
    fun release()
} 