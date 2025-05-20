package com.yinnho.upnpcast.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.yinnho.upnpcast.core.EnhancedThreadManager
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 精简版网络管理器
 * 1. 监控网络状态变化
 * 2. 提供基本的网络工具方法
 */
open class EnhancedNetworkManager internal constructor(protected val context: Context) {
    private val TAG = "EnhancedNetworkManager"

    // 网络状态常量
    enum class NetworkType {
        WIFI, MOBILE, ETHERNET, OTHER, NONE
    }
    
    // 网络状态监听器
    interface NetworkStateListener {
        fun onNetworkAvailable()
        fun onNetworkUnavailable()
        fun onNetworkTypeChanged(isWifi: Boolean)
    }
    
    // 基本配置
    private val CONNECTION_TIMEOUT = 8000 // 毫秒
    
    // 网络状态相关
    private var currentNetworkType = NetworkType.NONE
    private var isNetworkAvailable = false
    
    // 网络状态监听列表
    private val networkStateListeners = CopyOnWriteArrayList<NetworkStateListener>()
    
    // 连接管理器
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkStateReceiver: BroadcastReceiver? = null

    init {
        EnhancedThreadManager.d(TAG, "初始化网络管理器")
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        
        // 初始化网络状态
        updateNetworkStatus()
        
        // 注册网络变化监听
        registerNetworkCallbacks()
    }

    /**
     * 检查设备是否连接到网络
     */
    fun isNetworkAvailable(): Boolean = isNetworkAvailable
    
    /**
     * 检查设备是否连接到WIFI
     */
    fun isConnectedToWifi(): Boolean = currentNetworkType == NetworkType.WIFI
    
    /**
     * 检查设备是否连接到网络
     */
    private fun checkNetworkAvailability(context: Context): Boolean {
        connectivityManager?.let { cm ->
            // 获取当前网络能力
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                
                // 检查是否有互联网连接
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                activeNetworkInfo != null && activeNetworkInfo.isConnected
            }
        }
        
