package com.yinnho.upnpcast.service.transport

import com.yinnho.upnpcast.core.lifecycle.ListenerManager
import com.yinnho.upnpcast.interfaces.PositionInfo
import com.yinnho.upnpcast.model.PositionInfoImpl
import com.yinnho.upnpcast.network.SoapHelper
import java.util.Timer
import java.util.TimerTask

/**
 * 位置信息管理器
 * 精简版：使用ListenerManager统一管理监听器
 */
class PositionInfoManager {
    // 当前位置和时长信息
    private var currentPosition: String = "00:00:00"
    private var currentDuration: String = "00:00:00"
    private var currentMetadata: String = ""
    private var currentURI: String = ""
    
    // 自动更新位置的定时器
    private var updateTimer: Timer? = null
    
    // 使用ListenerManager统一管理监听器
    private val positionUpdateListenerManager = ListenerManager<PositionUpdateListener>()
    
    /**
     * 重置位置信息
     */
    fun reset() {
        currentPosition = "00:00:00"
    }
    
    /**
     * 更新位置信息
     */
    fun updatePosition(position: String) {
        this.currentPosition = position
        notifyPositionUpdate()
    }
    
    /**
     * 更新时长信息
     */
    fun updateDuration(duration: String) {
        this.currentDuration = duration
    }
    
    /**
     * 更新媒体信息
     */
    fun updateMediaInfo(metadata: String, uri: String) {
        this.currentMetadata = metadata
        this.currentURI = uri
    }
    
    /**
     * 解析SOAP响应中的位置信息
     */
    fun parsePositionInfo(soapResponse: String): PositionInfo {
        try {
            // 从SOAP响应提取相关参数
            val track = SoapHelper.parseElementFromSoapResponse(soapResponse, "Track")?.toIntOrNull()?.toUInt() ?: 1u
            val trackDuration = SoapHelper.parseElementFromSoapResponse(soapResponse, "TrackDuration") ?: currentDuration
            val trackMetaData = SoapHelper.parseElementFromSoapResponse(soapResponse, "TrackMetaData") ?: currentMetadata
            val trackURI = SoapHelper.parseElementFromSoapResponse(soapResponse, "TrackURI") ?: currentURI
            val relTime = SoapHelper.parseElementFromSoapResponse(soapResponse, "RelTime") ?: currentPosition
            
            // 更新当前管理的值
            if (trackDuration != "00:00:00" && trackDuration != "NOT_IMPLEMENTED") {
                currentDuration = trackDuration
            }
            
            if (relTime != "00:00:00" && relTime != "NOT_IMPLEMENTED") {
                currentPosition = relTime
                notifyPositionUpdate()
            }
            
            return PositionInfoImpl(
                track = track,
                trackDuration = trackDuration,
                trackMetaData = trackMetaData,
                trackURI = trackURI,
                relTime = relTime,
                absTime = SoapHelper.parseElementFromSoapResponse(soapResponse, "AbsTime") ?: "NOT_IMPLEMENTED",
                relCount = SoapHelper.parseElementFromSoapResponse(soapResponse, "RelCount")?.toIntOrNull() ?: 0,
                absCount = SoapHelper.parseElementFromSoapResponse(soapResponse, "AbsCount")?.toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            return getDefaultPositionInfo()
        }
    }
    
    /**
     * 获取默认位置信息
     */
    fun getDefaultPositionInfo(): PositionInfo {
        return PositionInfoImpl(
            track = 1u,
            trackDuration = currentDuration,
            trackMetaData = currentMetadata,
            trackURI = currentURI,
            relTime = currentPosition,
            absTime = "NOT_IMPLEMENTED",
            relCount = 0,
            absCount = 0
        )
    }
    
    /**
     * 添加位置更新监听器
     */
    fun addPositionUpdateListener(listener: PositionUpdateListener) {
        positionUpdateListenerManager.addListener(listener)
        startPositionUpdateTimer()
    }
    
    /**
     * 移除位置更新监听器
     */
    fun removePositionUpdateListener(listener: PositionUpdateListener) {
        positionUpdateListenerManager.removeListener(listener)
        if (positionUpdateListenerManager.isEmpty()) {
            stopPositionUpdateTimer()
        }
    }
    
    /**
     * 开始位置更新定时器
     */
    private fun startPositionUpdateTimer() {
        if (updateTimer == null) {
            updateTimer = Timer()
            updateTimer?.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    notifyPositionUpdate()
                }
            }, 0, 1000)
        }
    }
    
    /**
     * 停止位置更新定时器
     */
    private fun stopPositionUpdateTimer() {
        updateTimer?.cancel()
        updateTimer = null
    }
    
    /**
     * 通知所有监听器位置已更新
     */
    private fun notifyPositionUpdate() {
        positionUpdateListenerManager.notifyListeners { listener ->
            listener.onPositionUpdate(currentPosition, currentDuration)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopPositionUpdateTimer()
        positionUpdateListenerManager.clear()
    }
    
    /**
     * 位置更新监听器接口
     */
    interface PositionUpdateListener {
        /**
         * 当位置更新时回调
         */
        fun onPositionUpdate(position: String, duration: String)
    }
} 