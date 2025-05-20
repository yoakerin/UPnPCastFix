package com.yinnho.upnpcast.event

import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.state.DeviceStateManager

/**
 * UPnP标准事件定义
 * 
 * 包含系统中所有核心事件类型。
 */

/**
 * 设备事件基类
 * 所有设备相关事件的基类
 */
abstract class DeviceEvent(
    val device: RemoteDevice,
    private val sourceObj: Any?
) : UPnPEvent {
    
    /**
     * 获取事件源
     */
    override val source: Any?
        get() = sourceObj
}

/**
 * 设备发现事件
 * 当新设备被发现时触发
 */
class DeviceDiscoveredEvent(
    device: RemoteDevice,
    source: Any?
) : DeviceEvent(device, source)

/**
 * 设备丢失事件
 * 当设备离线或不可用时触发
 */
class DeviceLostEvent(
    device: RemoteDevice,
    source: Any?
) : DeviceEvent(device, source)

/**
 * 设备添加事件
 * 当设备被添加到注册表时触发
 */
class DeviceAddedEvent(
    device: RemoteDevice,
    source: Any?
) : DeviceEvent(device, source)

/**
 * 设备移除事件
 * 当设备从注册表移除时触发
 */
class DeviceRemovedEvent(
    device: RemoteDevice,
    source: Any?
) : DeviceEvent(device, source)

/**
 * 设备连接事件
 * 当设备连接成功时触发
 */
class DeviceConnectedEvent(
    device: RemoteDevice,
    source: Any?
) : DeviceEvent(device, source)

/**
 * 设备断开连接事件
 * 当设备断开连接时触发
 */
class DeviceDisconnectedEvent(
    device: RemoteDevice,
    source: Any?
) : DeviceEvent(device, source)

/**
 * 设备状态变化事件
 * 当设备状态发生变化时触发
 */
class DeviceStateChangedEvent(
    device: RemoteDevice,
    val oldState: DeviceStateManager.DeviceState,
    val newState: DeviceStateManager.DeviceState,
    val stateInfo: DeviceStateManager.DeviceStateInfo,
    source: Any?
) : DeviceEvent(device, source)

/**
 * 设备列表变化事件
 * 当设备列表发生变化时触发
 */
class DeviceListChangedEvent(
    val devices: List<RemoteDevice>,
    private val sourceObj: Any?
) : UPnPEvent {
    
    /**
     * 获取事件源
     */
    override val source: Any?
        get() = sourceObj
}

/**
 * 设备错误事件
 * 当设备操作遇到错误时触发
 */
class DeviceErrorEvent(
    device: RemoteDevice,
    val errorCode: Int,
    val errorMessage: String,
    source: Any?
) : DeviceEvent(device, source)

/**
 * 设备搜索开始事件
 * 当开始搜索设备时触发
 */
class SearchStartedEvent(private val sourceObj: Any?) : UPnPEvent {
    override val source: Any?
        get() = sourceObj
}

/**
 * 设备搜索结束事件
 * 当设备搜索完成时触发
 */
class SearchFinishedEvent(
    val devices: List<RemoteDevice>,
    private val sourceObj: Any?
) : UPnPEvent {
    
    /**
     * 获取事件源
     */
    override val source: Any?
        get() = sourceObj
}

/**
 * 播放状态事件基类
 * 所有播放相关事件的基类
 */
abstract class PlaybackEvent(
    val device: RemoteDevice,
    private val sourceObj: Any?
) : UPnPEvent {
    
    /**
     * 获取事件源
     */
    override val source: Any?
        get() = sourceObj
}

/**
 * 播放开始事件
 * 当媒体开始播放时触发
 */
class PlaybackStartedEvent(
    device: RemoteDevice,
    val mediaUrl: String,
    val title: String,
    source: Any?
) : PlaybackEvent(device, source)

/**
 * 播放暂停事件
 * 当媒体暂停播放时触发
 */
class PlaybackPausedEvent(
    device: RemoteDevice,
    source: Any?
) : PlaybackEvent(device, source)

/**
 * 播放停止事件
 * 当媒体停止播放时触发
 */
class PlaybackStoppedEvent(
    device: RemoteDevice,
    source: Any?
) : PlaybackEvent(device, source)

/**
 * 播放位置改变事件
 * 当播放位置发生变化时触发
 */
class PlaybackPositionChangedEvent(
    device: RemoteDevice,
    val positionMs: Long,
    val durationMs: Long,
    source: Any?
) : PlaybackEvent(device, source) {
    
    /**
     * 获取播放进度百分比
     */
    val progressPercent: Int
        get() {
            if (durationMs <= 0) return 0
            return ((positionMs.toFloat() / durationMs) * 100).toInt().coerceIn(0, 100)
        }
}

/**
 * 音量变化事件
 * 当设备音量发生变化时触发
 */
class VolumeChangedEvent(
    device: RemoteDevice,
    val volume: Int,
    source: Any?
) : PlaybackEvent(device, source)

/**
 * 系统事件基类
 * 所有系统级事件的基类
 */
abstract class SystemEvent(
    private val sourceObj: Any?
) : UPnPEvent {
    
    /**
     * 获取事件源
     */
    override val source: Any?
        get() = sourceObj
}

/**
 * 系统初始化事件
 * 当系统初始化完成时触发
 */
class SystemInitializedEvent(source: Any?) : SystemEvent(source)

/**
 * 系统关闭事件
 * 当系统关闭时触发
 */
class SystemShutdownEvent(source: Any?) : SystemEvent(source)

/**
 * 网络状态变化事件
 * 当网络状态发生变化时触发
 */
class NetworkStateChangedEvent(
    val isConnected: Boolean,
    source: Any?
) : SystemEvent(source) 