        EnhancedThreadManager.e(TAG, "无法获取ConnectivityManager服务")
        return false
    }

    /**
     * 获取当前网络类型
     */
    fun getCurrentNetworkType(): NetworkType = currentNetworkType
    
    /**
     * 添加网络状态监听器
     */
    fun addNetworkStateListener(listener: NetworkStateListener) {
        if (!networkStateListeners.contains(listener)) {
            networkStateListeners.add(listener)
            
            // 立即通知当前状态
            if (isNetworkAvailable) {
                listener.onNetworkAvailable()
            } else {
                listener.onNetworkUnavailable()
            }
            
            listener.onNetworkTypeChanged(currentNetworkType == NetworkType.WIFI)
        }
    }
    
    /**
     * 移除网络状态监听器
     */
    fun removeNetworkStateListener(listener: NetworkStateListener) {
        networkStateListeners.remove(listener)
    }
    
    /**
     * 注册网络回调
     */
    private fun registerNetworkCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
                
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    EnhancedThreadManager.d(TAG, "网络连接可用")
                    handleNetworkAvailable()
                }
                
                override fun onLost(network: Network) {
                    EnhancedThreadManager.d(TAG, "网络连接丢失")
                    handleNetworkUnavailable()
                }
                
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    updateNetworkType(capabilities)
                }
            }
            
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
        } else {
            // 旧版本使用广播接收器
            networkStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    updateNetworkStatus()
                }
            }
            
            val filter = IntentFilter().apply {
                // Android 7.0及以上使用NetworkCallback代替BroadcastReceiver
                // 为向下兼容Android 6.0及以下版本，保留广播接收器
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    // 使用兼容方式添加网络变化动作
                    @Suppress("DEPRECATION")
                    addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                    EnhancedThreadManager.w(TAG, "使用旧版API监听网络变化，此API已在Android N中弃用")
                }
            }
            
            // 仅在Android N以下版本注册广播接收器
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N && filter.countActions() > 0) {
                context.registerReceiver(networkStateReceiver, filter)
                EnhancedThreadManager.d(TAG, "已注册网络状态广播接收器")
            } else {
                // Android 7.0及以上版本主要依靠NetworkCallback，可以忽略广播接收器
                networkStateReceiver = null
                EnhancedThreadManager.d(TAG, "跳过注册网络状态广播接收器，使用NetworkCallback")
            }
        }
    }
    
    /**
     * 更新网络类型（新API）
     */
    private fun updateNetworkType(capabilities: NetworkCapabilities) {
        val newNetworkType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.OTHER
        }
        
        if (newNetworkType != currentNetworkType) {
            currentNetworkType = newNetworkType
            notifyNetworkTypeChanged()
        }
    }
    
    /**
     * 更新网络状态
     */
    internal fun updateNetworkStatus() {
        val previousState = isNetworkAvailable
        isNetworkAvailable = checkNetworkAvailability(context)
        
        // 检测网络类型
        currentNetworkType = determineNetworkType()
        
        // 网络状态变化处理
        if (isNetworkAvailable != previousState) {
            notifyNetworkStateChanged()
        }
    }
    
    /**
     * 确定当前网络类型
     */
    private fun determineNetworkType(): NetworkType {
        if (!isNetworkAvailable) return NetworkType.NONE
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager?.activeNetwork)
            
            when {
                capabilities == null -> NetworkType.NONE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                else -> NetworkType.OTHER
            }
        } else {
            @Suppress("DEPRECATION")
            when (connectivityManager?.activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                ConnectivityManager.TYPE_MOBILE -> NetworkType.MOBILE
                ConnectivityManager.TYPE_ETHERNET -> NetworkType.ETHERNET
                else -> NetworkType.OTHER
            }
        }
    }
    
    /**
     * 通知网络状态变化
     */
    private fun notifyNetworkStateChanged() {
        if (isNetworkAvailable) {
            notifyNetworkAvailable()
        } else {
            notifyNetworkUnavailable()
        }
    }
    
    /**
     * 处理网络可用
     */
    private fun handleNetworkAvailable() {
        isNetworkAvailable = true
        notifyNetworkAvailable()
    }
    
    /**
     * 处理网络不可用
     */
    private fun handleNetworkUnavailable() {
        isNetworkAvailable = false
        notifyNetworkUnavailable()
    }
    
    /**
     * 通知网络可用
     */
    private fun notifyNetworkAvailable() {
        networkStateListeners.forEach { it.onNetworkAvailable() }
    }
    
    /**
     * 通知网络不可用
     */
    private fun notifyNetworkUnavailable() {
        networkStateListeners.forEach { it.onNetworkUnavailable() }
    }
    
    /**
     * 通知网络类型变化
     */
    private fun notifyNetworkTypeChanged() {
        val isWifi = currentNetworkType == NetworkType.WIFI
        networkStateListeners.forEach { it.onNetworkTypeChanged(isWifi) }
    }
    
    /**
     * 从URL中提取主机和端口
     * @param urlString URL字符串
     * @return 主机和端口的Pair对象
     */
    fun extractHostAndPort(urlString: String): Pair<String, Int> {
        try {
            val url = URL(urlString)
            val host = url.host
            val port = if (url.port == -1) url.defaultPort else url.port
            return Pair(host, port)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "提取主机和端口失败: $urlString", e)
            
            // 尝试手动解析
            val withoutProtocol = urlString.substringAfter("://", urlString)
            val hostPort = withoutProtocol.substringBefore("/", withoutProtocol)
            val parts = hostPort.split(":")
            
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 
                       if (urlString.startsWith("https", ignoreCase = true)) 443 else 80
            
            return Pair(host, port)
        }
    }
    
    /**
     * 检查主机是否可达
     * @param host 主机地址
     * @param port 端口
     * @param timeout 超时时间(毫秒)
     * @return 是否可达
     */
    fun isHostReachable(host: String, port: Int, timeout: Int = CONNECTION_TIMEOUT): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeout)
                true
            }
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "主机连接测试失败: $host:$port", e)
            false
        }
    }
    
    /**
     * 创建HTTP连接
     * @param url URL
     * @param method 请求方法
     * @param connectTimeout 连接超时时间
     * @param readTimeout 读取超时时间
     * @return HttpURLConnection对象
     */
    fun createHttpConnection(
        url: String,
        method: String = "GET",
        connectTimeout: Int = CONNECTION_TIMEOUT,
        readTimeout: Int = CONNECTION_TIMEOUT
    ): HttpURLConnection {
        val urlObj = URL(url)
        return (urlObj.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            this.connectTimeout = connectTimeout
            this.readTimeout = readTimeout
            
            // 设置通用请求头
            setRequestProperty("Connection", "close") // 不使用长连接
            setRequestProperty("User-Agent", "UPnPCast/1.0")
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            // 取消网络回调和广播接收器
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            }
            
            networkStateReceiver?.let { context.unregisterReceiver(it) }
            
            // 清除所有引用
            networkStateListeners.clear()
            networkCallback = null
            networkStateReceiver = null
            
            EnhancedThreadManager.d(TAG, "网络管理器资源已释放")
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "释放网络管理器资源时发生错误", e)
        }
    }

    companion object {
        @Volatile
        private var instance: EnhancedNetworkManager? = null
        
        /**
         * 获取EnhancedNetworkManager实例
         */
        fun getInstance(context: Context): EnhancedNetworkManager {
            return instance ?: synchronized(this) {
                instance ?: EnhancedNetworkManager(context.applicationContext).also { instance = it }
            }
        }
        
        /**
         * 释放单例实例
         */
        fun releaseInstance() {
            synchronized(this) {
                instance?.release()
                instance = null
            }
        }
    }
} 