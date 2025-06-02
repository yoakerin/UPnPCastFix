package com.yinnho.upnpcast.types

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