package com.yinnho.upnpcast

/**
 * 投屏监听器
 */
interface CastListener {
    /**
     * 设备列表更新
     */
    fun onDeviceListUpdated(deviceList: List<RemoteDevice>)
    
    /**
     * 设备连接成功
     */
    fun onConnected(device: RemoteDevice)
    
    /**
     * 设备断开连接
     */
    fun onDisconnected()
    
    /**
     * 错误发生
     */
    fun onError(error: DLNAException)
}

/**
 * 播放状态监听器
 */
interface PlaybackStateListener {
    /**
     * 播放状态改变
     */
    fun onPlaybackStateChanged(state: PlaybackState)
    
    /**
     * 播放位置改变
     */
    fun onPositionChanged(positionMs: Long, durationMs: Long)
} 