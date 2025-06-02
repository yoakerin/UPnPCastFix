package com.yinnho.upnpcast.internal

import android.content.Context
import android.util.Log
import com.yinnho.upnpcast.DLNACast
import java.lang.ref.WeakReference
import kotlinx.coroutines.runBlocking

/**
 * DLNACast的内部实现类
 * 包含所有具体的DLNA投屏逻辑
 */
internal object DLNACastImpl {
    
    private const val TAG = "DLNACastImpl"
    private var registry: Registry? = null
    private var currentDevice: RemoteDevice? = null
    private var contextRef: WeakReference<Context>? = null
    
    @Volatile
    private var currentDeviceListCallback: ((devices: List<DLNACast.Device>) -> Unit)? = null
    
    // 搜索超时标志
    @Volatile
    private var searchCompleted = false
    
    // 已通知过的设备ID集合，用于增量回�?
    private val notifiedDeviceIds = mutableSetOf<String>()
    
    // 设备发现监听�?
    private val deviceListener = object : RegistryListener {
        override fun deviceAdded(registry: Registry, device: RemoteDevice) {
            // 增量回调：只通知新发现的设备
            notifyNewDevicesOnly()
        }
        
        override fun deviceRemoved(registry: Registry, device: RemoteDevice) {
            // 设备移除：更新已通知集合并回调变�?
            handleDeviceRemoved(device)
        }
        
        override fun deviceUpdated(registry: Registry, device: RemoteDevice) {
            // 设备更新：如果是新设备就通知
            notifyNewDevicesOnly()
        }
    }
    
