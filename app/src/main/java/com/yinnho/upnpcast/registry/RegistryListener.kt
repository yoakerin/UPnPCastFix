package com.yinnho.upnpcast.registry

import android.util.Log
import com.yinnho.upnpcast.manager.DLNACastManagerImpl
import com.yinnho.upnpcast.model.RemoteDevice

/**
 * 注册表监听器适配器
 * 
 * 用于连接新的DeviceRegistry与现有系统的回调机制，
 * 确保设备状态变化能够正确地通知给现有组件。
 */
class RegistryListener private constructor() : DeviceRegistry.Listener {
    private val TAG = "RegistryListener"
    
    /**
     * 设备添加时触发
     */
    override fun onDeviceAdded(device: RemoteDevice) {
        try {
            // 使用安全方式获取实例
            getManagerInstance()?.handleDeviceAdded(device)
        } catch (e: Exception) {
            Log.e(TAG, "通知设备添加失败", e)
        }
    }
    
    /**
     * 设备移除时触发
     */
    override fun onDeviceRemoved(device: RemoteDevice) {
        try {
            // 使用安全方式获取实例
            getManagerInstance()?.handleDeviceRemoved(device)
        } catch (e: Exception) {
            Log.e(TAG, "通知设备移除失败", e)
        }
    }
    
    /**
     * 设备更新时触发
     */
    override fun onDeviceUpdated(device: RemoteDevice) {
        try {
            // 使用安全方式获取实例
            getManagerInstance()?.handleDeviceUpdated(device)
        } catch (e: Exception) {
            Log.e(TAG, "通知设备更新失败", e)
        }
    }
    
    /**
     * 设备列表更新时触发
     */
    override fun onDeviceListUpdated(devices: List<RemoteDevice>) {
        try {
            Log.d(TAG, "设备列表更新回调 - ${devices.size}个设备")
            
            // 使用安全方式获取实例
            val manager = getManagerInstance()
            if (manager != null) {
                Log.d(TAG, "正在通知DLNACastManagerImpl更新设备列表")
                manager.notifyDeviceListUpdated(devices)
            } else {
                Log.w(TAG, "DLNACastManagerImpl为空，无法通知设备列表更新")
            }
        } catch (e: Exception) {
            Log.e(TAG, "通知设备列表更新失败", e)
        }
    }
    
    /**
     * 安全获取DLNACastManagerImpl实例
     * 如果未初始化，尝试通过DLNACastManager获取Context并初始化
     */
    private fun getManagerInstance(): DLNACastManagerImpl? {
        try {
            return DLNACastManagerImpl.getInstance()
        } catch (e: IllegalStateException) {
            // 尝试从DLNACastManager获取Context
            Log.d(TAG, "尝试获取DLNACastManagerImpl实例失败，尝试重新初始化")
            
            try {
                // 尝试从DLNACastManager获取上下文并初始化DLNACastManagerImpl
                val dlnaCastManager = com.yinnho.upnpcast.DLNACastManager.getInstance()
                if (dlnaCastManager != null) {
                    // 成功获取DLNACastManager，通过反射获取上下文
                    val contextField = com.yinnho.upnpcast.DLNACastManager::class.java.getDeclaredField("contextRef")
                    contextField.isAccessible = true
                    val contextRef = contextField.get(dlnaCastManager) as java.lang.ref.WeakReference<*>
                    val context = contextRef.get() as? android.content.Context
                    
                    if (context != null) {
                        Log.d(TAG, "使用DLNACastManager的Context初始化DLNACastManagerImpl")
                        return DLNACastManagerImpl.getInstance(context)
                    } else {
                        Log.w(TAG, "DLNACastManager的Context已被回收")
                    }
                } else {
                    Log.w(TAG, "无法获取DLNACastManager实例")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "尝试重新初始化DLNACastManagerImpl失败", e2)
            }
            
            // 仍然失败，返回null
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "DLNACastManagerImpl尚未初始化，忽略通知")
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "获取DLNACastManagerImpl实例失败", e)
            return null
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: RegistryListener? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(): RegistryListener {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RegistryListener().also { INSTANCE = it }
            }
        }
    }
} 