package com.yinnho.upnpcast.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.yinnho.upnpcast.core.BasicUpnpServiceConfiguration
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.interfaces.AndroidUpnpService
import com.yinnho.upnpcast.interfaces.DatagramProcessor
import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.interfaces.RegistryListener
import com.yinnho.upnpcast.interfaces.Router
import com.yinnho.upnpcast.interfaces.UnifiedDeviceListener
import com.yinnho.upnpcast.interfaces.UpnpServiceConfiguration
import com.yinnho.upnpcast.manager.RegistryImpl
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.network.RouterImpl
import com.yinnho.upnpcast.network.SsdpDatagramProcessor
import com.yinnho.upnpcast.registry.StandardDeviceRegistry
import com.yinnho.upnpcast.service.AndroidUpnpServiceImpl
import java.lang.ref.WeakReference
import java.net.DatagramPacket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * DLNA设备管理器
 * 负责DLNA设备的服务连接和发现
 * 注意：设备管理功能已委托给StandardDeviceRegistry，此类仅负责服务连接和SSDP发现
 */
class DLNADeviceManager internal constructor(context: Context) {
    companion object {
        private const val TAG = "DLNADeviceManager"
        private const val DEFAULT_SEARCH_TIMEOUT = 15000L  // 默认搜索超时时间，15秒

        @Volatile
        private var instance: DLNADeviceManager? = null

        fun getInstance(context: Context): DLNADeviceManager {
            return instance ?: synchronized(this) {
                instance ?: DLNADeviceManager(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): DLNADeviceManager {
            return instance
                ?: throw IllegalStateException("DLNADeviceManager未初始化，请先调用getInstance(Context)方法")
        }
        
        fun releaseInstance() {
            synchronized(this) {
                instance?.release()
                instance = null
            }
        }
    }

    // 使用弱引用存储Context，避免内存泄漏
    private val contextRef = WeakReference(context.applicationContext)

    // 服务和搜索状态
    private var upnpService: AndroidUpnpService? = null
    private var isConnected = false
    private var isSearching = false
    private var isDirectSearching = false
    
    // 监听器和回调
    private val listeners = CopyOnWriteArrayList<Any>() // 存储所有类型的监听器
    private var searchListener: SearchListener? = null
    
    // 搜索和超时
    private var searchFuture: ScheduledFuture<*>? = null
    private val searchExecutor = Executors.newSingleThreadScheduledExecutor()
    private val handler = Handler(Looper.getMainLooper())
    
    // 直接搜索相关组件
    private var directRegistry: Registry? = null
    private var directRouter: Router? = null
    private var ssdpProcessor: SsdpDatagramProcessor? = null

    // 设备注册表
    private val deviceRegistry = StandardDeviceRegistry.getInstance()

    // 注册表监听器 - 用于监听设备变化
    private val registryListener = object : RegistryListener {
        override fun deviceAdded(device: RemoteDevice) {
            threadSafeOperation {
                // 直接交给注册表添加设备，由注册表通知所有监听器
                val isNewDevice = deviceRegistry.addDevice(device)
                if (isNewDevice) {
                    notifyDeviceChange(device, true)
                }
            }
        }

        override fun deviceRemoved(device: RemoteDevice) {
            threadSafeOperation {
                // 直接交给注册表移除设备，由注册表通知所有监听器
                val wasRemoved = deviceRegistry.removeDevice(device)
                if (wasRemoved) {
                    notifyDeviceChange(device, false)
                }
            }
        }

        override fun beforeShutdown(registry: Registry) {
            // do nothing
        }

        override fun afterShutdown() {
            // do nothing
        }
    }

    // 服务连接回调
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "UPnP服务已连接: $name")
            
            if (service is AndroidUpnpService) {
                upnpService = service
                isConnected = true
                
                // 添加监听器
                service.registry.addListener(registryListener)
                
                // 设置默认的搜索超时时间
                service.configuration.setSearchTimeout(DEFAULT_SEARCH_TIMEOUT)
                
                // 通知连接成功
                notifyListeners<DLNAConnectionListener> { it.onConnectionEstablished() }
                
                // 立即搜索设备
                searchDevices()
            } else {
                Log.e(TAG, "服务类型不匹配: ${service?.javaClass?.name}")
                notifyListeners<DLNAConnectionListener> {
                    it.onConnectionFailed(IllegalStateException("服务类型不匹配")) 
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "UPnP服务已断开: $name")
            upnpService = null
            isConnected = false
            notifyListeners<DLNAConnectionListener> { it.onConnectionLost() }
        }
    }

