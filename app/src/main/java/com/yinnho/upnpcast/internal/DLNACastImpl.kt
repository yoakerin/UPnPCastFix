package com.yinnho.upnpcast.internal

import android.content.Context
import android.util.Log
import com.yinnho.upnpcast.types.Device
import com.yinnho.upnpcast.types.MediaAction
import com.yinnho.upnpcast.types.PlaybackState
import com.yinnho.upnpcast.types.State
import java.lang.ref.WeakReference
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal implementation class for DLNACast
 * Contains all concrete DLNA casting logic
 */
internal object DLNACastImpl {
    
    private const val TAG = "DLNACastImpl"
    
    // Built-in device storage, replacing Registry
    private val devices = ConcurrentHashMap<String, RemoteDevice>()
    private var ssdpDiscovery: SsdpDeviceDiscovery? = null
    private var currentDevice: RemoteDevice? = null
    private var contextRef: WeakReference<Context>? = null
    
    @Volatile
    private var currentDeviceListCallback: ((devices: List<Device>) -> Unit)? = null
    
    // Search timeout flag
    @Volatile
    private var searchCompleted = false
    
    // Set of notified device IDs for incremental callbacks
    private val notifiedDeviceIds = mutableSetOf<String>()

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
        ssdpDiscovery = SsdpDeviceDiscovery(
            onDeviceFound = { device ->
                addDevice(device)
            }
        )
    }

    fun cast(url: String, title: String?, callback: (success: Boolean) -> Unit) {
        ensureInitialized {
            search(10000L) { devices ->
                if (devices.isNotEmpty()) {
                    connectAndPlay(selectBestDevice(devices), url, title ?: "Media", callback)
                } else {
                    callback(false)
                }
            }
        }
    }

    fun castTo(url: String, title: String?, deviceSelector: (devices: List<Device>) -> Device?) {
        ensureInitialized {
            val currentDevices = getAllDevices()
            if (currentDevices.isNotEmpty()) {
                deviceSelector(currentDevices)?.let { device ->
                    connectAndPlay(device, url, title ?: "Media") { }
                }
            } else {
                search(5000L) { devices ->
                    deviceSelector(devices)?.let { device ->
                        connectAndPlay(device, url, title ?: "Media") { }
                    }
                }
            }
        }
    }

    fun castTo(url: String, title: String?, callback: (success: Boolean) -> Unit, deviceSelector: (devices: List<Device>) -> Device?) {
        ensureInitialized {
            val currentDevices = getAllDevices()
            if (currentDevices.isNotEmpty()) {
                deviceSelector(currentDevices)?.let { device ->
                    connectAndPlay(device, url, title ?: "Media", callback)
                } ?: callback(false)
            } else {
                search(5000L) { devices ->
                    deviceSelector(devices)?.let { device ->
                        connectAndPlay(device, url, title ?: "Media", callback)
                    } ?: callback(false)
                }
            }
        }
    }

    fun castToDevice(device: Device, url: String, title: String?, callback: (success: Boolean) -> Unit) {
        ensureInitialized {
            if (devices.containsKey(device.id)) {
                connectAndPlay(device, url, title ?: "Media", callback)
            } else {
                search(5000L) { devices ->
                    devices.find { it.id == device.id }?.let { foundDevice ->
                        connectAndPlay(foundDevice, url, title ?: "Media", callback)
                    } ?: callback(false)
                }
            }
        }
    }

    fun search(timeout: Long, callback: (devices: List<Device>) -> Unit) {
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

    fun control(action: MediaAction, value: Any?, callback: (success: Boolean) -> Unit) {
        val device = currentDevice
        if (device == null) {
            callback(false)
            return
        }
        val controller = DlnaMediaController.getController(device)
        Thread {
            try {
                val success = runBlocking {
                    when (action) {
                        MediaAction.PLAY -> controller.play()
                        MediaAction.PAUSE -> controller.pause()
                        MediaAction.STOP -> controller.stopDirect()
                        MediaAction.VOLUME -> {
                            val volume = value as? Int ?: return@runBlocking false
                            controller.setVolumeAsync(volume)
                        }
                        MediaAction.MUTE -> {
                            val mute = value as? Boolean ?: true
                            controller.setMuteAsync(mute)
                        }
                        MediaAction.SEEK -> {
                            // Simplified implementation, can be extended if needed
                            true
                        }
                        MediaAction.GET_STATE -> {
                            // Return status query result
                            true
                        }
                    }
                }
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "Control action failed: $action", e)
                callback(false)
            }
        }.start()
    }

    fun getState(): State {
        val device = currentDevice?.let { convertToDevice(it) }
        val playbackState = if (device != null) PlaybackState.PLAYING else PlaybackState.IDLE
        return State(
            isConnected = device != null,
            currentDevice = device,
            playbackState = playbackState
        )
    }

    fun release() {
        ssdpDiscovery?.shutdown()
        ssdpDiscovery = null
        devices.clear()
        currentDevice = null
        contextRef = null
        currentDeviceListCallback = null
        searchCompleted = false
        notifiedDeviceIds.clear()
        DlnaMediaController.clearAllControllers()
    }

    private fun ensureInitialized(action: () -> Unit) {
        if (ssdpDiscovery == null) {
            contextRef?.get()?.let { context ->
                init(context)
            } ?: return
        }
        action()
    }

    private fun selectBestDevice(devices: List<Device>): Device {
        return devices.find { it.isTV } ?: devices.first()
    }

    private fun connectAndPlay(device: Device, url: String, title: String, callback: (success: Boolean) -> Unit) {
        try {
            val remoteDevice = devices[device.id] ?: throw IllegalArgumentException("Device not found: ${device.id}")
            val services = remoteDevice.details["services"] as? List<*>
            if (!services.isNullOrEmpty()) {
                currentDevice = remoteDevice
                val controller = DlnaMediaController.getController(remoteDevice)
                Thread {
                    try {
                        val success = runBlocking { controller.playMediaDirect(url, title) }
                        callback(success)
                    } catch (e: Exception) {
                        callback(false)
                    }
                }.start()
            } else {
                callback(false)
            }
        } catch (e: Exception) {
            callback(false)
        }
    }

    private fun getAllDevices(): List<Device> {
        return devices.values.map { convertToDevice(it) }.sortedByDescending { it.isTV }
    }

    private fun notifyNewDevicesOnly() {
        if (!searchCompleted) {
            currentDeviceListCallback?.let { callback ->
                val allDevices = getAllDevices()
                val newDevices = allDevices.filter { !notifiedDeviceIds.contains(it.id) }
                notifiedDeviceIds.addAll(newDevices.map { it.id })
                callback(newDevices)
            }
        }
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
} 
