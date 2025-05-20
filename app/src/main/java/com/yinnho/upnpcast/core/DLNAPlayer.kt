package com.yinnho.upnpcast.core

import android.content.Context
import android.content.Intent
import com.yinnho.upnpcast.registry.StandardDeviceRegistry
import com.yinnho.upnpcast.api.DLNAErrorType
import com.yinnho.upnpcast.api.DLNAException
import com.yinnho.upnpcast.interfaces.ControlPoint
import com.yinnho.upnpcast.interfaces.UpnpServiceConfiguration
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.wrapper.DlnaControllerWrapper
import com.yinnho.upnpcast.core.SSDPIntegrationAdapter
import com.yinnho.upnpcast.state.DeviceStateAdapter
import com.yinnho.upnpcast.state.DeviceStateManager
import com.yinnho.upnpcast.event.UPnPEventBus
import com.yinnho.upnpcast.event.PlaybackStartedEvent
import com.yinnho.upnpcast.event.PlaybackStoppedEvent
import com.yinnho.upnpcast.event.DeviceConnectedEvent
import com.yinnho.upnpcast.event.DeviceDisconnectedEvent
import com.yinnho.upnpcast.event.DeviceErrorEvent

/**
 * DLNA播放器控制类
 * 职责：通过ControlPoint接口管理设备发现和连接，并提供媒体播放控制
 */
class DLNAPlayer(private val context: Context) {
    private val TAG = "DLNAPlayer"
    
    // 使用委托属性替代lazy
    private val controlPoint: ControlPoint by lazy {
        DefaultUpnpServiceConfiguration.getInstance(context).let { config ->
            DefaultControlPoint(config)
        }
    }
    
    private var playerListener: PlayerListener? = null
    private var isPlaying = false
    private var useNewSSDPDiscovery = true
    private var ssdpAdapter: SSDPIntegrationAdapter? = null
    private var isSearching = false
    private var lastDeviceListHash = 0
    
    // 事件总线
    private val eventBus = UPnPEventBus.getInstance()
    
    // 设备状态适配器
    private val stateAdapter = DeviceStateAdapter.getInstance()
    
    // 是否使用事件总线
    private var useEventBus = true

    init {
        initializeDlnaController()
        
        // 初始化SSDP适配器
        initializeSSDPAdapter()
    }
    
    /**
     * 初始化DLNA控制器
     */
    private fun initializeDlnaController() {
        runCatching("DLNA播放器初始化", DLNAErrorType.UNKNOWN_ERROR) {
            EnhancedThreadManager.d(TAG, "初始化DLNA播放器")
            
            // 初始化DLNA控制器
            DlnaControllerWrapper.setAppContext(context)
            DlnaControllerWrapper.setDeviceListener(createDeviceListener())
            true
        }
    }
    
    /**
     * 创建设备监听器
     */
    private fun createDeviceListener() = object : DlnaControllerWrapper.DeviceListener {
        override fun onDeviceFound(device: RemoteDevice) {
            handleDeviceFound(device)
        }

        override fun onDeviceLost(device: RemoteDevice) {
            handleDeviceLost(device)
        }
    }
    
    /**
     * 处理设备发现
     */
    private fun handleDeviceFound(device: RemoteDevice) {
        // 将新设备添加到控制点
        (controlPoint as? DefaultControlPoint)?.addDevice(device)
        
        // 通知监听器
        playerListener?.onDeviceFound(device)
        
        // 设备列表更新
        notifyDeviceListUpdated()
    }
    
    /**
     * 处理设备丢失
     */
    private fun handleDeviceLost(device: RemoteDevice) {
        // 从控制点移除设备
        (controlPoint as? DefaultControlPoint)?.removeDevice(device)
        
        // 如果是当前连接的设备，通知断开连接
        if (controlPoint.getCurrentDevice()?.identity?.udn == device.identity.udn) {
            playerListener?.onDisconnected()
        }
        
        // 设备列表更新
        notifyDeviceListUpdated()
    }
    
