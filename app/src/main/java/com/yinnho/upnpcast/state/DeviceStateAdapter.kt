package com.yinnho.upnpcast.state

import android.util.Log
import com.yinnho.upnpcast.device.locationKey
import com.yinnho.upnpcast.registry.DeviceRegistry
import com.yinnho.upnpcast.registry.StandardDeviceRegistry
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.state.DeviceStateManager.DeviceState
import java.util.concurrent.ConcurrentHashMap

/**
 * 设备状态适配器
 * 
 * 连接设备状态管理系统与设备注册表，确保设备状态更新时同步更新注册表
 * 优化状态和注册表的协调工作，减少重复逻辑
 */
class DeviceStateAdapter private constructor() : DeviceStateManager.StateChangeListener, DeviceRegistry.Listener {
    private val TAG = "DeviceStateAdapter"
    
    // 直接使用标准设备注册表
    private val deviceRegistry = StandardDeviceRegistry.getInstance()
    private val stateManager = DeviceStateManager.getInstance()
    
    // 缓存设备状态和位置的映射，避免重复查找
    private val deviceLocationMap = ConcurrentHashMap<String, String>()
    private val deviceUdnMap = ConcurrentHashMap<String, String>()
    
    init {
        Log.d(TAG, "设备状态适配器已初始化")
        
        // 双向监听，确保状态和注册表同步
        stateManager.addListener(this)
        deviceRegistry.addListener(this)
    }
    
    companion object {
        @Volatile
        private var instance: DeviceStateAdapter? = null
        
        fun getInstance(): DeviceStateAdapter {
            return instance ?: synchronized(this) {
                instance ?: DeviceStateAdapter().also { instance = it }
            }
        }
        
        fun releaseInstance() {
            synchronized(this) {
                instance?.release()
                instance = null
            }
        }
    }
    
