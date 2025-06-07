package com.yinnho.upnpcast.internal.core

import android.content.Context
import android.util.Log
import com.yinnho.upnpcast.types.Device
import com.yinnho.upnpcast.types.MediaAction
import com.yinnho.upnpcast.types.PlaybackState
import com.yinnho.upnpcast.types.State
import com.yinnho.upnpcast.internal.discovery.RemoteDevice
import com.yinnho.upnpcast.internal.discovery.SsdpDeviceDiscovery
import com.yinnho.upnpcast.internal.media.MediaPlayer
import com.yinnho.upnpcast.internal.media.DlnaMediaController
import com.yinnho.upnpcast.internal.localcast.LocalCastManager
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

/**
 * Core manager
 * Responsible for unified management of DLNA casting core functionality
 * 
 * Core management logic extracted from DLNACastImpl
 */
internal class CoreManager {
    
    companion object {
        private const val TAG = "CoreManager"
        
        // Device storage and current state
        private val devices = ConcurrentHashMap<String, RemoteDevice>()
        private var ssdpDiscovery: SsdpDeviceDiscovery? = null
        private var currentDevice: RemoteDevice? = null
        private var contextRef: WeakReference<Context>? = null
        
        @Volatile
        private var currentDeviceListCallback: ((devices: List<Device>) -> Unit)? = null
        
        @Volatile
        private var searchCompleted = false
        
        private val notifiedDeviceIds = mutableSetOf<String>()
        
        // Volume cache related fields
        @Volatile
        private var cachedVolume: Int = -1
        @Volatile
        private var cachedMuted: Boolean = false
        @Volatile
        private var lastVolumeUpdate: Long = 0
        
        // Progress cache related fields
        @Volatile
        private var cachedCurrentMs: Long = 0L
        @Volatile
        private var cachedTotalMs: Long = 0L
        @Volatile
        private var lastProgressUpdate: Long = 0L
        @Volatile
        private var isPlaying: Boolean = false
        
        private const val VOLUME_CACHE_DURATION = 5000L // 5-second volume cache validity period
        private const val PROGRESS_CACHE_DURATION = 3000L // 3-second progress cache validity period
        
        /**
         * Initialize core manager
         */
        fun init(context: Context) {
            contextRef = WeakReference(context.applicationContext)
            ssdpDiscovery = SsdpDeviceDiscovery(
                onDeviceFound = { device ->
                    addDevice(device)
                }
            )
            Log.i(TAG, "CoreManager initialized")
        }
        
        /**
         * Device search
         */
        fun searchDevices(timeout: Long, callback: (devices: List<Device>) -> Unit) {
            ensureInitialized {
                currentDeviceListCallback = callback
                searchCompleted = false
                notifiedDeviceIds.clear()
                ssdpDiscovery?.startSearch()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    searchCompleted = true
                    val currentDevices = getAllDevices()
                    if (currentDevices.size != notifiedDeviceIds.size) {
                        callback(currentDevices)
                    }
                    currentDeviceListCallback = null
                }, timeout)
            }
        }
        
        /**
         * Connect and play media
         */
        fun connectAndPlay(device: Device, url: String, title: String, callback: (success: Boolean) -> Unit) {
            try {
                val remoteDevice = devices[device.id] ?: throw IllegalArgumentException("Device not found: ${device.id}")
                val services = remoteDevice.details["services"] as? List<*>
                if (!services.isNullOrEmpty()) {
                    currentDevice = remoteDevice
                    MediaPlayer.playMedia(remoteDevice, url, title, callback)
                } else {
                    callback(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect and play: ${e.message}")
                callback(false)
            }
        }
        
        /**
         * Media control
         */
        fun controlMedia(action: MediaAction, value: Any?, callback: (success: Boolean) -> Unit) {
            executeWithDevice { device ->
                MediaPlayer.controlMedia(device, action, value, callback)
            } ?: callback(false)
        }
        
        /**
         * Get playback progress
         */
        fun getProgress(callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit) {
            executeWithDevice { device ->
                val now = System.currentTimeMillis()
                val timeSinceLastUpdate = now - lastProgressUpdate
                
                // If cache is still valid and has valid data, use interpolation to calculate current progress
                if (timeSinceLastUpdate < PROGRESS_CACHE_DURATION && cachedTotalMs > 0) {
                    val estimatedProgress = if (isPlaying) {
                        // Interpolation calculation: based on last retrieved progress + elapsed time
                        cachedCurrentMs + timeSinceLastUpdate
                    } else {
                        // Paused state, progress doesn't change
                        cachedCurrentMs
                    }
                    
                    callback(estimatedProgress.coerceAtMost(cachedTotalMs), cachedTotalMs, true)
                    
                    // Asynchronously refresh cache (update real progress)
                    refreshProgressCacheAsync()
                } else {
                    // Cache expired or first-time retrieval, request immediately
                    getProgressFromDevice(device, now, callback)
                }
            } ?: callback(0L, 0L, false)
        }
        
        /**
         * Get current volume
         */
        fun getVolume(callback: (volume: Int?, isMuted: Boolean?, success: Boolean) -> Unit) {
            executeWithDevice { device ->
                MediaPlayer.getCurrentVolume(device, callback)
            } ?: callback(null, null, false)
        }
        
        /**
         * Get current state
         */
        fun getCurrentState(): State {
            val device = currentDevice?.let { convertToDevice(it) }
            val playbackState = if (device != null) PlaybackState.PLAYING else PlaybackState.IDLE
            
            // If there's a connected device, try to refresh volume cache
            if (device != null && shouldUpdateVolumeCache()) {
                refreshVolumeCacheAsync()
            }
            
            return State(
                isConnected = device != null,
                currentDevice = device,
                playbackState = playbackState,
                volume = if (cachedVolume >= 0) cachedVolume else -1,
                isMuted = cachedMuted
            )
        }
        
        /**
         * Get real-time progress (force fetch from device, no cache)
         */
        fun getProgressRealtime(callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit) {
            executeWithDevice { device ->
                getProgressFromDevice(device, System.currentTimeMillis(), callback)
            } ?: callback(0L, 0L, false)
        }
        
        /**
         * Manually refresh volume cache
         */
        fun refreshVolumeCache(callback: (success: Boolean) -> Unit = {}) {
            executeWithDevice { device ->
                MediaPlayer.getCurrentVolume(device) { volume, isMuted, success ->
                    if (success && volume != null) {
                        updateVolumeCache(volume, isMuted ?: false)
                    }
                    callback(success)
                }
            } ?: callback(false)
        }
        
        /**
         * Manually refresh progress cache
         */
        fun refreshProgressCache(callback: (success: Boolean) -> Unit = {}) {
            executeWithDevice { device ->
                getProgressFromDevice(device, System.currentTimeMillis()) { _, _, success ->
                    callback(success)
                }
            } ?: callback(false)
        }
        
        /**
         * Clear progress cache (call when switching media)
         */
        fun clearProgressCache() {
            cachedCurrentMs = 0L
            cachedTotalMs = 0L
            lastProgressUpdate = 0L
            isPlaying = false
        }
        
        /**
         * Clear volume cache
         */
        fun clearVolumeCache() {
            cachedVolume = -1
            cachedMuted = false
            lastVolumeUpdate = 0L
        }
        
        /**
         * Cast local file to specified device
         */
        fun castLocalFileToDevice(filePath: String, device: Device, title: String?, callback: (success: Boolean, message: String) -> Unit) {
            ensureInitialized {
                val context = contextRef?.get()
                if (context == null) {
                    callback(false, "Context has been released, please reinitialize")
                    return@ensureInitialized
                }
                
                val remoteDevice = devices[device.id]
                if (remoteDevice == null) {
                    callback(false, "Device not found: ${device.id}")
                    return@ensureInitialized
                }
                
                currentDevice = remoteDevice
                LocalCastManager.castLocalFile(context, filePath, remoteDevice, title, callback)
            }
        }
        
        /**
         * Get all devices
         */
        fun getAllDevices(): List<Device> {
            return devices.values.map { convertToDevice(it) }
                .sortedWith(compareByDescending<Device> { it.isTV }.thenBy { it.name })
        }
        
        /**
         * Find device
         */
        fun findDevice(deviceId: String): Device? {
            return devices[deviceId]?.let { convertToDevice(it) }
        }
        
        /**
         * Select best device (prioritize TV)
         */
        fun selectBestDevice(devices: List<Device>): Device {
            return devices.find { it.isTV } ?: devices.first()
        }
        
        /**
         * Get Context
         */
        fun getContext(): Context? {
            return contextRef?.get()
        }
        
        /**
         * Clean up resources
         */
        fun cleanup() {
            ssdpDiscovery?.shutdown()
            ssdpDiscovery = null
            devices.clear()
            currentDevice = null
            contextRef = null
            currentDeviceListCallback = null
            searchCompleted = false
            notifiedDeviceIds.clear()
            
            // Clear cache
            clearVolumeCache()
            clearProgressCache()
            
            MediaPlayer.cleanup()
            LocalCastManager.cleanup()
            Log.i(TAG, "CoreManager cleaned up")
        }
        
        // Private helper methods - Extract common logic, reduce code duplication
        
        /**
         * Generic device check and execution method
         */
        private inline fun <T> executeWithDevice(action: (RemoteDevice) -> T): T? {
            return currentDevice?.let(action)
        }
        
        /**
         * Generic method for getting progress from device
         */
        private fun getProgressFromDevice(
            device: RemoteDevice, 
            timestamp: Long,
            callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit
        ) {
            MediaPlayer.getProgress(device) { currentMs, totalMs, success ->
                if (success) {
                    updateProgressCache(currentMs, totalMs, timestamp)
                }
                callback(currentMs, totalMs, success)
            }
        }
        
        /**
         * Generic method for updating progress cache
         */
        private fun updateProgressCache(currentMs: Long, totalMs: Long, timestamp: Long) {
            cachedCurrentMs = currentMs
            cachedTotalMs = totalMs
            lastProgressUpdate = timestamp
            updatePlayingState()
        }
        
        /**
         * Generic method for updating volume cache
         */
        private fun updateVolumeCache(volume: Int, muted: Boolean) {
            cachedVolume = volume
            cachedMuted = muted
            lastVolumeUpdate = System.currentTimeMillis()
        }
        
        /**
         * Check if volume cache needs updating
         */
        private fun shouldUpdateVolumeCache(): Boolean {
            return System.currentTimeMillis() - lastVolumeUpdate > VOLUME_CACHE_DURATION
        }
        
        /**
         * Asynchronously refresh volume cache
         */
        private fun refreshVolumeCacheAsync() {
            currentDevice?.let { device ->
                Thread {
                    try {
                        val controller = DlnaMediaController.getController(device)
                        runBlocking {
                            val volume = controller.getVolumeAsync()
                            val muted = controller.getMuteAsync()
                            
                            if (volume != null) {
                                updateVolumeCache(volume, muted ?: false)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to refresh volume cache: ${e.message}")
                    }
                }.start()
            }
        }
        
        /**
         * Asynchronously refresh progress cache
         */
        private fun refreshProgressCacheAsync() {
            currentDevice?.let { device ->
                Thread {
                    try {
                        val controller = DlnaMediaController.getController(device)
                        runBlocking {
                            val progressInfo = controller.getPositionInfo()
                            if (progressInfo != null) {
                                val (currentMs, totalMs) = progressInfo
                                updateProgressCache(currentMs, totalMs, System.currentTimeMillis())
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to refresh progress cache: ${e.message}")
                    }
                }.start()
            }
        }
        
        /**
         * Update playing state (for interpolation calculation)
         */
        private fun updatePlayingState() {
            // Simple playback state determination, can be obtained more precisely
            isPlaying = currentDevice != null
        }
        
        private fun ensureInitialized(action: () -> Unit) {
            if (ssdpDiscovery == null) {
                contextRef?.get()?.let { context ->
                    init(context)
                } ?: return
            }
            action()
        }
        
        private fun convertToDevice(remoteDevice: RemoteDevice): Device {
            val manufacturer = remoteDevice.manufacturer.lowercase()
            val model = (remoteDevice.details["modelName"] as? String ?: "").lowercase()
            val isTV = manufacturer.contains("tv") || model.contains("tv") || 
                      manufacturer.contains("samsung") || manufacturer.contains("lg") ||
                      manufacturer.contains("sony") || manufacturer.contains("xiaomi")
            return Device(
                id = remoteDevice.id,
                name = remoteDevice.displayName,
                address = remoteDevice.address,
                isTV = isTV
            )
        }
        
        private fun addDevice(device: RemoteDevice) {
            devices[device.id] = device
            notifyNewDevicesOnly()
        }
        
        private fun notifyNewDevicesOnly() {
            if (!searchCompleted) {
                currentDeviceListCallback?.let { callback ->
                    val allDevices = getAllDevices()
                    callback(allDevices)
                }
            }
        }
    }
} 