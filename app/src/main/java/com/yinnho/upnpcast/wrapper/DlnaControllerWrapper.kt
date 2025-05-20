package com.yinnho.upnpcast.wrapper

import android.content.Context
import android.net.wifi.WifiManager
import com.yinnho.upnpcast.controller.DlnaControllerFactory
import com.yinnho.upnpcast.core.DefaultUpnpServiceConfiguration
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.interfaces.RegistryListener
import com.yinnho.upnpcast.manager.RegistryImpl
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.network.RouterImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DLNA控制器包装类
 * 提供简化的API
 * 注意: 广播机制已于2025/05/12版本完全移除，改为直接方法调用
 */
class DlnaControllerWrapper {
    /**
     * 设备监听接口
     */
    interface DeviceListener {
        fun onDeviceFound(device: RemoteDevice)
        fun onDeviceLost(device: RemoteDevice)
    }
    
    companion object {
        private const val TAG = "DlnaControllerWrapper"
        internal var deviceListener: DeviceListener? = null
        
        // 协程作用域，使用SupervisorJob避免一个协程失败导致所有协程取消
        private val coroutineJob = SupervisorJob()
        private val coroutineScope = CoroutineScope(Dispatchers.IO + coroutineJob)
        
        // 状态标志
        private var isInitialized = false
        private var isSearching = false
        
        // 上下文
        private var appContext: Context? = null
        
        /**
         * 通用错误处理方法
         */
        private inline fun <T> handleSafely(operation: String, block: () -> T): T? {
            return try {
                block()
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "${operation}失败: ${e.message}", e)
                null
            }
        }

        /**
         * 获取应用上下文
         */
        fun getAppContext(): Context? {
            return appContext
        }

        /**
         * 设置应用上下文
         */
        fun setAppContext(context: Context) {
            if (isInitialized) {
                return
            }
            
            handleSafely("设置应用上下文") {
                isInitialized = true
                appContext = context.applicationContext
                
                // 设置工厂类的上下文
                DlnaControllerFactory.setAppContext(context)
                
                EnhancedThreadManager.d(TAG, "应用上下文已设置，DLNA服务已初始化")
            }
        }
        
        /**
         * 执行设备搜索
         */
        private fun executeDeviceSearch() {
            if (isSearching) {
                return
            }
            
            handleSafely("执行设备搜索") {
                EnhancedThreadManager.d(TAG, "开始执行设备搜索")
                
                isSearching = true
                
                // 确保搜索基础设施初始化
                ensureSearchInfrastructureInitialized()
                
                // 使用RouterImpl执行搜索
                try {
                    val configuration = DefaultUpnpServiceConfiguration.getInstance(appContext)
                    val router = RouterImpl.getInstance(configuration)
                    EnhancedThreadManager.d(TAG, "调用RouterImpl.search()方法")
                    router.search()
                } catch (e: Exception) {
                    EnhancedThreadManager.e(TAG, "RouterImpl搜索失败: ${e.message}", e)
                    isSearching = false
                    throw e
                }
                
                // 延迟重置搜索标志
                coroutineScope.launch {
                    kotlinx.coroutines.delay(30000) // 30秒搜索时间
                    isSearching = false
                }
            }
        }
        
        /**
         * 确保DLNA搜索基础设施初始化
         */
        private fun ensureSearchInfrastructureInitialized() {
            handleSafely("初始化DLNA搜索基础设施") {
                // 初始化RegistryImpl
                RegistryImpl.getInstance()
                
                // 初始化RouterImpl并确保其启动
                val configuration = DefaultUpnpServiceConfiguration.getInstance(appContext)
                val router = RouterImpl.getInstance(configuration)
                
                // 确保路由器启动
                if (!router.isRunning()) {
                    router.startup()
                }
            }
        }
        
