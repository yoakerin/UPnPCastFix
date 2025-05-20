package com.yinnho.upnpcast.interfaces

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

/**
 * 路由器接口
 * 负责处理网络数据包路由和多播功能
 */
interface Router {
    /**
     * 启动路由器
     */
    fun startup()

    /**
     * 关闭路由器
     */
    fun shutdown()

    /**
     * 是否正在运行
     */
    fun isRunning(): Boolean

    /**
     * 启用路由器
     */
    fun enable()

    /**
     * 禁用路由器
     */
    fun disable()

    /**
     * 启用多播接收
     */
    fun enableMulticastReceive()

    /**
     * 禁用多播接收
     */
    fun disableMulticastReceive()

    /**
     * 启用多播发送
     */
    fun enableMulticastSend()

    /**
     * 发送多播消息
     * @param message 发送的消息内容
     */
    fun sendMulticast(message: ByteArray)

    /**
     * 发送单播消息
     */
    fun sendUnicast(message: String, address: String, port: Int)

    /**
     * 处理收到的UPnP请求消息
     */
    fun receivedRequest(msg: IncomingDatagramMessage<UpnpRequest>)

    /**
     * 处理收到的UPnP响应消息
     */
    fun receivedResponse(msg: IncomingDatagramMessage<UpnpResponse>)

    /**
     * 设置搜索超时时间
     * @param timeoutMs 超时时间（毫秒）
     */
    fun setSearchTimeout(timeoutMs: Long)
}

/**
 * 注册表监听器接口
 * 用于监听设备的添加和移除
 */
interface RegistryListener {
    /**
     * 设备添加时调用
     */
    fun deviceAdded(device: RemoteDevice)

    /**
     * 设备移除时调用
     */
    fun deviceRemoved(device: RemoteDevice)

    /**
     * 注册表关闭前调用
     */
    fun beforeShutdown(registry: Registry)

    /**
     * 注册表关闭后调用
     */
    fun afterShutdown()
}

/**
 * 注册表接口
 * 负责管理UPnP设备
 */
interface Registry {
    /**
     * 添加监听器
     */
    fun addListener(listener: RegistryListener)

    /**
     * 移除监听器
     */
    fun removeListener(listener: RegistryListener)

    /**
     * 检查是否包含特定监听器
     */
    fun hasListener(listener: RegistryListener): Boolean

    /**
     * 添加设备
     */
    fun addDevice(device: RemoteDevice)

    /**
     * 移除设备
     */
    fun removeDevice(device: RemoteDevice)

    /**
     * 移除所有远程设备
     */
    fun removeAllRemoteDevices()

    /**
     * 获取所有设备
     */
    fun getDevices(): List<RemoteDevice>

    /**
     * 根据UDN获取设备
     */
    fun getDevice(udn: String): RemoteDevice?

    /**
     * 关闭注册表
     */
    fun shutdown()

    /**
     * 转储注册表状态信息
     * 用于调试和日志记录
     *
     * @param detailLevel 详细程度 (0-最少信息, 1-基本信息, 2-详细信息)
     * @return 包含注册表状态的字符串
     */
    fun dump(detailLevel: Int = 1): String
} 