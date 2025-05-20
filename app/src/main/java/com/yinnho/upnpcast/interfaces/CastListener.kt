package com.yinnho.upnpcast.interfaces

import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.api.DLNAException

/**
 * 内部投屏监听器接口
 * 用于内部模块与API之间的转换
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
    
    /**
     * 设备已添加（可选实现）
     * @param device 被添加的设备
     */
    fun onDeviceAdded(device: RemoteDevice) {}
    
    /**
     * 设备已移除（可选实现）
     * @param device 被移除的设备
     */
    fun onDeviceRemoved(device: RemoteDevice) {}
    
    /**
     * 设备已更新（可选实现）
     * @param device 被更新的设备
     */
    fun onDeviceUpdated(device: RemoteDevice) {}
} 