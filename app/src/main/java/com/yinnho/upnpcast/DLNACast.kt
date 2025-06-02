package com.yinnho.upnpcast

import android.content.Context
import com.yinnho.upnpcast.internal.DLNACastImpl
import com.yinnho.upnpcast.types.Device as TypesDevice
import com.yinnho.upnpcast.types.MediaAction as TypesMediaAction
import com.yinnho.upnpcast.types.PlaybackState as TypesPlaybackState
import com.yinnho.upnpcast.types.State as TypesState

/**
 * DLNACast - 极简DLNA投屏API
 * 
 * 使用方式：
 * ```
 * import com.yinnho.upnpcast.DLNACast
 * 
 * // 初始化
 * DLNACast.init(context)
 * 
 * // 搜索设备
 * DLNACast.search { devices: List<DLNACast.Device> ->
 *     // 处理设备列表
 * }
 * 
 * // 投屏
 * DLNACast.cast(url, title) { success ->
 *     // 处理结果
 * }
 * ```
 */
object DLNACast {
    
    // 类型定义 - 对外提供嵌套类型访问方式
    data class Device(
        val id: String,
        val name: String,
        val address: String,
        val isTV: Boolean
    ) {
        // 内部转换方法
        internal fun toTypes(): TypesDevice = TypesDevice(id, name, address, isTV)
        internal companion object {
            fun fromTypes(device: TypesDevice): Device = Device(device.id, device.name, device.address, device.isTV)
        }
    }
    
    enum class MediaAction {
        PLAY, PAUSE, STOP, VOLUME, MUTE, SEEK, GET_STATE;
        
        // 内部转换方法
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
    
    fun castToDevice(device: Device, url: String, title: String? = null, callback: (success: Boolean) -> Unit = {}) {
        DLNACastImpl.castToDevice(device.toTypes(), url, title, callback)
    }
    
    fun search(timeout: Long = 10000, callback: (devices: List<Device>) -> Unit) {
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