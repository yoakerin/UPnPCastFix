package com.yinnho.upnpcast.event

import android.util.Log
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.network.SSDPManager
import com.yinnho.upnpcast.state.DeviceStateManager
import com.yinnho.upnpcast.utils.DeviceStatusManager

/**
 * UPnP事件适配器
 * 
 * 负责将现有的监听器系统连接到新的事件总线，
 * 确保两种事件机制能够协同工作，在过渡阶段提供兼容性支持。
 */
class UPnPEventAdapter private constructor() : 
    SSDPManager.DeviceDiscoveryListener,
    DeviceStateManager.StateChangeListener {
    
    private val TAG = "UPnPEventAdapter"
    
    // 事件总线
    private val eventBus = UPnPEventBus.getInstance()
    
    // 状态管理器
    private val stateManager = DeviceStateManager.getInstance()
    
    init {
        Log.d(TAG, "UPnP事件适配器初始化")
    }
    
    /**
     * 注册为SSDPManager的监听器
     */
    fun registerWithSSDPManager(manager: SSDPManager) {
        manager.addListener(this)
        Log.d(TAG, "已注册为SSDP管理器监听器")
    }
    
    /**
     * 注册为DeviceStateManager的监听器
     */
    fun registerWithStateManager() {
        stateManager.addListener(this)
        Log.d(TAG, "已注册为设备状态管理器监听器")
    }
    
    /**
     * 设备发现回调
     */
    override fun onDeviceDiscovered(device: RemoteDevice) {
        // 发布设备发现事件
        eventBus.post(DeviceDiscoveredEvent(device, this))
    }
    
    /**
     * 设备丢失回调
     */
    override fun onDeviceLost(device: RemoteDevice) {
        // 发布设备丢失事件
        eventBus.post(DeviceLostEvent(device, this))
    }
    
    /**
     * 搜索开始回调
     */
    override fun onSearchStarted() {
        // 发布搜索开始事件
        eventBus.post(SearchStartedEvent(this))
    }
    
    /**
     * 搜索结束回调
     */
    override fun onSearchFinished(devices: List<RemoteDevice>) {
        // 发布搜索结束事件
        eventBus.post(SearchFinishedEvent(devices, this))
    }
    
    /**
     * 设备状态变化回调
     */
    override fun onDeviceStateChanged(
        deviceId: String, 
        oldState: DeviceStateManager.DeviceState, 
        newState: DeviceStateManager.DeviceState, 
        info: DeviceStateManager.DeviceStateInfo
    ) {
        info.device?.let { device ->
            // 发布设备状态变化事件
            eventBus.post(DeviceStateChangedEvent(device, oldState, newState, info, this))
            
            // 根据状态转换发布特定事件
            when (newState) {
                DeviceStateManager.DeviceState.CONNECTED -> {
                    if (oldState != DeviceStateManager.DeviceState.CONNECTED) {
                        eventBus.post(DeviceConnectedEvent(device, this))
                    }
                }
                DeviceStateManager.DeviceState.PLAYING -> {
                    if (oldState != DeviceStateManager.DeviceState.PLAYING) {
                        info.mediaUrl?.let { url ->
                            eventBus.post(PlaybackStartedEvent(
                                device, 
                                url, 
                                info.additionalInfo["title"] as? String ?: "",
                                this
                            ))
                        }
                    }
                }
                DeviceStateManager.DeviceState.PAUSED -> {
                    if (oldState != DeviceStateManager.DeviceState.PAUSED) {
                        eventBus.post(PlaybackPausedEvent(device, this))
                    }
                }
                DeviceStateManager.DeviceState.STOPPED -> {
                    if (oldState != DeviceStateManager.DeviceState.STOPPED) {
                        eventBus.post(PlaybackStoppedEvent(device, this))
                    }
                }
                DeviceStateManager.DeviceState.ERROR -> {
                    // 发布错误事件
                    eventBus.post(DeviceErrorEvent(
                        device, 
                        info.additionalInfo["errorCode"] as? Int ?: 0,
                        info.errorMessage ?: "未知错误",
                        this
                    ))
                }
                else -> { /* 其他状态不需要特殊处理 */ }
            }
        }
    }
    
    /**
     * 设备列表变化回调
     */
    override fun onDeviceListChanged(activeDevices: List<DeviceStateManager.DeviceStateInfo>) {
        val devices = activeDevices.mapNotNull { it.device }
        
        // 发布设备列表变化事件
        eventBus.post(DeviceListChangedEvent(devices, this))
    }
    
    /**
     * 创建一个桥接旧状态监听器到新事件总线的适配器
     */
    fun createLegacyStatusListenerAdapter(): DeviceStatusManager.StatusChangeListener {
        return object : DeviceStatusManager.StatusChangeListener {
            override fun onDeviceStatusChanged(deviceId: String, status: DeviceStatusManager.DeviceStatus) {
                // 不直接触发事件，这些状态变化已经被DeviceStateManager处理并转换为事件
            }
        }
    }
    
    /**
     * 创建一个桥接旧设备发现监听器到新事件总线的适配器
     */
    fun createLegacyDiscoveryListenerAdapter(): SSDPManager.DeviceDiscoveryListener {
        return object : SSDPManager.DeviceDiscoveryListener {
            override fun onDeviceDiscovered(device: RemoteDevice) {
                // 不直接触发事件，这些发现已经被新的SSDPManager处理并转换为事件
            }
            
            override fun onDeviceLost(device: RemoteDevice) {
                // 不直接触发事件，这些丢失已经被新的SSDPManager处理并转换为事件
            }
            
            override fun onSearchStarted() {
                // 不直接触发事件，这些状态已经被新的SSDPManager处理并转换为事件
            }
            
            override fun onSearchFinished(devices: List<RemoteDevice>) {
                // 不直接触发事件，这些状态已经被新的SSDPManager处理并转换为事件
            }
        }
    }
    
    /**
     * 创建适配器订阅：将新的事件总线转发到旧的监听器
     */
    fun setupEventToListenerBridge(listener: SSDPManager.DeviceDiscoveryListener) {
        // 订阅设备发现事件
        eventBus.subscribe(
            object : EventSubscriber<DeviceDiscoveredEvent> {
                override fun onEvent(event: DeviceDiscoveredEvent) {
                    listener.onDeviceDiscovered(event.device)
                }
            },
            DeviceDiscoveredEvent::class.java,
            ThreadMode.MAIN
        )
        
        // 订阅设备丢失事件
        eventBus.subscribe(
            object : EventSubscriber<DeviceLostEvent> {
                override fun onEvent(event: DeviceLostEvent) {
                    listener.onDeviceLost(event.device)
                }
            },
            DeviceLostEvent::class.java,
            ThreadMode.MAIN
        )
        
        // 订阅搜索开始事件
        eventBus.subscribe(
            object : EventSubscriber<SearchStartedEvent> {
                override fun onEvent(event: SearchStartedEvent) {
                    listener.onSearchStarted()
                }
            },
            SearchStartedEvent::class.java,
            ThreadMode.MAIN
        )
        
        // 订阅搜索结束事件
        eventBus.subscribe(
            object : EventSubscriber<SearchFinishedEvent> {
                override fun onEvent(event: SearchFinishedEvent) {
                    listener.onSearchFinished(event.devices)
                }
            },
            SearchFinishedEvent::class.java,
            ThreadMode.MAIN
        )
        
        Log.d(TAG, "已设置事件到监听器的桥接: ${listener.javaClass.simpleName}")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        // 从状态管理器移除监听
        stateManager.removeListener(this)
        
        Log.d(TAG, "UPnP事件适配器已释放")
    }
    
    companion object {
        @Volatile
        private var INSTANCE: UPnPEventAdapter? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(): UPnPEventAdapter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UPnPEventAdapter().also { INSTANCE = it }
            }
        }
        
        /**
         * 释放单例实例
         */
        fun releaseInstance() {
            synchronized(this) {
                INSTANCE?.release()
                INSTANCE = null
            }
        }
    }
} 