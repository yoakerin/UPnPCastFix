package com.yinnho.upnpcast.controller.device

import com.yinnho.upnpcast.model.DLNAUrl
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.service.AVTransportServiceImpl
import com.yinnho.upnpcast.service.RenderingControlServiceImpl
import com.yinnho.upnpcast.utils.DeviceUtils
import com.yinnho.upnpcast.core.EnhancedThreadManager

/**
 * 设备信息管理器
 * 负责设备信息获取和服务发现
 */
class DeviceInfoManager(
    private val device: RemoteDevice
) {
    companion object {
        private const val TAG = "DeviceInfoManager"
    }

    // 设备信息属性
    val friendlyName: String get() = DeviceUtils.getFriendlyName(device)
    val manufacturer: String get() = DeviceUtils.getManufacturer(device)
    val modelName: String get() = DeviceUtils.getModelName(device)
    val deviceId: String get() = device.identity.udn
    
    // 设备类型标识
    val isXiaomiDevice: Boolean get() = DeviceUtils.isXiaomiDevice(device)
    val isSamsungDevice: Boolean get() = DeviceUtils.isSamsungDevice(device)
    val isLGDevice: Boolean get() = DeviceUtils.isLGDevice(device)
    
    // 服务缓存
    private var _avTransportService: AVTransportServiceImpl? = null
    private var _renderingControlService: RenderingControlServiceImpl? = null
    
    // 服务属性
    val avTransportService: AVTransportServiceImpl?
        get() = _avTransportService
        
    val renderingControlService: RenderingControlServiceImpl?
        get() = _renderingControlService
    
    /**
     * 初始化设备信息管理器
     * 发现并创建设备服务
     */
    init {
        initializeServices()
    }
    
    /**
     * 初始化服务
     */
    private fun initializeServices() {
        try {
            EnhancedThreadManager.d(TAG, "初始化设备服务, 设备: $friendlyName")
            
            // 查找AVTransport服务
            val avTransportServiceInfo = device.services.find { 
                it.serviceType.contains("AVTransport", ignoreCase = true) 
            }
            
            if (avTransportServiceInfo != null) {
                EnhancedThreadManager.d(TAG, "找到AVTransport服务: ${avTransportServiceInfo.serviceType}")
                
                // 创建AVTransport服务
                _avTransportService = AVTransportServiceImpl(
                    controlURL = DLNAUrl.fromJavaUrl(avTransportServiceInfo.controlURL),
                    eventSubURL = DLNAUrl.fromJavaUrl(avTransportServiceInfo.eventSubURL),
                    SCPDURL = DLNAUrl.fromJavaUrl(avTransportServiceInfo.descriptorURL),
                    descriptorURL = DLNAUrl.fromJavaUrl(device.identity.descriptorURL)
                )
            } else {
                EnhancedThreadManager.w(TAG, "设备不支持AVTransport服务")
            }
            
            // 查找RenderingControl服务
            val renderingControlServiceInfo = device.services.find { 
                it.serviceType.contains("RenderingControl", ignoreCase = true) 
            }
            
            if (renderingControlServiceInfo != null) {
                EnhancedThreadManager.d(TAG, "找到RenderingControl服务: ${renderingControlServiceInfo.serviceType}")
                
                // 创建RenderingControl服务
                _renderingControlService = RenderingControlServiceImpl(
                    controlURL = DLNAUrl.fromJavaUrl(renderingControlServiceInfo.controlURL),
                    eventSubURL = DLNAUrl.fromJavaUrl(renderingControlServiceInfo.eventSubURL),
                    SCPDURL = DLNAUrl.fromJavaUrl(renderingControlServiceInfo.descriptorURL)
                )
            } else {
                EnhancedThreadManager.w(TAG, "设备不支持RenderingControl服务")
            }
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "初始化设备服务失败", e)
        }
    }
    
    /**
     * 获取控制URL
     */
    fun getControlURL(): String? {
        try {
            val avTransportService = device.services.firstOrNull { 
                it.serviceType.contains("AVTransport", ignoreCase = true) 
            }
            
            if (avTransportService == null) {
                EnhancedThreadManager.e(TAG, "设备没有AVTransport服务")
                return null
            }
            
            val controlURL = avTransportService.controlURL.toString()
            
            // 使用UrlUtils预处理URL
            return com.yinnho.upnpcast.utils.UrlUtils.preprocessControlURL(controlURL, friendlyName)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取控制URL失败: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 记录设备能力信息
     */
    fun logDeviceCapabilities() {
        try {
            EnhancedThreadManager.d(TAG, "==== 设备能力信息 ====")
            EnhancedThreadManager.d(TAG, "设备名称: $friendlyName")
            EnhancedThreadManager.d(TAG, "设备制造商: $manufacturer")
            EnhancedThreadManager.d(TAG, "设备型号: $modelName")
            EnhancedThreadManager.d(TAG, "设备ID: $deviceId")
            EnhancedThreadManager.d(TAG, "是否小米设备: $isXiaomiDevice")
            EnhancedThreadManager.d(TAG, "是否三星设备: $isSamsungDevice")
            EnhancedThreadManager.d(TAG, "是否LG设备: $isLGDevice")
            
            // 检查是否支持AVTransport服务
            val hasAVTransport = _avTransportService != null
            EnhancedThreadManager.d(TAG, "支持AVTransport服务: $hasAVTransport")
            
            // 检查是否支持RenderingControl服务
            val hasRenderingControl = _renderingControlService != null
            EnhancedThreadManager.d(TAG, "支持RenderingControl服务: $hasRenderingControl")
            
            // 查找并记录所有服务
            device.services.forEach { service ->
                EnhancedThreadManager.d(TAG, "服务: ${service.serviceType}")
                
                // 尝试获取服务详情
                try {
                    EnhancedThreadManager.d(TAG, "  服务ID: ${service.serviceId}")
                    EnhancedThreadManager.d(TAG, "  控制URL: ${service.controlURL}")
                    EnhancedThreadManager.d(TAG, "  事件URL: ${service.eventSubURL}")
                    EnhancedThreadManager.d(TAG, "  SCPD URL: ${service.descriptorURL}")
                } catch (e: Exception) {
                    EnhancedThreadManager.d(TAG, "  获取服务详情失败: ${e.message}")
                }
            }
            
            EnhancedThreadManager.d(TAG, "==== 设备能力信息结束 ====")
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "记录设备能力信息失败", e)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            EnhancedThreadManager.d(TAG, "释放设备信息管理器资源")
            
            // 释放服务资源
            _avTransportService?.release()
            _avTransportService = null
            
            _renderingControlService = null
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "释放资源失败: ${e.message}", e)
        }
    }
} 