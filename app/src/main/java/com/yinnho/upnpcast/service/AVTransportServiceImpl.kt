package com.yinnho.upnpcast.service

import android.util.Log
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.core.lifecycle.ListenerManager
import com.yinnho.upnpcast.interfaces.Argument
import com.yinnho.upnpcast.interfaces.DLNAAction
import com.yinnho.upnpcast.interfaces.PositionInfo
import com.yinnho.upnpcast.interfaces.TransportInfo
import com.yinnho.upnpcast.model.DLNAActionFactory
import com.yinnho.upnpcast.model.DLNAActionImpl
import com.yinnho.upnpcast.model.DLNAUrl
import com.yinnho.upnpcast.model.ServiceType
import com.yinnho.upnpcast.service.transport.ActionExecutionHandler
import com.yinnho.upnpcast.service.transport.PositionInfoManager
import com.yinnho.upnpcast.service.transport.TransportStateManager

/**
 * AVTransport服务实现类
 * 精简版：使用ActionExecutionHandler简化实现及ListenerManager统一管理监听器
 */
class AVTransportServiceImpl(
    controlURL: DLNAUrl,
    eventSubURL: DLNAUrl,
    SCPDURL: DLNAUrl,
    descriptorURL: DLNAUrl
) : BaseDLNAServiceImpl(controlURL, eventSubURL, SCPDURL, descriptorURL), AVTransportService, PositionInfoManager.PositionUpdateListener, TransportStateManager.StateChangeListener {

    override val TAG = "AVTransportServiceImpl"
    override val SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"

    override val serviceType: String = ServiceType.AV_TRANSPORT.toString()
    override val serviceId: String = "AVTransport"

    // 通过组合方式分解职责
    private val positionManager = PositionInfoManager()
    private val stateManager = TransportStateManager()
    private val executionHandler = ActionExecutionHandler(positionManager, stateManager, controlURL, SERVICE_TYPE)
    
    // 使用ListenerManager统一管理监听器
    private val stateChangeListenerManager = ListenerManager<StateChangeListener>()
    private val positionUpdateListenerManager = ListenerManager<PositionUpdateListener>()
    
    // 缓存常用动作，避免重复创建
    private val actionCache = mutableMapOf<String, DLNAAction>()
    
    // 动作列表
    private val _actions: List<DLNAAction>
    override val actions: List<DLNAAction>
        get() = _actions
    
    init {
        // 初始化动作定义 - 内联TransportActionFactory的功能
        _actions = createActions()
        
        // 注册内部监听器
        positionManager.addPositionUpdateListener(this)
        stateManager.addStateChangeListener(this)
        
        // 预缓存常用动作
        cacheCommonActions()
    }
    
    /**
     * 预缓存常用动作，降低运行时创建动作的开销
     */
    private fun cacheCommonActions() {
        // 播放动作
        createActionAndCache("Play", listOf(
            "InstanceID" to Argument.Direction.IN,
            "Speed" to Argument.Direction.IN
        ))
        
        // 暂停动作
        createActionAndCache("Pause", listOf(
            "InstanceID" to Argument.Direction.IN
        ))
        
        // 停止动作
        createActionAndCache("Stop", listOf(
            "InstanceID" to Argument.Direction.IN
        ))
        
        // 跳转动作
        createActionAndCache("Seek", listOf(
            "InstanceID" to Argument.Direction.IN,
            "Unit" to Argument.Direction.IN,
            "Target" to Argument.Direction.IN
        ))
        
        // 设置媒体URI动作
        createActionAndCache("SetAVTransportURI", listOf(
            "InstanceID" to Argument.Direction.IN,
            "CurrentURI" to Argument.Direction.IN,
            "CurrentURIMetaData" to Argument.Direction.IN
        ))
    }
    
    /**
     * 创建并缓存动作
     */
    private fun createActionAndCache(
        actionName: String, 
        inArgs: List<Pair<String, Argument.Direction>>,
        outArgs: List<Pair<String, Argument.Direction>> = emptyList()
    ): DLNAAction {
        val action = DLNAActionFactory.createAction(
            actionName,
            this,
            inArgs,
            outArgs
        )
        actionCache[actionName] = action
        return action
    }
    
    /**
     * 获取缓存的动作或创建新动作
     */
    private fun getAction(
        actionName: String,
        inArgs: List<Pair<String, Argument.Direction>>,
        outArgs: List<Pair<String, Argument.Direction>> = emptyList()
    ): DLNAAction {
        // 从缓存获取动作，如果不存在则创建新的
        return actionCache[actionName] ?: DLNAActionFactory.createAction(
            actionName,
            this,
            inArgs,
            outArgs
        )
    }
    
    /**
     * 创建所有AVTransport服务的动作
     * 将原TransportActionFactory功能内联到服务类中
     */
    private fun createActions(): List<DLNAAction> {
        return listOf(
            // Play动作
            DLNAActionFactory.createAction(
                "Play", 
                this, 
                listOf(
                    "InstanceID" to Argument.Direction.IN,
                    "Speed" to Argument.Direction.IN
                )
            ),
            
            // Pause动作
            DLNAActionFactory.createAction(
                "Pause", 
                this, 
                listOf(
                    "InstanceID" to Argument.Direction.IN
                )
            ),
            
            // Stop动作
            DLNAActionFactory.createAction(
                "Stop", 
                this, 
                listOf(
                    "InstanceID" to Argument.Direction.IN
                )
            ),
            
            // Seek动作
            DLNAActionFactory.createAction(
                "Seek", 
                this, 
                listOf(
                    "InstanceID" to Argument.Direction.IN,
                    "Unit" to Argument.Direction.IN,
                    "Target" to Argument.Direction.IN
                )
            ),
            
            // SetAVTransportURI动作
            DLNAActionFactory.createAction(
                "SetAVTransportURI", 
                this, 
                listOf(
                    "InstanceID" to Argument.Direction.IN,
                    "CurrentURI" to Argument.Direction.IN,
                    "CurrentURIMetaData" to Argument.Direction.IN
                )
            ),
            
            // GetPositionInfo动作
            DLNAActionFactory.createAction(
                "GetPositionInfo", 
                this, 
                listOf(
                    "InstanceID" to Argument.Direction.IN
                ),
                listOf(
                    "Track" to Argument.Direction.OUT,
                    "TrackDuration" to Argument.Direction.OUT,
                    "TrackMetaData" to Argument.Direction.OUT,
                    "TrackURI" to Argument.Direction.OUT,
                    "RelTime" to Argument.Direction.OUT,
                    "AbsTime" to Argument.Direction.OUT,
                    "RelCount" to Argument.Direction.OUT,
                    "AbsCount" to Argument.Direction.OUT
                )
            ),
            
            // GetTransportInfo动作
            DLNAActionFactory.createAction(
                "GetTransportInfo", 
                this, 
                listOf(
                    "InstanceID" to Argument.Direction.IN
                ),
                listOf(
                    "CurrentTransportState" to Argument.Direction.OUT,
                    "CurrentTransportStatus" to Argument.Direction.OUT,
                    "CurrentSpeed" to Argument.Direction.OUT
                )
            )
        )
    }

    /**
     * 执行动作
     */
    override fun execute(action: DLNAAction): Map<String, String> {
        try {
            // 使用执行处理器处理动作
            return executionHandler.execute(action)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "执行动作失败: ${e.message}", e)
            return mapOf("Error" to "执行失败: ${e.message}")
        }
    }
    
    /**
     * 通用执行方法
     */
    private fun executeAction(actionName: String, params: Map<String, String>): Map<String, String> {
        try {
            // 获取缓存的动作的副本或创建新动作
            val action = when (actionName) {
                "Play" -> DLNAActionFactory.createAction(
                    "Play", this,
                    listOf(
                        "InstanceID" to Argument.Direction.IN,
                        "Speed" to Argument.Direction.IN
                    )
                )
                "Pause" -> DLNAActionFactory.createAction(
                    "Pause", this,
                    listOf("InstanceID" to Argument.Direction.IN)
                )
                "Stop" -> DLNAActionFactory.createAction(
                    "Stop", this,
                    listOf("InstanceID" to Argument.Direction.IN)
                )
                "Seek" -> DLNAActionFactory.createAction(
                    "Seek", this,
                    listOf(
                        "InstanceID" to Argument.Direction.IN,
                        "Unit" to Argument.Direction.IN,
                        "Target" to Argument.Direction.IN
                    )
                )
                "SetAVTransportURI" -> DLNAActionFactory.createAction(
                    "SetAVTransportURI", this,
                    listOf(
                        "InstanceID" to Argument.Direction.IN,
                        "CurrentURI" to Argument.Direction.IN,
                        "CurrentURIMetaData" to Argument.Direction.IN
                    )
                )
                "GetPositionInfo" -> DLNAActionFactory.createAction(
                    "GetPositionInfo", this,
                    listOf("InstanceID" to Argument.Direction.IN),
                    listOf(
                        "Track" to Argument.Direction.OUT,
                        "TrackDuration" to Argument.Direction.OUT,
                        "TrackMetaData" to Argument.Direction.OUT,
                        "TrackURI" to Argument.Direction.OUT,
                        "RelTime" to Argument.Direction.OUT,
                        "AbsTime" to Argument.Direction.OUT,
                        "RelCount" to Argument.Direction.OUT,
                        "AbsCount" to Argument.Direction.OUT
                    )
                )
                "GetTransportInfo" -> DLNAActionFactory.createAction(
                    "GetTransportInfo", this,
                    listOf("InstanceID" to Argument.Direction.IN),
                    listOf(
                        "CurrentTransportState" to Argument.Direction.OUT,
                        "CurrentTransportStatus" to Argument.Direction.OUT,
                        "CurrentSpeed" to Argument.Direction.OUT
                    )
                )
                else -> return mapOf("Error" to "未知动作: $actionName")
            }
            
            // 设置输入参数
            params.forEach { (key, value) -> action.setInput(key, value) }
            
            // 执行动作
            return execute(action)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "执行${actionName}失败: ${e.message}", e)
            return mapOf("Error" to "执行失败: ${e.message}")
        }
    }

    /**
     * 播放媒体
     */
    override fun play(instanceId: String, speed: String) {
        val result = executeAction("Play", mapOf(
            "InstanceID" to instanceId,
            "Speed" to speed
        ))
        
        if (result.containsKey("Error")) {
            EnhancedThreadManager.e(TAG, "播放媒体失败: ${result["Error"]}")
        }
    }

    /**
     * 暂停播放
     */
    override fun pause(instanceId: String) {
        val result = executeAction("Pause", mapOf(
            "InstanceID" to instanceId
        ))
        
        if (result.containsKey("Error")) {
            EnhancedThreadManager.e(TAG, "暂停播放失败: ${result["Error"]}")
        }
    }

    /**
     * 停止播放
     */
    override fun stop(instanceId: String) {
        val result = executeAction("Stop", mapOf(
            "InstanceID" to instanceId
        ))
        
        if (result.containsKey("Error")) {
            EnhancedThreadManager.e(TAG, "停止播放失败: ${result["Error"]}")
        }
    }

    /**
     * 跳转到指定位置
     */
    override fun seek(instanceId: String, target: String) {
        val result = executeAction("Seek", mapOf(
            "InstanceID" to instanceId,
            "Unit" to "REL_TIME",
            "Target" to target
        ))
        
        if (result.containsKey("Error")) {
            EnhancedThreadManager.e(TAG, "跳转失败: ${result["Error"]}")
        }
    }

    /**
     * 设置媒体URI
     */
    override fun setAVTransportURI(instanceId: String, uri: String, metadata: String) {
        val result = executeAction("SetAVTransportURI", mapOf(
            "InstanceID" to instanceId,
            "CurrentURI" to uri,
            "CurrentURIMetaData" to metadata
        ))
        
        if (result.containsKey("Error")) {
            EnhancedThreadManager.e(TAG, "设置媒体URI失败: ${result["Error"]}")
        }
    }

    /**
     * 获取位置信息
     */
    override fun getPositionInfo(instanceId: String): PositionInfo {
        val result = executeAction("GetPositionInfo", mapOf(
            "InstanceID" to instanceId
        ))
        
        if (result.containsKey("Error")) {
            EnhancedThreadManager.e(TAG, "获取位置信息失败: ${result["Error"]}")
            return positionManager.getDefaultPositionInfo()
        }
        
        return positionManager.getDefaultPositionInfo() // 实际应从result构建
    }

    /**
     * 获取传输信息
     */
    override fun getTransportInfo(instanceId: String): TransportInfo {
        val result = executeAction("GetTransportInfo", mapOf(
            "InstanceID" to instanceId
        ))
        
        if (result.containsKey("Error")) {
            EnhancedThreadManager.e(TAG, "获取传输信息失败: ${result["Error"]}")
            return stateManager.getDefaultTransportInfo()
        }
        
        return stateManager.getDefaultTransportInfo() // 实际应从result构建
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
     * 添加位置更新监听器
     */
    fun addPositionUpdateListener(listener: PositionUpdateListener) {
        positionUpdateListenerManager.addListener(listener)
    }
    
    /**
     * 移除位置更新监听器
     */
    fun removePositionUpdateListener(listener: PositionUpdateListener) {
        positionUpdateListenerManager.removeListener(listener)
    }
    
    /**
     * 状态变化回调处理
     */
    override fun onStateChanged(oldState: String, newState: String) {
        stateChangeListenerManager.notifyListeners { listener ->
            listener.onStateChanged(oldState, newState)
        }
    }
    
    /**
     * 位置更新回调处理
     */
    override fun onPositionUpdate(position: String, duration: String) {
        positionUpdateListenerManager.notifyListeners { listener ->
            listener.onPositionUpdate(position, duration)
        }
    }
    
    /**
     * 状态变化监听器接口
     */
    interface StateChangeListener {
        fun onStateChanged(oldState: String, newState: String)
    }
    
    /**
     * 位置更新监听器接口
     */
    interface PositionUpdateListener {
        fun onPositionUpdate(position: String, duration: String)
    }
    
    /**
     * 释放资源
     */
    fun release() {
        positionManager.release()
        executionHandler.release()
        stateChangeListenerManager.clear()
        positionUpdateListenerManager.clear()
        actionCache.clear()
    }
}