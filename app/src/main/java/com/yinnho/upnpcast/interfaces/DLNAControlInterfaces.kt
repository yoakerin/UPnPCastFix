package com.yinnho.upnpcast.interfaces

/**
 * DLNA控制点接口
 * 提供DLNA设备控制功能
 */
interface DLNAControlPoint {
    fun search()
    fun execute(action: DLNAAction, device: IDevice)
    fun subscribe(service: DLNAService)
    fun unsubscribe(service: DLNAService)
    fun setAVTransportURI(uri: String)
    fun play()
    fun pause()
    fun stop()
    fun seek(position: Long)
    fun getPositionInfo(): PositionInfo
    fun setVolume(volume: Int)
    fun getVolume(): Int
    fun setMute(mute: Boolean)
    fun getMute(): Boolean
}

/**
 * DLNA媒体控制器接口
 * 提供媒体播放控制功能
 */
interface DLNAMediaController {
    fun play(url: String)
    fun pause()
    fun resume()
    fun stop()
    fun seek(position: String)
    fun setVolume(volume: Int)
    fun getPlaybackState(): String
    fun isPlaying(): Boolean
    fun isPaused(): Boolean
    fun isStopped(): Boolean

    /**
     * 播放器回调接口
     * 用于监听播放状态变化
     */
    interface PlayerCallback {
        fun onPlaybackStarted()
        fun onPlaybackPaused()
        fun onPlaybackStopped()
        fun onPlaybackError(error: Exception)
        fun onVolumeChanged(volume: Int)
        fun onPositionChanged(position: String)
    }
} 