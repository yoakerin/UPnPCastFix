package com.yinnho.upnpcast.controller

import com.yinnho.upnpcast.interfaces.MediaController
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.utils.DeviceUtils

/**
 * 媒体控制器工厂类
 * 负责创建不同类型的MediaController实现
 */
object MediaControllerFactory {
    
    /**
     * 创建媒体控制器
     * @param device 远程设备
     * @return 媒体控制器
     */
    fun create(device: RemoteDevice): MediaController {
        // 使用DlnaControllerFactory创建底层控制器
        val dlnaController = DlnaControllerFactory.getController(device)
        
        // 创建并返回媒体控制器
        return MediaControllerImpl(dlnaController)
    }
    
    /**
     * 从RemoteDevice创建MediaController
     * 兼容旧API
     */
    fun createController(device: RemoteDevice): MediaController {
        val dlnaController = DlnaControllerFactory.getController(device)
        return MediaControllerImpl(dlnaController)
    }
    
    /**
     * 从现有的DlnaController创建MediaController
     * 兼容旧API
     */
    fun createController(controller: DlnaController): MediaController {
        return MediaControllerImpl(controller)
    }
    
    /**
     * 为不同厂商设备创建专用MediaController
     * 根据设备制造商选择最适合的控制器实现
     */
    fun createControllerForDevice(device: RemoteDevice): MediaController {
        return when {
            DeviceUtils.isSamsungDevice(device) -> {
                // 创建三星专用控制器
                createController(device)
            }
            DeviceUtils.isLGDevice(device) -> {
                // 创建LG专用控制器
                createController(device)
            }
            DeviceUtils.isXiaomiDevice(device) -> {
                // 创建小米专用控制器
                createController(device)
            }
            else -> {
                // 创建通用控制器
                createController(device)
            }
        }
    }
} 