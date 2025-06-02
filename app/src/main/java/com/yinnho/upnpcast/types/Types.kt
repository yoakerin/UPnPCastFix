package com.yinnho.upnpcast.types

/**
 * DLNA设备信息
 */
data class Device(
    val id: String,
    val name: String,
    val address: String,
    val isTV: Boolean
)

/**
 * 媒体控制动作
 */
enum class MediaAction {
    PLAY, PAUSE, STOP, VOLUME, MUTE, SEEK, GET_STATE
}

/**
 * 播放状态
 */
enum class PlaybackState {
    IDLE, PLAYING, PAUSED, STOPPED, BUFFERING, ERROR
}

/**
 * DLNACast状态信息
 */
data class State(
    val isConnected: Boolean,
    val currentDevice: Device?,
    val playbackState: PlaybackState,
    val volume: Int = -1,
    val isMuted: Boolean = false
) {
    val isPlaying: Boolean get() = playbackState == PlaybackState.PLAYING
    val isPaused: Boolean get() = playbackState == PlaybackState.PAUSED
    val isIdle: Boolean get() = playbackState == PlaybackState.IDLE
} 