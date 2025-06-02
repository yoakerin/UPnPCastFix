# 🎯 UPnPCast 最佳实践指南

## 📋 目录
- [异步回调处理](#异步回调处理)
- [设备管理策略](#设备管理策略)
- [错误处理机制](#错误处理机制)
- [性能优化建议](#性能优化建议)
- [生产环境配置](#生产环境配置)

## 🔄 异步回调处理

### 1. 使用协程封装
```kotlin
class DLNACastHelper {
    // 封装搜索为协程
    suspend fun searchDevices(timeout: Long = 10000): List<Device> {
        return suspendCoroutine { continuation ->
            DLNACast.search(timeout) { devices ->
                continuation.resume(devices)
            }
        }
    }
    
    // 封装投屏为协程
    suspend fun castVideo(url: String, title: String? = null): Boolean {
        return suspendCoroutine { continuation ->
            DLNACast.cast(url, title) { success ->
                continuation.resume(success)
            }
        }
    }
    
    // 使用示例
    suspend fun performCast() {
        try {
            val devices = searchDevices()
            if (devices.isNotEmpty()) {
                val success = castVideo("http://example.com/video.mp4")
                if (success) {
                    println("投屏成功")
                }
            }
        } catch (e: Exception) {
            println("操作失败: ${e.message}")
        }
    }
}
```

### 2. 回调链管理
```kotlin
class CastWorkflow {
    private var currentCallback: ((Boolean) -> Unit)? = null
    
    fun startCastWorkflow(url: String, onComplete: (Boolean) -> Unit) {
        currentCallback = onComplete
        
        // 步骤1: 搜索设备
        DLNACast.search { devices ->
            if (devices.isEmpty()) {
                onComplete(false)
                return@search
            }
            
            // 步骤2: 选择最佳设备
            val targetDevice = selectBestDevice(devices)
            
            // 步骤3: 投屏
            DLNACast.castToDevice(targetDevice, url) { success ->
                if (success) {
                    // 步骤4: 验证播放状态
                    verifyPlayback { verified ->
                        onComplete(verified)
                    }
                } else {
                    onComplete(false)
                }
            }
        }
    }
    
    private fun selectBestDevice(devices: List<Device>): Device {
        return devices.firstOrNull { it.isTV } ?: devices.first()
    }
    
    private fun verifyPlayback(callback: (Boolean) -> Unit) {
        Handler().postDelayed({
            DLNACast.control(MediaAction.GET_STATE) { success ->
                if (success) {
                    val state = DLNACast.getState()
                    callback(state.playbackState == PlaybackState.PLAYING)
                } else {
                    callback(false)
                }
            }
        }, 2000)
    }
}
```

## 📱 设备管理策略

### 1. 智能设备缓存
```kotlin
class DeviceManager {
    private val deviceCache = mutableMapOf<String, Device>()
    private val deviceHistory = mutableListOf<String>()
    
    fun cacheDevices(devices: List<Device>) {
        devices.forEach { device ->
            deviceCache[device.id] = device
            updateDeviceHistory(device.id)
        }
    }
    
    private fun updateDeviceHistory(deviceId: String) {
        deviceHistory.removeAll { it == deviceId }
        deviceHistory.add(0, deviceId)
        
        // 保持历史记录不超过10个
        if (deviceHistory.size > 10) {
            deviceHistory.removeLast()
        }
    }
    
    fun getPreferredDevice(): Device? {
        // 优先返回最近使用的可用设备
        for (deviceId in deviceHistory) {
            deviceCache[deviceId]?.let { device ->
                if (isDeviceAvailable(device)) {
                    return device
                }
            }
        }
        return null
    }
    
    private fun isDeviceAvailable(device: Device): Boolean {
        // 这里可以添加设备可用性检查逻辑
        return true
    }
}
```

### 2. 同步等待所有设备
```kotlin
class DeviceDiscovery {
    fun waitForAllDevices(
        maxWaitTime: Long = 15000,
        minDeviceCount: Int = 1,
        callback: (List<Device>) -> Unit
    ) {
        val foundDevices = mutableSetOf<Device>()
        val startTime = System.currentTimeMillis()
        
        fun searchAndWait() {
            DLNACast.search(5000) { devices ->
                foundDevices.addAll(devices)
                
                val elapsed = System.currentTimeMillis() - startTime
                
                when {
                    foundDevices.size >= minDeviceCount && elapsed > 3000 -> {
                        // 找到足够设备且已等待3秒，返回结果
                        callback(foundDevices.toList())
                    }
                    elapsed < maxWaitTime -> {
                        // 继续搜索
                        Handler().postDelayed({ searchAndWait() }, 2000)
                    }
                    else -> {
                        // 超时，返回现有结果
                        callback(foundDevices.toList())
                    }
                }
            }
        }
        
        searchAndWait()
    }
}
```

## 🛡️ 错误处理机制

### 1. 统一错误处理
```kotlin
sealed class CastResult {
    object Success : CastResult()
    data class Error(val type: ErrorType, val message: String) : CastResult()
}

enum class ErrorType {
    NETWORK_ERROR,
    DEVICE_NOT_FOUND,
    MEDIA_FORMAT_ERROR,
    CONNECTION_TIMEOUT,
    PERMISSION_DENIED
}

class CastManager {
    fun cast(url: String, callback: (CastResult) -> Unit) {
        try {
            // 预检查
            if (!isNetworkAvailable()) {
                callback(CastResult.Error(ErrorType.NETWORK_ERROR, "网络不可用"))
                return
            }
            
            if (!isValidMediaUrl(url)) {
                callback(CastResult.Error(ErrorType.MEDIA_FORMAT_ERROR, "不支持的媒体格式"))
                return
            }
            
            // 搜索设备
            DLNACast.search(10000) { devices ->
                if (devices.isEmpty()) {
                    callback(CastResult.Error(ErrorType.DEVICE_NOT_FOUND, "未发现可用设备"))
                    return@search
                }
                
                // 执行投屏
                DLNACast.cast(url) { success ->
                    if (success) {
                        callback(CastResult.Success)
                    } else {
                        callback(CastResult.Error(ErrorType.CONNECTION_TIMEOUT, "投屏连接超时"))
                    }
                }
            }
            
        } catch (e: SecurityException) {
            callback(CastResult.Error(ErrorType.PERMISSION_DENIED, "缺少必要权限"))
        } catch (e: Exception) {
            callback(CastResult.Error(ErrorType.NETWORK_ERROR, e.message ?: "未知错误"))
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        // 网络检查逻辑
        return true
    }
    
    private fun isValidMediaUrl(url: String): Boolean {
        // URL格式检查
        return url.matches(Regex("^https?://.*\\.(mp4|mp3|jpg|jpeg|png)$"))
    }
}
```

### 2. 重试机制实现
```kotlin
class RetryableCast {
    fun castWithRetry(
        url: String,
        maxRetries: Int = 3,
        retryDelay: Long = 2000,
        callback: (Boolean) -> Unit
    ) {
        var attempts = 0
        
        fun attemptCast() {
            attempts++
            
            DLNACast.cast(url) { success ->
                when {
                    success -> {
                        Log.d("Cast", "投屏成功，尝试次数: $attempts")
                        callback(true)
                    }
                    attempts < maxRetries -> {
                        Log.w("Cast", "投屏失败，重试中... ($attempts/$maxRetries)")
                        Handler().postDelayed({ attemptCast() }, retryDelay)
                    }
                    else -> {
                        Log.e("Cast", "投屏最终失败，已重试 $maxRetries 次")
                        callback(false)
                    }
                }
            }
        }
        
        attemptCast()
    }
}
```

## ⚡ 性能优化建议

### 1. 资源管理
```kotlin
class CastService : Service() {
    private var isInitialized = false
    
    override fun onCreate() {
        super.onCreate()
        initializeCast()
    }
    
    private fun initializeCast() {
        if (!isInitialized) {
            DLNACast.init(this)
            isInitialized = true
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isInitialized) {
            DLNACast.release()
            isInitialized = false
        }
    }
}
```

### 2. 批量操作优化
```kotlin
class BatchCastManager {
    private val castQueue = mutableListOf<CastRequest>()
    private var isProcessing = false
    
    data class CastRequest(
        val device: Device,
        val url: String,
        val callback: (Boolean) -> Unit
    )
    
    fun addCastRequest(device: Device, url: String, callback: (Boolean) -> Unit) {
        castQueue.add(CastRequest(device, url, callback))
        processQueue()
    }
    
    private fun processQueue() {
        if (isProcessing || castQueue.isEmpty()) return
        
        isProcessing = true
        val request = castQueue.removeFirst()
        
        DLNACast.castToDevice(request.device, request.url) { success ->
            request.callback(success)
            
            // 处理下一个请求
            Handler().postDelayed({
                isProcessing = false
                processQueue()
            }, 1000) // 间隔1秒避免过于频繁
        }
    }
}
```

## 🏭 生产环境配置

### 1. 日志管理
```kotlin
object CastLogger {
    private const val TAG = "UPnPCast"
    private var isDebugMode = BuildConfig.DEBUG
    
    fun enableDebug(enable: Boolean) {
        isDebugMode = enable
    }
    
    fun d(message: String) {
        if (isDebugMode) {
            Log.d(TAG, message)
        }
    }
    
    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        // 生产环境可以上报错误到崩溃收集平台
        if (!isDebugMode) {
            // Crashlytics.recordException(throwable)
        }
    }
}
```

### 2. 配置管理
```kotlin
object CastConfig {
    // 默认搜索超时
    var searchTimeout = 10000L
    
    // 默认连接超时
    var connectionTimeout = 30000L
    
    // 是否启用设备缓存
    var enableDeviceCache = true
    
    // 最大重试次数
    var maxRetries = 3
    
    // 支持的媒体格式
    val supportedFormats = listOf("mp4", "mp3", "jpg", "jpeg", "png")
    
    fun loadFromPreferences(context: Context) {
        val prefs = context.getSharedPreferences("upnp_cast", Context.MODE_PRIVATE)
        searchTimeout = prefs.getLong("search_timeout", searchTimeout)
        connectionTimeout = prefs.getLong("connection_timeout", connectionTimeout)
        enableDeviceCache = prefs.getBoolean("enable_cache", enableDeviceCache)
        maxRetries = prefs.getInt("max_retries", maxRetries)
    }
}
```

### 3. 权限管理
```kotlin
class PermissionHelper(private val activity: Activity) {
    companion object {
        private const val REQUEST_CODE = 1001
        
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
        )
    }
    
    fun checkAndRequestPermissions(callback: (Boolean) -> Unit) {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            callback(true)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions.toTypedArray(),
                REQUEST_CODE
            )
        }
    }
    
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        callback: (Boolean) -> Unit
    ) {
        if (requestCode == REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            callback(allGranted)
        }
    }
}
```

## 🎯 总结

使用这些最佳实践可以显著提高UPnPCast库的稳定性和用户体验：

1. **异步处理** - 使用协程和合理的回调链管理
2. **设备管理** - 智能缓存和设备选择策略  
3. **错误处理** - 统一的错误类型和重试机制
4. **性能优化** - 资源管理和批量操作优化
5. **生产配置** - 完善的日志、配置和权限管理

这样可以让你的应用更加稳定可靠，用户体验更好。 