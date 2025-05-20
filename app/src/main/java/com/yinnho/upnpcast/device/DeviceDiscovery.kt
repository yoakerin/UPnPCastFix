package com.yinnho.upnpcast.device

import com.yinnho.upnpcast.interfaces.UnifiedDeviceListener
import com.yinnho.upnpcast.model.RemoteDevice

/**
 * 设备发现类
 * 用于搜索和发现网络上的DLNA设备
 */
class DeviceDiscovery : IDeviceDiscovery {
    private var isSearching = false
    private val discoveredDevices = mutableListOf<RemoteDevice>()
    private var searchTimeout: Long = 10000
    private var deviceFilter: ((RemoteDevice) -> Boolean)? = null
    
    private val listeners = mutableListOf<UnifiedDeviceListener>()
    
    override fun addListener(listener: UnifiedDeviceListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    override fun removeListener(listener: UnifiedDeviceListener) {
        listeners.remove(listener)
    }
    
    override fun startSearch() {
        if (isSearching) return
        
        isSearching = true
        discoveredDevices.clear()
        
        listeners.forEach { it.onSearchStarted() }
        
        // 通知监听器搜索已开始
        // 实际实现中会发送网络广播来发现设备
        // 简化实现
    }
    
    override fun stopSearch() {
        if (!isSearching) return
        
        isSearching = false
        
        // 通知监听器搜索已完成，并传递发现的设备列表
        listeners.forEach { it.onSearchCompleted(discoveredDevices) }
    }
    
    override fun setSearchTimeout(timeoutMs: Long) {
        searchTimeout = timeoutMs
    }
    
    override fun getCachedDevices(): List<RemoteDevice> {
        return discoveredDevices.toList()
    }
    
    override fun getDeviceByUDN(udn: String): RemoteDevice? {
        return discoveredDevices.find { it.identity.udn == udn }
    }
    
    override fun clearCache() {
        discoveredDevices.clear()
    }
    
    override fun setDeviceFilter(filter: (RemoteDevice) -> Boolean) {
        deviceFilter = filter
    }
    
    override fun onDeviceFound(device: RemoteDevice) {
        if (deviceFilter?.invoke(device) != false) {
            addDiscoveredDevice(device)
        }
    }
    
    override fun onDeviceLost(device: RemoteDevice) {
        removeDevice(device)
    }
    
    /**
     * 添加发现的设备
     * 内部使用，由网络层调用
     */
    internal fun addDiscoveredDevice(device: RemoteDevice) {
        if (!discoveredDevices.contains(device)) {
            discoveredDevices.add(device)
            
            // 通知监听器发现了新设备
            listeners.forEach { it.onDeviceAdded(device) }
        }
    }
    
    /**
     * 移除离线设备
     * 内部使用，由网络层调用
     */
    internal fun removeDevice(device: RemoteDevice) {
        if (discoveredDevices.remove(device)) {
            // 通知监听器设备已移除
            listeners.forEach { it.onDeviceRemoved(device) }
        }
    }
    
    /**
     * 是否正在搜索设备
     */
    fun isSearching(): Boolean {
        return isSearching
    }
} 