package com.yinnho.upnpcast.manager

import android.content.Context
import com.yinnho.upnpcast.DLNAException
import com.yinnho.upnpcast.DLNAErrorType
import com.yinnho.upnpcast.core.DLNAPlayer
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.registry.StandardDeviceRegistry
import java.lang.ref.WeakReference

/**
 * 设备管理器
 * 负责DLNA设备的发现、连接和管理
 */
class DeviceManager(context: Context) {
    private val TAG = "DeviceManager"
    
    // 使用弱引用存储Context，避免内存泄漏
    private val contextRef = WeakReference(context.applicationContext)
    
    // 核心组件
    private val deviceRegistry = StandardDeviceRegistry.getInstance()
    private val player by lazy {
        contextRef.get()?.let { DLNAPlayer(it) } ?: throw IllegalStateException("Context已被回收")
    }
    
    // 初始化标记
    private var isPlayerInitialized = false
    
    // 搜索超时时间
    private var searchTimeoutMs: Long = 30000 // 默认30秒
    
    // 监听器
    private var deviceFoundListener: ((com.yinnho.upnpcast.model.RemoteDevice) -> Unit)? = null
    private var deviceConnectedListener: ((com.yinnho.upnpcast.model.RemoteDevice) -> Unit)? = null
    private var deviceDisconnectedListener: (() -> Unit)? = null
    private var errorListener: ((DLNAException) -> Unit)? = null
    
    /**
     * 设置设备发现监听器
     */
    fun setDeviceFoundListener(listener: ((com.yinnho.upnpcast.model.RemoteDevice) -> Unit)?) {
        this.deviceFoundListener = listener
    }
    
    /**
     * 设置设备连接监听器
     */
    fun setDeviceConnectedListener(listener: ((com.yinnho.upnpcast.model.RemoteDevice) -> Unit)?) {
        this.deviceConnectedListener = listener
    }
    
    /**
     * 设置设备断开监听器
     */
    fun setDeviceDisconnectedListener(listener: (() -> Unit)?) {
        this.deviceDisconnectedListener = listener
    }
    
    /**
     * 设置错误监听器
     */
    fun setErrorListener(listener: ((DLNAException) -> Unit)?) {
        this.errorListener = listener
    }
    
    /**
     * 设置搜索超时时间
     */
    fun setSearchTimeout(timeoutMs: Long) {
        this.searchTimeoutMs = timeoutMs
    }
    
    /**
     * 初始化播放器及其监听器
     */
    private fun initializePlayer() {
        if (!isPlayerInitialized) {
            player.setPlayerListener(object : DLNAPlayer.PlayerListener {
                override fun onDeviceFound(device: com.yinnho.upnpcast.model.RemoteDevice) {
                    deviceRegistry.addDevice(device)
                    deviceFoundListener?.invoke(device)
                }
                
                override fun onConnected(device: com.yinnho.upnpcast.model.RemoteDevice) {
                    deviceConnectedListener?.invoke(device)
                }
                
                override fun onDisconnected() {
                    deviceDisconnectedListener?.invoke()
                }
                
                override fun onError(error: com.yinnho.upnpcast.DLNAException) {
                    errorListener?.invoke(error)
                }
            })
            isPlayerInitialized = true
        }
    }
    
    /**
     * 开始设备搜索
     */
    fun startDiscovery() {
        try {
            initializePlayer()
            player.searchDevices()
        } catch (e: Exception) {
            handleError(e, "开始设备搜索失败")
        }
    }

    /**
     * 停止设备搜索
     */
    fun stopDiscovery() {
        try {
            player.stopSearch()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "停止设备搜索失败: ${e.message}", e)
        }
    }

    /**
     * 获取所有设备
     */
    fun getAllDevices(): List<com.yinnho.upnpcast.model.RemoteDevice> {
        return deviceRegistry.getAllDevices()
    }
    
    /**
     * 通过ID获取设备
     */
    fun getDeviceById(deviceId: String): com.yinnho.upnpcast.model.RemoteDevice? {
        return deviceRegistry.getDeviceById(deviceId)
    }

    /**
     * 连接到设备
     */
    fun connectToDevice(device: com.yinnho.upnpcast.model.RemoteDevice) {
        try {
            initializePlayer()
            player.connectToDevice(device)
        } catch (e: Exception) {
            handleError(e, "连接到设备失败")
        }
    }
    
    /**
     * 通过ID连接到设备
     */
    fun connectToDeviceById(deviceId: String) {
        try {
            val device = getDeviceById(deviceId) 
                ?: throw DLNAException(DLNAErrorType.DEVICE_ERROR, "设备不存在: $deviceId")
            connectToDevice(device)
        } catch (e: Exception) {
            handleError(e, "通过ID连接设备失败")
        }
    }
    
    /**
     * 断开当前连接
     */
    fun disconnect() {
        try {
            player.disconnectFromDevice()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "断开连接失败: ${e.message}", e)
        }
    }
    
    /**
     * 清除设备缓存
     */
    fun clearDeviceCache() {
        deviceRegistry.clearDevices()
    }
    
    /**
     * 统一错误处理
     */
    private fun handleError(e: Exception, message: String) {
        EnhancedThreadManager.e(TAG, "$message: ${e.message}", e)
        val error = if (e is DLNAException) e else 
            DLNAException(DLNAErrorType.DISCOVERY_ERROR, "$message: ${e.message}", e)
        errorListener?.invoke(error)
        throw error
    }
    
    /**
     * 释放资源
     */
    fun release() {
        player.release()
        deviceFoundListener = null
        deviceConnectedListener = null
        deviceDisconnectedListener = null
        errorListener = null
    }
} 