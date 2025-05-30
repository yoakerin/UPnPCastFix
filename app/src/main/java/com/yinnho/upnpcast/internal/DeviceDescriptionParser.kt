package com.yinnho.upnpcast.internal

import android.util.Log
import com.yinnho.upnpcast.RemoteDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Device description information parser
 * Extracts real device name, manufacturer and other information from description.xml
 */
class DeviceDescriptionParser {
    companion object {
        private const val TAG = "DeviceDescriptionParser"
        private const val CONNECT_TIMEOUT = 5000 // 5 seconds connection timeout
        private const val READ_TIMEOUT = 10000    // 10 seconds read timeout
        
        // Cache parsed device information to avoid duplicate requests
        private val deviceCache = ConcurrentHashMap<String, DeviceInfo>()
    }
    
    data class DeviceInfo(
        val friendlyName: String,
        val manufacturer: String,
        val modelName: String,
        val deviceType: String,
        val services: List<ServiceInfo> = emptyList()
    )
    
    data class ServiceInfo(
        val serviceType: String,
        val serviceId: String,
        val controlURL: String,
        val eventSubURL: String,
        val descriptorURL: String
    )
    
    /**
     * Asynchronously parse device description information
     */
    suspend fun parseDeviceDescription(locationUrl: String): DeviceInfo? {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache
                deviceCache[locationUrl]?.let { 
                    Log.d(TAG, "Using cached device info: $locationUrl")
                    return@withContext it 
                }
                
                Log.d(TAG, "Starting to parse device description: $locationUrl")
                
                // Download XML content
                val xmlContent = downloadXmlContent(locationUrl)
                if (xmlContent.isEmpty()) {
                    Log.w(TAG, "Unable to download description file: $locationUrl")
                    return@withContext null
                }
                
                // Parse XML content
                val deviceInfo = parseXmlContent(xmlContent)
                
                // Cache result
                if (deviceInfo != null) {
                    deviceCache[locationUrl] = deviceInfo
                    Log.d(TAG, "Parsing successful and cached: ${deviceInfo.friendlyName} (${deviceInfo.manufacturer})")
                }
                
                deviceInfo
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse device description: $locationUrl", e)
                null
            }
        }
    }
    
    /**
     * Download XML description file content
     */
    private fun downloadXmlContent(locationUrl: String): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(locationUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "UPnPCast/1.0")
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP request failed, status code: $responseCode for $locationUrl")
                return ""
            }
            
            BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                return reader.readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download description file: $locationUrl", e)
            return ""
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Parse XML content to extract device information
     */
    private fun parseXmlContent(xmlContent: String): DeviceInfo? {
        try {
            // Simple regex parsing to avoid XML library dependencies
            val friendlyName = extractXmlValue(xmlContent, "friendlyName") ?: "DLNA Device"
            val manufacturer = extractXmlValue(xmlContent, "manufacturer") ?: "Unknown"
            val modelName = extractXmlValue(xmlContent, "modelName") ?: "Unknown Model"
            val deviceType = extractXmlValue(xmlContent, "deviceType") ?: "Unknown"
            
            // Parse service list
            val services = parseServices(xmlContent)
            
            // Smart device name processing
            val processedName = processDeviceName(friendlyName, manufacturer, modelName)
            val processedManufacturer = processManufacturer(manufacturer, friendlyName)
            
            return DeviceInfo(
                friendlyName = processedName,
                manufacturer = processedManufacturer,
                modelName = modelName,
                deviceType = deviceType,
                services = services
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse XML content", e)
            return null
        }
    }
    
    /**
     * Parse service list
     */
    private fun parseServices(xmlContent: String): List<ServiceInfo> {
        val services = mutableListOf<ServiceInfo>()
        
        try {
            // Find all service blocks
            val servicePattern = "<service>(.*?)</service>".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val serviceMatches = servicePattern.findAll(xmlContent)
            
            for (serviceMatch in serviceMatches) {
                val serviceXml = serviceMatch.groupValues[1]
                
                val serviceType = extractXmlValue(serviceXml, "serviceType") ?: continue
                val serviceId = extractXmlValue(serviceXml, "serviceId") ?: ""
                val controlURL = extractXmlValue(serviceXml, "controlURL") ?: ""
                val eventSubURL = extractXmlValue(serviceXml, "eventSubURL") ?: ""
                val descriptorURL = extractXmlValue(serviceXml, "SCPDURL") ?: ""
                
                // Only care about DLNA related services
                if (serviceType.contains("AVTransport", ignoreCase = true) ||
                    serviceType.contains("RenderingControl", ignoreCase = true) ||
                    serviceType.contains("ConnectionManager", ignoreCase = true)) {
                    
                    services.add(ServiceInfo(
                        serviceType = serviceType,
                        serviceId = serviceId,
                        controlURL = controlURL,
                        eventSubURL = eventSubURL,
                        descriptorURL = descriptorURL
                    ))
                    
                    Log.d(TAG, "Parsed service: $serviceType")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse service list", e)
        }
        
        return services
    }
    
    /**
     * Extract value of specified tag from XML
     */
    private fun extractXmlValue(xmlContent: String, tagName: String): String? {
        try {
            val pattern = "<$tagName[^>]*>([^<]+)</$tagName>".toRegex(RegexOption.IGNORE_CASE)
            val matchResult = pattern.find(xmlContent)
            return matchResult?.groupValues?.get(1)?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract XML tag: $tagName", e)
            return null
        }
    }
    
    /**
     * Process device name to provide a more friendly display
     */
    private fun processDeviceName(friendlyName: String, manufacturer: String, modelName: String): String {
        return when {
            friendlyName.isNotBlank() && friendlyName != "Unknown" -> {
                // If the device name already contains manufacturer information, use it directly
                if (manufacturer.isNotBlank() && 
                    !friendlyName.contains(manufacturer, ignoreCase = true)) {
                    "$manufacturer $friendlyName"
                } else {
                    friendlyName
                }
            }
            modelName.isNotBlank() && modelName != "Unknown Model" -> {
                if (manufacturer.isNotBlank() && manufacturer != "Unknown") {
                    "$manufacturer $modelName"
                } else {
                    modelName
                }
            }
            manufacturer.isNotBlank() && manufacturer != "Unknown" -> {
                "$manufacturer Device"
            }
            else -> "DLNA Device"
        }
    }
    
    /**
     * Process manufacturer information to provide standardized manufacturer name
     */
    private fun processManufacturer(manufacturer: String, friendlyName: String): String {
        return when {
            manufacturer.isNotBlank() && manufacturer != "Unknown" -> {
                // Standardize manufacturer name
                when (manufacturer.lowercase()) {
                    "xiaomi", "小米" -> "Xiaomi"
                    "samsung" -> "Samsung"
                    "lg electronics" -> "LG"
                    "sony" -> "Sony"
                    "panasonic" -> "Panasonic"
                    "tcl" -> "TCL"
                    "hisense" -> "Hisense"
                    else -> manufacturer
                }
            }
            friendlyName.isNotBlank() -> {
                // Infer manufacturer from device name
                when {
                    friendlyName.contains("小米", ignoreCase = true) ||
                    friendlyName.contains("xiaomi", ignoreCase = true) -> "Xiaomi"
                    friendlyName.contains("samsung", ignoreCase = true) -> "Samsung"
                    friendlyName.contains("lg", ignoreCase = true) -> "LG"
                    friendlyName.contains("sony", ignoreCase = true) -> "Sony"
                    friendlyName.contains("tcl", ignoreCase = true) -> "TCL"
                    friendlyName.contains("海信", ignoreCase = true) ||
                    friendlyName.contains("hisense", ignoreCase = true) -> "Hisense"
                    else -> "Unknown"
                }
            }
            else -> "Unknown"
        }
    }
    
    /**
     * Create enhanced RemoteDevice containing real device information
     */
    fun createEnhancedDevice(
        id: String,
        address: String,
        locationUrl: String,
        deviceInfo: DeviceInfo?
    ): RemoteDevice {
        val info = deviceInfo ?: DeviceInfo("DLNA Device", "Unknown", "Unknown Model", "Unknown")
        
        return RemoteDevice(
            id = id,
            displayName = info.friendlyName,
            manufacturer = info.manufacturer,
            address = address,
            details = mapOf(
                "friendlyName" to info.friendlyName,
                "manufacturer" to info.manufacturer,
                "modelName" to info.modelName,
                "deviceType" to info.deviceType,
                "locationUrl" to locationUrl,
                "location" to locationUrl,  // Add compatibility field
                "services" to info.services  // Add service information
            )
        )
    }
    
    /**
     * Clear cache
     */
    fun clearCache() {
        deviceCache.clear()
        Log.d(TAG, "Device info cache cleared")
    }
} 