    // ================ 核心API实现 ================
    
    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
        registry = RegistryImpl()
        registry?.addListener(deviceListener)
    }
    
    fun cast(url: String, title: String?, callback: (success: Boolean) -> Unit) {
        ensureInitialized {
            search(10000L) { devices: List<DLNACast.Device> ->
                if (devices.isNotEmpty()) {
                    val bestDevice = selectBestDevice(devices)
                    connectAndPlay(bestDevice, url, title ?: "Media", callback)
                } else {
                    callback(false)
                }
            }
        }
    }
    
    fun castTo(url: String, title: String?, deviceSelector: (devices: List<DLNACast.Device>) -> DLNACast.Device?) {
        ensureInitialized {
            // 直接获取当前已发现的设备，不再重新搜�?
            val currentDevices = getAllDevices()
            if (currentDevices.isNotEmpty()) {
                val selectedDevice = deviceSelector(currentDevices)
                if (selectedDevice != null) {
                    connectAndPlay(selectedDevice, url, title ?: "Media") { }
                }
            } else {
                // 如果没有设备，先搜索再选择
                search(5000L) { devices: List<DLNACast.Device> ->
                    if (devices.isNotEmpty()) {
                        val selectedDevice = deviceSelector(devices)
                        if (selectedDevice != null) {
                            connectAndPlay(selectedDevice, url, title ?: "Media") { }
                        }
                    }
                }
            }
        }
    }
    
    fun castToDevice(device: DLNACast.Device, url: String, title: String?, callback: (success: Boolean) -> Unit) {
        Log.d(TAG, "castToDevice called: device=${device.name}, url=$url, title=$title")
        ensureInitialized {
            // 先检查设备是否在注册表中
            val existingDevice = registry?.getDevices()?.find { it.id == device.id }
            if (existingDevice != null) {
                // 设备存在，直接投�?
                connectAndPlay(device, url, title ?: "Media", callback)
            } else {
                // 设备不存在，先搜索再投屏
                Log.d(TAG, "Device not found in registry, searching...")
                search(5000L) { devices ->
                    val foundDevice = devices.find { it.id == device.id }
                    if (foundDevice != null) {
                        connectAndPlay(foundDevice, url, title ?: "Media", callback)
                    } else {
                        Log.e(TAG, "Device not found after search: ${device.name}")
                        callback(false)
                    }
                }
            }
        }
    }
    
    fun search(timeout: Long, callback: (devices: List<DLNACast.Device>) -> Unit) {
        ensureInitialized {
            currentDeviceListCallback = callback
            searchCompleted = false
            notifiedDeviceIds.clear()
            registry?.startDiscovery()
            
            // 超时回调：只有在设备数量有变化时才回�?
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                searchCompleted = true
                val currentDevices = getAllDevices()
                // 优化：只有设备数量变化时才执行超时回�?
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
        registry?.removeListener(deviceListener)
        (registry as? RegistryImpl)?.shutdown()
        registry = null
        currentDevice = null
        contextRef = null
        currentDeviceListCallback = null
        searchCompleted = false
        notifiedDeviceIds.clear()
        
        DlnaMediaController.clearAllControllers()
    }
    
    // ================ 私有实现方法 ================
    
    private fun ensureInitialized(action: () -> Unit) {
        if (registry == null) {
            Log.d(TAG, "Registry is null, attempting to initialize...")
            val context = contextRef?.get()
            if (context != null) {
                init(context)
                Log.d(TAG, "Re-initialized successfully")
            } else {
                Log.e(TAG, "DLNACast not initialized! Call DLNACast.init(context) first")
                return
            }
        } else {
            Log.d(TAG, "Registry is available, proceeding with action")
        }
        action()
    }
    
    private fun selectBestDevice(devices: List<DLNACast.Device>): DLNACast.Device {
        return devices.find { it.isTV } 
            ?: devices.find { it.isBox } 
            ?: devices.first()
    }
    
    private fun connectAndPlay(device: DLNACast.Device, url: String, title: String, callback: (success: Boolean) -> Unit) {
        try {
            val remoteDevice = convertToRemoteDevice(device)
            if (connectToDevice(remoteDevice)) {
                playMedia(remoteDevice, url, title, callback)
            } else {
                Log.e(TAG, "Failed to connect to device: ${device.name}")
                callback(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert or connect to device: ${device.name}", e)
            callback(false)
        }
    }
    
    private fun connectToDevice(device: RemoteDevice): Boolean {
        return try {
            val services = device.details["services"] as? List<*>
            if (!services.isNullOrEmpty()) {
                currentDevice = device
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to device", e)
            false
        }
    }
    
    private fun playMedia(device: RemoteDevice, url: String, title: String, callback: (success: Boolean) -> Unit) {
        val controller = DlnaMediaController.getController(device)
        Thread {
            try {
                val success = runBlocking {
                    controller.playMediaDirect(url, title)
                }
                callback(success)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing media", e)
                callback(false)
            }
        }.start()
    }
    
    private fun getAllDevices(): List<DLNACast.Device> {
        return registry?.getDevices()
            ?.map { convertToDevice(it) }
            ?.sortedByDescending { it.priority }
            ?: emptyList()
    }
    
    private fun notifyNewDevicesOnly() {
        currentDeviceListCallback?.let { callback: (devices: List<DLNACast.Device>) -> Unit ->
            // 只有在搜索未完成时才回调
            if (!searchCompleted) {
                val devices = getAllDevices()
                val newDevices = devices.filter { !notifiedDeviceIds.contains(it.id) }
                notifiedDeviceIds.addAll(newDevices.map { it.id })
                callback(newDevices)
            }
        }
    }
    
    private fun handleDeviceRemoved(device: RemoteDevice) {
        notifiedDeviceIds.remove(device.id)
        notifyNewDevicesOnly()
    }
    
    // ================ 类型转换方法 ================
    
    private fun convertToDevice(remoteDevice: RemoteDevice): DLNACast.Device {
        // 根据制造商和型号判断设备类�?
        val manufacturer = remoteDevice.manufacturer.lowercase()
        val model = (remoteDevice.details["modelName"] as? String ?: "").lowercase()
        
        val isTV = manufacturer.contains("tv") || model.contains("tv") || 
                  manufacturer.contains("samsung") || manufacturer.contains("lg") ||
                  manufacturer.contains("sony") || manufacturer.contains("xiaomi")
        
        val isBox = !isTV && (manufacturer.contains("box") || model.contains("box") ||
                             manufacturer.contains("roku") || manufacturer.contains("apple"))
        
        val priority = when {
            isTV -> 100
            isBox -> 80
            else -> 60
        }
        
        return DLNACast.Device(
            id = remoteDevice.id,
            name = remoteDevice.displayName,
            address = remoteDevice.address,
            manufacturer = remoteDevice.manufacturer,
            model = remoteDevice.details["modelName"] as? String ?: "Unknown",
            isTV = isTV,
            isBox = isBox,
            priority = priority
        )
    }
    
    private fun convertToRemoteDevice(device: DLNACast.Device): RemoteDevice {
        // 从设备ID找到对应的RemoteDevice
        return registry?.getDevices()?.find { it.id == device.id }
            ?: throw IllegalArgumentException("Device not found: ${device.id}")
    }
} 
