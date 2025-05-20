package com.yinnho.upnpcast.controller.soap

import android.util.Log
import com.yinnho.upnpcast.api.PlaybackState
import com.yinnho.upnpcast.controller.device.DeviceInfoManager
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.model.DLNAUrl
import com.yinnho.upnpcast.network.SoapHelper
import com.yinnho.upnpcast.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SOAP传输执行器
 * 整合了DirectSoapCommunicator和TransportActionExecutor的功能
 * 负责执行DLNA设备的传输相关SOAP操作
 */
class SoapTransportExecutor {
    companion object {
        private const val TAG = "SoapTransportExecutor"
        private const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val DEFAULT_INSTANCE_ID = "0"
    }
    
    // 设备信息管理器，用于获取控制URL
    private val deviceInfoManager: DeviceInfoManager?
    
    // 固定的控制URL，如果没有设备信息管理器，则使用此URL
    private val fixedControlURL: DLNAUrl?
    
    // 服务类型，默认为AVTransport
    private val serviceType: String
    
    // 播放状态监听器
    private var playbackStateListener: ((PlaybackState) -> Unit)? = null
    
    /**
     * 通过设备信息管理器创建执行器
     */
    constructor(
        deviceInfoManager: DeviceInfoManager,
        serviceType: String = AV_TRANSPORT_SERVICE
    ) {
        this.deviceInfoManager = deviceInfoManager
        this.fixedControlURL = null
        this.serviceType = serviceType
    }
    
    /**
     * 通过固定控制URL创建执行器
     */
    constructor(
        controlURL: DLNAUrl,
        serviceType: String = AV_TRANSPORT_SERVICE
    ) {
        this.deviceInfoManager = null
        this.fixedControlURL = controlURL
        this.serviceType = serviceType
    }
    
    /**
     * 设置播放状态监听器
     */
    fun setPlaybackStateListener(listener: ((PlaybackState) -> Unit)?) {
        this.playbackStateListener = listener
    }
    
    /**
     * 获取控制URL
     * @return 控制URL字符串，如果无法获取则返回null
     */
    private suspend fun getControlURL(): String? = withContext(Dispatchers.IO) {
        when {
            deviceInfoManager != null -> deviceInfoManager.getControlURL()
            fixedControlURL != null -> fixedControlURL.toString()
            else -> null
        }
    }
    
    /**
     * 执行SOAP动作(同步版本)
     */
    fun executeSoapAction(
        actionName: String,
        instanceId: String = DEFAULT_INSTANCE_ID,
        arguments: Map<String, String> = emptyMap()
    ): String {
        val controlUrl = fixedControlURL?.toString() ?: return ""
        
        // 创建SOAP请求体
        val soapBody = SoapHelper.createDlnaControlSoapBody(
            serviceType = serviceType,
            action = actionName,
            instanceId = instanceId,
            arguments = arguments
        )
        
        // 发送SOAP请求
        val response = SoapHelper.sendSoapRequest(
            controlUrl = controlUrl,
            soapAction = "$serviceType#$actionName",
            soapBody = soapBody
        )
        
        Log.d(TAG, "执行$actionName, 响应: ${response.take(100)}${if (response.length > 100) "..." else ""}")
        return response
    }
    
