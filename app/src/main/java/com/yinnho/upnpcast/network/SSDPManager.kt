package com.yinnho.upnpcast.network

import android.util.Log
import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.interfaces.UpnpServiceConfiguration
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.core.EnhancedThreadManager
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import com.yinnho.upnpcast.registry.DeviceRegistry
import com.yinnho.upnpcast.registry.StandardDeviceRegistry
import com.yinnho.upnpcast.event.UPnPEventBus
import com.yinnho.upnpcast.event.DeviceDiscoveredEvent
import com.yinnho.upnpcast.event.DeviceLostEvent
import com.yinnho.upnpcast.event.SearchStartedEvent
import com.yinnho.upnpcast.event.SearchFinishedEvent
import com.yinnho.upnpcast.state.DeviceStateAdapter

/**
 * SSDP协议管理器 - 统一管理UPnP设备发现相关的网络通信
 * 参考Cling库的设计，实现高效率的SSDP协议处理
 */
class SSDPManager private constructor(private val configuration: UpnpServiceConfiguration) {
    private val TAG = "SSDPManager"
    
    // 状态定义
    enum class State { INACTIVE, LISTENING, SEARCHING }
    
    // 状态管理
    private val state = AtomicReference(State.INACTIVE)
    private val isSocketInitialized = AtomicBoolean(false)
    
    // 网络相关
    private var multicastSocket: MulticastSocket? = null
    private val socketLock = Any()
    
    // 设备缓存 - 用于去重
    private val discoveredDevices = ConcurrentHashMap<String, Long>()
    private val DEVICE_CACHE_EXPIRE_MS = 180000L // 设备缓存3分钟过期（原来是5秒）
    
    // 设备注册表 - 替代原有的deviceCache
    private val deviceRegistry: DeviceRegistry = StandardDeviceRegistry.getInstance()
    
    // 监听器和回调
    private val listeners = mutableListOf<DeviceDiscoveryListener>()
    
    // 任务管理
    private var listenerThread: Thread? = null
    private var searchTimeoutFuture: ScheduledFuture<*>? = null
    
    // SSDP消息处理器
    private val messageProcessor = SSDPMessageProcessor()
    
    // 事件总线
    private val eventBus = UPnPEventBus.getInstance()
    
    // 设备状态适配器
    private val stateAdapter = DeviceStateAdapter.getInstance()
    
    // 是否使用事件总线
    private var useEventBus = true
    
