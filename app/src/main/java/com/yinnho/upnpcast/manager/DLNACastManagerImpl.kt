package com.yinnho.upnpcast.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.yinnho.upnpcast.device.DeviceAdapterFactory
import com.yinnho.upnpcast.device.LGDeviceAdapter
import com.yinnho.upnpcast.device.SamsungDeviceAdapter
import com.yinnho.upnpcast.device.XiaomiDeviceAdapter
import com.yinnho.upnpcast.core.DLNAPlayer
import com.yinnho.upnpcast.registry.StandardDeviceRegistry
import com.yinnho.upnpcast.api.DLNAErrorType
import com.yinnho.upnpcast.api.DLNAException
import com.yinnho.upnpcast.interfaces.CastListener
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.network.EnhancedNetworkManager
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.utils.UrlUtils
import com.yinnho.upnpcast.device.getIpAddress
import com.yinnho.upnpcast.device.getPort
import com.yinnho.upnpcast.device.locationKey
import com.yinnho.upnpcast.wrapper.DlnaControllerWrapper
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import com.yinnho.upnpcast.cache.UPnPCacheManager

/**
 * DLNA投屏管理器实现类
 * 负责管理DLNA设备发现和控制的具体实现
 */
class DLNACastManagerImpl private constructor(context: Context) {
    private val TAG = "DLNACastManagerImpl"
    // 使用弱引用存储Context
    private val contextRef = WeakReference(context.applicationContext)
    
