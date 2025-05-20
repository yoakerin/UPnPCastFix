package com.yinnho.upnpcast.service.transport

import android.util.Log
import com.yinnho.upnpcast.controller.soap.SoapTransportExecutor
import com.yinnho.upnpcast.interfaces.DLNAAction
import com.yinnho.upnpcast.interfaces.PositionInfo
import com.yinnho.upnpcast.interfaces.TransportInfo
import com.yinnho.upnpcast.model.DLNAActionImpl
import com.yinnho.upnpcast.model.DLNAUrl

/**
 * 动作执行处理器
 * 精简版：使用SoapTransportExecutor实现SOAP操作，简化异常处理，统一执行流程
 */
class ActionExecutionHandler(
    private val positionManager: PositionInfoManager,
    private val stateManager: TransportStateManager,
    private val controlURL: DLNAUrl,
    private val serviceType: String
) {
    private val soapExecutor = SoapTransportExecutor(controlURL, serviceType)
    private val TAG = "ActionExecutionHandler"
    
    /**
     * 执行指定的动作
     */
    fun execute(action: DLNAAction): Map<String, String> {
        try {
            // 提取输入参数
            val inputs = (action as? DLNAActionImpl)?.getInputMap() ?: emptyMap()
            val instanceId = inputs["InstanceID"] ?: "0"
            
            return when (action.name) {
                "Play" -> {
                    val speed = inputs["Speed"] ?: "1"
                    executeAction("Play", instanceId, mapOf("Speed" to speed)) { 
                        updateState(TransportStateManager.STATE_PLAYING) 
                    }
                }
                "Pause" -> {
                    executeAction("Pause", instanceId) { 
                        updateState(TransportStateManager.STATE_PAUSED) 
                    }
                }
                "Stop" -> {
                    executeAction("Stop", instanceId) { 
                        updateState(TransportStateManager.STATE_STOPPED)
                        positionManager.reset()
                    }
                }
                "Seek" -> {
                    val target = inputs["Target"] ?: "00:00:00"
                    executeAction("Seek", instanceId, mapOf("Unit" to "REL_TIME", "Target" to target)) { 
                        positionManager.updatePosition(target) 
                    }
                }
                "SetAVTransportURI" -> {
                    val uri = inputs["CurrentURI"] ?: ""
                    val metadata = inputs["CurrentURIMetaData"] ?: ""
                    executeAction("SetAVTransportURI", instanceId, 
                        mapOf("CurrentURI" to uri, "CurrentURIMetaData" to metadata)) { 
                        stateManager.setCurrentInstanceId(instanceId.toIntOrNull() ?: 0)
                        positionManager.updateMediaInfo(metadata, uri)
                    }
                }
                "GetPositionInfo" -> getPositionInfoResult(instanceId)
                "GetTransportInfo" -> getTransportInfoResult(instanceId)
                else -> mapOf("Error" to "未知动作: ${action.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行${action.name}失败", e)
            return mapOf("Error" to "执行失败: ${e.message}")
        }
    }
    
    /**
     * 通用方法：执行动作并处理结果
     * 简化的执行方法，返回统一的成功响应格式
     */
    private fun executeAction(
        actionName: String,
        instanceId: String,
        args: Map<String, String> = emptyMap(),
        onSuccess: () -> Unit = {}
    ): Map<String, String> {
        return try {
            soapExecutor.executeSoapAction(actionName, instanceId, args)
            onSuccess()
            mapOf("Result" to "OK")
        } catch (e: Exception) {
            Log.e(TAG, "执行${actionName}失败: ${e.message}", e)
            mapOf("Error" to "执行失败: ${e.message}")
        }
    }
    
    /**
     * 更新播放状态
     */
    private fun updateState(state: String) {
        stateManager.updateTransportState(state)
    }
    
    /**
     * 获取位置信息结果
     */
    private fun getPositionInfoResult(instanceId: String): Map<String, String> {
        try {
            val response = soapExecutor.executeSoapAction("GetPositionInfo", instanceId)
            return positionManager.parsePositionInfo(response).toResultMap()
        } catch (e: Exception) {
            Log.e(TAG, "获取位置信息失败: ${e.message}", e)
            return positionManager.getDefaultPositionInfo().toResultMap()
        }
    }
    
    /**
     * 将PositionInfo转换为结果Map
     */
    private fun PositionInfo.toResultMap(): Map<String, String> = mapOf(
        "Track" to track.toString(),
        "TrackDuration" to trackDuration,
        "TrackMetaData" to trackMetaData,
        "TrackURI" to trackURI,
        "RelTime" to relTime,
        "AbsTime" to absTime,
        "RelCount" to relCount.toString(),
        "AbsCount" to absCount.toString()
    )
    
    /**
     * 获取传输状态结果
     */
    private fun getTransportInfoResult(instanceId: String): Map<String, String> {
        try {
            val response = soapExecutor.executeSoapAction("GetTransportInfo", instanceId)
            return stateManager.parseTransportInfo(response).toResultMap()
        } catch (e: Exception) {
            Log.e(TAG, "获取传输状态失败: ${e.message}", e)
            return stateManager.getDefaultTransportInfo().toResultMap()
        }
    }
    
    /**
     * 将TransportInfo转换为结果Map
     */
    private fun TransportInfo.toResultMap(): Map<String, String> = mapOf(
        "CurrentTransportState" to currentTransportState,
        "CurrentTransportStatus" to currentTransportStatus,
        "CurrentSpeed" to currentSpeed
    )
    
    /**
     * 释放资源
     */
    fun release() {
        soapExecutor.release()
    }
} 