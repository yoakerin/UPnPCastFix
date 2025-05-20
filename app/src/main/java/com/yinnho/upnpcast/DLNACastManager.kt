package com.yinnho.upnpcast

import android.content.Context
import android.content.SharedPreferences
import com.yinnho.upnpcast.api.CastListener
import com.yinnho.upnpcast.api.DLNAErrorType
import com.yinnho.upnpcast.api.DLNAException
import com.yinnho.upnpcast.api.RemoteDevice
import com.yinnho.upnpcast.cache.UPnPCacheManager
import com.yinnho.upnpcast.core.DLNAPlayer
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.device.DeviceDiscovery
import com.yinnho.upnpcast.interfaces.PlaybackState
import com.yinnho.upnpcast.manager.controller.ControllerManager
import com.yinnho.upnpcast.model.DeviceInfo
import com.yinnho.upnpcast.registry.StandardDeviceRegistry
import com.yinnho.upnpcast.utils.NotificationUtils
import java.lang.ref.WeakReference

/**
 * DLNA投屏管理器
 * 单例模式，负责管理DLNA设备发现和控制
 */
class DLNACastManager private constructor(context: Context) {

    private val TAG = "DLNACastManager"
    
    // 使用弱引用存储Context，避免内存泄漏
    private val contextRef = WeakReference(context.applicationContext)
    
    // 核心组件
    private val controllerManager = ControllerManager()
    private val deviceRegistry = StandardDeviceRegistry.getInstance()
    private val player by lazy {
        contextRef.get()?.let { DLNAPlayer(it) } ?: throw IllegalStateException("Context已被回收")
    }
    
    // 共享偏好设置
    private val sharedPreferences: SharedPreferences by lazy {
        contextRef.get()?.getSharedPreferences("dlna_preferences", Context.MODE_PRIVATE)
            ?: throw IllegalStateException("Context已被回收")
    }
    
    // 监听器
    private var castListener: CastListener? = null
    private var playbackStateListener: PlaybackStateListener? = null
    private var errorListener: ((DLNAException) -> Unit)? = null
    
    // 标志位
    private var isInitialized = false
    private var searchTimeoutMs: Long = 30000 // 默认30秒
    
    // 缓存管理器
    private val cacheManager = UPnPCacheManager.getInstance()
    
    // 设备发现组件和注册表
    private val deviceDiscovery by lazy { 
        contextRef.get()?.let { DeviceDiscovery() } ?: 
            throw IllegalStateException("Context已被回收") 
    }
    
    init {
        // 初始化组件
        isInitialized = true
        
        // 初始化线程管理器
        EnhancedThreadManager.setDebugMode(false)
        
        // 初始化通知工具类
        NotificationUtils.init(context)
        
        // 初始化缓存管理器
        cacheManager.initialize(context)
        
        // 确保DLNACastManagerImpl实例已初始化
        try {
            com.yinnho.upnpcast.manager.DLNACastManagerImpl.getInstance(context)
            EnhancedThreadManager.d(TAG, "DLNACastManagerImpl实例已初始化")
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "初始化DLNACastManagerImpl失败", e)
        }
        
