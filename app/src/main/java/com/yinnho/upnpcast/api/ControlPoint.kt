package com.yinnho.upnpcast.api

import com.yinnho.upnpcast.model.RemoteDevice

/**
 * UPnP控制点接口
 * 负责发现和控制UPnP设备
 */
interface ControlPoint {
    /**
     * 搜索UPnP设备
     */
    fun search()

    /**
     * 停止搜索设备
     */
    fun stopSearch()

    /**
     * 设置搜索超时时间
     * @param timeoutMs 超时时间（毫秒）
     */
    fun setSearchTimeout(timeoutMs: Long)

    /**
     * 获取已发现的设备列表
     */
    fun getDevices(): List<RemoteDevice>

    /**
     * 连接到指定设备
     */
    fun connect(device: RemoteDevice)

    /**
     * 断开与当前设备的连接
     */
    fun disconnect()

    /**
     * 是否正在搜索设备
     */
    fun isSearching(): Boolean

    /**
     * 是否已连接到设备
     */
    fun isConnected(): Boolean

    /**
     * 获取当前连接的设备
     */
    fun getCurrentDevice(): RemoteDevice?

    /**
     * 关闭控制点
     */
    fun shutdown()
} 