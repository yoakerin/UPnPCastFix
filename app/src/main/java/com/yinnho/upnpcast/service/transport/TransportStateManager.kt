package com.yinnho.upnpcast.service.transport

import com.yinnho.upnpcast.core.lifecycle.ListenerManager
import com.yinnho.upnpcast.interfaces.TransportInfo
import com.yinnho.upnpcast.model.TransportInfoImpl
import com.yinnho.upnpcast.network.SoapHelper

/**
 * 传输状态管理器
 * 精简版：使用ListenerManager统一管理监听器
 */
class TransportStateManager {
    companion object {
        // 定义传输状态常量
        const val STATE_PLAYING = "PLAYING"
        const val STATE_PAUSED = "PAUSED_PLAYBACK"
        const val STATE_STOPPED = "STOPPED"
        const val STATE_TRANSITIONING = "TRANSITIONING"
        const val STATE_NO_MEDIA = "NO_MEDIA_PRESENT"
    }
    
    // 当前传输状态相关字段
    private var currentInstanceId: Int = 0
    private var currentTransportState: String = STATE_STOPPED
    private var currentTransportStatus: String = "OK"
    private var currentSpeed: String = "1"
    
    // 使用ListenerManager统一管理监听器
    private val stateChangeListenerManager = ListenerManager<StateChangeListener>()
    
    /**
     * 设置当前实例ID
     */
    fun setCurrentInstanceId(instanceId: Int) {
        this.currentInstanceId = instanceId
    }
    
    /**
     * 获取当前实例ID
     */
    fun getCurrentInstanceId(): Int = currentInstanceId
    
    /**
     * 更新传输状态
     */
    fun updateTransportState(state: String) {
        if (currentTransportState != state) {
            val oldState = currentTransportState
            currentTransportState = state
            notifyStateChanged(oldState, state)
        }
    }
    
    /**
     * 更新传输状态信息
     */
    fun updateTransportInfo(status: String, speed: String) {
        this.currentTransportStatus = status
        this.currentSpeed = speed
    }
    
    /**
     * 解析SOAP响应中的传输状态信息
     */
    fun parseTransportInfo(soapResponse: String): TransportInfo {
        try {
            // 从SOAP响应提取相关参数
            val state = SoapHelper.parseElementFromSoapResponse(soapResponse, "CurrentTransportState") ?: currentTransportState
            val status = SoapHelper.parseElementFromSoapResponse(soapResponse, "CurrentTransportStatus") ?: currentTransportStatus
            val speed = SoapHelper.parseElementFromSoapResponse(soapResponse, "CurrentSpeed") ?: currentSpeed
            
            // 如果状态有变化，更新并通知
            if (state != currentTransportState) {
                val oldState = currentTransportState
                currentTransportState = state
                notifyStateChanged(oldState, state)
            }
            
            // 更新其他状态信息
            currentTransportStatus = status
            currentSpeed = speed
            
            return TransportInfoImpl(
                currentTransportState = state,
                currentTransportStatus = status,
                currentSpeed = speed
            )
        } catch (e: Exception) {
            return getDefaultTransportInfo()
        }
    }
    
    /**
     * 获取默认传输状态信息
     */
    fun getDefaultTransportInfo(): TransportInfo {
        return TransportInfoImpl(
            currentTransportState = currentTransportState,
            currentTransportStatus = currentTransportStatus,
            currentSpeed = currentSpeed
        )
    }
    
    /**
     * 添加状态变化监听器
     */
    fun addStateChangeListener(listener: StateChangeListener) {
        stateChangeListenerManager.addListener(listener)
    }
    
    /**
     * 移除状态变化监听器
     */
    fun removeStateChangeListener(listener: StateChangeListener) {
        stateChangeListenerManager.removeListener(listener)
    }
    
    /**
     * 通知所有监听器状态已变化
     */
    private fun notifyStateChanged(oldState: String, newState: String) {
        stateChangeListenerManager.notifyListeners { listener ->
            listener.onStateChanged(oldState, newState)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stateChangeListenerManager.clear()
    }
    
    /**
     * 状态变化监听器接口
     */
    interface StateChangeListener {
        /**
         * 当状态变化时回调
         */
        fun onStateChanged(oldState: String, newState: String)
    }
} 