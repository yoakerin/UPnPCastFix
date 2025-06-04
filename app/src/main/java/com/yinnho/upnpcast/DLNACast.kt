package com.yinnho.upnpcast

import android.content.Context
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
    
    /**
     * Get playback progress information
     * @param callback Callback function, returns (currentMs, totalMs, success)
     */
    fun getProgress(callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit) {
        DLNACastImpl.getProgress(callback)
    }
    
    /**
     * Get current volume information
     * @param callback Callback function, returns (volume: Int?, isMuted: Boolean?, success: Boolean)
     *                 volume: Current volume (0-100), null if failed to get
     *                 isMuted: Current mute state, null if failed to get
     *                 success: Whether the request was successful
     */
    fun getVolume(callback: (volume: Int?, isMuted: Boolean?, success: Boolean) -> Unit) {
        DLNACastImpl.getVolume(callback)
    }
    
    /**
     * Get real-time playback progress (force fetch from device, no cache)
     * @param callback Callback function, returns (currentMs, totalMs, success)
     */
    fun getProgressRealtime(callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit) {
        DLNACastImpl.getProgressRealtime(callback)
    }
    
    /**
     * Manually refresh volume cache
     * @param callback Callback function, returns success status
     */
    fun refreshVolumeCache(callback: (success: Boolean) -> Unit = {}) {
        DLNACastImpl.refreshVolumeCache(callback)
    }
    
    /**
     * Manually refresh progress cache
     * @param callback Callback function, returns success status
     */
    fun refreshProgressCache(callback: (success: Boolean) -> Unit = {}) {
        DLNACastImpl.refreshProgressCache(callback)
    }
    
    /**
     * Clear progress cache (call when switching media)
     */
    fun clearProgressCache() {
        DLNACastImpl.clearProgressCache()
    }
    
    /**
     * Cast local file
     * @param filePath Local file path
     * @param device Target device
     * @param title Media title (optional)
     * @param callback Callback function, returns (success, message)
     */
    fun castLocalFile(filePath: String, device: Device, title: String? = null, callback: (success: Boolean, message: String) -> Unit) {
        DLNACastImpl.castLocalFile(filePath, device.toTypes(), title, callback)
    }
    
    /**
     * Cast local file - auto select device
     * @param filePath Local file path
     * @param title Media title (optional)
     * @param callback Callback function, returns (success, message)
     */
    fun castLocalFile(filePath: String, title: String? = null, callback: (success: Boolean, message: String) -> Unit) {
        DLNACastImpl.castLocalFile(filePath, title, callback)
    }
    
    /**
     * Get network access URL for local file
     * @param filePath Local file path
     * @return HTTP URL of the file, null if failed
     */
    fun getLocalFileUrl(filePath: String): String? {
        return DLNACastImpl.getLocalFileUrl(filePath)
    }
    
    /**
     * Simplified local video data class
     */
    data class LocalVideo(
        val id: String,
        val title: String,
        val path: String,
        val duration: String,
        val size: String,
        val durationMs: Long
    )
    
    /**
     * Scan local video files
     * @param context Android Context
     * @param callback Callback function, returns list of scanned videos
     */
    fun scanLocalVideos(context: Context, callback: (videos: List<LocalVideo>) -> Unit) {
        DLNACastImpl.scanLocalVideos(context, callback)
    }
    
    /**
     * Launch local video selector
     * @param context Android Context (must be Activity)
     * @param device Target casting device
     */
    fun showVideoSelector(context: Context, device: Device) {
        DLNACastImpl.showVideoSelector(context, device.toTypes())
    }
    
    fun release() {
        DLNACastImpl.release()
    }
} 