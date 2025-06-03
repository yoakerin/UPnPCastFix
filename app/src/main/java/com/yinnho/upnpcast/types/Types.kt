package com.yinnho.upnpcast.types

/**
 * DLNA device information
 */
data class Device(
    val id: String,
    val name: String,
    val address: String,
    val isTV: Boolean
)

/**
 * Media control actions
 */
enum class MediaAction {
    PLAY, PAUSE, STOP, VOLUME, MUTE, SEEK, GET_STATE
}

/**
 * Playback states
 */
enum class PlaybackState {
    IDLE, PLAYING, PAUSED, STOPPED, BUFFERING, ERROR
}

/**
 * DLNACast state information
 */
data class State(
    val isConnected: Boolean,
    val currentDevice: Device?,
    val playbackState: PlaybackState,
    val volume: Int = -1,
    val isMuted: Boolean = false
) {
}