package com.yinnho.upnpcast.internal

import android.content.Context
import android.util.Log
import com.yinnho.upnpcast.types.Device
import com.yinnho.upnpcast.types.MediaAction
import com.yinnho.upnpcast.types.State
import com.yinnho.upnpcast.internal.core.CoreManager
import com.yinnho.upnpcast.internal.localcast.LocalCastManager

/**
 * Internal implementation class for DLNACast
 * Contains all concrete DLNA casting logic
 */
internal object DLNACastImpl {
    
    private const val TAG = "DLNACastImpl"
    
    // Search related constants
    private const val DEFAULT_SEARCH_TIMEOUT = 5000L
    private const val DEVICE_SEARCH_TIMEOUT = 10000L

    fun init(context: Context) {
        CoreManager.init(context)
    }

    fun cast(url: String, title: String?, callback: (success: Boolean) -> Unit) {
        CoreManager.searchDevices(DEVICE_SEARCH_TIMEOUT) { devices ->
            if (devices.isNotEmpty()) {
                CoreManager.connectAndPlay(CoreManager.selectBestDevice(devices), url, title ?: "Media", callback)
            } else {
                callback(false)
            }
        }
    }

    fun castTo(url: String, title: String?, deviceSelector: (devices: List<Device>) -> Device?) {
        val currentDevices = CoreManager.getAllDevices()
        if (currentDevices.isNotEmpty()) {
            deviceSelector(currentDevices)?.let { device ->
                CoreManager.connectAndPlay(device, url, title ?: "Media") { }
            }
        } else {
            CoreManager.searchDevices(DEFAULT_SEARCH_TIMEOUT) { devices ->
                deviceSelector(devices)?.let { device ->
                    CoreManager.connectAndPlay(device, url, title ?: "Media") { }
                }
            }
        }
    }

    fun castTo(url: String, title: String?, callback: (success: Boolean) -> Unit, deviceSelector: (devices: List<Device>) -> Device?) {
        val currentDevices = CoreManager.getAllDevices()
        if (currentDevices.isNotEmpty()) {
            deviceSelector(currentDevices)?.let { device ->
                CoreManager.connectAndPlay(device, url, title ?: "Media", callback)
            } ?: callback(false)
        } else {
            CoreManager.searchDevices(DEFAULT_SEARCH_TIMEOUT) { devices ->
                deviceSelector(devices)?.let { device ->
                    CoreManager.connectAndPlay(device, url, title ?: "Media", callback)
                } ?: callback(false)
            }
        }
    }

    fun castToDevice(device: Device, url: String, title: String?, callback: (success: Boolean) -> Unit) {
        val foundDevice = CoreManager.findDevice(device.id)
        if (foundDevice != null) {
            CoreManager.connectAndPlay(foundDevice, url, title ?: "Media", callback)
        } else {
            CoreManager.searchDevices(DEFAULT_SEARCH_TIMEOUT) { devices ->
                devices.find { it.id == device.id }?.let { foundDevice ->
                    CoreManager.connectAndPlay(foundDevice, url, title ?: "Media", callback)
                } ?: callback(false)
            }
        }
    }

    fun search(timeout: Long, callback: (devices: List<Device>) -> Unit) {
        CoreManager.searchDevices(timeout, callback)
    }

    fun control(action: MediaAction, value: Any?, callback: (success: Boolean) -> Unit) {
        CoreManager.controlMedia(action, value, callback)
    }

    fun getState(): State {
        return CoreManager.getCurrentState()
    }

    fun getProgress(callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit) {
        CoreManager.getProgress(callback)
    }

    fun castLocalFile(filePath: String, device: Device, title: String?, callback: (success: Boolean, message: String) -> Unit) {
        CoreManager.castLocalFileToDevice(filePath, device, title, callback)
    }
    
    fun castLocalFile(filePath: String, title: String?, callback: (success: Boolean, message: String) -> Unit) {
        CoreManager.searchDevices(DEFAULT_SEARCH_TIMEOUT) { devices ->
            if (devices.isNotEmpty()) {
                val selectedDevice = CoreManager.selectBestDevice(devices)
                castLocalFile(filePath, selectedDevice, title, callback)
            } else {
                callback(false, "No available DLNA devices found")
            }
        }
    }
    
    fun getLocalFileUrl(filePath: String): String? {
        val context = CoreManager.getContext() ?: return null
        return LocalCastManager.getLocalFileUrl(context, filePath)
    }

    /**
     * Scan local video files
     * 
     * @param context Android context for accessing ContentResolver
     * @param callback Scan completion callback, returns video file list
     */
    fun scanLocalVideos(context: Context, callback: (videos: List<com.yinnho.upnpcast.DLNACast.LocalVideo>) -> Unit) {
        LocalCastManager.scanLocalVideos(context, callback)
    }
    
    /**
     * Launch video selector
     */
    fun showVideoSelector(context: Context, device: Device) {
        try {
            // Convert type: from types.Device to DLNACast.Device
            val dlnaCastDevice = com.yinnho.upnpcast.DLNACast.Device(
                id = device.id,
                name = device.name,
                address = device.address,
                isTV = device.isTV
            )
            VideoSelectorActivity.start(context, dlnaCastDevice)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch video selector: ${e.message}")
        }
    }

    fun release() {
        CoreManager.cleanup()
    }
} 