    // 创建自己的调度器，而不是使用EnhancedThreadManager
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(
        2,
        object : ThreadFactory {
            private val threadNumber = AtomicInteger(1)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "SSDP-Scheduled-${threadNumber.getAndIncrement()}").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY
                }
            }
        }
    )
    
    // 当前正在处理的设备描述获取任务，键为location URL
    private val pendingDescriptorFetches = ConcurrentHashMap<String, Boolean>()
    
    companion object {
        // SSDP协议常量
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val BUFFER_SIZE = 8192
        private const val TTL = 4
        
        // 搜索超时时间（毫秒）
        private const val DEFAULT_SEARCH_TIMEOUT = 15000L
        
        // 搜索目标
        private val SEARCH_TARGETS = listOf(
            "ssdp:all",                                   // 所有设备
            "upnp:rootdevice",                            // 根设备
            "urn:schemas-upnp-org:device:MediaRenderer:1" // 媒体渲染器
        )
        
        // 单例实例
        @Volatile
        private var INSTANCE: SSDPManager? = null
        
        // 获取实例
        fun getInstance(configuration: UpnpServiceConfiguration): SSDPManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SSDPManager(configuration).also { INSTANCE = it }
            }
        }
        
        // 释放实例
        fun releaseInstance() {
            synchronized(this) {
                INSTANCE?.shutdown()
                INSTANCE = null
            }
        }
    }
    
    /**
     * 设备发现监听器接口
     */
    interface DeviceDiscoveryListener {
        fun onDeviceDiscovered(device: RemoteDevice)
        fun onDeviceLost(device: RemoteDevice)
        fun onSearchStarted()
        fun onSearchFinished(devices: List<RemoteDevice>)
    }
    
    /**
     * 添加设备发现监听器
     */
    fun addListener(listener: DeviceDiscoveryListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }
    
    /**
     * 移除设备发现监听器
     */
    fun removeListener(listener: DeviceDiscoveryListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }
    
    /**
     * 设置是否使用事件总线
     */
    fun setUseEventBus(use: Boolean) {
        this.useEventBus = use
        Log.d(TAG, "设置使用事件总线: $use")
    }
    
    /**
     * 获取所有发现的设备
     */
    fun getDiscoveredDevices(): List<RemoteDevice> {
        return deviceRegistry.getAllDevices()
    }
    
    /**
     * 开始搜索设备
     */
    fun startDiscovery() {
        Log.d(TAG, "请求开始SSDP设备搜索，当前状态: ${state.get()}")
        
        // 如果已经在搜索，先停止当前搜索
        if (state.get() == State.SEARCHING) {
            Log.d(TAG, "已经在搜索中，重置搜索状态")
            stopDiscovery()
        }
        
        // 如果当前是INACTIVE状态，先转到LISTENING状态
        if (state.get() == State.INACTIVE) {
            if (!transitionTo(State.LISTENING)) {
                Log.e(TAG, "无法切换到LISTENING状态，搜索失败")
                return
            }
            
            // 初始化Socket
            try {
                initializeSocketIfNeeded()
                
                // 启动接收线程
                startListenerThread()
            } catch (e: Exception) {
                Log.e(TAG, "初始化失败", e)
                transitionTo(State.INACTIVE)
                return
            }
        }
        
        // 转到SEARCHING状态
        if (!transitionTo(State.SEARCHING)) {
            Log.e(TAG, "无法切换到搜索状态")
            return
        }
        
        // 清理过期设备
        cleanExpiredDevices()
        
        // 通知搜索开始
        if (useEventBus) {
            // 通过事件总线发布搜索开始事件
            eventBus.post(SearchStartedEvent(this))
        }
        
        // 通知监听器
        notifyListeners { it.onSearchStarted() }
        
        // 发送搜索消息
        sendSearchMessages()
        
        // 设置搜索超时
        scheduleSearchTimeout()
        
        Log.d(TAG, "SSDP搜索已成功启动")
    }
    
    /**
     * 停止搜索设备
     */
    fun stopDiscovery() {
        if (state.get() != State.SEARCHING) {
            return
        }
        
        // 取消超时任务
        searchTimeoutFuture?.cancel(false)
        searchTimeoutFuture = null
        
        // 收集发现的设备
        val devices = deviceRegistry.getAllDevices()
        
        // 转为监听状态
        transitionTo(State.LISTENING)
        
        // 通过事件总线发布搜索结束事件
        if (useEventBus) {
            eventBus.post(SearchFinishedEvent(devices, this))
        }
        
        // 通知监听器
        notifyListeners { it.onSearchFinished(devices) }
    }
    
    /**
     * 初始化Socket
     */
    private fun initializeSocketIfNeeded() {
        if (isSocketInitialized.get()) {
            Log.d(TAG, "Socket已经初始化，无需重复操作")
            return
        }
        
        synchronized(socketLock) {
            if (isSocketInitialized.get()) return
            
            Log.d(TAG, "开始初始化SSDP Socket...")
            
            try {
                // 创建多播Socket
                multicastSocket = MulticastSocket(SSDP_PORT).apply {
                    reuseAddress = true
                    timeToLive = TTL
                    
                    // 设置接收超时，防止阻塞
                    soTimeout = 3000
                    
                    // 获取最佳网络接口
                    val networkInterface = configuration.multicastInterface
                    if (networkInterface != null) {
                        setNetworkInterface(networkInterface)
                        Log.d(TAG, "设置多播网络接口: ${networkInterface.displayName}")
                    } else {
                        Log.w(TAG, "未找到有效的多播网络接口，将使用默认接口")
                    }
                    
                    // 加入多播组
                    joinGroup(InetSocketAddress(
                        InetAddress.getByName(SSDP_MULTICAST_ADDRESS),
                        SSDP_PORT
                    ), networkInterface)
                    
                    Log.d(TAG, "已加入多播组: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT")
                }
                
                isSocketInitialized.set(true)
                Log.d(TAG, "SSDP Socket初始化成功")
                
                // 启动清理任务，定期清理过期设备
                startCleanupTask()
            } catch (e: Exception) {
                Log.e(TAG, "初始化SSDP Socket失败", e)
                shutdown()
            }
        }
    }
    
    /**
     * 启动清理任务
     */
    private fun startCleanupTask() {
        // 清理任务
        executor.scheduleAtFixedRate({
            try {
                cleanExpiredDevices()
            } catch (e: Exception) {
                Log.e(TAG, "清理过期设备失败", e)
            }
        }, 10000, 30000, TimeUnit.MILLISECONDS)
        
        // 添加设备活跃刷新任务，每30秒刷新一次设备的活跃状态
        executor.scheduleAtFixedRate({
            try {
                refreshDeviceActivity()
            } catch (e: Exception) {
                Log.e(TAG, "刷新设备活跃状态失败", e)
            }
        }, 15000, 30000, TimeUnit.MILLISECONDS)
        
        Log.d(TAG, "设备清理和活跃刷新任务已启动")
    }
    
    /**
     * 刷新设备活跃状态，防止设备被标记为丢失
     */
    private fun refreshDeviceActivity() {
        val devices = deviceRegistry.getAllDevices()
        if (devices.isEmpty()) return
        
        // 减少日志频率，使用VERBOSE级别
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "刷新${devices.size}个设备的活跃状态")
        }
        
        devices.forEach { device ->
            // 对每个设备，更新其在状态管理器中的最后活跃时间
            try {
                stateAdapter.updateDeviceActivity(device.identity.udn)
            } catch (e: Exception) {
                // 防止单个设备异常影响整体
                Log.w(TAG, "更新设备活跃状态失败: ${device.identity.udn}, ${e.message}")
            }
        }
    }
    
    /**
     * 启动监听线程
     */
    private fun startListenerThread() {
        if (listenerThread?.isAlive == true) return
        
        listenerThread = Thread({
            val buffer = ByteArray(BUFFER_SIZE)
            val packet = DatagramPacket(buffer, buffer.size)
            
            while (state.get() != State.INACTIVE) {
                try {
                    // 接收数据包
                    multicastSocket?.receive(packet)
                    
                    // 处理数据包
                    val message = String(packet.data, 0, packet.length)
                    processIncomingMessage(message, packet.address)
                    
                    // 重置包大小
                    packet.length = buffer.size
                } catch (e: IOException) {
                    if (state.get() != State.INACTIVE) {
                        // 对于SocketTimeoutException不记录日志，这是正常的超时行为
                        if (e !is java.net.SocketTimeoutException) {
                            Log.e(TAG, "接收SSDP消息失败", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理SSDP消息失败", e)
                }
            }
        }, "SSDP-Listener").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
        
        listenerThread?.start()
        Log.d(TAG, "SSDP监听线程已启动")
    }
    
    /**
     * 发送搜索消息
     */
    private fun sendSearchMessages() {
        if (SEARCH_TARGETS.size > 1) {
            Log.d(TAG, "开始发送搜索消息至${SEARCH_TARGETS.size}个目标...")
        }
        
        // 将搜索消息发送移到工作线程中执行
        executor.execute {
            SEARCH_TARGETS.forEach { target ->
                val message = buildSearchMessage(target)
                sendMulticast(message.toByteArray())
                Log.v(TAG, "发送搜索消息: $target")
                
                // 添加短暂延迟，避免网络拥塞
                try {
                    Thread.sleep(100)
                } catch (ignored: InterruptedException) {}
            }
            
            if (SEARCH_TARGETS.size > 1) {
                Log.d(TAG, "搜索消息发送完成")
            }
        }
    }
    
    /**
     * 生成搜索消息
     */
    private fun buildSearchMessage(searchTarget: String): String {
        return """
            M-SEARCH * HTTP/1.1
            HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT
            MAN: "ssdp:discover"
            MX: 5
            ST: $searchTarget
            USER-AGENT: UPnPCast/1.0
            
        """.trimIndent()
    }
    
    /**
     * 发送多播消息
     */
    private fun sendMulticast(data: ByteArray) {
        try {
            val packet = DatagramPacket(
                data,
                data.size,
                InetAddress.getByName(SSDP_MULTICAST_ADDRESS),
                SSDP_PORT
            )
            multicastSocket?.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "发送SSDP多播消息失败", e)
        }
    }
    
    /**
     * 处理接收到的消息
     */
    private fun processIncomingMessage(message: String, address: InetAddress) {
        // 根据消息类型分发处理
        when {
            message.startsWith("HTTP/1.1 200 OK", ignoreCase = true) ->
                processSearchResponse(message, address)
            message.startsWith("NOTIFY", ignoreCase = true) ->
                processNotify(message, address)
        }
    }
    
    /**
     * 处理搜索响应
     */
    private fun processSearchResponse(message: String, address: InetAddress) {
        val deviceInfo = messageProcessor.processSearchResponse(message, address) ?: return
        handleDeviceDiscovery(deviceInfo)
    }
    
    /**
     * 处理NOTIFY消息
     */
    private fun processNotify(message: String, address: InetAddress) {
        val result = messageProcessor.processNotify(message, address) ?: return
        val (deviceInfo, notifyType) = result
        
        when (notifyType) {
            SSDPMessageProcessor.NotifyType.ALIVE -> handleDeviceDiscovery(deviceInfo)
            SSDPMessageProcessor.NotifyType.BYEBYE -> handleDeviceLost(deviceInfo)
            else -> Log.d(TAG, "未知的通知类型: $notifyType")
        }
    }
    
    /**
     * 处理设备发现
     */
    private fun handleDeviceDiscovery(deviceInfo: SSDPMessageProcessor.DeviceInfo) {
        // 使用location作为唯一标识进行去重
        val locationKey = deviceInfo.location
        
        // 更新设备缓存时间戳，使用location作为键
        discoveredDevices[locationKey] = System.currentTimeMillis()
        
        // 已有设备只需更新时间戳，不重复处理，使用location检查缓存
        if (deviceRegistry.getDeviceByLocation(locationKey) != null) {
            // 降级为VERBOSE日志，减少日志输出
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "设备已存在，更新缓存时间: $locationKey")
            }
            return
        }
        
        // 检查是否已经有正在处理的相同location请求
        if (pendingDescriptorFetches.putIfAbsent(locationKey, true) != null) {
            // 已有相同请求正在处理中，无需重复获取
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "设备描述已在处理中，跳过: $locationKey")
            }
            return
        }
        
        // 使用线程池异步执行，避免阻塞SSDP监听线程
        fetchDeviceDescription(deviceInfo)
    }
    
    /**
     * 获取设备描述信息
     * 将设备描述获取和处理逻辑分离，提高代码可读性
     */
    private fun fetchDeviceDescription(deviceInfo: SSDPMessageProcessor.DeviceInfo) {
        val locationKey = deviceInfo.location
        
        executor.execute {
            // 获取设备描述文件并创建设备
            com.yinnho.upnpcast.device.DeviceParser.retrieveDeviceDescriptionAsync(
                deviceInfo.location, 
                deviceInfo.uuid
            ) { result ->
                result.fold(
                    onSuccess = { deviceDescription ->
                        try {
                            // 创建完整的RemoteDevice对象
                            val device = com.yinnho.upnpcast.device.DeviceParser.createRemoteDevice(
                                deviceInfo.usn,
                                deviceDescription,  // 这里传递的是DeviceDescription对象
                                deviceInfo.location
                            )
                            
                            // 使用设备注册表添加设备
                            val isNewDevice = deviceRegistry.addDevice(device)
                            
                            // 更新设备状态
                            try {
                                // 通过状态适配器注册设备
                                stateAdapter.registerDevice(device)
                                
                                // 如果设备详情已验证，更新为VALIDATED状态
                                stateAdapter.updateDeviceState(
                                    device,
                                    com.yinnho.upnpcast.state.DeviceStateManager.DeviceState.VALIDATED
                                )
                            } catch (e: Exception) {
                                // 状态管理器异常不应影响主流程
                                Log.w(TAG, "更新设备状态失败: ${e.message}")
                            }
                            
                            // 仅对新设备记录日志并发送事件，减少日志和事件数量
                            if (isNewDevice) {
                                Log.i(TAG, "发现新设备: ${device.details.friendlyName ?: "未命名设备"}")
                                
                                // 通过事件总线发布设备发现事件
                                if (useEventBus) {
                                    eventBus.post(DeviceDiscoveredEvent(device, this))
                                }
                                
                                // 通知监听器
                                notifyListeners { it.onDeviceDiscovered(device) }
                            } else if (Log.isLoggable(TAG, Log.VERBOSE)) {
                                // 对已有设备仅记录详细日志
                                Log.v(TAG, "设备重新发现: ${device.details.friendlyName ?: "未命名设备"}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "创建设备对象失败: ${deviceInfo.location}", e)
                        } finally {
                            // 完成后移除正在处理的标记
                            pendingDescriptorFetches.remove(locationKey)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "获取设备描述失败: ${deviceInfo.location}", error)
                        // 移除正在处理的标记
                        pendingDescriptorFetches.remove(locationKey)
                    }
                )
            }
        }
    }
    
    /**
     * 处理设备丢失
     */
    private fun handleDeviceLost(deviceInfo: SSDPMessageProcessor.DeviceInfo) {
        // 使用location作为唯一标识
        val locationKey = deviceInfo.location
        
        // 移除设备缓存时间戳
        discoveredDevices.remove(locationKey)
        
        // 获取并移除设备
        val device = deviceRegistry.getDeviceByLocation(locationKey)
        
        if (device != null) {
            // 更新设备状态为LOST
            try {
                stateAdapter.updateDeviceState(
                    device,
                    com.yinnho.upnpcast.state.DeviceStateManager.DeviceState.LOST
                )
            } catch (e: Exception) {
                // 状态管理器异常不应影响主流程
                Log.w(TAG, "更新设备状态失败: ${e.message}")
            }
            
            // 从注册表移除设备
            deviceRegistry.removeDevice(device)
            
            // 记录日志
            Log.i(TAG, "设备离线: ${device.details.friendlyName ?: "未命名设备"}")
            
            // 通过事件总线发布设备丢失事件
            if (useEventBus) {
                eventBus.post(DeviceLostEvent(device, this))
            }
            
            // 通知监听器
            notifyListeners { it.onDeviceLost(device) }
        }
    }
    
    /**
     * 清理过期设备
     */
    private fun cleanExpiredDevices() {
        val now = System.currentTimeMillis()
        val expiredKeys = discoveredDevices.entries
            .filter { now - it.value > DEVICE_CACHE_EXPIRE_MS }
            .map { it.key }
            
        if (expiredKeys.isNotEmpty()) {
            Log.d(TAG, "清理${expiredKeys.size}个过期设备")
            
            // 不要立即清理所有过期设备，而是每次只清理一部分，防止网络波动导致大量设备同时丢失
            val keysToRemove = expiredKeys.take(Math.min(2, expiredKeys.size))
            
            keysToRemove.forEach { key ->
                discoveredDevices.remove(key)
                // 同时从设备缓存中移除
                deviceRegistry.getDeviceByLocation(key)?.let { device ->
                    // 更新设备状态为LOST
                    try {
                        stateAdapter.updateDeviceState(
                            device,
                            com.yinnho.upnpcast.state.DeviceStateManager.DeviceState.LOST
                        )
                    } catch (e: Exception) {
                        // 状态管理器异常不应影响主流程
                        Log.w(TAG, "更新设备状态失败: ${e.message}")
                    }
                    
                    // 通过事件总线发布设备丢失事件
                    if (useEventBus) {
                        eventBus.post(DeviceLostEvent(device, this))
                    }
                    
                    // 通知监听器设备丢失
                    notifyListeners { it.onDeviceLost(device) }
                }
            }
        }
    }
    
    /**
     * 设置搜索超时
     */
    private fun scheduleSearchTimeout() {
        searchTimeoutFuture?.cancel(false)
        
        searchTimeoutFuture = executor.schedule({
            if (state.get() == State.SEARCHING) {
                stopDiscovery()
            }
        }, DEFAULT_SEARCH_TIMEOUT, TimeUnit.MILLISECONDS)
    }
    
    /**
     * 转换状态
     * @return 是否成功转换
     */
    private fun transitionTo(newState: State): Boolean {
        val currentState = state.get()
        
        // 相同状态无需转换
        if (currentState == newState) {
            if (newState != State.INACTIVE) { // 避免记录INACTIVE相关的日志
                // 降级为VERBOSE日志
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "当前已经是${newState}状态，无需转换")
                }
            }
            return true
        }
        
        // 验证状态转换 - 修改逻辑，允许从INACTIVE直接到SEARCHING
        val isValid = when(newState) {
            State.INACTIVE -> true // 可以从任何状态转为非活动
            State.LISTENING -> true // 允许从任何状态转到监听
            State.SEARCHING -> true // 允许从任何状态转到搜索
        }
        
        if (!isValid) {
            Log.w(TAG, "无效的状态转换: $currentState -> $newState")
            return false
        }
        
        // 执行转换
        val result = state.compareAndSet(currentState, newState)
        if (result) {
            // 只记录重要的状态转换
            if (newState == State.SEARCHING || (currentState == State.SEARCHING && newState != State.LISTENING)) {
                Log.d(TAG, "状态转换: $currentState -> $newState")
            } else if (Log.isLoggable(TAG, Log.VERBOSE)) {
                // 其他状态转换降级为VERBOSE
                Log.v(TAG, "状态转换: $currentState -> $newState")
            }
        }
        return result
    }
    
    /**
     * 通知所有监听器
     */
    private fun notifyListeners(action: (DeviceDiscoveryListener) -> Unit) {
        synchronized(listeners) {
            listeners.forEach { listener ->
                try {
                    action(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "通知监听器失败", e)
                }
            }
        }
    }
    
    /**
     * 关闭资源
     */
    fun shutdown() {
        // 设置状态为非活动
        state.set(State.INACTIVE)
        
        // 取消超时任务
        searchTimeoutFuture?.cancel(false)
        searchTimeoutFuture = null
        
        // 关闭Socket
        synchronized(socketLock) {
            try {
                multicastSocket?.let { socket ->
                    try {
                        socket.leaveGroup(
                            InetSocketAddress(InetAddress.getByName(SSDP_MULTICAST_ADDRESS), SSDP_PORT),
                            configuration.multicastInterface
                        )
                    } catch (ignored: Exception) {}
                    
                    try {
                        socket.close()
                    } catch (ignored: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "关闭SSDP Socket异常", e)
            } finally {
                multicastSocket = null
                isSocketInitialized.set(false)
            }
        }
        
        // 关闭调度器
        try {
            executor.shutdown()
            executor.awaitTermination(1, TimeUnit.SECONDS)
        } catch (ignored: Exception) {}
        
        // 清理资源
        discoveredDevices.clear()
        
        // 清空监听器
        synchronized(listeners) {
            listeners.clear()
        }
        
        // 通过事件总线发布系统关闭事件
        if (useEventBus) {
            eventBus.post(com.yinnho.upnpcast.event.SystemShutdownEvent(this))
        }
        
        Log.d(TAG, "SSDP管理器已关闭")
    }
} 