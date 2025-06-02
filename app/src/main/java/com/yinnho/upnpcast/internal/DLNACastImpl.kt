package com.yinnho.upnpcast.internal
import android.content.Context
import android.util.Log
import com.yinnho.upnpcast.DLNACast
import java.lang.ref.WeakReference
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
/**
 * DLNACast的内部实现类
 * 包含所有具体的DLNA投屏逻辑
 */
internal object DLNACastImpl {
    private const val TAG = "DLNACastImpl"
    // 内置设备存储，替代Registry
    private val devices = ConcurrentHashMap<String, RemoteDevice>()
    private var ssdpDiscovery: SsdpDeviceDiscovery? = null
    private var currentDevice: RemoteDevice? = null
    private var contextRef: WeakReference<Context>? = null
    @Volatile
    private var currentDeviceListCallback: ((devices: List<DLNACast.Device>) -> Unit)? = null
    // 搜索超时标志
    @Volatile
    private var searchCompleted = false
    // 已通知过的设备ID集合，用于增量回�?
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
    fun castTo(url: String, title: String?, deviceSelector: (devices: List<DLNACast.Device>) -> DLNACast.Device?) {
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
    fun castToDevice(device: DLNACast.Device, url: String, title: String?, callback: (success: Boolean) -> Unit) {
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
    fun search(timeout: Long, callback: (devices: List<DLNACast.Device>) -> Unit) {
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
    fun control(action: DLNACast.MediaAction, value: Any?, callback: (success: Boolean) -> Unit) {
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
                        DLNACast.MediaAction.PLAY -> controller.play()
                        DLNACast.MediaAction.PAUSE -> controller.pause()
                        DLNACast.MediaAction.STOP -> controller.stopDirect()
                        DLNACast.MediaAction.VOLUME -> {
                            val volume = value as? Int ?: return@runBlocking false
                            controller.setVolumeAsync(volume)
                        }
                        DLNACast.MediaAction.MUTE -> {
                            val mute = value as? Boolean ?: true
                            controller.setMuteAsync(mute)
                        }
                        DLNACast.MediaAction.SEEK -> {
                            // 简化实现，如需要可以扩�?
                            true
                        }
                        DLNACast.MediaAction.GET_STATE -> {
                            // 返回状态查询结�?
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
    fun getState(): DLNACast.State {
        val device = currentDevice?.let { convertToDevice(it) }
        val playbackState = if (device != null) DLNACast.PlaybackState.PLAYING else DLNACast.PlaybackState.IDLE
        return DLNACast.State(
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
    private fun selectBestDevice(devices: List<DLNACast.Device>): DLNACast.Device {
        return devices.find { it.isTV } ?: devices.first()
    }
    private fun connectAndPlay(device: DLNACast.Device, url: String, title: String, callback: (success: Boolean) -> Unit) {
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
    private fun getAllDevices(): List<DLNACast.Device> {
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
    private fun convertToDevice(remoteDevice: RemoteDevice): DLNACast.Device {
        val manufacturer = remoteDevice.manufacturer.lowercase()
        val model = (remoteDevice.details["modelName"] as? String ?: "").lowercase()
        val isTV = manufacturer.contains("tv") || model.contains("tv") || 
                  manufacturer.contains("samsung") || manufacturer.contains("lg") ||
                  manufacturer.contains("sony") || manufacturer.contains("xiaomi")
        return DLNACast.Device(
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
