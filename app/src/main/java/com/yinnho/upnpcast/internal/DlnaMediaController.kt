package com.yinnho.upnpcast.internal

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

/**
 * DLNA media controller - SOAP control implementation
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
            deviceControllers.remove(deviceId)?.release()
        }
        
        fun clearAllControllers() {
            deviceControllers.values.forEach { it.release() }
            deviceControllers.clear()
        }
        
        fun getControllerCount(): Int = deviceControllers.size
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

    fun buildAVTransportUrl(): String? {
        return buildServiceUrl("AVTransport", "AVTransport/control")
    }
    
    /**
     * 播放媒体
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
            Log.e(TAG, "Failed to play media: ${e.message}")
            false
        }
    }
    
    /**
     * 停止播放
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
            Log.e(TAG, "Exception stopping playback: ${e.message}")
            false
        }
    }
    
    /**
     * 暂停播放
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
            Log.e(TAG, "Failed to pause playback: ${e.message}")
            false
        }
    }
    
    /**
     * 开始播放
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
            Log.e(TAG, "Failed to start playing: ${e.message}")
            false
        }
    }
    
    /**
     * 设置媒体URI
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
            Log.e(TAG, "Failed to set media URI: ${e.message}")
            false
        }
    }
    
    /**
     * 跳转到指定位置
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
            Log.e(TAG, "Failed to seek: ${e.message}")
            false
        }
    }
    
    /**
     * 设置音量
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
            Log.e(TAG, "Failed to set volume: ${e.message}")
            false
        }
    }
    
    /**
     * 设置静音
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
            Log.e(TAG, "Failed to set mute: ${e.message}")
            false
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
            Log.e(TAG, "SOAP request failed: $soapAction, ${e.message}")
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 发送SOAP action - with retries
     */
    suspend fun sendSoapAction(action: String, serviceType: String, body: String): Boolean = withContext(Dispatchers.IO) {
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
     * 发送RenderingControl SOAP action
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
            Log.e(TAG, "RenderingControl request failed: $action, ${e.message}")
            false
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
     * Release resources
     */
    fun release() {
        coroutineScope.cancel()
        isReleased = true
    }
} 