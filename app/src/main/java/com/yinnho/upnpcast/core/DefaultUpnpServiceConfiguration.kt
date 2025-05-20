package com.yinnho.upnpcast.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yinnho.upnpcast.interfaces.DatagramProcessor
import com.yinnho.upnpcast.interfaces.UpnpServiceConfiguration
import com.yinnho.upnpcast.manager.RegistryImpl
import com.yinnho.upnpcast.network.SsdpDatagramProcessor
import com.yinnho.upnpcast.model.RemoteDevice
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 默认UPnP服务配置实现
 * 提供UPnP服务所需的基本配置
 * 单例模式实现，避免重复初始化
 */
class DefaultUpnpServiceConfiguration private constructor(private val context: Context? = null) :
    UpnpServiceConfiguration {
    private val TAG = "DefaultUpnpServiceConfiguration"

    // 使用AtomicReference保存当前网络地址，便于动态更新
    private val currentNetworkAddress = AtomicReference<String>(null)
    
    // 缓存多播网络接口
    private val cachedMulticastInterface = AtomicReference<NetworkInterface?>(null)

    // 注册表实例
    private val registryImpl = RegistryImpl.getInstance()

    override val networkAddress: String
        get() = currentNetworkAddress.get() ?: getLocalIPAddress(false)

    override val streamListenPort: Int = 0
    override val multicastPort: Int = 1900
    override val executorService: ExecutorService = Executors.newCachedThreadPool()
    override val multicastInterface: NetworkInterface?
        get() {
            // 优先使用缓存的网络接口
            return cachedMulticastInterface.get() ?: getBestMulticastInterface().also {
                cachedMulticastInterface.set(it)
            }
        }
    override val multicastAddress: String = "239.255.255.250"
    
    // 使用原子引用来存储可变的搜索超时时间
    private val timeoutValue = AtomicReference<Long>(45000L)
    
    override val searchTimeout: Long
        get() = timeoutValue.get()
        
    override val maxRetries: Int = 5
    override val retryInterval: Long = 1000L
    
    /**
     * 设置搜索超时时间
     * @param timeoutMs 超时时间（毫秒）
     */
    override fun setSearchTimeout(timeoutMs: Long) {
        Log.d(TAG, "设置搜索超时时间: ${timeoutMs}ms")
        timeoutValue.set(timeoutMs)
    }
    
    override val datagramProcessor: DatagramProcessor by lazy {
        SsdpDatagramProcessor.getInstance(registryImpl, syncProtocolExecutor)
    }

    private val syncProtocolExecutor: Executor = Executors.newSingleThreadExecutor()
    private var isShutdown = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // 重试策略相关参数
    private val maxConnectionRetries = 3
    private val retryBackoffMultiplier = 1.5f

    companion object {
        @Volatile
        private var INSTANCE: DefaultUpnpServiceConfiguration? = null
        private val instanceLock = Any()
        
        /**
         * 获取DefaultUpnpServiceConfiguration单例
         * @param context 可选的上下文参数
         * @return DefaultUpnpServiceConfiguration实例
         */
        fun getInstance(context: Context? = null): DefaultUpnpServiceConfiguration {
            return INSTANCE ?: synchronized(instanceLock) {
                INSTANCE ?: DefaultUpnpServiceConfiguration(context).also { INSTANCE = it }
            }
        }
    }

    init {
        // 初始化网络地址
        currentNetworkAddress.set(getLocalIPAddress(true))

        // 注册网络变化监听，只在context非空时注册
        context?.let { ctx ->
            registerNetworkCallback(ctx)
        }
    }

    fun isRunning(): Boolean = !isShutdown

    override fun shutdown() {
        Log.d(TAG, "关闭UPnP服务配置")
        if (isShutdown) return
        isShutdown = true

        // 注销网络状态监听
        unregisterNetworkCallback()

        try {
            executorService.shutdown()
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }

        try {
            (syncProtocolExecutor as? ExecutorService)?.let { executor ->
                executor.shutdown()
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }
            }
        } catch (e: InterruptedException) {
            (syncProtocolExecutor as? ExecutorService)?.shutdownNow()
            Thread.currentThread().interrupt()
        }

        (datagramProcessor as? SsdpDatagramProcessor)?.shutdown()
        
        // 清除缓存和引用
        currentNetworkAddress.set(null)
        cachedMulticastInterface.set(null)
        
        // 设置单例实例为null，允许垃圾回收
        synchronized(instanceLock) {
            if (INSTANCE === this) {
                INSTANCE = null
            }
        }
    }

    /**
     * 注册网络状态变化监听
     */
    private fun registerNetworkCallback(context: Context) {
        try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                Log.w(TAG, "无法获取ConnectivityManager服务")
                return
            }

            // 如果已经有回调注册，先注销
            unregisterNetworkCallback()

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "网络连接可用，更新网络地址")
                    updateNetworkAddress()
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "网络连接断开，更新网络地址")
                    updateNetworkAddress()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    val hasCellular =
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    val hasEthernet =
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

                    Log.d(
                        TAG,
                        "网络能力变化: WiFi=$hasWifi, 蜂窝=$hasCellular, 以太网=$hasEthernet"
                    )
                    updateNetworkAddress()
                }
            }

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
            Log.d(TAG, "网络状态监听注册成功")
        } catch (e: Exception) {
            Log.e(TAG, "注册网络状态监听失败", e)
        }
    }

    /**
     * 注销网络状态监听
     */
    private fun unregisterNetworkCallback() {
        if (context == null || networkCallback == null) return

        try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager?.unregisterNetworkCallback(networkCallback!!)
            networkCallback = null
            Log.d(TAG, "网络状态监听注销成功")
        } catch (e: Exception) {
            Log.e(TAG, "注销网络状态监听失败", e)
            // 确保回调引用被清除，即使注销失败
            networkCallback = null
        }
    }

    /**
     * 更新网络地址
     */
    private fun updateNetworkAddress() {
        mainHandler.post {
            try {
                val newAddress = getLocalIPAddress(false)
                val oldAddress = currentNetworkAddress.getAndSet(newAddress)

                if (oldAddress != newAddress) {
                    Log.d(TAG, "网络地址已更新: $oldAddress -> $newAddress")
                    // 当网络地址变化时，同时刷新多播接口缓存
                    cachedMulticastInterface.set(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新网络地址失败", e)
            }
        }
    }

    /**
     * 获取最佳的多播网络接口
     */
    private fun getBestMulticastInterface(): NetworkInterface? {
        try {
            // 检查缓存
            cachedMulticastInterface.get()?.let {
                if (it.isUp && it.supportsMulticast()) {
                    return it
                }
            }
            
            // 获取并过滤网络接口
            val networkInterfaces = getFilteredNetworkInterfaces { it.isUp && !it.isLoopback && it.supportsMulticast() }
            
            if (networkInterfaces.isEmpty()) {
                Log.w(TAG, "未找到可用的多播网络接口")
                return null
            }

            // 选择最佳接口
            val bestInterface = networkInterfaces.firstOrNull()
            if (bestInterface != null) {
                Log.d(TAG, "选择最佳多播网络接口: ${bestInterface.displayName}, 索引: ${bestInterface.index}")
                return bestInterface
            }

            Log.w(TAG, "无法找到任何多播网络接口")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "获取最佳多播网络接口失败", e)
            return null
        }
    }

    /**
     * 获取本地IP地址
     * @param verbose 是否输出详细日志
     * @return 本地IP地址字符串
     */
    private fun getLocalIPAddress(verbose: Boolean): String {
        try {
            if (verbose) {
                Log.d(TAG, "开始获取本地IP地址")
            }
            
            // 检查当前缓存
            currentNetworkAddress.get()?.let { cachedAddress ->
                if (cachedAddress != "127.0.0.1" && cachedAddress.isNotEmpty()) {
                    return cachedAddress
                }
            }

            // 获取并过滤网络接口
            val networkInterfaces = getFilteredNetworkInterfaces { it.isUp && !it.isLoopback }
            
            if (networkInterfaces.isEmpty()) {
                Log.w(TAG, "未找到可用的网络接口")
                return "127.0.0.1"
            }

            // 记录所有可用的IP地址
            val availableAddresses = collectAvailableAddresses(networkInterfaces, verbose)

            if (availableAddresses.isEmpty()) {
                Log.w(TAG, "未找到可用的IP地址")
                return "127.0.0.1"
            }

            // 选择地址：优先WiFi > 以太网 > 其他
            val selectedAddress = selectBestAddress(availableAddresses, verbose)
            return selectedAddress ?: "127.0.0.1"
        } catch (e: Exception) {
            Log.e(TAG, "获取本地IP地址失败", e)
            // 如果无法获取IP地址，返回本地回环地址
            return "127.0.0.1"
        }
    }
    
    /**
     * 根据给定的过滤条件获取排序后的网络接口列表
     */
    private fun getFilteredNetworkInterfaces(filter: (NetworkInterface) -> Boolean): List<NetworkInterface> {
        return NetworkInterface.getNetworkInterfaces().toList()
            .filter(filter)
            .sortedWith(compareBy<NetworkInterface> {
                // 首先按照接口类型排序（WiFi > 以太网 > 其他）
                when {
                    it.displayName.contains("wlan", ignoreCase = true) ||
                            it.name.contains("wlan", ignoreCase = true) ||
                            it.displayName.contains("wifi", ignoreCase = true) ||
                            it.name.contains("wifi", ignoreCase = true) -> 0

                    it.displayName.contains("eth", ignoreCase = true) ||
                            it.name.contains("eth", ignoreCase = true) -> 1

                    else -> 2
                }
            }.thenBy {
                // 然后按照接口索引排序
                it.index
            })
    }
    
    /**
     * 收集网络接口的可用地址
     */
    private fun collectAvailableAddresses(
        networkInterfaces: List<NetworkInterface>,
        verbose: Boolean
    ): List<Pair<NetworkInterface, InetAddress>> {
        val availableAddresses = mutableListOf<Pair<NetworkInterface, InetAddress>>()

        for (networkInterface in networkInterfaces) {
            val addresses = networkInterface.inetAddresses.toList()
                .filter { !it.isLoopbackAddress && it.hostAddress?.indexOf(':') ?: -1 < 0 }

            for (address in addresses) {
                availableAddresses.add(networkInterface to address)
                if (verbose) {
                    Log.d(TAG, "找到网络接口: ${networkInterface.displayName}, 地址: ${address.hostAddress ?: "未知"}")
                }
            }
        }
        
        return availableAddresses
    }
    
    /**
     * 从可用地址中选择最佳地址
     */
    private fun selectBestAddress(
        availableAddresses: List<Pair<NetworkInterface, InetAddress>>,
        verbose: Boolean
    ): String? {
        // 优先选择WiFi网络接口
        val wifiAddress = availableAddresses.firstOrNull {
            it.first.displayName.contains("wlan", ignoreCase = true) ||
                    it.first.name.contains("wlan", ignoreCase = true) ||
                    it.first.displayName.contains("wifi", ignoreCase = true) ||
                    it.first.name.contains("wifi", ignoreCase = true)
        }

        if (wifiAddress != null) {
            val hostAddress = wifiAddress.second.hostAddress ?: ""
            if (verbose) {
                Log.d(TAG, "选择WiFi网络接口: ${wifiAddress.first.displayName}, 地址: $hostAddress")
            }
            return hostAddress
        }

        // 其次选择以太网接口
        val ethernetAddress = availableAddresses.firstOrNull {
            it.first.displayName.contains("eth", ignoreCase = true) ||
                    it.first.name.contains("eth", ignoreCase = true)
        }

        if (ethernetAddress != null) {
            val hostAddress = ethernetAddress.second.hostAddress ?: ""
            if (verbose) {
                Log.d(TAG, "选择以太网接口: ${ethernetAddress.first.displayName}, 地址: $hostAddress")
            }
            return hostAddress
        }

        // 最后选择第一个可用的接口
        val firstAddress = availableAddresses.firstOrNull()
        if (firstAddress != null) {
            val hostAddress = firstAddress.second.hostAddress ?: ""
            if (verbose) {
                Log.d(TAG, "选择默认网络接口: ${firstAddress.first.displayName}, 地址: $hostAddress")
            }
            return hostAddress
        }
        
        return null
    }

    /**
     * 获取设备详细信息
     */
    private fun getDeviceDetails(device: RemoteDevice): String {
        val friendlyName =
            device.details.friendlyName ?: "Unknown Device"
        val manufacturer =
            device.details.manufacturerInfo?.name ?: "Unknown Manufacturer"
        return "$friendlyName by $manufacturer"
    }
}