package com.yinnho.upnpcast.internal.media

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import com.yinnho.upnpcast.internal.discovery.RemoteDevice
import com.yinnho.upnpcast.internal.discovery.DeviceDescriptionParser

/**
 * DLNA media controller - SOAP control implementation
 */
internal class DlnaMediaController(private val device: RemoteDevice) {
    
    private val tag = "DlnaMediaController"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Device controller instance cache
    companion object {
        private val deviceControllers = ConcurrentHashMap<String, DlnaMediaController>()
        
        fun getController(device: RemoteDevice): DlnaMediaController {
            return deviceControllers.getOrPut(device.id) {
                DlnaMediaController(device)
            }
        }
        
        fun clearAllControllers() {
            deviceControllers.values.forEach { it.release() }
            deviceControllers.clear()
        }
    }
    
    @Volatile
    private var isReleased = false
    
    private fun checkAvailable(): Boolean {
        return !isReleased && coroutineScope.isActive
    }
    
    /**
     * Generic service URL builder method
     */
    private fun buildServiceUrl(serviceTypePattern: String, defaultPath: String): String? {
        return try {
            val services = device.details["services"] as? List<*>
            if (services != null) {
                for (service in services) {
                    if (service is DeviceDescriptionParser.ServiceInfo) {
                        if (service.serviceType.contains(serviceTypePattern, ignoreCase = true)) {
                            var controlUrl = service.controlURL
                            
                            if (!controlUrl.startsWith("http://") && !controlUrl.startsWith("https://")) {
                                val location = device.details["location"] as? String
                                if (location != null) {
                                    val url = java.net.URL(location)
                                    val baseUrl = "http://${device.address}:${url.port}"
                                    controlUrl = if (controlUrl.startsWith("/")) {
                                        "$baseUrl$controlUrl"
                                    } else {
                                        "$baseUrl/$controlUrl"
                                    }
                                }
                            }
                            return controlUrl
                        }
                    }
                }
            }
            
            val location = device.details["location"] as? String
            val port = if (location != null) {
                try {
                    val url = java.net.URL(location)
                    url.port
                } catch (e: Exception) {
                    return null
                }
            } else {
                return null
            }
            
            "http://${device.address}:$port/$defaultPath"
        } catch (e: Exception) {
            null
        }
    }

    private fun buildAVTransportUrl(): String? {
        return buildServiceUrl("AVTransport", "AVTransport/control")
    }
    
    /**
     * Play media
     */
    suspend fun playMediaDirect(
        mediaUrl: String, 
        title: String, 
        episodeLabel: String = "", 
        positionMs: Long = 0
    ): Boolean = withContext(Dispatchers.IO) {
        if (!checkAvailable()) return@withContext false
        
        try {
            val setUriSuccess = setMediaUri(mediaUrl, createMetadata(title, episodeLabel, mediaUrl))
            if (!setUriSuccess) return@withContext false
            
            val playSuccess = play()
            if (!playSuccess) return@withContext false
            
            if (positionMs > 0) {
                delay(1000)
                seek(positionMs)
            }
            
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to play media: ${e.message}")
            false
        }
    }
    
    /**
     * Stop playback
     */
    suspend fun stopDirect(): Boolean = withContext(Dispatchers.IO) {
        if (!checkAvailable()) return@withContext false
        
        try {
            sendSoapAction(
                action = "Stop",
                serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                body = """
                    <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:Stop>
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(tag, "Exception stopping playback: ${e.message}")
            false
        }
    }
    
    /**
     * Pause playback
     */
    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        if (!checkAvailable()) return@withContext false
        
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
            Log.e(tag, "Failed to pause playback: ${e.message}")
            false
        }
    }
    
    /**
     * Start playback
     */
    suspend fun play(): Boolean = withContext(Dispatchers.IO) {
        if (!checkAvailable()) return@withContext false
        
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
            Log.e(tag, "Failed to start playing: ${e.message}")
            false
        }
    }
    
    /**
     * Set media URI
     */
    private suspend fun setMediaUri(mediaUrl: String, metadata: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sendSoapAction(
                action = "SetAVTransportURI",
                serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                body = """
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <CurrentURI>${escapeXmlUrl(mediaUrl)}</CurrentURI>
                        <CurrentURIMetaData><![CDATA[$metadata]]></CurrentURIMetaData>
                    </u:SetAVTransportURI>
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to set media URI: ${e.message}")
            false
        }
    }
    
    /**
     * Seek to specified position
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
                        <Target>${escapeXmlContent(timeString)}</Target>
                    </u:Seek>
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to seek: ${e.message}")
            false
        }
    }
    
    /**
     * Set volume
     */
    suspend fun setVolumeAsync(volume: Int): Boolean = withContext(Dispatchers.IO) {
        if (!checkAvailable()) return@withContext false
        
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
            Log.e(tag, "Failed to set volume: ${e.message}")
            false
        }
    }
    
