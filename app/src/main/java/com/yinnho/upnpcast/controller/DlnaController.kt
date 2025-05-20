package com.yinnho.upnpcast.controller

import com.yinnho.upnpcast.api.PlaybackState
import com.yinnho.upnpcast.controller.device.DeviceInfoManager
import com.yinnho.upnpcast.controller.playback.PlaybackController
import com.yinnho.upnpcast.controller.playback.DirectPlaybackController
import com.yinnho.upnpcast.controller.soap.SoapTransportExecutor
import com.yinnho.upnpcast.controller.volume.VolumeController
import com.yinnho.upnpcast.controller.sync.SyncOperationsAdapter
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.core.EnhancedThreadManager

/**
 * DLNA控制器
 * 负责与DLNA设备通信的门面类，内部委托给各个专门的组件
 */
class DlnaController(
    val device: RemoteDevice
) {
    companion object {
        private const val TAG = "DlnaController"
    }

    // 子组件
    private val deviceInfoManager = DeviceInfoManager(device)
    private val soapExecutor = SoapTransportExecutor(deviceInfoManager)
    private val playbackController = PlaybackController(deviceInfoManager)
    private val volumeController = VolumeController(deviceInfoManager)
    private val directPlaybackController = DirectPlaybackController(deviceInfoManager, soapExecutor)
    private val syncOperations = SyncOperationsAdapter(deviceInfoManager, playbackController, volumeController)
    
    // 设备信息委托
    val friendlyName: String get() = deviceInfoManager.friendlyName
    val manufacturer: String get() = deviceInfoManager.manufacturer
    val modelName: String get() = deviceInfoManager.modelName
    val deviceId: String get() = deviceInfoManager.deviceId
    
    // 设备类型标识委托
    val isXiaomiDevice: Boolean get() = deviceInfoManager.isXiaomiDevice
    val isSamsungDevice: Boolean get() = deviceInfoManager.isSamsungDevice
    val isLGDevice: Boolean get() = deviceInfoManager.isLGDevice
    
    init {
        EnhancedThreadManager.d(TAG, "初始化DlnaController门面类, 设备: $friendlyName")
    }
    
    /**
     * 设置播放状态监听器
     */
    fun setPlaybackStateListener(listener: (PlaybackState) -> Unit) {
        playbackController.setPlaybackStateListener(listener)
        soapExecutor.setPlaybackStateListener(listener)
    }
    
    /**
     * 设置播放位置变化监听器
     */
    fun setPositionChangeListener(listener: (position: Long, duration: Long) -> Unit) {
        playbackController.setPositionChangeListener(listener)
    }
    
    // ===== 协程异步方法，通过PlaybackController实现 =====
    
    /**
     * 设置媒体URI
     */
    suspend fun setAVTransportURI(uri: String, metadata: String = "") = 
        playbackController.setAVTransportURI(uri, metadata)
    
    /**
     * 开始播放
     */
    suspend fun play() = playbackController.play()
    
    /**
     * 暂停播放
     */
    suspend fun pause() = playbackController.pause()
    
    /**
     * 停止播放
     */
    suspend fun stop() = playbackController.stop()
    
    /**
     * 跳转到指定时间
     */
    suspend fun seek(position: String) = playbackController.seek(position)
    
    /**
     * 跳转到指定毫秒位置
     */
    suspend fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    
    /**
     * 获取当前播放位置信息
     */
    suspend fun getPositionInfo() = playbackController.getPositionInfo()
    
    /**
     * 获取传输状态信息
     */
    suspend fun getTransportInfo() = playbackController.getTransportInfo()
    
    /**
     * 播放媒体
     */
    suspend fun playMedia(mediaUrl: String, title: String, episodeLabel: String = "", positionMs: Long = 0) = 
        playbackController.playMedia(mediaUrl, title, episodeLabel, positionMs)
    
    // ===== 音量控制方法，通过VolumeController实现 =====
    
    /**
     * 设置音量
     */
    suspend fun setVolume(volume: UInt) = volumeController.setVolume(volume)
    
    /**
     * 获取当前音量
     */
    suspend fun getVolume() = volumeController.getVolume()
    
    /**
     * 设置静音状态
     */
    suspend fun setMute(mute: Boolean) = volumeController.setMute(mute)
    
    /**
     * 获取当前静音状态
     */
    suspend fun getMute() = volumeController.getMute()
    
    // ===== 直接SOAP方法，通过SoapTransportExecutor实现 =====
    
    /**
     * 通过直接SOAP请求设置AVTransportURI
     */
    suspend fun setAVTransportURIDirect(mediaUrl: String, metadata: String) = 
        soapExecutor.setAVTransportURI(mediaUrl, metadata)
    
    /**
     * 通过直接SOAP请求播放媒体
     */
    suspend fun playDirect() = soapExecutor.play()
    
    /**
     * 通过直接SOAP请求停止播放
     */
    suspend fun stopDirect() = soapExecutor.stop()
    
    /**
     * 通过直接SOAP请求定位播放位置
     */
    suspend fun seekDirect(positionMs: Long) = soapExecutor.seek(positionMs)
    
    /**
     * 使用直接SOAP通信播放媒体（完整流程）
     */
    suspend fun playMediaDirect(mediaUrl: String, title: String, episodeLabel: String = "", positionMs: Long = 0) = 
        directPlaybackController.playMediaDirect(mediaUrl, title, episodeLabel, positionMs)
    
    // ===== 同步方法，通过SyncOperationsAdapter实现 =====
    
    /**
     * 同步版本 - 播放媒体
     */
    fun playMediaSync(mediaUrl: String, title: String, episodeLabel: String = "", positionMs: Long = 0) = 
        syncOperations.playMediaSync(mediaUrl, title, episodeLabel, positionMs)
    
    /**
     * 同步版本 - 暂停播放
     */
    fun pauseSync() = syncOperations.pauseSync()
    
    /**
     * 同步版本 - 开始/恢复播放
     */
    fun playSync() = syncOperations.playSync()
    
    /**
     * 同步版本 - 停止播放
     */
    fun stopSync() = syncOperations.stopSync()
    
    /**
     * 同步版本 - 跳转到指定位置
     */
    fun seekToSync(positionMs: Long) = syncOperations.seekToSync(positionMs)
    
    /**
     * 同步版本 - 设置音量
     */
    fun setVolumeSync(volume: UInt) = syncOperations.setVolumeSync(volume)
    
    /**
     * 同步版本 - 设置静音状态
     */
    fun setMuteSync(mute: Boolean) = syncOperations.setMuteSync(mute)
    
    // ===== 设备信息方法，通过DeviceInfoManager实现 =====
    
    /**
     * 记录设备能力信息
     */
    fun logDeviceCapabilities() {
        deviceInfoManager.logDeviceCapabilities()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        EnhancedThreadManager.d(TAG, "释放DlnaController资源")
        // 释放各个组件
        try {
            deviceInfoManager.release()
            soapExecutor.release()
            
            // 清除监听器
            playbackController.setPlaybackStateListener { /* 空实现 */ }
            playbackController.setPositionChangeListener { _, _ -> /* 空实现 */ }
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "释放资源失败：${e.message}", e)
        }
    }
} 