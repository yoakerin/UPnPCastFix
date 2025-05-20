package com.yinnho.upnpcast.network

import android.util.Log
import com.yinnho.upnpcast.interfaces.DatagramProcessor
import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.interfaces.RegistryListener
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.manager.RegistryImpl
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SSDP数据包处理器 - 负责DLNA设备发现的网络通信和Socket管理
 * 协议解析和设备处理已转移到SsdpProtocolHandler
 */
class SsdpDatagramProcessor private constructor(
    private val registry: Registry,
    private val executor: Executor
) : DatagramProcessor {
    private val TAG = "SsdpProcessor"

    companion object {
        // 网络配置
        private const val MULTICAST_TTL = 4
        private const val DEFAULT_MULTICAST_ADDRESS = "239.255.255.250"
        private const val DEFAULT_MULTICAST_PORT = 1900
        
        // 搜索相关
        private val SEARCH_TARGETS = listOf(
            "ssdp:all",                                   // 所有设备
            "upnp:rootdevice",                            // 根设备
            "urn:schemas-upnp-org:device:MediaRenderer:1" // 媒体渲染器
        )

        // 单例实例
        @Volatile
        private var INSTANCE: SsdpDatagramProcessor? = null

        // 获取实例的工厂方法
        fun getInstance(registry: Registry, executor: Executor): SsdpDatagramProcessor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SsdpDatagramProcessor(registry, executor).also { INSTANCE = it }
            }
        }
    }

    // 创建处理响应的线程池
    private val responseProcessorExecutor: ExecutorService = Executors.newFixedThreadPool(4) { r ->
        Thread(r, "SSDP-ResponseProcessor-${System.currentTimeMillis()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }

    // 状态和网络
    private val isShutdown = AtomicBoolean(false)
    private val registryChecked = AtomicBoolean(false)
    private var socket: MulticastSocket? = null
    private var globalListener: RegistryListener? = null

    // 组播配置
    private val multicastAddress = DEFAULT_MULTICAST_ADDRESS
    private val multicastPort = DEFAULT_MULTICAST_PORT
    private val multicastGroup by lazy { InetSocketAddress(multicastAddress, multicastPort) }

    // SSDP协议处理器
    private val protocolHandler = SsdpProtocolHandler(registry, responseProcessorExecutor)

    /**
     * 设置全局监听器
     */
    fun setGlobalListener(listener: RegistryListener) {
        if (!isShutdown.get()) {
            globalListener = listener
        }
    }

    /**
     * 确保监听器已注册
     */
    private fun ensureListenerRegistered() {
        if (registryChecked.get()) return
        
        // 尝试添加全局监听器
        globalListener?.let { listener ->
            if (registry is RegistryImpl) {
                registry.addListener(listener)
            } else {
                forceAddListenerToRegistry(registry, listener)
            }
        }
        
        registryChecked.set(true)
    }

    /**
     * 强制添加监听器到注册表
     */
    private fun forceAddListenerToRegistry(registry: Registry, listener: RegistryListener) {
        try {
            if (registry is RegistryImpl) {
                val listenersField = registry.javaClass.getDeclaredField("registryListeners")
                listenersField.isAccessible = true
                
                @Suppress("UNCHECKED_CAST")
                val listeners = listenersField.get(registry) as? MutableCollection<RegistryListener>
                
                listeners?.add(listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "强制添加监听器失败", e)
        }
    }

    /**
     * 初始化Socket
     */
    private fun initializeSocket() {
        if (socket != null) return
        
        try {
            socket = MulticastSocket(multicastPort).apply {
                reuseAddress = true
                timeToLive = MULTICAST_TTL
                joinGroup(multicastGroup, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化Socket失败", e)
        }
    }

    /**
     * 关闭Socket
     */
    private fun closeSocket() {
        try {
            socket?.let { s ->
                s.leaveGroup(multicastGroup, null)
                s.close()
                socket = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "关闭Socket失败", e)
        }
    }

    /**
     * 发送搜索请求
     */
    override fun sendSearchRequest(multicastAddress: String, multicastPort: Int) {
        if (isShutdown.get()) return

        ensureListenerRegistered()
        initializeSocket()

        executor.execute {
            SEARCH_TARGETS.forEach { target ->
                sendSingleSearchRequest(multicastAddress, multicastPort, target)
            }
        }
    }

    /**
     * 发送单个目标的搜索请求
     */
    private fun sendSingleSearchRequest(multicastAddress: String, multicastPort: Int, target: String) {
        if (isShutdown.get()) return
        
        try {
            val message = """
                M-SEARCH * HTTP/1.1
                HOST: $multicastAddress:$multicastPort
                MAN: "ssdp:discover"
                MX: 3
                ST: $target
                USER-AGENT: UPnPCast/1.0
                
            """.trimIndent()
            
            val bytes = message.toByteArray()
            val packet = DatagramPacket(
                bytes,
                bytes.size,
                InetAddress.getByName(multicastAddress),
                multicastPort
            )
            
            socket?.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "发送搜索请求失败: ${target}", e)
        }
    }

    /**
     * 处理接收到的数据包
     */
    override fun process(receivedOnAddress: InetAddress, packet: DatagramPacket) {
        if (isShutdown.get()) return
        
        executor.execute {
            try {
                val message = String(packet.data, 0, packet.length)
                val fromAddress = packet.address
                
                when {
                    message.startsWith("HTTP/1.1 200 OK", ignoreCase = true) -> 
                        protocolHandler.processSearchResponse(message, fromAddress)
                    message.startsWith("NOTIFY", ignoreCase = true) -> 
                        protocolHandler.processNotify(message, fromAddress)
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理数据包失败", e)
            }
        }
    }
    
    /**
     * 发送数据包
     */
    override fun send(packet: DatagramPacket) {
        if (isShutdown.get()) return
        
        initializeSocket()
        try {
            socket?.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "发送数据包失败", e)
        }
    }

    /**
     * 检查是否处理事件
     */
    override fun isProcessingEvent(packet: DatagramPacket): Boolean {
        if (isShutdown.get()) return false

        val message = String(packet.data, 0, packet.length)
        return message.startsWith("HTTP/1.1 200 OK", ignoreCase = true) || 
               message.startsWith("NOTIFY", ignoreCase = true)
    }

    override fun isProcessingControl(packet: DatagramPacket): Boolean = false

    override fun shutdown() {
        if (isShutdown.getAndSet(true)) return

        // 关闭协议处理器
        protocolHandler.setShutdown(true)

        // 关闭线程池
        responseProcessorExecutor.shutdownNow()
        try {
            responseProcessorExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "关闭执行器失败", e)
        }

        // 关闭Socket
        closeSocket()
    }

    /**
     * 检查处理器是否已关闭
     */
    override fun isShutDown(): Boolean = isShutdown.get()

    /**
     * 获取当前处理器中的所有设备
     */
    fun getDevices(): Map<String, RemoteDevice> = protocolHandler.getDevices()
}