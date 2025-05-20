package com.yinnho.upnpcast.api

/**
 * 投屏监听器接口
 */
interface CastListener {
    /**
     * 设备列表已更新
     */
    fun onDeviceListUpdated(deviceList: List<RemoteDevice>)
    
    /**
     * 已连接到设备
     */
    fun onConnected(device: RemoteDevice)
    
    /**
     * 设备已断开连接
     */
    fun onDisconnected()
    
    /**
     * 发生错误
     * @param error DLNA异常对象，包含错误类型和详细信息
     */
    fun onError(error: DLNAException)
} 