    // 添加SharedPreferences变量
    private val sharedPreferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        "dlna_preferences", Context.MODE_PRIVATE
    )
    
    private val player by lazy {
        EnhancedThreadManager.d(TAG, "懒加载初始化DLNAPlayer")
        contextRef.get()?.let { DLNAPlayer(it) } ?: throw IllegalStateException("Context已被回收")
    }
    private var castListener: CastListener? = null
    
    // 网络管理器
    private val networkManager by lazy {
        EnhancedNetworkManager.getInstance(contextRef.get()!!)
    }

    // 设备控制URL映射，用于缓存已知工作的控制URL
    private val deviceControlURLs = mutableMapOf<String, String>()

    // 使用缓存管理器替代本地controllerCache
    private val cacheManager = UPnPCacheManager.getInstance()
    
    // 控制器缓存已迁移到UPnPCacheManager
    // private val controllerCache = ConcurrentHashMap<String, com.yinnho.upnpcast.controller.DlnaController>()

    /**
     * 播放状态监听器接口
     */
    interface PlaybackStateListener {
        /**
         * 播放状态变化回调
         */
        fun onPlaybackStateChanged(state: String)
        
        /**
         * 播放位置变化回调
         */
        fun onPositionChanged(positionMs: Long, durationMs: Long)
    }

    // 私有变量
    private var playbackStateListener: PlaybackStateListener? = null

    // 记录上一次通知的设备location集合，而非UDN集合
    private var lastNotifiedLocationKeys: Set<String> = emptySet()
    
    // 添加锁对象，用于同步设备通知
    private val notifyLock = Any()

    /**
     * 静态单例模式
     */
    companion object {
        @Volatile
        private var instance: DLNACastManagerImpl? = null

        /**
         * 获取DLNACastManagerImpl实例
         * 线程安全的双重检查锁定模式
         */
        fun getInstance(context: Context): DLNACastManagerImpl {
            return instance ?: synchronized(this) {
                instance ?: DLNACastManagerImpl(context.applicationContext).also { instance = it }
            }
        }

        /**
         * 获取已初始化的实例
         */
        fun getInstance(): DLNACastManagerImpl {
            return instance ?: throw IllegalStateException("DLNACastManagerImpl未初始化")
        }
        
        /**
         * 释放单例实例，避免内存泄漏
         */
        fun releaseInstance() {
            synchronized(this) {
                instance?.release()
                instance = null
            }
        }
    }

    init {
        EnhancedThreadManager.d(TAG, "初始化DLNACastManagerImpl")
        // 注册设备适配器
        registerDeviceAdapters()
    }

    /**
     * 注册设备适配器
     */
    private fun registerDeviceAdapters() {
        runSafely("register device adapters") {
            EnhancedThreadManager.d(TAG, "注册设备适配器")
            DeviceAdapterFactory.registerAdapter(XiaomiDeviceAdapter())
            DeviceAdapterFactory.registerAdapter(SamsungDeviceAdapter())
            DeviceAdapterFactory.registerAdapter(LGDeviceAdapter())
        }
    }

    /**
     * 初始化播放器相关信息
     */
    private fun initializePlayer() {
        if (!isPlayerInitialized) {
            EnhancedThreadManager.d(TAG, "正在初始化播放器监听器")
            player.setPlayerListener(object : DLNAPlayer.PlayerListener {
                override fun onDeviceFound(device: RemoteDevice) {
                    EnhancedThreadManager.d(TAG, "发现设备: ${device.displayString}")
                    // 直接使用标准设备注册表
                    StandardDeviceRegistry.getInstance().addDevice(device)
                }

                override fun onConnected(device: RemoteDevice) {
                    EnhancedThreadManager.d(TAG, "设备已连接: ${device.displayString}")
                    castListener?.onConnected(device)
                }

                override fun onDisconnected() {
                    EnhancedThreadManager.d(TAG, "设备已断开")
                    castListener?.onDisconnected()
                }

                override fun onError(error: DLNAException) {
                    EnhancedThreadManager.e(TAG, "播放错误: ${error.message}", error)
                    castListener?.onError(error)
                }
            })
            isPlayerInitialized = true
        } else {
            EnhancedThreadManager.d(TAG, "播放器监听器已初始化，无需重复初始化")
        }
    }

    private var isPlayerInitialized = false
    private var dlnaControllerInitialized = false

    /**
     * 设置投屏监听器
     */
    fun setCastListener(listener: CastListener?) {
        this.castListener = listener
    }

    /**
     * 开始设备搜索
     */
    fun startDiscovery() {
        runSafely("start device discovery", DLNAErrorType.DISCOVERY_ERROR) {
            // 确保先初始化播放器
            initializePlayer()

            EnhancedThreadManager.d(TAG, "开始搜索设备")
            // 注意：现在直接调用方法，不再使用广播机制 - 2025/05/12优化
            player.searchDevices()
        }
    }

    /**
     * 停止设备搜索
     */
    fun stopDiscovery() {
        runSafely("stop device discovery", logErrorOnly = true) {
            EnhancedThreadManager.d(TAG, "停止搜索设备")
            // 注意：现在直接调用方法，不再使用广播机制 - 2025/05/12优化
            player.stopSearch()
        }
    }

    /**
     * 获取所有设备
     */
    fun getAllDevices(): List<RemoteDevice> {
        return StandardDeviceRegistry.getInstance().getAllDevices()
    }

    /**
     * 连接到设备
     */
    fun connectToDevice(device: RemoteDevice) {
        runSafely("connect to device", DLNAErrorType.DEVICE_CONNECTION_ERROR) {
            // 确保先初始化播放器
            initializePlayer()

            EnhancedThreadManager.d(TAG, "连接到设备: ${device.displayString}")
            player.connectToDevice(device)
        }
    }

    /**
     * 断开当前连接
     */
    fun disconnect() {
        runSafely("disconnect device", DLNAErrorType.DEVICE_CONNECTION_ERROR) {
            // 确保先初始化播放器
            initializePlayer()

            EnhancedThreadManager.d(TAG, "断开设备连接")
            player.disconnectFromDevice()
        }
    }

    /**
     * 获取设备控制器(优先从缓存获取)
     * @param deviceId 设备ID
     * @return 设备控制器
     */
    private fun getDeviceController(deviceId: String): com.yinnho.upnpcast.controller.DlnaController? {
        // 使用缓存管理器获取控制器
        val cachedController = cacheManager.getController(deviceId, com.yinnho.upnpcast.controller.DlnaController::class.java)
        if (cachedController != null) {
            EnhancedThreadManager.d(TAG, "使用缓存的控制器: ${deviceId}")
            return cachedController
        }
        
        // 没有控制器，需要创建新的
        val device = getDeviceById(deviceId)
        if (device == null) {
            EnhancedThreadManager.e(TAG, "没有找到设备: ${deviceId}")
            return null
        }
        
        try {
            EnhancedThreadManager.d(TAG, "为设备创建新控制器: ${device.displayString}")
            val controller = com.yinnho.upnpcast.controller.DlnaController(device)
            
            // 使用缓存管理器缓存控制器
            cacheManager.cacheController(deviceId, controller)
            
            return controller
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "创建控制器失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 使用新控制器播放媒体
     * @param deviceId 设备ID
     * @param mediaUrl 媒体URL
     * @param title 标题
     * @param episodeLabel 集数标签
     * @param positionMs 开始播放的位置（毫秒）
     */
    fun playMedia(deviceId: String, mediaUrl: String, title: String, episodeLabel: String = "", positionMs: Long = 0) {
        runSafely("play media with new controller", DLNAErrorType.PLAYBACK_ERROR) {
            EnhancedThreadManager.d(TAG, "播放媒体: $mediaUrl")
            
            // 获取设备控制器(优先从缓存获取)
            val controller = getDeviceController(deviceId) ?: throw DLNAException(
                DLNAErrorType.DEVICE_ERROR, "设备不存在: $deviceId"
            )
            
            // 记录设备能力信息，帮助调试(仅当是新创建的控制器时)
            if (!cacheManager.isControllerCached(deviceId)) {
                controller.logDeviceCapabilities()
            }
            
            // 设置播放状态监听器
            controller.setPlaybackStateListener { state ->
                val stateString = when(state) {
                    com.yinnho.upnpcast.interfaces.PlaybackState.PLAYING -> "PLAYING"
                    com.yinnho.upnpcast.interfaces.PlaybackState.PAUSED -> "PAUSED"
                    com.yinnho.upnpcast.interfaces.PlaybackState.STOPPED -> "STOPPED"
                    com.yinnho.upnpcast.interfaces.PlaybackState.TRANSITIONING -> "TRANSITIONING"
                    else -> "UNKNOWN"
                }
                playbackStateListener?.onPlaybackStateChanged(stateString)
            }
            
            // 使用executeControllerAction方法执行播放
            executeControllerAction(deviceId, "播放媒体") { ctrl ->
                ctrl.playMediaSync(mediaUrl, title, episodeLabel, positionMs)
            }
        }
    }
    
    /**
     * 执行控制器动作的通用方法
     * @param deviceId 设备ID
     * @param actionName 动作名称(用于日志)
     * @param errorType 错误类型
     * @param action 要执行的动作
     */
    private fun executeControllerAction(
        deviceId: String,
        actionName: String,
        errorType: DLNAErrorType = DLNAErrorType.PLAYBACK_ERROR,
        action: (com.yinnho.upnpcast.controller.DlnaController) -> Boolean
    ) {
        runSafely("$actionName playback", errorType) {
            EnhancedThreadManager.d(TAG, "$actionName")
            
            // 获取设备控制器(优先从缓存获取)
            val controller = getDeviceController(deviceId) ?: throw DLNAException(
                DLNAErrorType.DEVICE_ERROR, "设备不存在: $deviceId"
            )
            
            // 启动异步任务执行动作
            EnhancedThreadManager.executeTask {
                try {
                    val result = action(controller)
                    if (result) {
                        EnhancedThreadManager.d(TAG, "${actionName}成功")
                    } else {
                        EnhancedThreadManager.e(TAG, "${actionName}失败")
                        castListener?.onError(DLNAException(errorType, "${actionName}失败"))
                    }
                } catch (e: Exception) {
                    EnhancedThreadManager.e(TAG, "${actionName}异常: ${e.message}", e)
                    castListener?.onError(DLNAException(errorType, "${actionName}异常: ${e.message}", e))
                }
            }
        }
    }
    
    /**
     * 暂停播放
     * @param deviceId 设备ID
     */
    fun pausePlayback(deviceId: String) {
        executeControllerAction(deviceId, "暂停") { controller ->
            controller.pauseSync()
        }
    }
    
    /**
     * 恢复播放
     * @param deviceId 设备ID
     */
    fun resumePlayback(deviceId: String) {
        executeControllerAction(deviceId, "恢复播放") { controller ->
            controller.playSync()
        }
    }
    
    /**
     * 停止播放
     * @param deviceId 设备ID
     */
    fun stopPlayback(deviceId: String) {
        executeControllerAction(deviceId, "停止播放") { controller ->
            controller.stopSync()
        }
    }
    
    /**
     * 跳转到指定位置
     * @param deviceId 设备ID
     * @param positionMs 位置(毫秒)
     */
    fun seekTo(deviceId: String, positionMs: Long) {
        executeControllerAction(deviceId, "跳转到位置") { controller ->
            controller.seekToSync(positionMs)
        }
    }
    
    /**
     * 设置音量
     * @param deviceId 设备ID
     * @param volume 音量值(0-100)
     */
    fun setVolume(deviceId: String, volume: Int) {
        executeControllerAction(deviceId, "设置音量") { controller ->
            // 检查音量范围
            if (volume < 0 || volume > 100) {
                throw DLNAException(DLNAErrorType.INVALID_PARAMETER, "音量必须在0-100之间")
            }
            controller.setVolumeSync(volume.toUInt())
        }
    }
    
    /**
     * 设置静音状态
     * @param deviceId 设备ID
     * @param mute 是否静音
     */
    fun setMute(deviceId: String, mute: Boolean) {
        executeControllerAction(deviceId, "设置静音") { controller ->
            controller.setMuteSync(mute)
        }
    }
    
    /**
     * 设置播放状态监听器
     */
    fun setPlaybackStateListener(listener: PlaybackStateListener?) {
        this.playbackStateListener = listener
    }
    
    /**
     * 根据设备ID获取设备
     */
    fun getDeviceById(deviceId: String): RemoteDevice? {
        return StandardDeviceRegistry.getInstance().getDeviceById(deviceId)
    }
    
    /**
     * 清除控制器缓存
     */
    fun clearControllerCache() {
        runSafely("clear controller cache", DLNAErrorType.RESOURCE_ERROR) {
            EnhancedThreadManager.d(TAG, "清除控制器缓存")
            
            // 使用缓存管理器清除控制器缓存
            cacheManager.clearCache(UPnPCacheManager.CacheType.CONTROLLER)
        }
    }

    /**
     * 停止投屏
     */
    fun stopCasting() {
        runSafely("stop casting", DLNAErrorType.CONTROL_ERROR) {
            val device = player.getCurrentDevice()
            if (device != null) {
                val deviceUdn = device.identity.udn
                EnhancedThreadManager.d(TAG, "停止投屏, 设备: ${device.details.friendlyName}")

                if (dlnaControllerInitialized) {
                    // 使用DlnaControllerWrapper停止投屏
                    DlnaControllerWrapper.stopCasting(deviceUdn)
                }

                // 断开设备连接
                disconnect()
            }
        }
    }

    /**
     * 预加载必要组件（可在应用启动时调用）
     * 这有助于解决某些华为设备的初始化问题
     */
    fun preloadComponents() {
        runSafely("preload DLNA components", DLNAErrorType.RESOURCE_ERROR) {
            EnhancedThreadManager.d(TAG, "预加载DLNA组件...")

            // 初始化播放器
            initializePlayer()

            // 预加载DlnaControllerWrapper
            DlnaControllerWrapper.setAppContext(contextRef.get()!!)
            dlnaControllerInitialized = true

            EnhancedThreadManager.d(TAG, "DLNA组件预加载完成")
        }
    }

    /**
     * 处理没有连接设备的情况
     */
    private fun handleNoDeviceConnected(operation: String) {
        EnhancedThreadManager.e(TAG, "没有连接的设备，无法$operation")
        val error = DLNAException(DLNAErrorType.DEVICE_CONNECTION_ERROR, "没有连接的设备")
        castListener?.onError(error)
    }

    /**
     * 安全执行代码块，统一处理异常
     */
    private inline fun runSafely(
        operationName: String,
        errorType: DLNAErrorType? = null,
        logErrorOnly: Boolean = false,
        action: () -> Unit
    ) {
        try {
            action()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "$operationName failed", e)
            if (!logErrorOnly && errorType != null) {
                handleException(e, "$operationName failed", errorType)
            }
        }
    }

    /**
     * 安全执行代码块，并返回结果，统一处理异常
     */
    private inline fun <T> runSafelyWithResult(
        operationName: String,
        defaultValue: T,
        errorType: DLNAErrorType? = null,
        action: () -> T
    ): T {
        return try {
            action()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "$operationName failed", e)
            if (errorType != null) {
                handleException(e, "$operationName failed", errorType)
            }
            defaultValue
        }
    }

    /**
     * 统一处理异常
     */
    private fun handleException(e: Exception, message: String, errorType: DLNAErrorType) {
        val dlnaException = if (e is DLNAException) {
            e
        } else {
            DLNAException(errorType, "$message: ${e.message}", e)
        }
        castListener?.onError(dlnaException)
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            EnhancedThreadManager.d(TAG, "释放DLNACastManagerImpl资源")
            
            // 停止设备搜索
            player.stopSearch()
            
            // 断开当前连接
            try {
                player.disconnectFromDevice()
            } catch (e: Exception) {
                // 忽略异常
            }
            
            // 释放播放器资源
            player.release()
            
            // 清除控制器和设备缓存
            cacheManager.clearCache(UPnPCacheManager.CacheType.CONTROLLER)
            cacheManager.clearCache(UPnPCacheManager.CacheType.DEVICE)
            
            // 清除所有监听器
            castListener = null
            playbackStateListener = null
            
            EnhancedThreadManager.d(TAG, "DLNACastManagerImpl资源已释放")
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "释放资源失败: ${e.message}", e)
        }
    }

    /**
     * 获取设备基础URL
     */
    private fun getDeviceBaseUrl(device: RemoteDevice): String {
        // descriptorURL通常不会为null，直接使用
        val url = device.identity.descriptorURL
        return try {
            UrlUtils.getBaseUrl(url)
        } catch (e: Exception) {
            // 如果URL解析失败，尝试使用IP地址和端口构建URL
            val ip = device.getIpAddress() 
            val port = device.getPort()
            "http://$ip:$port"
        }
    }

    /**
     * 更新设备控制URL
     * 将成功的控制URL保存起来，避免每次都需要重新尝试多个URL
     */
    fun updateControlURL(controlURL: String) {
        runSafely("更新控制URL") {
            // 解析控制URL中的主机部分
            val hostPart = UrlUtils.getBaseUrl(controlURL)
            if (hostPart.isEmpty()) {
                Log.e(TAG, "解析控制URL失败")
                return@runSafely
            }
            
            // 查找对应的设备
            val matchedDevice = getAllDevices().firstOrNull { device ->
                getDeviceBaseUrl(device) == hostPart
            }
            
            if (matchedDevice != null) {
                Log.d(TAG, "找到匹配的设备: ${matchedDevice.details.friendlyName}")
                // 缓存这个URL供后续使用
                deviceControlURLs[matchedDevice.identity.udn] = controlURL
                Log.d(TAG, "已为设备${matchedDevice.identity.udn}更新控制URL: $controlURL")
            } else {
                Log.w(TAG, "未找到与控制URL匹配的设备")
            }
        }
    }

    /**
     * 获取设备已知工作的控制URL
     * @param deviceId 设备唯一标识符（USN或URL生成的ID）
     * @return 已知工作的控制URL
     */
    fun getKnownControlURL(deviceId: String): String? {
        // 先从内存缓存中查找
        val memoryURL = deviceControlURLs[deviceId]
        if (memoryURL != null) {
            EnhancedThreadManager.d(TAG, "从内存缓存中获取到设备 $deviceId 的工作控制URL: $memoryURL")
            return memoryURL
        }
        
        // 再从持久化存储中查找
        val storedURL = sharedPreferences.getString("control_url_$deviceId", null)
        if (storedURL != null) {
            EnhancedThreadManager.d(TAG, "从持久化存储中获取到设备 $deviceId 的工作控制URL: $storedURL")
            // 同步到内存缓存中
            deviceControlURLs[deviceId] = storedURL
        }
        return storedURL
    }

    /**
     * 保存已验证工作的控制URL
     * @param deviceId 设备唯一标识符
     * @param controlURL 工作的控制URL
     */
    fun saveWorkingControlURL(deviceId: String, controlURL: String) {
        runSafely("保存工作控制URL") {
            EnhancedThreadManager.d(TAG, "保存设备 $deviceId 的工作控制URL: $controlURL")
            
            // 先保存到内存缓存
            deviceControlURLs[deviceId] = controlURL
            
            // 再保存到持久化存储
            sharedPreferences.edit()
                .putString("control_url_$deviceId", controlURL)
                .apply()
        }
    }

    /**
     * 设置自动连接模式
     */
    fun setAutoConnect(enabled: Boolean) {
        EnhancedThreadManager.d(TAG, "设置自动连接模式: $enabled")
        sharedPreferences.edit()
            .putBoolean("auto_connect", enabled)
            .apply()
    }

    /**
     * 设置调试模式
     */
    fun setDebugMode(enabled: Boolean) {
        EnhancedThreadManager.d(TAG, "设置调试模式: $enabled")
        sharedPreferences.edit()
            .putBoolean("debug_mode", enabled)
            .apply()
    }

    /**
     * 设置搜索超时时间
     */
    fun setSearchTimeout(timeoutMs: Long) {
        EnhancedThreadManager.d(TAG, "设置搜索超时: ${timeoutMs}ms")
        sharedPreferences.edit()
            .putLong("search_timeout", timeoutMs)
            .apply()
    }

    /**
     * 清除设备缓存
     */
    fun clearDeviceCache() {
        runSafely("clear device cache", DLNAErrorType.RESOURCE_ERROR) {
            EnhancedThreadManager.d(TAG, "清除设备缓存")
            // 清除设备列表
            StandardDeviceRegistry.getInstance().clearDevices()
            
            // 清除控制URL缓存
            deviceControlURLs.clear()
            // 清除持久化存储中的控制URL
            val editor = sharedPreferences.edit()
            sharedPreferences.all.keys.filter { key: String -> key.startsWith("control_url_") }.forEach { key: String ->
                editor.remove(key)
            }
            editor.apply()
            EnhancedThreadManager.d(TAG, "设备缓存已清除")
        }
    }

    /**
     * 处理设备添加事件
     * 供RegistryListener调用
     */
    internal fun handleDeviceAdded(device: RemoteDevice) {
        runSafely("handle device added") {
            EnhancedThreadManager.d(TAG, "设备添加: ${device.details.friendlyName ?: "未命名设备"}")
            
            // 通知监听器
            castListener?.onDeviceAdded(device)
            
            // 更新设备列表
            val devices = getAllDevices()
            notifyDeviceListUpdated(devices)
        }
    }
    
    /**
     * 处理设备移除事件
     * 供RegistryListener调用
     */
    internal fun handleDeviceRemoved(device: RemoteDevice) {
        runSafely("handle device removed") {
            EnhancedThreadManager.d(TAG, "设备移除: ${device.details.friendlyName ?: "未命名设备"}")
            
            // 如果是当前连接的设备，通知断开连接
            val currentDevice = player.getCurrentDevice()
            if (currentDevice?.identity?.udn == device.identity.udn) {
                castListener?.onDisconnected()
            }
            
            // 通知监听器
            castListener?.onDeviceRemoved(device)
            
            // 更新设备列表
            val devices = getAllDevices()
            notifyDeviceListUpdated(devices)
        }
    }
    
    /**
     * 处理设备更新事件
     * 供RegistryListener调用
     */
    internal fun handleDeviceUpdated(device: RemoteDevice) {
        runSafely("handle device updated") {
            EnhancedThreadManager.d(TAG, "设备更新: ${device.details.friendlyName ?: "未命名设备"}")
            
            // 通知监听器
            castListener?.onDeviceUpdated(device)
            
            // 更新设备列表
            val devices = getAllDevices()
            notifyDeviceListUpdated(devices)
        }
    }

    /**
     * 内部方法：通知设备列表更新
     * 用于StandardDeviceRegistry调用
     */
    internal fun notifyDeviceListUpdated(devices: List<RemoteDevice>) {
        synchronized(notifyLock) {
            val threadId = Thread.currentThread().name
            Log.d(TAG, "接收到设备列表更新通知: ${devices.size}个设备 | 线程=$threadId")
            
            // 记录设备详情日志
            devices.forEachIndexed { index, device ->
                Log.d(TAG, "设备[$index]: ${device.details.friendlyName}, IP: ${device.getIpAddress()}, " +
                      "制造商: ${device.details.manufacturerInfo?.name ?: "未知"}, " +
                      "ID: ${device.identity.udn}")
            }
            
            // 计算当前设备的位置ID集合
            val currentLocationKeys = devices.map { it.locationKey }.toSet()
            
            // 检查是否有监听器
            if (castListener == null) {
                Log.w(TAG, "没有设置CastListener，无法通知UI更新设备列表")
            }
            
            // 强制每次通知（暂时用于调试）
            Log.d(TAG, "通知UI更新设备列表 | 线程=$threadId")
            lastNotifiedLocationKeys = currentLocationKeys
            castListener?.onDeviceListUpdated(devices)
            
            if (devices.isNotEmpty()) {
                Log.d(TAG, "设备列表通知后 - 发送了${devices.size}个设备到UI")
            }
        }
    }

    /**
     * 根据位置获取设备 
     * 用于StandardDeviceRegistry调用
     */
    fun getDeviceByLocation(location: String): RemoteDevice? {
        // 使用设备注册表根据位置查找设备
        return StandardDeviceRegistry.getInstance().getDeviceByLocation(location)
    }

    /**
     * 清空所有设备列表
     */
    fun clearDevices() {
        runSafely("clear devices", logErrorOnly = true) {
            StandardDeviceRegistry.getInstance().clearDevices()
        }
    }

    /**
     * 获取当前所有设备
     */
    fun getCurrentDevices(): List<RemoteDevice> {
        return StandardDeviceRegistry.getInstance().getAllDevices()
    }

    /**
     * 获取在线设备列表
     */
    fun getOnlineDevices(): List<RemoteDevice> {
        return StandardDeviceRegistry.getInstance().getAllDevices()
    }
}