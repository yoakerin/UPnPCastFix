package com.yinnho.upnpcast

import android.content.Context
import com.yinnho.upnpcast.internal.DLNACastImpl

/**
 * DLNACast - 极简DLNA投屏API
 */
object DLNACast {
    
    enum class MediaAction {
        PLAY, PAUSE, STOP, VOLUME, MUTE, SEEK, GET_STATE
    }
    
    enum class PlaybackState {
        IDLE, PLAYING, PAUSED, STOPPED, BUFFERING, ERROR
    }
    
    data class Device(
        val id: String,
        val name: String,
        val address: String,
        val isTV: Boolean
    )
    
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
    
    fun init(context: Context) {
        DLNACastImpl.init(context)
    }
    
    fun cast(url: String, title: String? = null, callback: (success: Boolean) -> Unit = {}) {
        DLNACastImpl.cast(url, title, callback)
    }
    
    fun castTo(url: String, title: String? = null, deviceSelector: (devices: List<Device>) -> Device?) {
        DLNACastImpl.castTo(url, title, deviceSelector)
    }
    
    fun castToDevice(device: Device, url: String, title: String? = null, callback: (success: Boolean) -> Unit = {}) {
        DLNACastImpl.castToDevice(device, url, title, callback)
    }
    
    fun search(timeout: Long = 10000, callback: (devices: List<Device>) -> Unit) {
        DLNACastImpl.search(timeout, callback)
    }
    
    fun control(action: MediaAction, value: Any? = null, callback: (success: Boolean) -> Unit = {}) {
        DLNACastImpl.control(action, value, callback)
    }
    
    fun getState(): State {
        return DLNACastImpl.getState()
    }
    
    fun release() {
        DLNACastImpl.release()
    }
} 