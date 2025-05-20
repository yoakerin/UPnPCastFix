package com.yinnho.upnpcast.device

import com.yinnho.upnpcast.interfaces.UnifiedDeviceListener
import com.yinnho.upnpcast.model.RemoteDevice

/**
 * 设备发现接口
 * 用于发现网络上的DLNA设备
 */
interface IDeviceDiscovery {
    /**
     * 添加设备发现监听器
     * @param listener 监听器
     */
    fun addListener(listener: UnifiedDeviceListener)
    
    /**
     * 移除设备发现监听器
     * @param listener 监听器
     */
    fun removeListener(listener: UnifiedDeviceListener)
    
    /**
     * 开始搜索设备
     */
    fun startSearch()
    
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
     * 获取缓存的设备列表
     * @return 设备列表
     */
    fun getCachedDevices(): List<RemoteDevice>
    
    /**
     * 根据UDN获取设备
     * @param udn 设备UDN
     * @return 设备，如果不存在则返回null
     */
    fun getDeviceByUDN(udn: String): RemoteDevice?
    
    /**
     * 清除设备缓存
     */
    fun clearCache()
    
    /**
     * 设置设备过滤器
     * @param filter 设备过滤器
     */
    fun setDeviceFilter(filter: (RemoteDevice) -> Boolean)
    
    /**
     * 设备发现回调
     * 内部使用，由搜索引擎调用
     */
    fun onDeviceFound(device: RemoteDevice)
    
    /**
     * 设备消失回调
     * 内部使用，由搜索引擎调用
     */
    fun onDeviceLost(device: RemoteDevice)
} 