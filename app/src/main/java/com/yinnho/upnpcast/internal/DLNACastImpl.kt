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
    
    // 已通知过的设备ID集合，用于增量回调
    private val notifiedDeviceIds = mutableSetOf<String>()
    
    // ================ 核心API实现 ================
    
    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
        ssdpDiscovery = SsdpDeviceDiscovery(
            onDeviceFound = { device ->
                // 直接处理设备发现，不再需要Registry监听器
                addDevice(device)
            }
        )
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
            // 直接获取当前已发现的设备，不再重新搜索
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
            val existingDevice = devices[device.id]
            if (existingDevice != null) {
                // 设备存在，直接投屏
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
            ssdpDiscovery?.startSearch()
            
            // 超时回调：只有在设备数量有变化时才回调
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                searchCompleted = true
                val currentDevices = getAllDevices()
                // 优化：只有设备数量变化时才执行超时回调
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
                            // 简化实现，如需要可以扩展
                            true
                        }
                        DLNACast.MediaAction.GET_STATE -> {
                            // 返回状态查询结果
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
    
    // ================ 私有实现方法 ================
    
    private fun ensureInitialized(action: () -> Unit) {
        if (ssdpDiscovery == null) {
            Log.d(TAG, "SsdpDiscovery is null, attempting to initialize...")
            val context = contextRef?.get()
            if (context != null) {
                init(context)
                Log.d(TAG, "Re-initialized successfully")
            } else {
                Log.e(TAG, "DLNACast not initialized! Call DLNACast.init(context) first")
                return
            }
        } else {
            Log.d(TAG, "SsdpDiscovery is available, proceeding with action")
        }
        action()
    }
    
    private fun selectBestDevice(devices: List<DLNACast.Device>): DLNACast.Device {
        // 优先选择电视设备，其次选择第一个设备
        return devices.find { it.isTV } ?: devices.first()
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
        return devices.values
            .map { convertToDevice(it) }
            .sortedByDescending { it.isTV } // TV设备优先
    }
    
    private fun notifyNewDevicesOnly() {
        currentDeviceListCallback?.let { callback: (devices: List<DLNACast.Device>) -> Unit ->
            // 只有在搜索未完成时才回调
            if (!searchCompleted) {
                val allDevices = getAllDevices()
                val newDevices = allDevices.filter { !notifiedDeviceIds.contains(it.id) }
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
        // 根据制造商和型号判断是否为电视设备
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
    
    private fun convertToRemoteDevice(device: DLNACast.Device): RemoteDevice {
        // 从设备ID找到对应的RemoteDevice
        return devices[device.id]
            ?: throw IllegalArgumentException("Device not found: ${device.id}")
    }
    
    // ================ 内置设备管理方法 ================
    
    private fun addDevice(device: RemoteDevice) {
        devices[device.id] = device
        notifyNewDevicesOnly()
    }
    
    private fun removeDevice(device: RemoteDevice) {
        devices.remove(device.id)
        handleDeviceRemoved(device)
    }
    
    private fun getDevices(): Collection<RemoteDevice> = devices.values
    
    private fun getDevice(id: String): RemoteDevice? = devices[id]
} 
