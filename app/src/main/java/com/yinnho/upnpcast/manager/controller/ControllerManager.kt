package com.yinnho.upnpcast.manager.controller

import com.yinnho.upnpcast.DLNAErrorType
import com.yinnho.upnpcast.DLNAException
import com.yinnho.upnpcast.controller.DlnaController
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.cache.UPnPCacheManager

/**
 * 控制器管理器
 * 负责设备控制器的创建和缓存管理
 */
class ControllerManager {
    companion object {
        private const val TAG = "ControllerManager"
    }
    
    // 使用UPnPCacheManager替代自己的缓存
    private val cacheManager = UPnPCacheManager.getInstance()
    
    // 控制器缓存已迁移到UPnPCacheManager
    // private val controllerCache = ConcurrentHashMap<String, DlnaController>()
    
    // 错误监听器
    private var errorListener: ((DLNAException) -> Unit)? = null
    
    /**
     * 设置错误监听器
     */
    fun setErrorListener(listener: ((DLNAException) -> Unit)?) {
        this.errorListener = listener
    }
    
    /**
     * 获取设备控制器(优先从缓存获取)
     * @param deviceId 设备ID
     * @param throwOnNotFound 是否在未找到时抛出异常
     * @return 设备控制器，如果未找到且不抛出异常则返回null
     */
    fun getController(deviceId: String, throwOnNotFound: Boolean = false): DlnaController? {
        // 使用UPnPCacheManager获取控制器
        val cachedController = cacheManager.getController(deviceId, DlnaController::class.java)
        if (cachedController != null) {
            EnhancedThreadManager.d(TAG, "使用缓存的控制器: $deviceId")
            return cachedController
        }
        
        if (throwOnNotFound) {
            val error = DLNAException(DLNAErrorType.DEVICE_ERROR, "设备控制器不存在: $deviceId")
            errorListener?.invoke(error)
            throw error
        }
        
        return null
    }
    
    /**
     * 添加设备控制器
     * @param deviceId 设备ID
     * @param device 设备对象
     * @return 创建的控制器
     */
    fun addController(deviceId: String, device: RemoteDevice): DlnaController {
        // 先使用UPnPCacheManager检查缓存
        val existingController = cacheManager.getController(deviceId, DlnaController::class.java)
        if (existingController != null) {
            EnhancedThreadManager.d(TAG, "已存在控制器，直接返回: $deviceId")
            return existingController
        }
        
        // 创建新控制器
        val controller = DlnaController(device)
        
        // 使用UPnPCacheManager缓存控制器
        cacheManager.cacheController(deviceId, controller)
        EnhancedThreadManager.d(TAG, "创建并缓存新控制器: $deviceId")
        
        return controller
    }
    
    /**
     * 移除控制器
     * @param deviceId 设备ID
     */
    fun removeController(deviceId: String) {
        try {
            // 检查控制器是否存在
            val controller = cacheManager.getController(deviceId, DlnaController::class.java)
            if (controller != null) {
                // 尝试停止控制器
                stopControllerSafely(controller)
                
                // 从缓存中移除 - 通过更新为null值实现
                cacheManager.cacheController(deviceId, controller.javaClass.newInstance().apply { release() })
                EnhancedThreadManager.d(TAG, "移除控制器: $deviceId")
            }
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "移除控制器失败: ${e.message}", e)
        }
    }
    
    /**
     * 控制器是否存在
     * @param deviceId 设备ID
     * @return 是否存在
     */
    fun hasController(deviceId: String): Boolean {
        return cacheManager.getController(deviceId, DlnaController::class.java) != null
    }
    
    /**
     * 获取所有控制器ID - 此功能无法通过UPnPCacheManager直接实现，保留为空列表
     * @return 控制器ID列表
     */
    fun getAllControllerIds(): List<String> {
        // UPnPCacheManager无法提供此功能，返回空列表
        EnhancedThreadManager.w(TAG, "getAllControllerIds功能在统一缓存后不再可用")
        return emptyList()
    }
    
    /**
     * 清除所有控制器
     */
    fun clearAllControllers() {
        try {
            EnhancedThreadManager.d(TAG, "清除所有控制器")
            
            // 使用UPnPCacheManager清除控制器缓存
            cacheManager.clearCache(UPnPCacheManager.CacheType.CONTROLLER)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "清除所有控制器失败: ${e.message}", e)
        }
    }
    
    /**
     * 安全地停止控制器
     */
    private fun stopControllerSafely(controller: DlnaController?) {
        controller ?: return
        
        EnhancedThreadManager.executeTask {
            try {
                controller.stopSync()
                controller.release()
            } catch (e: Exception) {
                // 忽略异常
                EnhancedThreadManager.d(TAG, "安全停止控制器时忽略异常: ${e.message}")
            }
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            clearAllControllers()
            errorListener = null
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "释放资源失败: ${e.message}", e)
        }
    }
} 