        EnhancedThreadManager.i(TAG, "DLNA投屏库初始化完成")
    }
    

    
    /**
     * 初始化播放器
     */
    private fun initializePlayer() {
        // 添加日志
        EnhancedThreadManager.d(TAG, "initializePlayer called, isInitialized=$isInitialized")
        
        // 修改为每次都设置，不论isInitialized状态如何
        player.setPlayerListener(object : DLNAPlayer.PlayerListener {
            override fun onDeviceFound(device: com.yinnho.upnpcast.model.RemoteDevice) {
                deviceRegistry.addDevice(device)
                
                // 通知设备列表更新
                castListener?.let { listener ->
                    val deviceList = getAllDevices()
                    listener.onDeviceListUpdated(deviceList)
                }
            }

            override fun onConnected(device: com.yinnho.upnpcast.model.RemoteDevice) {
                EnhancedThreadManager.d(TAG, "DLNAPlayer回调onConnected: ${device.details.friendlyName}")
                
                val apiDevice = RemoteDevice(
                    id = device.identity.udn,
                    displayName = device.details.friendlyName ?: device.identity.udn,
                    address = device.identity.descriptorURL.host ?: "",
                    manufacturer = device.details.manufacturerInfo?.name ?: "",
                    model = device.details.modelInfo?.name ?: "",
                    details = device
                )
                controllerManager.addController(device.identity.udn, device)
                
                // 检查并记录castListener状态
                if (castListener == null) {
                    EnhancedThreadManager.e(TAG, "错误: castListener为空，无法传递连接成功事件")
                } else {
                    EnhancedThreadManager.d(TAG, "调用castListener.onConnected: ${apiDevice.displayName}, ID: ${apiDevice.id}")
                    try {
                        castListener?.onConnected(apiDevice)
                    } catch (e: Exception) {
                        EnhancedThreadManager.e(TAG, "调用castListener.onConnected时出现异常", e)
                    }
                }
            }

            override fun onDisconnected() {
                castListener?.onDisconnected()
            }

            override fun onError(error: DLNAException) {
                errorListener?.invoke(error)
                castListener?.onError(error)
            }
        })
        
        isInitialized = true
        EnhancedThreadManager.d(TAG, "播放器监听器已重新设置")
    }
    
    /**
     * 设置投屏监听器
     */
    fun setCastListener(listener: CastListener?) {
        this.castListener = listener
        
        // 同时设置DLNACastManagerImpl的监听器，解决通知不到UI的问题
        try {
            // 创建一个adapter将api.CastListener转换为interfaces.CastListener
            val internalListener = if (listener != null) {
                object : com.yinnho.upnpcast.interfaces.CastListener {
                    override fun onDeviceListUpdated(deviceList: List<com.yinnho.upnpcast.model.RemoteDevice>) {
                        val apiDevices = deviceList.map { device ->
                            RemoteDevice.getOrCreate(
                                id = device.identity.udn,
                                displayName = device.details.friendlyName ?: device.identity.udn,
                                address = device.identity.descriptorURL.host ?: "",
                                manufacturer = device.details.manufacturerInfo?.name ?: "",
                                model = device.details.modelInfo?.name ?: "",
                                details = device
                            )
                        }
                        listener.onDeviceListUpdated(apiDevices)
                    }
                    
                    override fun onConnected(device: com.yinnho.upnpcast.model.RemoteDevice) {
                        val apiDevice = RemoteDevice.getOrCreate(
                            id = device.identity.udn,
                            displayName = device.details.friendlyName ?: device.identity.udn,
                            address = device.identity.descriptorURL.host ?: "",
                            manufacturer = device.details.manufacturerInfo?.name ?: "",
                            model = device.details.modelInfo?.name ?: "",
                            details = device
                        )
                        listener.onConnected(apiDevice)
                    }
                    
                    override fun onDisconnected() {
                        listener.onDisconnected()
                    }
                    
                    override fun onError(error: com.yinnho.upnpcast.api.DLNAException) {
                        listener.onError(error)
                    }
                }
            } else null
            
            com.yinnho.upnpcast.manager.DLNACastManagerImpl.getInstance().setCastListener(internalListener)
            EnhancedThreadManager.d(TAG, "已同步设置DLNACastManagerImpl的CastListener")
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "设置DLNACastManagerImpl的CastListener失败", e)
        }
    }
    
    /**
     * 设置播放状态监听器
     */
    fun setPlaybackStateListener(listener: PlaybackStateListener?) {
        this.playbackStateListener = listener
    }
    
    /**
     * 设置错误监听器
     */
    fun setErrorListener(listener: ((DLNAException) -> Unit)?) {
        this.errorListener = listener
        controllerManager.setErrorListener(listener)
    }
    
    /**
     * 开始设备搜索
     */
    fun startDiscovery() {
        try {
            EnhancedThreadManager.d(TAG, "开始搜索设备")
            initializePlayer()
            player.searchDevices()
        } catch (e: Exception) {
            handleError(e, "开始设备搜索失败", DLNAErrorType.DISCOVERY_ERROR)
        }
    }
    
    /**
     * 停止设备搜索
     */
    fun stopDiscovery() {
        try {
            EnhancedThreadManager.d(TAG, "停止搜索设备")
            player.stopSearch()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "停止设备搜索失败: ${e.message}", e)
        }
    }
    
    /**
     * 获取所有设备
     */
    fun getAllDevices(): List<RemoteDevice> {
        return deviceRegistry.getAllDevices().map { device ->
            // 使用RemoteDevice.getOrCreate方法确保设备实例的一致性
            RemoteDevice.getOrCreate(
                id = device.identity.udn,
                displayName = device.details.friendlyName ?: device.identity.udn,
                address = device.identity.descriptorURL.host ?: "",
                manufacturer = device.details.manufacturerInfo?.name ?: "",
                model = device.details.modelInfo?.name ?: "",
                details = device
            )
        }
    }
    
    /**
     * 连接到设备
     */
    fun connectToDevice(device: RemoteDevice) {
        try {
            EnhancedThreadManager.d(TAG, "连接到设备: ${device.displayName}, ID: ${device.id}")
            
            // 先初始化播放器，确保监听器被设置
            initializePlayer()
            
            // 获取真实设备对象
            val internalDevice = device.details as? com.yinnho.upnpcast.model.RemoteDevice
            
            if (internalDevice == null) {
                EnhancedThreadManager.e(TAG, "错误: 设备对象转换失败，details=${device.details?.javaClass?.simpleName}")
                throw DLNAException(DLNAErrorType.DEVICE_ERROR, "无效的设备对象: ${device.displayName}")
            }
            
            // 记录内部设备信息
            EnhancedThreadManager.d(TAG, "内部设备信息: ${internalDevice.details.friendlyName}, ID: ${internalDevice.identity.udn}")
            
            // 连接设备
            player.connectToDevice(internalDevice)
            
            // 为了确保回调能够正常传递，直接在这里也触发一次onConnected
            //EnhancedThreadManager.d(TAG, "额外保障: 直接触发castListener.onConnected")
            //castListener?.onConnected(device)
            
        } catch (e: Exception) {
            handleError(e, "连接到设备失败", DLNAErrorType.CONNECTION_ERROR)
        }
    }
    
    /**
     * 断开当前连接
     */
    fun disconnect() {
        try {
            EnhancedThreadManager.d(TAG, "断开设备连接")
            player.disconnectFromDevice()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "断开设备连接失败: ${e.message}", e)
        }
    }
    
    /**
     * 播放媒体
     */
    fun playMedia(deviceId: String, mediaUrl: String, title: String, episodeLabel: String = "", positionMs: Long = 0) {
        try {
            EnhancedThreadManager.d(TAG, "播放媒体: $mediaUrl")
            val controller = getControllerOrThrow(deviceId)
            
            // 设置状态监听器
            controller.setPlaybackStateListener { state ->
                val stateString = when(state) {
                    PlaybackState.PLAYING -> "PLAYING"
                    PlaybackState.PAUSED -> "PAUSED"
                    PlaybackState.STOPPED -> "STOPPED"
                    PlaybackState.TRANSITIONING -> "TRANSITIONING"
                    else -> "UNKNOWN"
                }
                playbackStateListener?.onPlaybackStateChanged(stateString)
            }
            
            // 设置位置监听器
            controller.setPositionChangeListener { position, duration ->
                playbackStateListener?.onPositionChanged(position, duration)
            }
            
            // 执行播放
            controller.playMediaSync(mediaUrl, title, episodeLabel, positionMs)
        } catch (e: Exception) {
            handleError(e, "播放媒体失败", DLNAErrorType.PLAYBACK_ERROR)
        }
    }
    
    /**
     * 暂停播放
     */
    fun pausePlayback(deviceId: String) {
        try {
            EnhancedThreadManager.d(TAG, "暂停播放")
            getControllerOrThrow(deviceId).pauseSync()
        } catch (e: Exception) {
            handleError(e, "暂停播放失败", DLNAErrorType.PLAYBACK_ERROR)
        }
    }
    
    /**
     * 恢复播放
     */
    fun resumePlayback(deviceId: String) {
        try {
            EnhancedThreadManager.d(TAG, "恢复播放")
            getControllerOrThrow(deviceId).playSync()
        } catch (e: Exception) {
            handleError(e, "恢复播放失败", DLNAErrorType.PLAYBACK_ERROR)
        }
    }
    
    /**
     * 停止播放
     */
    fun stopPlayback(deviceId: String) {
        try {
            EnhancedThreadManager.d(TAG, "停止播放")
            getControllerOrThrow(deviceId).stopSync()
        } catch (e: Exception) {
            handleError(e, "停止播放失败", DLNAErrorType.PLAYBACK_ERROR)
        }
    }
    
    /**
     * 跳转到指定位置
     */
    fun seekTo(deviceId: String, positionMs: Long) {
        try {
            EnhancedThreadManager.d(TAG, "跳转到位置: ${positionMs}ms")
            getControllerOrThrow(deviceId).seekToSync(positionMs)
        } catch (e: Exception) {
            handleError(e, "跳转失败", DLNAErrorType.PLAYBACK_ERROR)
        }
    }
    
    /**
     * 设置音量
     */
    fun setVolume(deviceId: String, volume: Int) {
        try {
            EnhancedThreadManager.d(TAG, "设置音量: $volume")
            getControllerOrThrow(deviceId).setVolumeSync(volume.toUInt())
        } catch (e: Exception) {
            handleError(e, "设置音量失败", DLNAErrorType.PLAYBACK_ERROR)
        }
    }
    
    /**
     * 设置静音状态
     */
    fun setMute(deviceId: String, mute: Boolean) {
        try {
            EnhancedThreadManager.d(TAG, "设置静音: $mute")
            getControllerOrThrow(deviceId).setMuteSync(mute)
        } catch (e: Exception) {
            handleError(e, "设置静音失败", DLNAErrorType.PLAYBACK_ERROR)
        }
    }
    
    /**
     * 清除控制器缓存
     */
    fun clearControllerCache() {
        EnhancedThreadManager.d(TAG, "清除控制器缓存")
        // 使用缓存管理器清除控制器缓存
        cacheManager.clearCache(UPnPCacheManager.CacheType.CONTROLLER)
        // 同时通知控制器管理器清理资源
        controllerManager.clearAllControllers()
    }
    
    /**
     * 清除设备缓存
     */
    fun clearDeviceCache() {
        EnhancedThreadManager.d(TAG, "清除设备缓存")
        // 使用缓存管理器清除设备缓存
        cacheManager.clearCache(UPnPCacheManager.CacheType.DEVICE)
        // 通知设备注册表清理设备
        deviceRegistry.clearDevices()
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCaches() {
        EnhancedThreadManager.d(TAG, "清除所有缓存")
        // 使用缓存管理器清除所有缓存
        cacheManager.clearAllCaches()
        // 同时通知其他管理器清理资源
        controllerManager.clearAllControllers()
        deviceRegistry.clearDevices()
    }
    
    /**
     * 停止投屏
     */
    fun stopCasting() {
        try {
            EnhancedThreadManager.d(TAG, "停止投屏")
            
            // 停止设备搜索
            stopDiscovery()
            
            // 断开连接
            disconnect()
            
            // 清理控制器缓存
            clearControllerCache()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "停止投屏失败: ${e.message}", e)
        }
    }
    
    /**
     * 设置调试模式
     */
    fun setDebugMode(enabled: Boolean) {
        EnhancedThreadManager.setDebugMode(enabled)
    }
    
    /**
     * 设置搜索超时时间
     */
    fun setSearchTimeout(timeoutMs: Long) {
        this.searchTimeoutMs = timeoutMs
    }
    
    /**
     * 获取控制器或抛出异常
     */
    private fun getControllerOrThrow(deviceId: String) = 
        controllerManager.getController(deviceId, true)
            ?: throw DLNAException(DLNAErrorType.DEVICE_ERROR, "设备不存在: $deviceId")
    
    /**
     * 统一错误处理
     */
    private fun handleError(e: Exception, message: String, errorType: DLNAErrorType) {
        EnhancedThreadManager.e(TAG, "$message: ${e.message}", e)
        val error = if (e is DLNAException) e else 
            DLNAException(errorType, "$message: ${e.message}", e)
        errorListener?.invoke(error)
        throw error
    }
    
    /**
     * 发布资源
     */
    fun release() {
        try {
            EnhancedThreadManager.d(TAG, "释放资源")
            
            // 释放通知工具类
            NotificationUtils.release()
            
            // 停止搜索和断开连接
            player.release()
            
            // 清除所有缓存
            clearAllCaches()
            
            // 释放缓存管理器
            cacheManager.release()
            
            // 清除所有监听器
            castListener = null
            playbackStateListener = null
            errorListener = null
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "释放资源失败: ${e.message}", e)
        }
    }
    
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
    
    /**
     * 单例对象
     */
    companion object {
        @Volatile
        private var instance: DLNACastManager? = null
        
        /**
         * 获取DLNACastManager实例
         * 线程安全的双重检查锁定模式
         */
        fun getInstance(context: Context): DLNACastManager {
            return instance ?: synchronized(this) {
                instance ?: DLNACastManager(context.applicationContext).also { instance = it }
            }
        }
        
        /**
         * 获取已初始化的实例
         */
        fun getInstance(): DLNACastManager? {
            return instance
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

    /**
     * 获取所有设备
     */
    fun getDevices(): List<DeviceInfo> {
        return deviceRegistry.getAllDevices().map { 
            DeviceInfo(it.identity.udn, it.details.friendlyName ?: "未命名设备", it.services.isEmpty())
        }
    }
} 