package com.yinnho.upnpcast.internal

import android.util.Log
import com.yinnho.upnpcast.RemoteDevice
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * DLNA media controller - Actual control logic copied from backup
 * Responsible for SOAP communication with DLNA devices to execute playback control
 */
internal class DlnaMediaController(private val device: RemoteDevice) {
    
    private val TAG = "DlnaMediaController"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Device controller instance cache
    companion object {
        private val deviceControllers = ConcurrentHashMap<String, DlnaMediaController>()
        
        fun getController(device: RemoteDevice): DlnaMediaController {
            return deviceControllers.getOrPut(device.id) {
                DlnaMediaController(device)
            }
        }
        
        fun removeController(deviceId: String) {
            deviceControllers.remove(deviceId)
        }
    }
    
    /**
     * Build AVTransport URL for validation
     */
    fun buildAVTransportUrl(): String? {
        return try {
            // Try to get AVTransport control URL from device service information
            val services = device.details["services"] as? List<*>
            if (services != null) {
                Log.d(TAG, "Found ${services.size} services")
                
                // Look for AVTransport service
                for (service in services) {
                    if (service is DeviceDescriptionParser.ServiceInfo) {
                        Log.d(TAG, "Checking service: ${service.serviceType}")
                        
                        if (service.serviceType.contains("AVTransport", ignoreCase = true)) {
                            var controlUrl = service.controlURL
                            
                            // If controlURL is not a complete URL, need to combine with device base URL
                            if (!controlUrl.startsWith("http://") && !controlUrl.startsWith("https://")) {
                                val location = device.details["location"] as? String
                                if (location != null) {
                                    val baseUrl = if (location.startsWith("http://")) {
                                        val afterProtocol = location.substring(7)
                                        val hostAndPort = if (afterProtocol.contains("/")) {
                                            afterProtocol.substringBefore("/")
                                        } else {
                                            afterProtocol
                                        }
                                        "http://$hostAndPort"
                                    } else {
                                        "http://${device.address}"
                                    }
                                    // Ensure controlUrl starts with /, add if not
                                    val normalizedPath = if (controlUrl.startsWith("/")) {
                                        controlUrl
                                    } else {
                                        "/$controlUrl"
                                    }
                                    controlUrl = "$baseUrl$normalizedPath"
                                }
                            }
                            
                            Log.d(TAG, "Found AVTransport control URL: $controlUrl")
                            return controlUrl
                        }
                    }
                }
            }
            
            // Fallback to default URL
            val defaultUrl = "http://${device.address}:8080/AVTransport/control"
            Log.d(TAG, "Using default AVTransport URL: $defaultUrl")
            defaultUrl
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build AVTransport URL", e)
            null
        }
    }
    
    /**
     * Play media - Synchronous wrapper for async method
     */
    fun playMedia(url: String, title: String): Boolean {
        return runBlocking {
            playMediaDirect(url, title)
        }
    }
    
    /**
     * Stop playback - Synchronous wrapper
     */
    fun stopPlayback(): Boolean {
        return runBlocking {
            stopDirect()
        }
    }
    
    /**
     * Pause playback - Synchronous wrapper
     */
    fun pausePlayback(): Boolean {
        return runBlocking {
            pause()
        }
    }
    
    /**
     * Resume playback - Synchronous wrapper
     */
    fun resumePlayback(): Boolean {
        return runBlocking {
            play()
        }
    }
    
    /**
     * Seek to position - Synchronous wrapper
     */
    fun seekTo(positionMs: Long): Boolean {
        return runBlocking {
            seek(positionMs)
        }
    }
    