        /**
         * 停止设备搜索
         */
        private fun stopDeviceSearch() {
            handleSafely("停止搜索") {
                // 使用RouterImpl停止搜索
                val configuration = DefaultUpnpServiceConfiguration.getInstance(appContext)
                val router = RouterImpl.getInstance(configuration)
                
                if (router.isSearching()) {
                    router.stopSearch()
                }
                
                // 重置搜索标志
                isSearching = false
            }
        }
        
        /**
         * 公共方法：停止搜索设备
         */
        fun stopSearch() {
            if (!isInitialized) {
                EnhancedThreadManager.e(TAG, "未初始化，无法停止搜索")
                return
            }
            
            handleSafely("停止搜索设备") {
                // 直接调用停止搜索方法
                EnhancedThreadManager.d(TAG, "直接调用stopDeviceSearch()方法")
                stopDeviceSearch()
            }
        }
        
        /**
         * 设置设备监听器
         */
        fun setDeviceListener(listener: DeviceListener) {
            deviceListener = listener
            
            handleSafely("设置设备监听器") {
                // 创建RegistryListener适配器
                val registryListenerAdapter = object : RegistryListener {
                    override fun deviceAdded(device: RemoteDevice) {
                        EnhancedThreadManager.d(TAG, "设备添加: ${device.details.friendlyName}")
                        deviceListener?.onDeviceFound(device)
                    }
                    
                    override fun deviceRemoved(device: RemoteDevice) {
                        EnhancedThreadManager.d(TAG, "设备移除: ${device.details.friendlyName}")
                        deviceListener?.onDeviceLost(device)
                    }
                    
                    override fun beforeShutdown(registry: Registry) {
                        // 不需要日志，这是内部事件
                    }
                    
                    override fun afterShutdown() {
                        // 不需要日志，这是内部事件
                    }
                }
                
                // 注册监听器
                val registry = RegistryImpl.getInstance()
                registry.addListener(registryListenerAdapter)
            }
        }
        
        /**
         * 获取多播锁
         */
        private fun acquireMulticastLock(): WifiManager.MulticastLock? {
            return handleSafely("获取多播锁") {
                val context = appContext ?: return@handleSafely null
                
                // 检查WiFi服务
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                
                // 如果WiFi未开启，记录警告并返回null
                if (!wifiManager.isWifiEnabled) {
                    EnhancedThreadManager.w(TAG, "WiFi未开启，无法使用多播")
                    return@handleSafely null
                }
                
                // 创建并获取多播锁
                val multicastLock = wifiManager.createMulticastLock("dlnaMulticastLock")
                if (!multicastLock.isHeld) {
                    multicastLock.acquire()
                    multicastLock
                } else {
                    null
                }
            }
        }
        
        /**
         * 多播锁扩展函数，确保使用后自动释放
         */
        private inline fun <T> WifiManager.MulticastLock?.withLock(block: () -> T): T? {
            if (this == null) return block()
            
            try {
                if (!this.isHeld) {
                    this.acquire()
                }
                return block()
            } finally {
                if (this.isHeld) {
                    try {
                        this.release()
                    } catch (e: Exception) {
                        EnhancedThreadManager.e(TAG, "释放多播锁失败: ${e.message}")
                    }
                }
            }
        }

        /**
         * 开始搜索设备
         */
        fun searchDevices() {
            if (!isInitialized) {
                EnhancedThreadManager.e(TAG, "未初始化，无法搜索设备")
                return
            }
            
            handleSafely("搜索设备") {
                EnhancedThreadManager.d(TAG, "开始搜索DLNA设备")
                
                // 获取多播锁
                val multicastLock = acquireMulticastLock()
                
                // 使用扩展函数确保锁被释放
                multicastLock.withLock {
                    // 直接调用设备搜索方法
                    EnhancedThreadManager.d(TAG, "直接调用executeDeviceSearch()方法")
                    executeDeviceSearch()
                }
            }
        }
        
