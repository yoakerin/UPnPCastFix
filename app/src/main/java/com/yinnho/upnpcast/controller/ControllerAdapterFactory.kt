package com.yinnho.upnpcast.controller

import com.yinnho.upnpcast.model.RemoteDevice

/**
 * 控制器适配器工厂
 * 负责创建适合不同设备的控制器适配器
 */
object ControllerAdapterFactory {
    /**
     * 从RemoteDevice创建适配器
     */
    fun createAdapter(device: RemoteDevice): ControllerAdapter {
        // 使用DlnaControllerFactory创建底层控制器
        val controller = DlnaControllerFactory.getController(device)
        
        // 创建并返回适配器
        return ControllerAdapterImpl(controller)
    }
    
    /**
     * 从现有DlnaController创建适配器
     */
    fun createAdapter(controller: DlnaController): ControllerAdapter {
        return ControllerAdapterImpl(controller)
    }
} 