    /**
     * 通知设备列表更新
     */
    private fun notifyDeviceListUpdated() {
        try {
            // 获取设备列表
            val devices = controlPoint.getDevices()
            
            // 防止频繁更新，使用设备列表哈希值判断是否有实质变化
            val deviceListHash = devices.map { it.identity.udn }.hashCode()
            if (deviceListHash == lastDeviceListHash && devices.isNotEmpty()) {
                EnhancedThreadManager.d(TAG, "设备列表无变化，跳过更新")
                return
            }
            lastDeviceListHash = deviceListHash
            
            // 使用标准设备注册表更新设备列表
            EnhancedThreadManager.d(TAG, "设备列表更新：共${devices.size}个设备")
            StandardDeviceRegistry.getInstance().updateDeviceList(devices)
            
            // 记录设备详情到日志
            devices.forEach { device ->
                EnhancedThreadManager.d(TAG, "设备信息: ${device.details.friendlyName}, " +
                    "制造商: ${device.details.manufacturerInfo?.name ?: "未知"}, " +
                    "ID: ${device.identity.udn}")
            }
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "通知设备列表更新失败", e)
        }
    }
    
    /**
     * 设置是否使用事件总线
     */
    fun setUseEventBus(use: Boolean) {
        this.useEventBus = use
        
        // 如果使用SSDP适配器，也同步设置
        ssdpAdapter?.let { adapter ->
            if (adapter is SSDPIntegrationAdapter) {
                adapter.setUseEventBus(use)
            }
        }
        
        EnhancedThreadManager.d(TAG, "设置使用事件总线: $use")
    }
    
    /**
     * 设置播放器回调
     */
    fun setPlayerListener(listener: PlayerListener) {
        this.playerListener = listener
        
        // 初次设置监听器时，如果已有设备，立即通知
        try {
            val devices = controlPoint.getDevices()
            if (devices.isNotEmpty()) {
                StandardDeviceRegistry.getInstance().updateDeviceList(devices)
            }
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置播放器回调时通知设备列表更新失败", e)
        }
    }
    
    /**
     * 设置是否使用新的SSDP设备发现机制
     */
    fun setUseNewSSDPDiscovery(useNew: Boolean) {
        if (useNewSSDPDiscovery == useNew) return
        
        // 如果已经在搜索中，先停止
        if (isSearching) {
            stopSearch()
        }
        
        useNewSSDPDiscovery = useNew
        
        // 如果启用新发现机制，初始化适配器
        if (useNewSSDPDiscovery && ssdpAdapter == null) {
            initializeSSDPAdapter()
        }
    }
    
    /**
     * 初始化SSDP适配器
     */
    private fun initializeSSDPAdapter() {
        val config = DefaultUpnpServiceConfiguration.getInstance(context)
        ssdpAdapter = SSDPIntegrationAdapter.getInstance(config)
        
        // 设置设备发现回调
        ssdpAdapter?.setDeviceFoundCallback { device ->
            handleDeviceFound(device)
        }
        
        // 设置是否使用事件总线
        val adapter = ssdpAdapter // 创建局部变量
        if (adapter is SSDPIntegrationAdapter) {
            adapter.setUseEventBus(useEventBus) // 使用局部变量
        }
    }
    
    /**
     * 开始搜索设备
     * 使用新的SSDP发现机制或传统方式
     */
    fun searchDevices() {
        if (isSearching) return
        
        isSearching = true
        EnhancedThreadManager.d(TAG, "开始搜索设备")
        
        // 使用网络线程池执行设备搜索，避免在主线程执行网络操作
        EnhancedThreadManager.executeNetworkTask {
            try {
                if (useNewSSDPDiscovery) {
                    // 使用新的SSDP发现机制
                    if (ssdpAdapter == null) {
                        initializeSSDPAdapter()
                    }
                    ssdpAdapter?.startDeviceSearch()
                } else {
                    // 使用传统的发现机制
                    // 原有代码...
                }
            } catch (e: Exception) {
                // 设置搜索状态为false，允许重试
                isSearching = false
                EnhancedThreadManager.e(TAG, "设备搜索失败", e)
                
                // 通知错误
                handleError("设备搜索失败", e, DLNAErrorType.DISCOVERY_ERROR)
            }
        }
    }
    
    /**
     * 停止搜索设备
     */
    fun stopSearch() {
        if (!isSearching) return
        
        isSearching = false
        EnhancedThreadManager.d(TAG, "停止搜索设备")
        
        if (useNewSSDPDiscovery) {
            // 使用新的SSDP发现机制
            ssdpAdapter?.stopDeviceSearch()
        } else {
            // 使用传统的发现机制
            // 原有代码...
        }
    }
    
    /**
     * 设置搜索超时时间
     * @param timeoutMs 超时时间（毫秒）
     */
    fun setSearchTimeout(timeoutMs: Long) {
        try {
            EnhancedThreadManager.d(TAG, "设置搜索超时时间: ${timeoutMs}ms")
            controlPoint.setSearchTimeout(timeoutMs)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置搜索超时时间失败", e)
        }
    }
    
    /**
     * 获取所有设备
     */
    fun getDevices(): List<RemoteDevice> = controlPoint.getDevices()
    
    /**
     * 连接到设备
     */
    fun connectToDevice(device: RemoteDevice) {
        runCatching("连接设备", DLNAErrorType.CONNECTION_ERROR) {
            // 更新设备状态为正在连接
            try {
                // 注册设备到状态管理器并更新状态
                stateAdapter.registerDevice(device)
                stateAdapter.updateDeviceState(device, DeviceStateManager.DeviceState.CONNECTED)
            } catch (e: Exception) {
                EnhancedThreadManager.w(TAG, "更新设备状态失败: ${e.message}")
            }
            
            // 使用控制点连接
            controlPoint.connect(device)
            
            // 通过事件总线发布设备连接事件
            if (useEventBus) {
                eventBus.post(DeviceConnectedEvent(device, this))
            }
            
            // 通知监听器
            playerListener?.onConnected(device)
            EnhancedThreadManager.d(TAG, "已通知监听器设备已连接: ${device.details.friendlyName}")
            
            true
        }
    }
    
    /**
     * 断开设备连接
     */
    fun disconnectFromDevice() {
        try {
            // 如果正在播放，需要先停止播放
            if (isPlaying) {
                stopPlayback()
            }
            
            // 获取当前设备
            val currentDevice = controlPoint.getCurrentDevice()
            
            // 更新设备状态
            currentDevice?.let { device ->
                try {
                    stateAdapter.updateDeviceState(
                        device,
                        DeviceStateManager.DeviceState.DISCOVERED
                    )
                    
                    // 通过事件总线发布设备断开连接事件
                    if (useEventBus) {
                        eventBus.post(DeviceDisconnectedEvent(device, this))
                    }
                } catch (e: Exception) {
                    EnhancedThreadManager.w(TAG, "更新设备状态失败: ${e.message}")
                }
            }
            
            // 使用控制点断开连接
            controlPoint.disconnect()
            
            // 通知监听器
            playerListener?.onDisconnected()
        } catch (e: Exception) {
            handleError("断开设备连接失败", e, DLNAErrorType.CONNECTION_ERROR)
        }
    }
    
    /**
     * 获取当前连接的设备
     */
    fun getCurrentDevice(): RemoteDevice? = controlPoint.getCurrentDevice()
    
    /**
     * 播放媒体
     */
    fun play(mediaUrl: String, title: String) {
        val device = controlPoint.getCurrentDevice()
        if (device == null) {
            handleError("没有连接的设备", null, DLNAErrorType.PLAYBACK_ERROR)
            return
        }
        
        runCatching("播放媒体", DLNAErrorType.PLAYBACK_ERROR) {
            // 更新设备状态为播放中
            try {
                stateAdapter.updateDeviceState(device, DeviceStateManager.DeviceState.PLAYING)
                
                // 更新媒体信息
                val deviceStateManager = DeviceStateManager.getInstance()
                deviceStateManager.updateMediaInfo(
                    device.identity.udn, 
                    mediaUrl,
                    0, // 初始位置为0
                    0 // 初始时长为0
                )
                
                // 单独更新标题信息到additionalInfo
                deviceStateManager.getStateInfo(device.identity.udn)?.let { info ->
                    val updatedInfo = info.additionalInfo.toMutableMap()
                    updatedInfo["title"] = title
                    info.additionalInfo = updatedInfo
                }
                
                // 通过事件总线发布播放开始事件
                if (useEventBus) {
                    eventBus.post(PlaybackStartedEvent(device, mediaUrl, title, this))
                }
            } catch (e: Exception) {
                EnhancedThreadManager.w(TAG, "更新设备状态失败: ${e.message}")
            }
            
            // 使用DlnaController播放媒体
            DlnaControllerWrapper.playMedia(
                usn = device.identity.udn,
                mediaUrl = mediaUrl,
                _metadata = null,
                videoTitle = title,
                episodeLabel = "",
                positionMs = 0
            )
            isPlaying = true
            true
        }.onFailure {
            isPlaying = false
            
            // 更新设备状态为错误
            try {
                stateAdapter.recordError(device.identity.udn, "播放失败: ${it.message}")
                
                // 通过事件总线发布错误事件
                if (useEventBus) {
                    eventBus.post(DeviceErrorEvent(
                        device,
                        errorCode = 1001,
                        errorMessage = "播放失败: ${it.message}",
                        this
                    ))
                }
            } catch (e: Exception) {
                EnhancedThreadManager.w(TAG, "更新设备状态失败: ${e.message}")
            }
        }
    }
    
    /**
     * 停止播放
     */
    fun stopPlayback() {
        try {
            if (isPlaying) {
                controlPoint.getCurrentDevice()?.let { device ->
                    // 更新设备状态为已停止
                    try {
                        stateAdapter.updateDeviceState(device, DeviceStateManager.DeviceState.STOPPED)
                        
                        // 通过事件总线发布播放停止事件
                        if (useEventBus) {
                            eventBus.post(PlaybackStoppedEvent(device, this))
                        }
                    } catch (e: Exception) {
                        EnhancedThreadManager.w(TAG, "更新设备状态失败: ${e.message}")
                    }
                    
                    DlnaControllerWrapper.stopCasting(device.identity.udn)
                    isPlaying = false
                }
            }
        } catch (e: Exception) {
            handleError("停止播放失败", e, DLNAErrorType.PLAYBACK_ERROR)
        }
    }
    
    /**
     * 使用Kotlin的Result运行可能抛出异常的操作
     */
    private inline fun <T> runCatching(
        operation: String, 
        errorType: DLNAErrorType,
        action: () -> T
    ): Result<T> {
        return kotlin.runCatching { 
            action()
        }.onFailure { e ->
            handleError(operation, e, errorType)
        }
    }
    
    /**
     * 统一错误处理方法
     */
    private fun handleError(message: String, cause: Throwable?, errorType: DLNAErrorType) {
        val errorMessage = buildErrorMessage(message, cause)
        logError(errorMessage, cause)
        
        // 获取当前设备
        val currentDevice = controlPoint.getCurrentDevice()
        
        // 通过事件总线发布错误事件
        if (useEventBus && currentDevice != null) {
            eventBus.post(DeviceErrorEvent(
                currentDevice,
                errorCode = when(errorType) {
                    DLNAErrorType.DISCOVERY_ERROR -> 2001
                    DLNAErrorType.CONNECTION_ERROR -> 2002
                    DLNAErrorType.PLAYBACK_ERROR -> 2003
                    else -> 2000
                },
                errorMessage = errorMessage,
                this
            ))
        }
        
        notifyErrorToListener(errorMessage, cause, errorType)
    }
    
    /**
     * 构建错误消息
     */
    private fun buildErrorMessage(message: String, cause: Throwable?): String {
        return "$message${cause?.message?.let { ": $it" } ?: ""}"
    }
    
    /**
     * 记录错误日志
     */
    private fun logError(errorMessage: String, cause: Throwable?) {
        EnhancedThreadManager.e(TAG, errorMessage, cause ?: Exception("未知错误"))
    }
    
    /**
     * 通知监听器发生错误
     */
    private fun notifyErrorToListener(errorMessage: String, cause: Throwable?, errorType: DLNAErrorType) {
        val dlnaException = createDlnaException(errorMessage, cause, errorType)
        playerListener?.onError(dlnaException)
    }
    
    /**
     * 创建DLNA异常
     */
    private fun createDlnaException(message: String, cause: Throwable?, errorType: DLNAErrorType): DLNAException {
        return when (errorType) {
            DLNAErrorType.DISCOVERY_ERROR -> DLNAException.discoveryError(message, cause)
            DLNAErrorType.CONNECTION_ERROR -> DLNAException.connectionError(message, cause)
            DLNAErrorType.PLAYBACK_ERROR -> DLNAException.playbackError(message, cause)
            else -> DLNAException(errorType, message, cause)
        }
    }
    
    /**
     * 添加位置变化到事件总线的功能
     */
    fun updatePosition(positionMs: Long, durationMs: Long) {
        controlPoint.getCurrentDevice()?.let { device ->
            try {
                // 更新设备状态管理器中的位置
                DeviceStateManager.getInstance().updatePosition(
                    device.identity.udn,
                    positionMs
                )
                
                // 通过事件总线发布播放位置变化事件
                if (useEventBus) {
                    eventBus.post(com.yinnho.upnpcast.event.PlaybackPositionChangedEvent(
                        device,
                        positionMs,
                        durationMs,
                        this
                    ))
                }
            } catch (e: Exception) {
                EnhancedThreadManager.w(TAG, "更新播放位置失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新音量并发布事件
     */
    fun updateVolume(volume: Int) {
        controlPoint.getCurrentDevice()?.let { device ->
            try {
                // 更新设备状态管理器中的音量
                DeviceStateManager.getInstance().updateVolume(
                    device.identity.udn,
                    volume,
                    false // 默认非静音状态
                )
                
                // 通过事件总线发布音量变化事件
                if (useEventBus) {
                    eventBus.post(com.yinnho.upnpcast.event.VolumeChangedEvent(
                        device,
                        volume,
                        this
                    ))
                }
            } catch (e: Exception) {
                EnhancedThreadManager.w(TAG, "更新音量失败: ${e.message}")
            }
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            // 停止搜索
            stopSearch()
            
            // 释放SSDP适配器
            if (useNewSSDPDiscovery) {
                SSDPIntegrationAdapter.releaseInstance()
                ssdpAdapter = null
            }
            
            // 停止播放
            if (isPlaying) {
                stopPlayback()
            }
            
            // 断开连接
            if (controlPoint.isConnected()) {
                controlPoint.disconnect()
            }
            
            // 关闭控制点
            controlPoint.shutdown()
            
            // 释放其他资源
            DlnaControllerWrapper.release()
            playerListener = null
            
            // 通过事件总线发布系统关闭事件
            if (useEventBus) {
                eventBus.post(com.yinnho.upnpcast.event.SystemShutdownEvent(this))
            }
            
            EnhancedThreadManager.d(TAG, "DLNAPlayer资源释放完成")
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "释放DLNAPlayer资源时发生错误", e)
        }
    }
    
    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * 是否已连接设备
     */
    fun isConnected(): Boolean = controlPoint.isConnected()
    
    /**
     * 播放器回调接口
     */
    interface PlayerListener {
        fun onDeviceFound(device: RemoteDevice)
        fun onConnected(device: RemoteDevice)
        fun onDisconnected()
        fun onError(error: DLNAException)
    }
}