    /**
     * 实现StateChangeListener接口的状态变化回调
     */
    override fun onDeviceStateChanged(
        deviceId: String,
        oldState: DeviceState,
        newState: DeviceState,
        info: DeviceStateManager.DeviceStateInfo
    ) {
        val device = info.device ?: return
        
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "设备状态变化: ${device.details.friendlyName ?: "未命名"} - ${oldState.name} -> ${newState.name}")
        }
        
        // 更新缓存
        deviceUdnMap[device.locationKey] = deviceId
        deviceLocationMap[deviceId] = device.locationKey
        
        when (newState) {
            DeviceState.UNKNOWN -> {
                // 不处理未知状态
                Log.w(TAG, "设备状态为未知: ${device.details.friendlyName}")
            }
            
            DeviceState.LOST, DeviceState.REMOVED -> {
                // 设备离线，从设备列表中移除
                Log.d(TAG, "设备离线，从注册表中移除: ${device.details.friendlyName}")
                deviceRegistry.removeDevice(device)
                
                // 清理缓存
                deviceLocationMap.remove(deviceId)
                deviceUdnMap.remove(device.locationKey)
            }
            
            DeviceState.ERROR -> {
                // 设备出错，视为离线
                Log.e(TAG, "设备出错，从注册表中移除: ${device.details.friendlyName}")
                deviceRegistry.removeDevice(device)
                
                // 清理缓存
                deviceLocationMap.remove(deviceId)
                deviceUdnMap.remove(device.locationKey)
            }
            
            DeviceState.DISCOVERED, 
            DeviceState.VALIDATED,
            DeviceState.CONNECTED,
            DeviceState.PLAYING,
            DeviceState.PAUSED,
            DeviceState.STOPPED,
            DeviceState.BUFFERING,
            DeviceState.TRANSITIONING -> {
                // 设备在各种在线状态，确保在设备列表中
                if (oldState == DeviceState.LOST || oldState == DeviceState.REMOVED || oldState == DeviceState.ERROR) {
                    // 之前标记为离线的设备重新出现，添加回列表
                    Log.d(TAG, "设备恢复在线，添加到注册表: ${device.details.friendlyName}")
                    deviceRegistry.addDevice(device)
                }
            }
        }
    }
    
    /**
     * 实现设备列表变化回调
     */
    override fun onDeviceListChanged(activeDevices: List<DeviceStateManager.DeviceStateInfo>) {
        // 由于双向绑定，此处不需要多余处理
    }
    
    /**
     * 实现DeviceRegistry.Listener接口
     */
    override fun onDeviceAdded(device: RemoteDevice) {
        // 检查设备是否已在状态管理器中
        val deviceId = device.identity.udn
        
        if (!stateManager.hasDevice(deviceId)) {
            // 注册设备到状态管理器
            registerDevice(device)
        }
        
        // 更新缓存
        deviceUdnMap[device.locationKey] = deviceId
        deviceLocationMap[deviceId] = device.locationKey
    }
    
    override fun onDeviceRemoved(device: RemoteDevice) {
        // 更新设备状态为LOST
        val deviceId = device.identity.udn
        
        try {
            stateManager.updateDeviceState(deviceId, DeviceState.LOST)
        } catch (e: Exception) {
            Log.w(TAG, "设备移除时更新状态失败: ${e.message}")
        }
        
        // 清理缓存
        deviceLocationMap.remove(deviceId)
        deviceUdnMap.remove(device.locationKey)
    }
    
    override fun onDeviceUpdated(device: RemoteDevice) {
        // 当设备更新时，刷新设备活跃状态
        updateDeviceActivity(device.identity.udn)
    }
    
    override fun onDeviceListUpdated(devices: List<RemoteDevice>) {
        // 由于直接绑定了状态管理器和设备注册表，这里可以处理轻量级的同步
        // 在这个简化版本中，我们不进行任何处理
    }
    
    /**
     * 注册设备并设置初始状态
     */
    fun registerDevice(device: RemoteDevice) {
        val deviceId = device.identity.udn
        stateManager.registerDevice(deviceId, device)
        
        // 更新缓存
        deviceUdnMap[device.locationKey] = deviceId
        deviceLocationMap[deviceId] = device.locationKey
    }
    
    /**
     * 更新设备状态
     */
    fun updateDeviceState(device: RemoteDevice, newState: DeviceState) {
        stateManager.updateDeviceState(device.identity.udn, newState)
    }
    
    /**
     * 记录设备错误
     */
    fun recordError(deviceId: String, errorMessage: String) {
        stateManager.recordError(deviceId, errorMessage)
    }
    
    /**
     * 更新设备活跃状态
     */
    fun updateDeviceActivity(deviceId: String) {
        stateManager.updateDeviceActivity(deviceId)
    }
    
    /**
     * 检查设备是否存在
     */
    fun hasDevice(deviceId: String): Boolean {
        return stateManager.hasDevice(deviceId)
    }
    
    /**
     * 批量同步设备状态
     */
    fun syncDeviceList(remoteDevices: List<RemoteDevice>) {
        Log.d(TAG, "批量同步设备列表 - ${remoteDevices.size}个设备")
        
        // 使用设备注册表更新设备列表
        deviceRegistry.updateDeviceList(remoteDevices)
        
        // 同时注册到状态管理器
        remoteDevices.forEach { device ->
            registerDevice(device)
        }
    }
    
    /**
     * 根据位置键获取设备ID
     */
    fun getDeviceIdByLocation(locationKey: String): String? {
        return deviceUdnMap[locationKey]
    }
    
    /**
     * 根据设备ID获取位置键
     */
    fun getLocationByDeviceId(deviceId: String): String? {
        return deviceLocationMap[deviceId]
    }
    
    /**
     * 释放资源
     */
    fun release() {
        // 移除监听器
        stateManager.removeListener(this)
        deviceRegistry.removeListener(this)
        
        // 清理缓存
        deviceLocationMap.clear()
        deviceUdnMap.clear()
        
        Log.d(TAG, "设备状态适配器已释放")
    }
} 