    /**
     * Play media - Core logic copied from backup
     */
    private suspend fun playMediaDirect(
        mediaUrl: String, 
        title: String, 
        episodeLabel: String = "", 
        positionMs: Long = 0
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to play media: $title on ${device.displayName}")
            
            // 1. Set media URI
            val setUriSuccess = setAVTransportURI(mediaUrl, createMetadata(title, episodeLabel))
            if (!setUriSuccess) {
                Log.e(TAG, "Failed to set media URI")
                return@withContext false
            }
            
            // 2. Start playing
            val playSuccess = play()
            if (!playSuccess) {
                Log.e(TAG, "Failed to start playing")
                return@withContext false
            }
            
            // 3. If need to seek to a specific position
            if (positionMs > 0) {
                delay(1000) // Wait for playback to start
                seek(positionMs)
            }
            
            Log.d(TAG, "Playback succeeded: $title")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play media: ${e.message}", e)
            false
        }
    }
    
    /**
     * Stop playing - Copied from backup
     */
    private suspend fun stopDirect(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Stopping playback: ${device.displayName}")
            
            val success = sendSoapAction(
                action = "Stop",
                serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                body = """
                    <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:Stop>
                """.trimIndent()
            )
            
            if (success) {
                Log.d(TAG, "Stopped playback successfully")
            } else {
                Log.w(TAG, "Failed to stop playback")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping playback: ${e.message}", e)
            false
        }
    }
    
    /**
     * Pause playing
     */
    private suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        try {
            sendSoapAction(
                action = "Pause",
                serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                body = """
                    <u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:Pause>
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause playback: ${e.message}", e)
            false
        }
    }
    
    /**
     * Start playing
     */
    private suspend fun play(): Boolean = withContext(Dispatchers.IO) {
        try {
            sendSoapAction(
                action = "Play",
                serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                body = """
                    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <Speed>1</Speed>
                    </u:Play>
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playing: ${e.message}", e)
            false
        }
    }
    
    /**
     * Set volume - Synchronous wrapper
     */
    fun setVolume(volume: Int): Boolean {
        return runBlocking {
            setVolumeAsync(volume)
        }
    }
    
    /**
     * Set mute - Synchronous wrapper
     */
    fun setMute(mute: Boolean): Boolean {
        return runBlocking {
            setMuteAsync(mute)
        }
    }
    
    /**
     * Set media URI - Copied from backup
     */
    private suspend fun setAVTransportURI(mediaUrl: String, metadata: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sendSoapAction(
                action = "SetAVTransportURI",
                serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                body = """
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <CurrentURI>$mediaUrl</CurrentURI>
                        <CurrentURIMetaData>$metadata</CurrentURIMetaData>
                    </u:SetAVTransportURI>
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set media URI: ${e.message}", e)
            false
        }
    }
    
    /**
     * Seek to playback position
     */
    private suspend fun seek(positionMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val timeString = formatTime(positionMs)
            sendSoapAction(
                action = "Seek",
                serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                body = """
                    <u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <Unit>REL_TIME</Unit>
                        <Target>$timeString</Target>
                    </u:Seek>
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek to playback position: ${e.message}", e)
            false
        }
    }
    
    /**
     * Set volume
     */
    private suspend fun setVolumeAsync(volume: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val clampedVolume = volume.coerceIn(0, 100)
            sendRenderingControlAction(
                action = "SetVolume",
                body = """
                    <u:SetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                        <InstanceID>0</InstanceID>
                        <Channel>Master</Channel>
                        <DesiredVolume>$clampedVolume</DesiredVolume>
                    </u:SetVolume>
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume: ${e.message}", e)
            false
        }
    }
    
    /**
     * Set mute
     */
    private suspend fun setMuteAsync(mute: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val muteValue = if (mute) "1" else "0"
            sendRenderingControlAction(
                action = "SetMute",
                body = """
                    <u:SetMute xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                        <InstanceID>0</InstanceID>
                        <Channel>Master</Channel>
                        <DesiredMute>$muteValue</DesiredMute>
                    </u:SetMute>
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set mute: ${e.message}", e)
            false
        }
    }
    
    /**
     * Send SOAP request - Simplified version
     */
    private suspend fun sendSoapAction(action: String, serviceType: String, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val avTransportUrl = buildAVTransportUrl() ?: return@withContext false
            Log.d(TAG, "Sending SOAP request: $action to $avTransportUrl")
            
            val soapEnvelope = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        $body
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            
            val url = URL(avTransportUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            connection.setRequestProperty("SOAPAction", "\"$serviceType#$action\"")
            connection.setRequestProperty("User-Agent", "UPnPCast/1.0")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            
            // Send request
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(soapEnvelope)
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream
                val errorMessage = errorStream?.bufferedReader()?.readText()
                Log.w(TAG, "SOAP request failed: $action, Response code: $responseCode, Error: $errorMessage")
            } else {
                Log.d(TAG, "SOAP request succeeded: $action")
            }
            
            responseCode == HttpURLConnection.HTTP_OK
            
        } catch (e: Exception) {
            Log.e(TAG, "SOAP request exception: $action, ${e.message}", e)
            false
        }
    }
    
    /**
     * Send RenderingControl SOAP request
     */
    private suspend fun sendRenderingControlAction(action: String, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val renderingControlUrl = "http://${device.address}:8080/RenderingControl/control"
            Log.d(TAG, "Sending RenderingControl request: $action to $renderingControlUrl")
            
            val soapEnvelope = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        $body
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            
            val url = URL(renderingControlUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            connection.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:RenderingControl:1#$action\"")
            connection.setRequestProperty("User-Agent", "UPnPCast/1.0")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            
            // Send request
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(soapEnvelope)
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream
                val errorMessage = errorStream?.bufferedReader()?.readText()
                Log.w(TAG, "RenderingControl request failed: $action, Response code: $responseCode, Error: $errorMessage")
            } else {
                Log.d(TAG, "RenderingControl request succeeded: $action")
            }
            
            responseCode == HttpURLConnection.HTTP_OK
            
        } catch (e: Exception) {
            Log.e(TAG, "RenderingControl request exception: $action, ${e.message}", e)
            false
        }
    }
    
    /**
     * Create DIDL-Lite metadata - Copied from backup
     */
    private fun createMetadata(title: String, episodeLabel: String): String {
        val displayTitle = if (episodeLabel.isNotEmpty()) "$title - $episodeLabel" else title
        return """
            &lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;
                &lt;item id="1" parentID="0" restricted="1"&gt;
                    &lt;dc:title&gt;$displayTitle&lt;/dc:title&gt;
                    &lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;
                &lt;/item&gt;
            &lt;/DIDL-Lite&gt;
        """.trimIndent()
    }
    
    /**
     * Format time as HH:MM:SS format
     */
    private fun formatTime(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    /**
     * Release resources
     */
    fun release() {
        Log.d(TAG, "Releasing DlnaMediaController resources")
        coroutineScope.cancel()
    }
} 