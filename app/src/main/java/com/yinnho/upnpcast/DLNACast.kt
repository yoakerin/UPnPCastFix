package com.yinnho.upnpcast

import android.content.Context
import com.yinnho.upnpcast.internal.DLNACastImpl
import com.yinnho.upnpcast.types.Device as DeviceType
import com.yinnho.upnpcast.types.MediaAction as MediaActionType
import com.yinnho.upnpcast.types.PlaybackState as PlaybackStateType
import com.yinnho.upnpcast.types.State as StateType

// 类型别名定义
typealias MediaAction = MediaActionType
typealias PlaybackState = PlaybackStateType  
typealias Device = DeviceType
typealias State = StateType

/**
 * DLNACast - 极简DLNA投屏API
 */
object DLNACast {
    
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