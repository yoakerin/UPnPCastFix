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
import com.yinnho.upnpcast.internal.localcast.LocalCastManager
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

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
            val device = currentDevice
            if (device == null) {
                callback(false)
                return
            }
            MediaPlayer.controlMedia(device, action, value, callback)
        }
        
        /**
         * Get playback progress
         */
        fun getProgress(callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit) {
            val device = currentDevice
            if (device == null) {
                callback(0L, 0L, false)
                return
            }
            MediaPlayer.getProgress(device, callback)
        }
        
        /**
         * Get current state
         */
        fun getCurrentState(): State {
            val device = currentDevice?.let { convertToDevice(it) }
            val playbackState = if (device != null) PlaybackState.PLAYING else PlaybackState.IDLE
            return State(
                isConnected = device != null,
                currentDevice = device,
                playbackState = playbackState
            )
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
            MediaPlayer.cleanup()
            LocalCastManager.cleanup()
            Log.i(TAG, "CoreManager cleaned up")
        }
        
        // Private methods
        
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