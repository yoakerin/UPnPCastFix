package com.yinnho.upnpcast

import android.content.Context
import com.yinnho.upnpcast.internal.DLNACastImpl

/**
 * 操作结果回调
 */
typealias DLNAResult = (success: Boolean) -> Unit

/**
 * 设备列表回调
 */
typealias DLNADeviceList = (devices: List<DLNACast.Device>) -> Unit

/**
 * 设备选择回调
 */
typealias DLNADeviceSelector = (devices: List<DLNACast.Device>) -> DLNACast.Device?

/**
 * DLNACast - 极简DLNA投屏API
 * 
 * 🎯 单文件导入，功能全覆盖：
 * 
 * ```kotlin
 * import com.yinnho.upnpcast.DLNACast
 * 
 * // 初始化
 * DLNACast.init(this)
 * 
 * // 一键投屏
 * DLNACast.cast("http://video.mp4") { success -> }
 * 
 * // 智能选择设备投屏
 * DLNACast.castTo("http://video.mp4") { devices -> devices.firstOrNull() }
 * 
 * // 搜索设备
 * DLNACast.search { devices: List<DLNACast.Device> -> }
 * 
 * // 媒体控制
 * DLNACast.control(DLNACast.MediaAction.PAUSE)
 * DLNACast.control(DLNACast.MediaAction.VOLUME, 50)
 * 
 * // 获取状态
 * val state: DLNACast.State = DLNACast.getState()
 * if (state.isPlaying) { /* 正在播放 */ }
 * ```
 */
object DLNACast {
    
    // ================ 类型定义 ================
    
    /**
     * 媒体控制动作
     */
    enum class MediaAction {
        PLAY,           // 播放/恢复
        PAUSE,          // 暂停  
        STOP,           // 停止
        VOLUME,         // 设置音量 (需要value参数: Int 0-100)
        MUTE,           // 静音切换 (可选value参数: Boolean)
        SEEK,           // 跳转到指定位置 (需要value参数: Long 毫秒)
        GET_STATE       // 获取播放状态
    }
    
    /**
     * 播放状态
     */
    enum class PlaybackState {
        IDLE,        // 空闲
        PLAYING,     // 播放中
        PAUSED,      // 暂停
        STOPPED,     // 停止
        BUFFERING,   // 缓冲中
        ERROR        // 错误状态
    }
    
    /**
     * DLNA设备信息
     */
    data class Device(
        val id: String,              // 设备唯一标识
        val name: String,            // 设备显示名称
        val address: String,         // 设备网络地址
        val manufacturer: String,    // 制造商
        val model: String,           // 型号
        val isTV: Boolean,           // 是否为电视
        val isBox: Boolean,          // 是否为盒子
        val priority: Int            // 优先级（TV=100, Box=80, 其他=60）
    )
    
    /**
     * DLNA连接和播放状态
     */
    data class State(
        val isConnected: Boolean,           // 是否连接到设备
        val currentDevice: Device?,         // 当前连接的设备
        val playbackState: PlaybackState,   // 播放状态
        val volume: Int = -1,               // 当前音量 (-1表示未知)
        val isMuted: Boolean = false        // 是否静音
    ) {
        /**
         * 是否正在播放
         */
        val isPlaying: Boolean get() = playbackState == PlaybackState.PLAYING
        
        /**
         * 是否已暂停
         */
        val isPaused: Boolean get() = playbackState == PlaybackState.PAUSED
        
        /**
         * 是否空闲状态
         */
        val isIdle: Boolean get() = playbackState == PlaybackState.IDLE
    }
    
    // ================ 核心API ================
    
    /**
     * 初始化DLNACast（建议在Application中调用）
     * 
     * @param context 应用上下文
     */
    fun init(context: Context) {
        DLNACastImpl.init(context)
    }
    
    /**
     * 一键投屏 - 自动选择最佳设备
     * 
     * @param url 媒体URL
     * @param title 媒体标题（可选）
     * @param callback 成功/失败回调
     */
    fun cast(url: String, title: String? = null, callback: DLNAResult = {}) {
        DLNACastImpl.cast(url, title, callback)
    }
    
    /**
     * 智能投屏 - 用户选择设备
     * 
     * @param url 媒体URL
     * @param title 媒体标题（可选）
     * @param deviceSelector 设备选择回调，返回null表示取消
     */
    fun castTo(url: String, title: String? = null, deviceSelector: DLNADeviceSelector) {
        DLNACastImpl.castTo(url, title, deviceSelector)
    }
    
    /**
     * 直接向指定设备投屏
     * 
     * @param device 目标设备
     * @param url 媒体URL
     * @param title 媒体标题（可选）
     * @param callback 成功/失败回调
     */
    fun castToDevice(device: Device, url: String, title: String? = null, callback: DLNAResult = {}) {
        DLNACastImpl.castToDevice(device, url, title, callback)
    }
    
    /**
     * 搜索DLNA设备
     * 
     * @param timeout 搜索超时时间（毫秒），默认10秒
     * @param callback 设备列表回调（增量回调，每发现一个设备就回调一次）
     */
    fun search(timeout: Long = 10000, callback: DLNADeviceList) {
        DLNACastImpl.search(timeout, callback)
    }
    
    /**
     * 统一控制接口 - 替代所有分散的控制方法
     * 
     * @param action 控制动作
     * @param value 动作参数（音量值、静音状态等）
     * @param callback 成功/失败回调
     * 
     * 使用示例：
     * ```kotlin
     * DLNACast.control(MediaAction.PLAY)           // 播放
     * DLNACast.control(MediaAction.PAUSE)          // 暂停
     * DLNACast.control(MediaAction.VOLUME, 50)     // 设置音量50%
     * DLNACast.control(MediaAction.MUTE, true)     // 静音
     * ```
     */
    fun control(action: MediaAction, value: Any? = null, callback: DLNAResult = {}) {
        DLNACastImpl.control(action, value, callback)
    }
    
    /**
     * 获取当前DLNA状态
     * 
     * @return 包含连接状态、当前设备、播放状态等信息
     */
    fun getState(): State {
        return DLNACastImpl.getState()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        DLNACastImpl.release()
    }
    
    // ================ 兼容性API (标记为过时) ================
    
    @Deprecated("Use cast() instead", ReplaceWith("cast(url, title, callback)"))
    fun castAuto(url: String, title: String? = null, callback: DLNAResult) = cast(url, title, callback)
    
    @Deprecated("Use castTo() instead", ReplaceWith("castTo(url, title, deviceSelector)"))
    fun castWithSelection(url: String, title: String? = null, deviceSelector: DLNADeviceSelector) = castTo(url, title, deviceSelector)
    
    @Deprecated("Use control(MediaAction.PAUSE) instead", ReplaceWith("control(MediaAction.PAUSE, callback = callback)"))
    fun pause(callback: DLNAResult) = control(MediaAction.PAUSE, callback = callback)
    
    @Deprecated("Use control(MediaAction.PLAY) instead", ReplaceWith("control(MediaAction.PLAY, callback = callback)"))
    fun resume(callback: DLNAResult) = control(MediaAction.PLAY, callback = callback)
    
    @Deprecated("Use control(MediaAction.STOP) instead", ReplaceWith("control(MediaAction.STOP, callback = callback)"))
    fun stop(callback: DLNAResult) = control(MediaAction.STOP, callback = callback)
    
    @Deprecated("Use control(MediaAction.VOLUME, volume) instead", ReplaceWith("control(MediaAction.VOLUME, volume, callback)"))
    fun setVolume(volume: Int, callback: DLNAResult) = control(MediaAction.VOLUME, volume, callback)
    
    @Deprecated("Use control(MediaAction.MUTE, mute) instead", ReplaceWith("control(MediaAction.MUTE, mute, callback)"))
    fun setMute(mute: Boolean, callback: DLNAResult) = control(MediaAction.MUTE, mute, callback)
    
    @Deprecated("Use getState().isPlaying instead", ReplaceWith("getState().isPlaying"))
    fun isPlaying(): Boolean = getState().isPlaying
    
    @Deprecated("Use getState().currentDevice instead", ReplaceWith("getState().currentDevice"))
    fun getCurrentDevice(): Device? = getState().currentDevice
    
    @Deprecated("Use getState().playbackState instead", ReplaceWith("getState().playbackState"))
    fun getCurrentState(): PlaybackState = getState().playbackState
} 