    /**
     * 初始化 - 连接UPnP服务
     */
    fun initialize() {
        if (isConnected) {
            Log.d(TAG, "已连接服务，跳过初始化")
            return
        }

        val context = contextRef.get() ?: return
        
        try {
            // 绑定UPnP服务
            val intent = Intent(context, AndroidUpnpServiceImpl::class.java)
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            
            if (!bound) {
                Log.e(TAG, "绑定UPnP服务失败")
                notifyListeners<DLNAConnectionListener> {
                    it.onConnectionFailed(IllegalStateException("绑定服务失败")) 
                }
                initDirectSearch()
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败: ${e.message}", e)
            notifyListeners<DLNAConnectionListener> { it.onConnectionFailed(e) }
            initDirectSearch()
        }
    }

    /**
     * 初始化直接搜索
     * 不依赖UPnP服务，直接使用SSDP搜索设备
     */
    private fun initDirectSearch() {
        if (isDirectSearching) return

        Log.d(TAG, "初始化直接搜索")
        try {
            // 获取上下文
            val context = contextRef.get() ?: return
            
            // 创建注册表、配置和路由器
            directRegistry = RegistryImpl.getInstance()
            
            // 使用上下文初始化配置
            val configuration = BasicUpnpServiceConfiguration(context)
            
            // 创建实现了UpnpServiceConfiguration接口的配置对象
            val routerConfig = object : UpnpServiceConfiguration {
                override val networkAddress: String = "0.0.0.0"
                override val streamListenPort: Int = 8085
                override val multicastPort: Int = 1900
                override val executorService = configuration.createDefaultExecutor()
                override val multicastInterface = null
                override val multicastAddress: String = "239.255.255.250"
                override val searchTimeout: Long = configuration.getSearchTimeout().toLong()
                override val maxRetries: Int = 3
                override val retryInterval: Long = 500
                override val datagramProcessor: DatagramProcessor = object : DatagramProcessor {
                    override fun sendSearchRequest(multicastAddress: String, multicastPort: Int) {
                        // 简化实现
                        Log.d(TAG, "发送搜索请求到 $multicastAddress:$multicastPort")
                    }
                    
                    override fun process(receivedOnAddress: InetAddress, packet: DatagramPacket) {
                        // 简化实现
                        Log.d(TAG, "处理来自 ${packet.address} 的数据包")
                    }
                    
                    override fun send(packet: DatagramPacket) {
                        // 简化实现
                        Log.d(TAG, "发送数据包到 ${packet.address}")
                    }
                    
                    override fun isProcessingControl(packet: DatagramPacket): Boolean = true
                    
                    override fun isProcessingEvent(packet: DatagramPacket): Boolean = true
                    
                    override fun shutdown() {
                        Log.d(TAG, "关闭数据包处理器")
                    }
                    
                    override fun isShutDown(): Boolean = false
                }
                
                override fun shutdown() {
                    executorService.shutdown()
                }
                
                /**
                 * 设置搜索超时时间
                 * @param timeoutMs 超时时间（毫秒）
                 */
                override fun setSearchTimeout(timeoutMs: Long) {
                    Log.d(TAG, "设置搜索超时时间: ${timeoutMs}ms")
                    // 此处为匿名对象实现，实际上不需要存储值，因为不会被调用
                }
            }
            
            // 使用符合接口的配置对象
            directRouter = RouterImpl.getInstance(routerConfig)
            
            // 创建SSDP处理器
            ssdpProcessor = SsdpDatagramProcessor.getInstance(
                directRegistry!!,
                Executors.newCachedThreadPool()
            )
            
            // 添加监听器
            ssdpProcessor?.setGlobalListener(registryListener)
            
            isDirectSearching = true
            Log.d(TAG, "直接搜索初始化完成")
            
            // 开始搜索
            searchDevices()
        } catch (e: Exception) {
            Log.e(TAG, "初始化直接搜索失败: ${e.message}", e)
            isDirectSearching = false
        }
    }

    /**
     * 搜索设备
     */
    fun searchDevices() {
        if (isSearching) return

        Log.d(TAG, "开始搜索设备")
        isSearching = true
        searchListener?.onSearchStarted()
        
        when {
            isConnected && upnpService != null -> upnpService?.controlPoint?.search()
            isDirectSearching && ssdpProcessor != null -> ssdpProcessor?.sendSearchRequest("239.255.255.250", 1900)
            else -> {
                Log.e(TAG, "没有可用的搜索方法")
                isSearching = false
                searchListener?.onSearchFinished(emptyList())
                return
            }
        }
        
        // 设置搜索超时
        searchFuture?.cancel(false)
        // 获取当前的搜索超时时间
        val searchTimeoutMs = upnpService?.configuration?.searchTimeout ?: DEFAULT_SEARCH_TIMEOUT
        searchFuture = searchExecutor.schedule({ finishSearch() }, searchTimeoutMs, TimeUnit.MILLISECONDS)
    }

    /**
     * 结束搜索
     */
    private fun finishSearch() {
        if (!isSearching) return
        
        isSearching = false
        val devices = deviceRegistry.getAllDevices()
        
        handler.post { 
            searchListener?.onSearchFinished(devices) 
        }
        
        Log.d(TAG, "搜索已完成，找到${devices.size}个设备")
    }

    /**
     * 在主线程安全执行操作
     */
    private inline fun threadSafeOperation(crossinline action: () -> Unit) {
        handler.post {
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "操作执行失败: ${e.message}", e)
            }
        }
    }

    /**
     * 通知设备变化
     */
    private fun notifyDeviceChange(device: RemoteDevice, isAdded: Boolean) {
        val deviceName = device.details.friendlyName
        Log.d(TAG, "设备${if (isAdded) "添加" else "移除"}: $deviceName")
        
        listeners.forEach { listener ->
            try {
                when (listener) {
                    is UnifiedDeviceListener -> {
                        if (isAdded) listener.onDeviceAdded(device) else listener.onDeviceRemoved(device)
                    }
                    is LegacyDeviceListener -> {
                        if (isAdded) listener.onDeviceAdded(device) else listener.onDeviceRemoved(device)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "通知设备${if (isAdded) "添加" else "移除"}失败: ${e.message}", e)
            }
        }
    }

    /**
     * 通知指定类型的监听器
     */
    private inline fun <reified T> notifyListeners(action: (T) -> Unit) {
        listeners.filterIsInstance<T>().forEach { listener ->
            try {
                action(listener)
            } catch (e: Exception) {
                Log.e(TAG, "通知监听器失败: ${e.message}", e)
            }
        }
    }

    /**
     * 获取所有设备
     * 委托给设备注册表
     */
    fun getAllDevices(): List<RemoteDevice> = deviceRegistry.getAllDevices()

    /**
     * 根据ID获取设备
     * 委托给设备注册表
     */
    fun getDeviceById(deviceId: String): RemoteDevice? = deviceRegistry.getDeviceById(deviceId)

    /**
     * 添加监听器
     */
    fun <T> addListener(listener: T) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener)
            Log.d(TAG, "已添加监听器: ${listener.javaClass.simpleName}")
        }
    }

    /**
     * 移除监听器
     */
    fun <T> removeListener(listener: T) {
        if (listener != null && listeners.remove(listener)) {
            Log.d(TAG, "已移除监听器: ${listener.javaClass.simpleName}")
        }
    }

    /**
     * 设置搜索监听器
     */
    fun setSearchListener(listener: SearchListener?) {
        searchListener = listener
    }

    /**
     * 设置搜索超时时间
     * @param timeoutMs 超时时间（毫秒）
     */
    fun setSearchTimeout(timeoutMs: Long) {
        if (timeoutMs <= 0) {
            Log.w(TAG, "无效的搜索超时时间: $timeoutMs ms")
            return
        }
        
        Log.d(TAG, "设置搜索超时时间: $timeoutMs ms")
        
        // 如果已连接服务，则设置其超时时间
        if (isConnected && upnpService != null) {
            upnpService?.configuration?.setSearchTimeout(timeoutMs)
        }
        
        // 如果使用直接搜索，则设置直接搜索的超时时间
        if (isDirectSearching && directRouter != null) {
            directRouter?.setSearchTimeout(timeoutMs)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "释放资源")
        
        // 取消搜索
        searchFuture?.cancel(true)
        searchFuture = null
        isSearching = false
        
        // 关闭直接搜索
        if (isDirectSearching) {
            ssdpProcessor?.shutdown()
            ssdpProcessor = null
            directRouter = null
            directRegistry = null
            isDirectSearching = false
        }
        
        // 解绑服务
        contextRef.get()?.let { context ->
            if (isConnected) {
                try {
                    context.unbindService(serviceConnection)
                } catch (e: Exception) {
                    Log.e(TAG, "解绑UPnP服务失败: ${e.message}", e)
                }
            }
        }
        
        isConnected = false
        upnpService = null
        
        // 清空监听器
        listeners.clear()
        searchListener = null
        
        Log.d(TAG, "资源已释放")
    }

    /**
     * 连接监听器接口
     */
    interface DLNAConnectionListener {
        fun onConnectionEstablished()
        fun onConnectionFailed(exception: Exception)
        fun onConnectionLost()
    }

    /**
     * 搜索监听器接口
     */
    interface SearchListener {
        fun onSearchStarted()
        fun onSearchFinished(devices: List<RemoteDevice>)
    }

    /**
     * 旧版设备监听器接口
     */
    interface LegacyDeviceListener {
        fun onDeviceAdded(device: RemoteDevice)
        fun onDeviceRemoved(device: RemoteDevice)
    }

    /**
     * 设备回调接口
     */
    interface DeviceCallback {
        fun onDevicesUpdated(devices: List<RemoteDevice>)
    }
}