        /**
         * 播放媒体内容到DLNA设备
         */
        fun playMedia(
            usn: String,
            mediaUrl: String?,
            _metadata: String? = null, // 已废弃参数，不再使用
            videoTitle: String? = null,
            episodeLabel: String? = null,
            positionMs: Long = 0
        ) {
            if (!isInitialized) {
                EnhancedThreadManager.e(TAG, "未初始化，无法播放媒体")
                return
            }
            
            handleSafely("播放媒体") {
                coroutineScope.launch {
                    try {
                        // 获取设备
                        val device = DlnaControllerFactory.getDeviceByUSN(usn)
                        
                        if (device == null) {
                            EnhancedThreadManager.e(TAG, "未找到设备(USN: $usn)，尝试刷新设备列表")
                            // 设备未找到时，尝试重新执行一次设备搜索
                            executeDeviceSearch()
                            
                            // 稍等片刻，给设备搜索一些时间
                            kotlinx.coroutines.delay(2000)
                            
                            // 再次尝试获取设备
                            val retryDevice = DlnaControllerFactory.getDeviceByUSN(usn)
                            if (retryDevice == null) {
                                throw IllegalArgumentException("设备不可用，请重试或检查设备是否在线: $usn")
                            }
                            
                            EnhancedThreadManager.d(TAG, "重新搜索后找到设备: ${retryDevice.details.friendlyName}")
                            
                            // 获取控制器并播放
                            val controller = DlnaControllerFactory.getController(retryDevice)
                            
                            // 执行播放
                            val success = controller.playMediaDirect(
                                mediaUrl ?: "",
                                videoTitle ?: "未知视频",
                                episodeLabel ?: "",
                                positionMs
                            )
                            
                            // 检查结果
                            if (!success) {
                                throw IllegalStateException("播放失败: $usn")
                            }
                        } else {
                            // 获取控制器并播放
                            EnhancedThreadManager.d(TAG, "开始播放: ${device.details.friendlyName}")
                            val controller = DlnaControllerFactory.getController(device)
                                
                            // 执行播放
                            val success = controller.playMediaDirect(
                                mediaUrl ?: "",
                                videoTitle ?: "未知视频",
                                episodeLabel ?: "",
                                positionMs
                            )
                                
                            // 检查结果
                            if (!success) {
                                throw IllegalStateException("播放失败: $usn")
                            }
                        }
                    } catch (e: Exception) {
                        EnhancedThreadManager.e(TAG, "播放失败: ${e.message}", e)
                        DlnaControllerFactory.sendErrorBroadcast(e.message ?: "未知错误")
                    }
                }
            }
        }

        /**
         * 停止设备的当前投屏
         */
        fun stopCasting(usn: String) {
            if (!isInitialized) {
                EnhancedThreadManager.e(TAG, "未初始化，无法停止投屏")
                return
            }
            
            handleSafely("停止投屏") {
                coroutineScope.launch {
                    try {
                        // 获取设备并停止投屏
                        val device = DlnaControllerFactory.getDeviceByUSN(usn)
                            ?: throw IllegalArgumentException("未找到设备: $usn")
                            
                        val controller = DlnaControllerFactory.getController(device)
                        val success = controller.stopDirect()
                            
                        if (!success) {
                            throw IllegalStateException("停止投屏失败: $usn")
                        }
                    } catch (e: Exception) {
                        EnhancedThreadManager.e(TAG, "停止失败: ${e.message}", e)
                        DlnaControllerFactory.sendErrorBroadcast(e.message ?: "未知错误")
                    }
                }
            }
        }
        
        /**
         * 释放所有资源并重置状态
         */
        fun release() {
            if (!isInitialized) {
                return
            }
            
            handleSafely("释放资源") {
                // 先停止所有搜索活动
                if (isSearching) {
                    stopDeviceSearch()
                }
                
                // 取消所有协程
                try {
                    coroutineJob.cancel()
                } catch (e: Exception) {
                    EnhancedThreadManager.e(TAG, "取消协程失败: ${e.message}")
                }
                
                // 重置所有状态
                deviceListener = null
                appContext = null
                DlnaControllerFactory.clearAll()
                isInitialized = false
                isSearching = false
                
                EnhancedThreadManager.d(TAG, "所有资源已释放")
            }
        }
    }
} 