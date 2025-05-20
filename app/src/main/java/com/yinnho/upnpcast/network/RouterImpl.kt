package com.yinnho.upnpcast.network

import android.util.Log
import com.yinnho.upnpcast.core.LogManager
import com.yinnho.upnpcast.interfaces.ControlPoint
import com.yinnho.upnpcast.interfaces.IncomingDatagramMessage
import com.yinnho.upnpcast.interfaces.Router
import com.yinnho.upnpcast.interfaces.UpnpRequest
import com.yinnho.upnpcast.interfaces.UpnpResponse
import com.yinnho.upnpcast.interfaces.UpnpServiceConfiguration
import com.yinnho.upnpcast.model.RemoteDevice
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 路由器实现类 (精简版)
 * 负责处理DLNA消息的路由，处理设备发现逻辑
 */
class RouterImpl private constructor(private val configuration: UpnpServiceConfiguration) : Router, ControlPoint {
    private val TAG = "RouterImpl"
    private val running = AtomicBoolean(false)
    private var executor: ScheduledExecutorService? = null
    private var multicastListenerExecutor: ScheduledExecutorService? = null

    // 多播监听相关
    private var multicastSocket: MulticastSocket? = null
    private val isListening = AtomicBoolean(false)

    // 搜索控制
    private val searchActive = AtomicBoolean(false)
    private val messageProcessingEnabled = AtomicBoolean(true)

    // SSDP协议常量
    private val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
    private val SSDP_PORT = 1900
    private val TTL = 4

    // 已发现设备缓存 - 用于去重
    private val discoveredDevices = ConcurrentHashMap<String, Long>()
    private val DEVICE_CACHE_EXPIRE_MS = 5000L // 设备缓存5秒过期
    
    // ControlPoint接口相关成员
    private var currentDevice: RemoteDevice? = null
    private var connected = AtomicBoolean(false)
    private val devices = ConcurrentHashMap<String, RemoteDevice>()

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: RouterImpl? = null

