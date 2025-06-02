package com.yinnho.upnpcast

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.yinnho.upnpcast.internal.DLNACastImpl
import com.yinnho.upnpcast.types.Device as TypesDevice
import com.yinnho.upnpcast.types.MediaAction as TypesMediaAction
import com.yinnho.upnpcast.types.PlaybackState as TypesPlaybackState
import com.yinnho.upnpcast.types.State as TypesState

/**
 * DLNACast - Simplified DLNA Casting API
 * 
 * Usage:
 * ```
 * import com.yinnho.upnpcast.DLNACast
 * 
 * // Initialize
 * DLNACast.init(context)
 * 
 * // Search for devices - returns all devices at once
 * DLNACast.search { devices: List<DLNACast.Device> ->
 *     // All devices returned in single callback
 * }
 * 
 * // Cast media
 * DLNACast.cast(url, title) { success ->
 *     // Handle result
 * }
 * ```
 */
object DLNACast {
    
    // Type definitions - Provide nested type access for external use
    data class Device(
        val id: String,
        val name: String,
        val address: String,
        val isTV: Boolean
    ) {
        // Internal conversion methods
        internal fun toTypes(): TypesDevice = TypesDevice(id, name, address, isTV)
        internal companion object {
            fun fromTypes(device: TypesDevice): Device = Device(device.id, device.name, device.address, device.isTV)
        }
    }
    
    enum class MediaAction {
        PLAY, PAUSE, STOP, VOLUME, MUTE, SEEK, GET_STATE;
        
        // Internal conversion methods
        internal fun toTypes(): TypesMediaAction = when (this) {
            PLAY -> TypesMediaAction.PLAY
            PAUSE -> TypesMediaAction.PAUSE
            STOP -> TypesMediaAction.STOP
            VOLUME -> TypesMediaAction.VOLUME
            MUTE -> TypesMediaAction.MUTE
            SEEK -> TypesMediaAction.SEEK
            GET_STATE -> TypesMediaAction.GET_STATE
        }
    }
    
    enum class PlaybackState {
        IDLE, PLAYING, PAUSED, STOPPED, BUFFERING, ERROR;
        
        internal companion object {
            fun fromTypes(state: TypesPlaybackState): PlaybackState = when (state) {
                TypesPlaybackState.IDLE -> IDLE
                TypesPlaybackState.PLAYING -> PLAYING
                TypesPlaybackState.PAUSED -> PAUSED
                TypesPlaybackState.STOPPED -> STOPPED
                TypesPlaybackState.BUFFERING -> BUFFERING
                TypesPlaybackState.ERROR -> ERROR
            }
        }
    }
    
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
        
        internal companion object {
            fun fromTypes(state: TypesState): State = State(
                isConnected = state.isConnected,
                currentDevice = state.currentDevice?.let { Device.fromTypes(it) },
                playbackState = PlaybackState.fromTypes(state.playbackState),
                volume = state.volume,
                isMuted = state.isMuted
            )
        }
    }
    
    fun init(context: Context) {
        DLNACastImpl.init(context)
    }
    
    fun cast(url: String, title: String? = null, callback: (success: Boolean) -> Unit = {}) {
        DLNACastImpl.cast(url, title, callback)
    }
    
    fun castTo(url: String, title: String? = null, deviceSelector: (devices: List<Device>) -> Device?) {
        DLNACastImpl.castTo(url, title) { typesDevices ->
            val devices = typesDevices.map { Device.fromTypes(it) }
            deviceSelector(devices)?.toTypes()
        }
    }
    
    fun smartCast(url: String, title: String? = null, callback: (success: Boolean) -> Unit = {}, deviceSelector: (devices: List<Device>) -> Device?) {
        DLNACastImpl.castTo(url, title, callback) { typesDevices ->
            val devices = typesDevices.map { Device.fromTypes(it) }
            deviceSelector(devices)?.toTypes()
        }
    }
    
    fun castToDevice(device: Device, url: String, title: String? = null, callback: (success: Boolean) -> Unit = {}) {
        DLNACastImpl.castToDevice(device.toTypes(), url, title, callback)
    }
    
    /**
     * Search for devices - returns new devices in real-time batches
     * @param timeout Total search timeout (default 5 seconds)
     * @param callback Called with newly discovered devices (may be called multiple times)
     */
    fun search(timeout: Long = 5000, callback: (devices: List<Device>) -> Unit) {
        DLNACastImpl.search(timeout) { typesDevices ->
            val devices = typesDevices.map { Device.fromTypes(it) }
            callback(devices)
        }
    }
    
    fun control(action: MediaAction, value: Any? = null, callback: (success: Boolean) -> Unit = {}) {
        DLNACastImpl.control(action.toTypes(), value, callback)
    }
    
    fun getState(): State {
        return State.fromTypes(DLNACastImpl.getState())
    }
    
    fun release() {
        DLNACastImpl.release()
    }
} 