    /**
     * 执行SOAP动作(协程版本)
     * 通用方法，处理所有SOAP请求
     */
    private suspend fun executeActionAsync(
        actionName: String,
        arguments: Map<String, String> = emptyMap(),
        onSuccess: (String) -> Unit = { _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val controlURL = getControlURL() ?: return@withContext false
            
            // 使用SoapHelper创建并发送请求
            val response = SoapHelper.createAndSendSoapRequestWithResponse(
                controlUrl = controlURL,
                serviceType = serviceType,
                actionName = actionName,
                instanceId = DEFAULT_INSTANCE_ID,
                arguments = arguments
            )
            
            if (response != null) {
                onSuccess(response)
                return@withContext true
            }
            
            return@withContext false
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "SOAP请求 $actionName 失败: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * 设置媒体URI
     */
    suspend fun setAVTransportURI(mediaUrl: String, metadata: String): Boolean {
        return executeActionAsync(
            actionName = "SetAVTransportURI",
            arguments = mapOf(
                "CurrentURI" to mediaUrl,
                "CurrentURIMetaData" to metadata
            )
        )
    }
    
    /**
     * 执行播放控制动作并更新状态
     */
    private suspend fun executeTransportAction(
        actionName: String, 
        state: PlaybackState,
        arguments: Map<String, String> = emptyMap()
    ): Boolean {
        return executeActionAsync(
            actionName = actionName,
            arguments = arguments
        ) { _ ->
            playbackStateListener?.invoke(state)
        }
    }
    
    /**
     * 播放媒体
     */
    suspend fun play(speed: String = "1"): Boolean {
        return executeTransportAction(
            actionName = "Play",
            state = PlaybackState.PLAYING,
            arguments = mapOf("Speed" to speed)
        )
    }
    
    /**
     * 暂停播放
     */
    suspend fun pause(): Boolean {
        return executeTransportAction(
            actionName = "Pause",
            state = PlaybackState.PAUSED
        )
    }
    
    /**
     * 停止播放
     */
    suspend fun stop(): Boolean {
        return executeTransportAction(
            actionName = "Stop",
            state = PlaybackState.STOPPED
        )
    }
    
    /**
     * 跳转到指定位置
     * @param positionMs 位置(毫秒)
     */
    suspend fun seek(positionMs: Long): Boolean {
        try {
            if (positionMs <= 0) {
                EnhancedThreadManager.d(TAG, "播放位置为0或负数，不设置位置")
                return true
            }
            
            // 转换位置为hh:mm:ss格式
            val timeString = TimeUtils.millisecondsToTimeString(positionMs)
            
            EnhancedThreadManager.d(TAG, "SOAP定位到: $timeString")
            
            return executeActionAsync(
                actionName = "Seek",
                arguments = mapOf(
                    "Unit" to "REL_TIME",
                    "Target" to timeString
                )
            )
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "定位失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 获取信息结果的辅助方法
     */
    private suspend fun getInfoResult(
        actionName: String,
        responseParsing: (String) -> Map<String, String>
    ): Map<String, String>? = withContext(Dispatchers.IO) {
        try {
            val controlURL = getControlURL() ?: return@withContext null
            
            val response = SoapHelper.createAndSendSoapRequestWithResponse(
                controlUrl = controlURL,
                serviceType = serviceType,
                actionName = actionName,
                instanceId = DEFAULT_INSTANCE_ID
            )
            
            if (response != null) {
                return@withContext responseParsing(response)
            }
            
            return@withContext null
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取${actionName}信息失败: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * 获取位置信息
     * @return 包含位置信息的Map，如果获取失败则返回null
     */
    suspend fun getPositionInfo(): Map<String, String>? {
        return getInfoResult("GetPositionInfo") { response ->
            mapOf(
                "TrackDuration" to (SoapHelper.parseElementFromSoapResponse(response, "TrackDuration") ?: "00:00:00"),
                "RelTime" to (SoapHelper.parseElementFromSoapResponse(response, "RelTime") ?: "00:00:00")
            )
        }
    }
    
    /**
     * 获取传输状态信息
     * @return 包含传输状态信息的Map，如果获取失败则返回null
     */
    suspend fun getTransportInfo(): Map<String, String>? {
        return getInfoResult("GetTransportInfo") { response ->
            val currentTransportState = SoapHelper.parseElementFromSoapResponse(response, "CurrentTransportState") ?: "STOPPED"
            val currentTransportStatus = SoapHelper.parseElementFromSoapResponse(response, "CurrentTransportStatus") ?: "OK"
            val currentSpeed = SoapHelper.parseElementFromSoapResponse(response, "CurrentSpeed") ?: "1"
            
            // 更新播放状态
            when (currentTransportState) {
                "PLAYING" -> playbackStateListener?.invoke(PlaybackState.PLAYING)
                "PAUSED_PLAYBACK" -> playbackStateListener?.invoke(PlaybackState.PAUSED)
                "STOPPED" -> playbackStateListener?.invoke(PlaybackState.STOPPED)
            }
            
            mapOf(
                "CurrentTransportState" to currentTransportState,
                "CurrentTransportStatus" to currentTransportStatus,
                "CurrentSpeed" to currentSpeed
            )
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        playbackStateListener = null
        EnhancedThreadManager.d(TAG, "释放SoapTransportExecutor资源")
    }
} 