    /**
     * Set mute
     */
    suspend fun setMuteAsync(mute: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!checkAvailable()) return@withContext false
        
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
            Log.e(tag, "Failed to set mute: ${e.message}")
            false
        }
    }
    
    /**
     * Seek to specified playback position
     */
    suspend fun seekTo(positionMs: Long): Boolean = withContext(Dispatchers.IO) {
        if (!checkAvailable()) return@withContext false
        
        try {
            seek(positionMs)
        } catch (e: Exception) {
            Log.e(tag, "Failed to seek to position: ${e.message}")
            false
        }
    }
    
    /**
     * Get playback position information
     */
    suspend fun getPositionInfo(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        if (!checkAvailable()) return@withContext null
        
        try {
            getPositionInfoInternal()
        } catch (e: Exception) {
            Log.e(tag, "Failed to get position info: ${e.message}")
            null
        }
    }
    
    /**
     * Internal implementation of getting playback position information
     */
    private suspend fun getPositionInfoInternal(): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        try {
            val avTransportUrl = buildAVTransportUrl() ?: return@withContext null
            
            val soapEnvelope = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        <u:GetPositionInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                            <InstanceID>0</InstanceID>
                        </u:GetPositionInfo>
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            
            val connection = URL(avTransportUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            connection.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:AVTransport:1#GetPositionInfo\"")
            connection.setRequestProperty("User-Agent", "UPnPCast/1.0")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            
            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(soapEnvelope)
                    writer.flush()
                }
            }
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                        reader.readText()
                    }
                }
                
                // Parse response to get playback position
                parsePositionInfo(response)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to get position info: ${e.message}")
            null
        }
    }
    
    /**
     * Parse playback position information
     */
    private fun parsePositionInfo(response: String): Pair<Long, Long>? {
        try {
            // Simple XML parsing to get RelTime and TrackDuration
            val relTimePattern = "<RelTime>(.*?)</RelTime>".toRegex()
            val durationPattern = "<TrackDuration>(.*?)</TrackDuration>".toRegex()
            
            val relTimeMatch = relTimePattern.find(response)
            val durationMatch = durationPattern.find(response)
            
            val currentTime = relTimeMatch?.groupValues?.get(1)?.let { parseTimeToMs(it) } ?: 0L
            val totalTime = durationMatch?.groupValues?.get(1)?.let { parseTimeToMs(it) } ?: 0L
            
            return Pair(currentTime, totalTime)
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse position info: ${e.message}")
            return null
        }
    }
    
    /**
     * Parse time string to milliseconds
     */
    private fun parseTimeToMs(timeString: String): Long {
        try {
            if (timeString == "NOT_IMPLEMENTED" || timeString.isEmpty()) {
                return 0L
            }
            
            val parts = timeString.split(":")
            if (parts.size == 3) {
                val hours = parts[0].toLongOrNull() ?: 0L
                val minutes = parts[1].toLongOrNull() ?: 0L
                val seconds = parts[2].toDoubleOrNull() ?: 0.0
                
                return (hours * 3600 + minutes * 60 + seconds).toLong() * 1000
            }
            return 0L
        } catch (e: Exception) {
            return 0L
        }
    }
    
    /**
     * HTTP SOAP request
     */
    private suspend fun sendHttpSoapRequest(
        url: String, 
        soapAction: String, 
        soapEnvelope: String
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            connection.setRequestProperty("SOAPAction", "\"$soapAction\"")
            connection.setRequestProperty("User-Agent", "UPnPCast/1.0")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            
            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(soapEnvelope)
                    writer.flush()
                }
            }
            
            connection.responseCode == HttpURLConnection.HTTP_OK
            
        } catch (e: Exception) {
            Log.e(tag, "SOAP request failed: $soapAction, ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Send SOAP action - with retries
     */
    private suspend fun sendSoapAction(action: String, serviceType: String, body: String): Boolean = withContext(Dispatchers.IO) {
        val maxRetries = 3
        
        for (attempt in 1..maxRetries) {
            try {
                val avTransportUrl = buildAVTransportUrl() ?: return@withContext false
                
                val soapEnvelope = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                        <s:Body>
                            $body
                        </s:Body>
                    </s:Envelope>
                """.trimIndent()
                
                val success = sendHttpSoapRequest(avTransportUrl, "$serviceType#$action", soapEnvelope)
                if (success || attempt == maxRetries) {
                    return@withContext success
                }
                
                delay(1000L * attempt)
                
            } catch (e: Exception) {
                if (attempt < maxRetries) {
                    delay(1000L * attempt)
                }
            }
        }
        
        false
    }

    /**
     * Send RenderingControl SOAP action
     */
    private suspend fun sendRenderingControlAction(action: String, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val renderingControlUrl = buildServiceUrl("RenderingControl", "RenderingControl/control")
                ?: return@withContext false
            
            val maxRetries = 3
            
            for (attempt in 1..maxRetries) {
                try {
                    val soapEnvelope = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                            <s:Body>
                                $body
                            </s:Body>
                        </s:Envelope>
                    """.trimIndent()
                    
                    val success = sendHttpSoapRequest(renderingControlUrl, "urn:schemas-upnp-org:service:RenderingControl:1#$action", soapEnvelope)
                    if (success || attempt == maxRetries) {
                        return@withContext success
                    }
                    
                    delay(1000L * attempt)
                    
                } catch (e: Exception) {
                    if (attempt < maxRetries) {
                        delay(1000L * attempt)
                    }
                }
            }
            
            false
            
        } catch (e: Exception) {
            Log.e(tag, "RenderingControl request failed: $action, ${e.message}")
            false
        }
    }
    
    /**
     * Send RenderingControl SOAP action with response
     */
    private suspend fun sendRenderingControlActionWithResponse(action: String, body: String): String? = withContext(Dispatchers.IO) {
        try {
            val renderingControlUrl = buildServiceUrl("RenderingControl", "RenderingControl/control")
                ?: return@withContext null
            
            val soapEnvelope = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        $body
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
            
            val connection = URL(renderingControlUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            connection.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:RenderingControl:1#$action\"")
            connection.setRequestProperty("User-Agent", "UPnPCast/1.0")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            
            connection.outputStream.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write(soapEnvelope)
                    writer.flush()
                }
            }
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                        reader.readText()
                    }
                }
            } else {
                null
            }
            
        } catch (e: Exception) {
            Log.e(tag, "RenderingControl request with response failed: $action, ${e.message}")
            null
        }
    }
    
    /**
     * Parse volume from response
     */
    private fun parseVolumeFromResponse(response: String): Int? {
        try {
            val volumePattern = "<CurrentVolume>(.*?)</CurrentVolume>".toRegex()
            val match = volumePattern.find(response)
            return match?.groupValues?.get(1)?.toIntOrNull()
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse volume response: ${e.message}")
            return null
        }
    }
    
    /**
     * Parse mute state from response
     */
    private fun parseMuteFromResponse(response: String): Boolean? {
        try {
            val mutePattern = "<CurrentMute>(.*?)</CurrentMute>".toRegex()
            val match = mutePattern.find(response)
            val muteValue = match?.groupValues?.get(1)
            return when (muteValue) {
                "1", "true", "True" -> true
                "0", "false", "False" -> false
                else -> null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse mute response: ${e.message}")
            return null
        }
    }
    
    /**
     * XML escape
     */
    private fun escapeXmlContent(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
    
    /**
     * URL escape
     */
    private fun escapeXmlUrl(url: String): String {
        return url
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
    
    /**
     * Create DIDL-Lite metadata - simplified version
     */
    private fun createMetadata(title: String, episodeLabel: String, mediaUrl: String = ""): String {
        val displayTitle = if (episodeLabel.isNotEmpty()) "$title - $episodeLabel" else title
        val safeDisplayTitle = escapeXmlContent(displayTitle)
        val safeMediaUrl = escapeXmlUrl(mediaUrl)
        
        val mediaType = when {
            mediaUrl.contains(".mp4", ignoreCase = true) -> "video/mp4"
            mediaUrl.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
            mediaUrl.contains(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
            mediaUrl.contains(".mp3", ignoreCase = true) -> "audio/mpeg"
            else -> "video/mp4"
        }
        
        val upnpClass = if (mediaType.startsWith("video") || mediaType.contains("mpegurl")) {
            "object.item.videoItem"
        } else {
            "object.item.audioItem.musicTrack"
        }
        
        return """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
    <item id="1" parentID="0" restricted="1">
        <dc:title>$safeDisplayTitle</dc:title>
        <upnp:class>$upnpClass</upnp:class>
        <res protocolInfo="http-get:*:$mediaType:*">$safeMediaUrl</res>
    </item>
</DIDL-Lite>"""
    }
    
    /**
     * Time format
     */
    private fun formatTime(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    /**
     * Get current volume
     */
    suspend fun getVolumeAsync(): Int? = withContext(Dispatchers.IO) {
        if (!checkAvailable()) return@withContext null
        
        try {
            val response = sendRenderingControlActionWithResponse(
                action = "GetVolume",
                body = """
                    <u:GetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                        <InstanceID>0</InstanceID>
                        <Channel>Master</Channel>
                    </u:GetVolume>
                """.trimIndent()
            )
            
            response?.let { parseVolumeFromResponse(it) }
        } catch (e: Exception) {
            Log.e(tag, "Failed to get volume: ${e.message}")
            null
        }
    }
    
    /**
     * Get current mute state
     */
    suspend fun getMuteAsync(): Boolean? = withContext(Dispatchers.IO) {
        if (!checkAvailable()) return@withContext null
        
        try {
            val response = sendRenderingControlActionWithResponse(
                action = "GetMute",
                body = """
                    <u:GetMute xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
                        <InstanceID>0</InstanceID>
                        <Channel>Master</Channel>
                    </u:GetMute>
                """.trimIndent()
            )
            
            response?.let { parseMuteFromResponse(it) }
        } catch (e: Exception) {
            Log.e(tag, "Failed to get mute state: ${e.message}")
            null
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        coroutineScope.cancel()
        isReleased = true
    }
} 