        // 获取实例的工厂方法
        fun getInstance(configuration: UpnpServiceConfiguration): RouterImpl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RouterImpl(configuration).also { INSTANCE = it }
            }
        }
    }

    // ControlPoint接口实现
    override fun getDevices(): List<RemoteDevice> = devices.values.toList()
    
    override fun connect(device: RemoteDevice) {
        currentDevice = device
        connected.set(true)
    }
    
    override fun disconnect() {
        currentDevice = null
        connected.set(false)
    }
    
    override fun isConnected(): Boolean = connected.get()
    
    override fun getCurrentDevice(): RemoteDevice? = currentDevice
    
    override fun search() = sendSearchMessage()
    
    override fun stopSearch() = stopSearchInternal()
    
    override fun shutdown() = shutdownInternal()
    
    override fun isSearching(): Boolean = searchActive.get()
    
    /**
     * 设置搜索超时时间
     * @param timeoutMs 超时时间（毫秒）
     */
    override fun setSearchTimeout(timeoutMs: Long) {
        LogManager.d(TAG, "设置搜索超时时间: ${timeoutMs}ms")
        // 将超时时间传递给配置
        configuration.setSearchTimeout(timeoutMs)
    }

    override fun startup() {
        if (running.getAndSet(true)) return
        
        executor = Executors.newScheduledThreadPool(1)
        multicastListenerExecutor = Executors.newSingleThreadScheduledExecutor()
        
        // 确保消息处理被启用
        messageProcessingEnabled.set(true)
    }

    /**
     * 启动多播监听，接收设备响应
     */
    private fun startMulticastListener() {
        if (isListening.getAndSet(true)) return
        
        multicastListenerExecutor?.execute {
            try {
                // 创建并配置多播套接字
                val socket = MulticastSocket(SSDP_PORT).apply {
                    reuseAddress = true
                    soTimeout = 500  // 设置较短的超时以便快速响应停止请求
                }
                
                synchronized(this@RouterImpl) { multicastSocket = socket }
                
                // 尝试加入多播组
                val group = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                val networkInterfaces = getAvailableNetworkInterfaces()
                
                try {
                    if (networkInterfaces.isNotEmpty()) {
                        socket.joinGroup(InetSocketAddress(group, SSDP_PORT), networkInterfaces[0])
                    } else {
                        socket.joinGroup(group)
                    }
                    
                    // 开始数据包接收循环
                    receivePackets(socket)
                } catch (e: Exception) {
                    LogManager.e(TAG, "加入多播组失败", e)
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "多播监听执行出错", e)
            } finally {
                cleanupMulticastSocket()
                isListening.set(false)
            }
        }
    }
    
    /**
     * 接收并处理数据包
     */
    private fun receivePackets(socket: MulticastSocket) {
        val buffer = ByteArray(8192)
        
        while (!Thread.currentThread().isInterrupted && isListening.get() && searchActive.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                
                // 检查状态确认是否继续处理
                if (isListening.get() && searchActive.get() && messageProcessingEnabled.get()) {
                    processReceivedPacket(packet)
                }
            } catch (e: SocketTimeoutException) {
                // 超时是正常的，继续监听
            } catch (e: SocketException) {
                // 套接字异常，可能是套接字已关闭
                break
            } catch (e: Exception) {
                // 其他错误，延迟短暂时间后继续
                try {
                    Thread.sleep(100)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }
    
    /**
     * 清理多播套接字资源
     */
    private fun cleanupMulticastSocket() {
        synchronized(this@RouterImpl) {
            try {
                multicastSocket?.let { 
                    if (!it.isClosed) it.close()
                    multicastSocket = null
                }
            } catch (e: Exception) {
                LogManager.w(TAG, "清理多播资源时出错", e)
            }
        }
    }

    override fun isRunning(): Boolean = running.get()

    override fun enable() {
        if (!running.get()) startup()
    }

    override fun disable() {
        if (running.get()) shutdownInternal()
    }

    override fun enableMulticastReceive() = enable()

    override fun disableMulticastReceive() {
        // 设置标志，使监听循环能够快速退出
        isListening.set(false)
        searchActive.set(false)
        
        synchronized(this) {
            try {
                multicastSocket?.let { 
                    if (!it.isClosed) it.close()
                    multicastSocket = null
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "禁用多播接收时出错", e)
            }
        }
    }

    override fun enableMulticastSend() = enable()

    /**
     * 发送多播消息
     */
    override fun sendMulticast(message: ByteArray) {
        try {
            val groupAddress = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
            val packet = DatagramPacket(message, message.size, groupAddress, SSDP_PORT)
            multicastSocket?.send(packet)
        } catch (e: Exception) {
            LogManager.e(TAG, "发送多播消息失败", e)
        }
    }

    override fun sendUnicast(message: String, address: String, port: Int) {
        if (!running.get()) startup()
        
        executor?.execute {
            try {
                val messageBytes = message.toByteArray()
                val packet = DatagramPacket(
                    messageBytes,
                    messageBytes.size,
                    InetAddress.getByName(address),
                    port
                )
                
                DatagramSocket().use { it.send(packet) }
            } catch (e: Exception) {
                LogManager.e(TAG, "发送单播消息失败", e)
            }
        }
    }

    /**
     * 解析消息头部
     */
    private fun parseHeaders(message: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        
        try {
            message.split("\r\n")
                .filter { it.contains(":") }
                .forEach { line ->
                    try {
                        val colonIndex = line.indexOf(":")
                        if (colonIndex > 0) {
                            val key = line.substring(0, colonIndex).trim().uppercase()
                            val value = line.substring(colonIndex + 1).trim()
                            headers[key] = value
                        }
                    } catch (e: Exception) {
                        // 单行解析错误不影响整体
                    }
                }
        } catch (e: Exception) {
            LogManager.e(TAG, "解析消息头部失败", e)
        }
        
        return headers
    }

    /**
     * 获取可用的网络接口
     */
    private fun getAvailableNetworkInterfaces(): List<NetworkInterface> {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter {
                    try {
                        it.isUp && !it.isLoopback && it.supportsMulticast()
                    } catch (e: SocketException) {
                        false
                    }
                }
        } catch (e: SocketException) {
            emptyList()
        }
    }

    override fun receivedRequest(msg: IncomingDatagramMessage<UpnpRequest>) {
        if (!messageProcessingEnabled.get()) return
        
        try {
            val request = msg.operation
            if (request.method.equals("NOTIFY", ignoreCase = true)) {
                configuration.datagramProcessor.process(msg.senderAddress, msg.datagramPacket)
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "处理请求消息时出错", e)
        }
    }

    override fun receivedResponse(msg: IncomingDatagramMessage<UpnpResponse>) {
        if (!messageProcessingEnabled.get()) return
        
        try {
            // 处理HTTP 200 OK响应（设备响应）
            val response = msg.operation
            if (response.statusCode == 200) {
                configuration.datagramProcessor.process(msg.senderAddress, msg.datagramPacket)
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "处理响应消息时出错", e)
        }
    }

    /**
     * 处理接收到的数据包
     */
    private fun processReceivedPacket(packet: DatagramPacket) {
        if (!messageProcessingEnabled.get()) return
        
        try {
            val receivedOnAddress = packet.address
            val message = String(packet.data, 0, packet.length)
            
            // 提取设备ID用于去重
            val usn = extractDeviceId(message)
            if (usn.isNotEmpty()) {
                // 检查是否是短时间内重复的设备
                val now = System.currentTimeMillis()
                val lastSeen = discoveredDevices[usn]
                
                if (lastSeen != null && now - lastSeen < DEVICE_CACHE_EXPIRE_MS) {
                    // 忽略短时间内的重复设备消息
                    return
                }
                
                // 更新设备时间戳
                discoveredDevices[usn] = now
            }
            
            // 根据消息类型处理
            if (message.contains("HTTP/1.1 200 OK") || 
                (message.contains("LOCATION:", true) && message.contains("USN:", true)) ||
                message.startsWith("NOTIFY")) {
                configuration.datagramProcessor.process(receivedOnAddress, packet)
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "处理数据包时出错", e)
        }
    }
    
    /**
     * 从消息中提取设备唯一标识符
     */
    private fun extractDeviceId(message: String): String {
        val headers = parseHeaders(message)
        
        // 尝试从USN获取设备标识
        val usn = headers["USN"] ?: headers["NT"] ?: ""
        if (usn.isNotEmpty()) return usn
        
        // 如果没有USN，尝试用其他字段组合生成标识
        val location = headers["LOCATION"] ?: ""
        val server = headers["SERVER"] ?: ""
        
        return if (location.isNotEmpty() && server.isNotEmpty()) {
            "$location#$server"
        } else {
            // 没有合适的标识字段
            ""
        }
    }

    /**
     * 启动设备搜索
     */
    fun sendSearchMessage() {
        if (!running.get()) startup()
        
        // 清理过期设备缓存
        cleanExpiredDevices()
        
        // 启用搜索状态
        searchActive.set(true)
        messageProcessingEnabled.set(true)
        
        // 确保监听器运行
        if (!isListening.get()) startMulticastListener()
        
        // 发送搜索消息
        sendMulticast(buildSearchMessage().toByteArray())
        
        // 设置搜索超时
        executor?.schedule({
            if (searchActive.get()) {
                searchActive.set(false)
            }
        }, configuration.searchTimeout, TimeUnit.MILLISECONDS)
    }
    
    /**
     * 清理过期的设备记录
     */
    private fun cleanExpiredDevices() {
        val now = System.currentTimeMillis()
        val expiredKeys = discoveredDevices.entries
            .filter { now - it.value > DEVICE_CACHE_EXPIRE_MS }
            .map { it.key }
            
        expiredKeys.forEach { discoveredDevices.remove(it) }
    }

    /**
     * 构建标准的SSDP搜索消息
     */
    private fun buildSearchMessage(): String {
        return "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 5\r\n" +
                "ST: ssdp:all\r\n" +
                "\r\n"
    }

    /**
     * 关闭路由器
     */
    private fun shutdownInternal() {
        // 停止搜索
        stopSearchInternal()
        
        // 设置运行状态为false
        running.set(false)
        
        // 确保套接字已关闭
        cleanupMulticastSocket()
        
        // 关闭执行器
        executor?.shutdownNow()
        multicastListenerExecutor?.shutdownNow()
        
        // 清理资源
        discoveredDevices.clear()
    }
    
    /**
     * 停止设备搜索
     */
    private fun stopSearchInternal() {
        // 禁用搜索活动标志
        searchActive.set(false)
        
        // 关闭多播监听
        disableMulticastReceive()
    }
} 