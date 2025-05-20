package com.yinnho.upnpcast.registry

import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.utils.Releasable

/**
 * 设备注册表接口
 * 
 * 提供统一的设备管理接口，负责DLNA设备的注册、查询和移除功能。
 * 设计为可扩展的接口，允许不同的实现类采用不同的存储和通知策略。
 */
interface DeviceRegistry : Releasable {
    
    /**
     * 监听器接口，用于设备注册表状态变化通知
     */
    interface Listener {
        /**
         * 设备添加时触发
         */
        fun onDeviceAdded(device: RemoteDevice)
        
        /**
         * 设备移除时触发
         */
        fun onDeviceRemoved(device: RemoteDevice)
        
        /**
         * 设备更新时触发
         */
        fun onDeviceUpdated(device: RemoteDevice)
        
        /**
         * 设备列表更新时触发
         */
        fun onDeviceListUpdated(devices: List<RemoteDevice>)
    }
    
    /**
     * 添加监听器
     */
    fun addListener(listener: Listener)
    
    /**
     * 移除监听器
     */
    fun removeListener(listener: Listener)
    
    /**
     * 添加设备
     * 
     * @param device 要添加的设备
     * @return 如果设备是新添加的，返回true；如果是更新已有设备，返回false
     */
    fun addDevice(device: RemoteDevice): Boolean
    
    /**
     * 移除设备
     * 
     * @param device 要移除的设备
     * @return 是否成功移除
     */
    fun removeDevice(device: RemoteDevice): Boolean
    
    /**
     * 根据设备ID获取设备
     * 
     * @param deviceId 设备ID (UDN)
     * @return 找到的设备，如果不存在则返回null
     */
    fun getDeviceById(deviceId: String): RemoteDevice?
    
    /**
     * 根据位置键获取设备
     * 通常是设备的描述文件URL
     * 
     * @param locationKey 设备位置键
     * @return 找到的设备，如果不存在则返回null
     */
    fun getDeviceByLocation(locationKey: String): RemoteDevice?
    
    /**
     * 获取所有设备
     * 
     * @return 所有注册的设备列表
     */
    fun getAllDevices(): List<RemoteDevice>
    
    /**
     * 批量更新设备列表
     * 
     * @param devices 新的设备列表
     */
    fun updateDeviceList(devices: List<RemoteDevice>)
    
    /**
     * 清空设备列表
     */
    fun clearDevices()
    
    /**
     * 获取设备数量
     * 
     * @return 当前注册表中的设备数量
     */
    fun getDeviceCount(): Int
    
    /**
     * 导出注册表状态
     * 
     * @param detailLevel 详细级别(0-基本信息，1-包含设备列表，2-包含详细设备信息)
     * @return 注册表状态的字符串表示
     */
    fun dump(detailLevel: Int = 0): String
} 