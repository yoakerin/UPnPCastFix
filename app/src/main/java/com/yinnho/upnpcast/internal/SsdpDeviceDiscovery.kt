package com.yinnho.upnpcast.internal

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SSDP device discoverer
 */
internal class SsdpDeviceDiscovery(
    private val registry: Registry,
    private val executor: Executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SSDP-Discovery").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }
) {
    private val TAG = "SsdpDeviceDiscovery"

    companion object {
        private const val MULTICAST_ADDRESS = "239.255.255.250"
        private const val MULTICAST_PORT = 1900
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val DEVICE_TIMEOUT_MS = 300000L
        private const val MAX_PROCESSED_LOCATIONS = 200
        
        private val SEARCH_TARGETS = arrayOf(
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "ssdp:all"
        )
    }

    private val isShutdown = AtomicBoolean(false)
    private var socket: MulticastSocket? = null
    private val multicastGroup by lazy { InetSocketAddress(MULTICAST_ADDRESS, MULTICAST_PORT) }
    private val descriptionParser = DeviceDescriptionParser()
    private val searchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // LRU缓存防止内存泄漏
    private val processedLocations = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_PROCESSED_LOCATIONS
        }
    }
    private val processedLock = Any()
    private val deviceTimeouts = ConcurrentHashMap<String, Long>()

    /**
     * 初始化Socket
     */
    private fun initializeSocket() {
        if (socket != null) return
        
        try {
            socket = MulticastSocket(MULTICAST_PORT).apply {
                reuseAddress = true
                timeToLive = 4
                joinGroup(multicastGroup, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize socket", e)
        }
    }

    /**
     * 关闭Socket
     */
    private fun closeSocket() {
        socket?.let { s ->
            try {
                try {
                    s.leaveGroup(multicastGroup, null)
                } catch (e: Exception) {
                    // 继续执行关闭操作
                }
                s.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close socket", e)
            } finally {
                socket = null
            }
        }
    }

    /**
     * 开始设备搜索
     */
    fun startSearch() {
        if (isShutdown.get()) return

        initializeSocket()

        executor.execute {
            SEARCH_TARGETS.forEach { target ->
                sendSearchRequest(target)
            }
            startResponseListener()
        }
    }

    /**
     * 发送搜索请求
     */
    private fun sendSearchRequest(target: String) {
        if (isShutdown.get()) return
        
        try {
            val message = """
                M-SEARCH * HTTP/1.1
                HOST: $MULTICAST_ADDRESS:$MULTICAST_PORT
                MAN: "ssdp:discover"
                MX: 3
                ST: $target
                USER-AGENT: UPnPCast/1.0
                
            """.trimIndent()
            
            val bytes = message.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(
                bytes,
                bytes.size,
                InetAddress.getByName(MULTICAST_ADDRESS),
                MULTICAST_PORT
            )
            
            socket?.send(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send search request: $target", e)
        }
    }

    /**
     * 开始响应监听
     */
    private fun startResponseListener() {
        if (isShutdown.get()) return
        
        executor.execute {
            try {
                val buffer = ByteArray(4096)
                val packet = DatagramPacket(buffer, buffer.size)
                
                socket?.soTimeout = SOCKET_TIMEOUT_MS
                
                while (!isShutdown.get()) {
                    try {
                        socket?.receive(packet)
                        processResponse(packet)
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Response listener failed", e)
            }
        }
    }

    /**
     * 处理SSDP响应
     */
    private fun processResponse(packet: DatagramPacket) {
        try {
            val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val fromAddress = packet.address
            
            when {
                message.startsWith("HTTP/1.1 200 OK", ignoreCase = true) -> {
                    processSsdpResponse(message, fromAddress)
                }
                message.startsWith("NOTIFY", ignoreCase = true) -> {
                    processNotify(message, fromAddress)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process response", e)
        }
    }

    /**
     * 处理NOTIFY消息
     */
    private fun processNotify(message: String, fromAddress: InetAddress) {
        try {
            val nts = extractHeader(message, "NTS")
            if (nts == "ssdp:alive") {
                val headers = parseSsdpHeaders(message)
                val location = headers["location"]
                
                if (location != null) {
                    synchronized(processedLock) {
                        if (processedLocations.containsKey(location)) {
                            // 设备已知，只更新时间戳
                            processedLocations[location] = System.currentTimeMillis()
                            deviceTimeouts[location] = System.currentTimeMillis()
                            return
                        }
                    }
                    
                    // 新设备，进行完整处理
                    processSsdpResponse(message, fromAddress)
                }
            } else if (nts == "ssdp:byebye") {
                val usn = extractHeader(message, "USN")
                if (usn != null) {
                    registry.getDevice(usn)?.let { device ->
                        registry.removeDevice(device)
                        
                        val headers = parseSsdpHeaders(message)
                        val location = headers["location"]
                        if (location != null) {
                            synchronized(processedLock) {
                                processedLocations.remove(location)
                                deviceTimeouts.remove(location)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process NOTIFY", e)
        }
    }

    /**
     * 提取HTTP头信息
     */
    private fun extractHeader(message: String, headerName: String): String? {
        val regex = "$headerName:\\s*(.+)".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(message)?.groupValues?.get(1)?.trim()
    }

    /**
     * 解析SSDP头信息
     */
    private fun parseSsdpHeaders(message: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        message.lines().forEach { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim().lowercase()
                val value = line.substring(colonIndex + 1).trim()
                headers[key] = value
            }
        }
        return headers
    }

    /**
     * 处理SSDP响应
     */
    private fun processSsdpResponse(response: String, fromAddress: InetAddress) {
        try {
            val headers = parseSsdpHeaders(response)
            val location = headers["location"]
            val usn = headers["usn"]
            
            if (location != null && usn != null) {
                // 检查和标记已处理
                synchronized(processedLock) {
                    if (processedLocations.containsKey(location)) {
                        return
                    }
                    processedLocations[location] = System.currentTimeMillis()
                    deviceTimeouts[location] = System.currentTimeMillis()
                }
                
                val deviceId = location
                
                // 检查设备是否已存在
                val existingDevice = registry.getDevice(deviceId)
                if (existingDevice != null) {
                    return
                }
                
                // 异步解析设备描述信息
                searchScope.launch {
                    try {
                        val deviceInfo = descriptionParser.parseDeviceDescription(location)
                        
                        val device = descriptionParser.createEnhancedDevice(
                            id = deviceId,
                            address = fromAddress.hostAddress ?: "unknown",
                            locationUrl = location,
                            deviceInfo = deviceInfo
                        )
                        
                        registry.addDevice(device)
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process device description: $location", e)
                        
                        // 解析失败时从缓存中移除
                        synchronized(processedLock) {
                            processedLocations.remove(location)
                            deviceTimeouts.remove(location)
                        }
                        
                        // 对于结构性错误创建fallback设备
                        if (isStructuralError(e)) {
                            val fallbackDevice = createFallbackDevice(deviceId, fromAddress.hostAddress ?: "unknown", location)
                            
                            if (registry.getDevice(deviceId) == null) {
                                registry.addDevice(fallbackDevice)
                            }
                            
                            synchronized(processedLock) {
                                processedLocations[location] = System.currentTimeMillis()
                                deviceTimeouts[location] = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process SSDP response", e)
        }
    }

    /**
     * 判断是否为结构性错误
     */
    private fun isStructuralError(exception: Exception): Boolean {
        return when (exception) {
            is java.net.MalformedURLException,
            is java.io.FileNotFoundException,
            is java.net.UnknownHostException -> true
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.io.IOException -> false
            else -> {
                val message = exception.message?.lowercase() ?: ""
                "xml" in message || "parse" in message
            }
        }
    }

    /**
     * 创建fallback设备信息
     */
    private fun createFallbackDevice(deviceId: String, address: String, location: String): RemoteDevice {
        return RemoteDevice(
            id = deviceId,
            displayName = "DLNA device",
            manufacturer = "Unknown",
            address = address,
            details = mapOf(
                "friendlyName" to "DLNA device",
                "manufacturer" to "Unknown",
                "modelName" to "Unknown Model",
                "deviceType" to "Unknown",
                "locationUrl" to location
            )
        )
    }

    /**
     * 停止搜索
     */
    fun stopSearch() {
        cleanupTimeoutDevices()
    }

    /**
     * 清理超时设备
     */
    private fun cleanupTimeoutDevices() {
        val currentTime = System.currentTimeMillis()
        val timeoutDevices = mutableListOf<String>()
        
        synchronized(processedLock) {
            deviceTimeouts.entries.removeAll { (location, timestamp) ->
                val isTimeout = currentTime - timestamp > DEVICE_TIMEOUT_MS
                if (isTimeout) {
                    timeoutDevices.add(location)
                    processedLocations.remove(location)
                }
                isTimeout
            }
        }
    }

    /**
     * 关闭discoverer
     */
    fun shutdown() {
        if (isShutdown.getAndSet(true)) return
        
        try {
            searchScope.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel search scope", e)
        }
        
        try {
            if (executor is ExecutorService) {
                executor.shutdown()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to shutdown executor", e)
        }
        
        closeSocket()
        
        synchronized(processedLock) {
            processedLocations.clear()
            deviceTimeouts.clear()
        }
    }
} 