package com.yinnho.upnpcast.internal

import android.util.Log
import com.yinnho.upnpcast.Registry
import com.yinnho.upnpcast.internal.RegistryImpl
import com.yinnho.upnpcast.RemoteDevice
import java.net.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap

/**
 * SSDP device discoverer - Actual working code copied from backup
 * Responsible for network communication of DLNA device discovery
 */
internal class SsdpDeviceDiscovery(
    private val registry: RegistryImpl,
    private val executor: Executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SSDP-Discovery").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }
) {
    private val TAG = "SsdpDeviceDiscovery"

    companion object {
        // Network configuration - Copied from backup
        private const val MULTICAST_TTL = 4
        private const val DEFAULT_MULTICAST_ADDRESS = "239.255.255.250"
        private const val DEFAULT_MULTICAST_PORT = 1900
        
        // Search targets - Copied from backup
        private val SEARCH_TARGETS = listOf(
            "ssdp:all",                                   // All devices
            "upnp:rootdevice",                            // Root devices
            "urn:schemas-upnp-org:device:MediaRenderer:1" // Media renderer
        )

        private const val MULTICAST_ADDRESS = "239.255.255.250"
        private const val MULTICAST_PORT = 1900
        private const val SEARCH_TIMEOUT = 3000L
    }

    // State and network
    private val isShutdown = AtomicBoolean(false)
    private var socket: MulticastSocket? = null
    
    // Multicast configuration
    private val multicastAddress = DEFAULT_MULTICAST_ADDRESS
    private val multicastPort = DEFAULT_MULTICAST_PORT
    private val multicastGroup by lazy { InetSocketAddress(multicastAddress, multicastPort) }

    // Add device description parser
    private val descriptionParser = DeviceDescriptionParser()

    // Add coroutine scope for async device description parsing
    private val searchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Add response deduplication mechanism - Record processed location URLs
    private val processedLocations = mutableSetOf<String>()
    private val processedLock = Any()

    // Add device timeout management
    private val deviceTimeouts = ConcurrentHashMap<String, Long>()
    private val DEVICE_TIMEOUT_MS = 60000L // 1 minute timeout

    /**
     * Initialize Socket - Copied from backup
     */
    private fun initializeSocket() {
        if (socket != null) return
        
        try {
            socket = MulticastSocket(multicastPort).apply {
                reuseAddress = true
                timeToLive = MULTICAST_TTL
                joinGroup(multicastGroup, null)
            }
            Log.d(TAG, "SSDP Socket initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize socket", e)
        }
    }

    /**
     * Close Socket - Copied from backup
     */
    private fun closeSocket() {
        try {
            socket?.let { s ->
                s.leaveGroup(multicastGroup, null)
                s.close()
                socket = null
            }
            Log.d(TAG, "SSDP Socket closed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close socket", e)
        }
    }

    /**
     * Start device search - Core logic copied from backup
     */
    fun startSearch() {
        if (isShutdown.get()) return

        Log.d(TAG, "Starting SSDP device search")
        initializeSocket()

        executor.execute {
            SEARCH_TARGETS.forEach { target ->
                sendSearchRequest(target)
            }
            
            // Start response listener
            startResponseListener()
        }
    }

    /**
     * Send search request - Copied from backup
     */
    private fun sendSearchRequest(target: String) {
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
            Log.d(TAG, "SSDP search request sent: $target")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send search request: $target", e)
        }
    }

    /**
     * Start response listener - Simplified version
     */
    private fun startResponseListener() {
        if (isShutdown.get()) return
        
        executor.execute {
            try {
                val buffer = ByteArray(4096)
                val packet = DatagramPacket(buffer, buffer.size)
                
                // Listen for responses, maximum 30 seconds
                socket?.soTimeout = 30000
                
                while (!isShutdown.get()) {
                    try {
                        socket?.receive(packet)
                        processResponse(packet)
                    } catch (e: Exception) {
                        if (!isShutdown.get()) {
                            Log.w(TAG, "Response receive timeout or failed", e)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Response listener failed", e)
            }
        }
    }

    /**
     * Process SSDP response - Simplified version, adapted from backup
     */
    private fun processResponse(packet: DatagramPacket) {
        try {
            val message = String(packet.data, 0, packet.length)
            val fromAddress = packet.address
            
            // Pre-check if same location already processed - Fast filtering
            if (isLocationAlreadyProcessed(message)) {
                // Don't print log, silently skip
                return
            }
            
            Log.d(TAG, "Received SSDP response: ${fromAddress.hostAddress}")
            
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
     * Fast check if location already processed (avoid full header parsing)
     */
    private fun isLocationAlreadyProcessed(message: String): Boolean {
        try {
            // Fast regex match for location header, avoid full header parsing
            val locationPattern = "location:\\s*([^\\r\\n]+)".toRegex(RegexOption.IGNORE_CASE)
            val match = locationPattern.find(message)
            val location = match?.groupValues?.get(1)?.trim()
            
            if (location != null) {
                synchronized(processedLock) {
                    return processedLocations.contains(location)
                }
            }
            return false
        } catch (e: Exception) {
            // Don't skip if parsing fails, continue processing
            return false
        }
    }

    /**
     * Process NOTIFY message - Simplified version
     */
    private fun processNotify(message: String, fromAddress: InetAddress) {
        try {
            val nts = extractHeader(message, "NTS")
            if (nts == "ssdp:alive") {
                // Process device online
                processSsdpResponse(message, fromAddress)
            } else if (nts == "ssdp:byebye") {
                // Process device offline
                val usn = extractHeader(message, "USN")
                if (usn != null) {
                    registry.getDevice(usn)?.let { device ->
                        registry.removeDevice(device)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process NOTIFY", e)
        }
    }

    /**
     * Extract HTTP header information - Utility method
     */
    private fun extractHeader(message: String, headerName: String): String? {
        val regex = "$headerName:\\s*(.+)".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(message)?.groupValues?.get(1)?.trim()
    }

    /**
     * Parse SSDP header information
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
     * Process SSDP response - Use DeviceDescriptionParser to parse real device information
     */
    private fun processSsdpResponse(response: String, fromAddress: InetAddress) {
        try {
            val headers = parseSsdpHeaders(response)
            val location = headers["location"]
            val usn = headers["usn"]
            
            if (location != null && usn != null) {
                // Again check and mark processed (prevent concurrent competition)
                synchronized(processedLock) {
                    if (processedLocations.contains(location)) {
                        return  // Silent skip
                    }
                    // Mark as processed and record timestamp
                    processedLocations.add(location)
                    deviceTimeouts[location] = System.currentTimeMillis()
                }
                
                Log.d(TAG, "Starting to process new device: USN=$usn, Location=$location")
                
                // Use location URL as device unique identifier, avoid multiple services of same device
                val deviceId = location // Use location URL as unique identifier
                
                // Check if device already exists (based on location)
                val existingDevice = registry.getDevice(deviceId)
                if (existingDevice != null) {
                    Log.d(TAG, "Device already exists in registry, skipping: $location")
                    return
                }
                
                // Async parse device description information
                searchScope.launch {
                    try {
                        // Parse device description information
                        val deviceInfo = descriptionParser.parseDeviceDescription(location)
                        
                        // Create enhanced device object
                        val device = descriptionParser.createEnhancedDevice(
                            id = deviceId,  // Use location as ID
                            address = fromAddress.hostAddress ?: "unknown",
                            locationUrl = location,
                            deviceInfo = deviceInfo
                        )
                        
                        // Add to registry
                        registry.addDevice(device)
                        
                        Log.d(TAG, "Device description parsing completed: ${device.displayName} (${device.manufacturer}) at $location")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process device description: $location", e)
                        
                        // Create fallback device information if description parsing fails
                        val fallbackDevice = createFallbackDevice(deviceId, fromAddress.hostAddress ?: "unknown", location)
                        registry.addDevice(fallbackDevice)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process SSDP response", e)
        }
    }

    /**
     * Create fallback device information (when description parsing fails)
     */
    private fun createFallbackDevice(deviceId: String, address: String, location: String): RemoteDevice {
        return RemoteDevice(
            id = deviceId,  // Use location as ID
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
     * Stop search - Don't clean up cache, maintain device stability
     */
    fun stopSearch() {
        Log.d(TAG, "Stopping SSDP device search")
        // Don't clean up processed location cache, maintain device list stability
        // processedLocations.clear() // Commented out this line
        
        // Clean up timeout devices
        cleanupTimeoutDevices()
    }

    /**
     * Clean up timeout devices
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
        
        if (timeoutDevices.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${timeoutDevices.size} timeout devices")
        }
    }

    /**
     * Shutdown discoverer - Completely clean up
     */
    fun shutdown() {
        if (isShutdown.getAndSet(true)) return
        
        Log.d(TAG, "Shutting down SSDP device discoverer")
        
        // Completely clean up all caches
        synchronized(processedLock) {
            processedLocations.clear()
            deviceTimeouts.clear()
        }
        
        closeSocket()
    }
} 