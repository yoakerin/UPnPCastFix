package com.yinnho.upnpcast.manager.connection

import com.yinnho.upnpcast.DLNAErrorType
import com.yinnho.upnpcast.DLNAException
import com.yinnho.upnpcast.RemoteDevice
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.manager.DeviceManager
import com.yinnho.upnpcast.manager.callback.CallbackManager

/**
 * 设备连接管理器
 * 负责管理设备连接状态和连接操作
 */
class DeviceConnectionManager(
    private val deviceManager: DeviceManager,
    private val callbackManager: CallbackManager
) {
    companion object {
        private const val TAG = "DeviceConnectionManager"
    }
    
    // 当前连接的设备
    private var currentConnectedDevice: com.yinnho.upnpcast.model.RemoteDevice? = null
    
    /**
     * 获取当前连接的设备
     */
    fun getCurrentDevice(): com.yinnho.upnpcast.model.RemoteDevice? = currentConnectedDevice
    
    /**
     * 是否已连接设备
     */
    fun isConnected(): Boolean = currentConnectedDevice != null
    
    /**
     * 连接到设备
     */
    fun connectToDevice(device: RemoteDevice) {
        try {
            EnhancedThreadManager.d(TAG, "连接到设备: ${device.displayName}")
            
            // 尝试获取内部设备对象
            val internalDevice = device.details as? com.yinnho.upnpcast.model.RemoteDevice
                ?: deviceManager.getDeviceById(device.id)
                
            // 如果找到设备，尝试连接
            if (internalDevice != null) {
                connectToInternalDevice(internalDevice)
            } else {
                val error = DLNAException(DLNAErrorType.DEVICE_ERROR, "无法连接到设备：找不到有效设备")
                callbackManager.notifyError(error)
                throw error
            }
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "连接到设备失败: ${e.message}", e)
            
            if (e !is DLNAException) {
                val error = DLNAException(DLNAErrorType.CONNECTION_ERROR, "连接设备失败: ${e.message}")
                callbackManager.notifyError(error)
                throw error
            } else {
                throw e
            }
        }
    }
    
    /**
     * 连接到内部设备对象
     */
    internal fun connectToInternalDevice(device: com.yinnho.upnpcast.model.RemoteDevice) {
        try {
            // 如果当前有连接的设备，先断开
            if (currentConnectedDevice != null) {
                disconnect()
            }
            
            // 更新当前连接的设备
            currentConnectedDevice = device
            
            // 确保设备控制器被添加到DLNACastManager的PlayerManager中
            val deviceId = device.identity.udn
            if (deviceId != null) {
                // 直接使用设备管理器的设备
                try {
                    // 设备已经在deviceManager中，无需添加
                    EnhancedThreadManager.d(TAG, "设备可用: $deviceId")
                } catch (e: Exception) {
                    EnhancedThreadManager.e(TAG, "设备检查失败: ${e.message}", e)
                }
            }
            
            // 通知内部连接成功
            callbackManager.notifyInternalDeviceConnected(device)
            
            // 转换为API设备对象并通知回调
            val convertedDevice = RemoteDevice(
                id = device.identity.udn,
                displayName = device.details.friendlyName ?: device.identity.udn,
                address = device.identity.descriptorURL.host ?: "",
                manufacturer = device.details.manufacturerInfo?.name ?: "",
                model = device.details.modelInfo?.name ?: "",
                details = device
            )
            
            // 通知用户连接成功
            callbackManager.notifyDeviceConnected(convertedDevice)
            
            EnhancedThreadManager.d(TAG, "设备连接成功: ${device.details.friendlyName}")
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "连接内部设备失败: ${e.message}", e)
            
            // 断开连接，清理状态
            currentConnectedDevice = null
            
            // 构造并通知错误
            val error = DLNAException(DLNAErrorType.CONNECTION_ERROR, "连接设备失败: ${e.message}")
            callbackManager.notifyError(error)
            throw error
        }
    }
    
    /**
     * 断开当前连接
     */
    fun disconnect() {
        try {
            EnhancedThreadManager.d(TAG, "断开设备连接")
            
            if (currentConnectedDevice == null) {
                EnhancedThreadManager.d(TAG, "没有连接的设备，无需断开")
                return
            }
            
            // 清除当前连接
            currentConnectedDevice = null
            
            // 通知内部断开连接
            callbackManager.notifyInternalDeviceDisconnected()
            
            // 通知用户断开连接
            callbackManager.notifyDeviceDisconnected()
            
            EnhancedThreadManager.d(TAG, "设备断开成功")
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "断开设备连接失败: ${e.message}", e)
            
            // 确保清除当前连接
            currentConnectedDevice = null
            
            // 构造并通知错误
            val error = DLNAException(DLNAErrorType.DEVICE_ERROR, "断开设备连接失败: ${e.message}")
            callbackManager.notifyError(error)
            throw error
        }
    }
    
    /**
     * 检查设备是否可用
     */
    fun isDeviceAvailable(deviceId: String): Boolean {
        return deviceManager.getDeviceById(deviceId) != null
    }
    
    /**
     * 获取设备友好名称
     */
    fun getDeviceName(deviceId: String): String {
        val device = deviceManager.getDeviceById(deviceId)
        return device?.details?.friendlyName ?: deviceId
    }
    
    /**
     * 重新连接到上次连接的设备
     * @return 是否成功重新连接
     */
    fun reconnect(): Boolean {
        val device = currentConnectedDevice ?: return false
        
        try {
            // 先断开连接
            disconnect()
            
            // 重新连接
            connectToInternalDevice(device)
            return true
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "重新连接失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 连接到指定ID的设备
     */
    fun connectToDeviceById(deviceId: String): Boolean {
        val device = deviceManager.getDeviceById(deviceId) ?: return false
        
        try {
            connectToInternalDevice(device)
            return true
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "通过ID连接设备失败: ${e.message}", e)
            return false
        }
    }
} 