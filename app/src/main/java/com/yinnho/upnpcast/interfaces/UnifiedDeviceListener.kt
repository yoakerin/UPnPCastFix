package com.yinnho.upnpcast.interfaces

import com.yinnho.upnpcast.model.RemoteDevice

/**
 * 统一设备监听器接口
 *
 * 提供统一的设备事件回调机制。
 */
interface UnifiedDeviceListener {
    // =============== 设备生命周期 ===============

    /**
     * 设备添加时调用
     */
    fun onDeviceAdded(device: RemoteDevice) {}

    /**
     * 设备移除时调用
     */
    fun onDeviceRemoved(device: RemoteDevice) {}

    /**
     * 设备更新时调用
     */
    fun onDeviceUpdated(device: RemoteDevice) {}

    /**
     * 设备连接时调用
     */
    fun onDeviceConnected(device: RemoteDevice) {}

    /**
     * 设备断开连接时调用
     */
    fun onDeviceDisconnected(device: RemoteDevice) {}

    // =============== 搜索事件 ===============

    /**
     * 开始设备搜索时调用
     */
    fun onSearchStarted() {}

    /**
     * 设备搜索完成时调用
     */
    fun onSearchCompleted(devices: List<RemoteDevice>) {}

    /**
     * 设备搜索取消时调用
     */
    fun onSearchCancelled() {}

    // =============== 服务状态 ===============

    /**
     * 服务初始化完成时调用
     */
    fun onServiceInitialized() {}

    /**
     * 服务连接时调用
     */
    fun onServiceConnected() {}

    /**
     * 服务断开时调用
     */
    fun onServiceDisconnected() {}

    /**
     * 服务即将关闭时调用
     */
    fun onServiceShutdown() {}

    // =============== 错误处理 ===============

    /**
     * 发生错误时调用
     */
    fun onError(error: String) {}

    /**
     * 服务错误时调用
     */
    fun onServiceError(error: Exception) {}

    /**
     * 接口适配器，提供所有方法的默认空实现
     */
    open class Adapter : UnifiedDeviceListener
} 