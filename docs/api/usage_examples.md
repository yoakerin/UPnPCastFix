# UPnPCast 使用示例

## 基础使用示例

### 初始化与设备发现

```kotlin
// 初始化
val dlnaCastManager = DLNACastManager.getInstance(context)

// 设置投屏监听器
dlnaCastManager.setCastListener(object : CastListener {
    override fun onDeviceListUpdated(deviceList: List<RemoteDevice>) {
        // 设备列表更新，可以在UI中展示
        adapter.submitList(deviceList)
    }
    
    override fun onConnected(device: RemoteDevice) {
        // 设备连接成功
        showToast("已连接到设备: ${device.name}")
        updateUI(isConnected = true)
    }
    
    override fun onDisconnected() {
        // 设备断开连接
        showToast("设备已断开连接")
        updateUI(isConnected = false)
    }
    
    override fun onError(errorMessage: String) {
        // 错误处理
        showToast("错误: $errorMessage")
    }
    
    override fun onPlaybackStateChanged(state: PlaybackState) {
        // 播放状态变更
        updatePlaybackUI(state)
    }
})

// 开始搜索设备
dlnaCastManager.startDiscovery()

// 在Activity或Fragment销毁时停止搜索
override fun onDestroy() {
    super.onDestroy()
    dlnaCastManager.stopDiscovery()
    dlnaCastManager.release()
}
```

### 连接设备并播放媒体

```kotlin
// 连接到选中的设备
fun connectToDevice(device: RemoteDevice) {
    dlnaCastManager.connectToDevice(device)
}

// 播放媒体
fun playVideo(videoUrl: String, title: String) {
    dlnaCastManager.playMedia(
        mediaUrl = videoUrl,
        title = title,
        metadata = null,
        positionMs = 0
    )
}

// 暂停播放
fun pausePlayback() {
    dlnaCastManager.pauseMedia()
}

// 恢复播放
fun resumePlayback() {
    dlnaCastManager.resumeMedia()
}

// 跳转到指定位置
fun seekTo(positionMs: Long) {
    dlnaCastManager.seekTo(positionMs)
}

// 调整音量
fun adjustVolume(volumeLevel: Int) {
    dlnaCastManager.setVolume(volumeLevel)
}

// 断开连接
fun disconnectDevice() {
    dlnaCastManager.disconnect()
}
```

## 高级使用示例

### 自定义设备过滤

```kotlin
// 过滤设备列表示例
fun filterDevices(devices: List<RemoteDevice>): List<RemoteDevice> {
    // 仅显示特定制造商的设备
    return devices.filter { device ->
        device.manufacturer == "Samsung" || 
        device.manufacturer == "LG" ||
        device.manufacturer == "Xiaomi"
    }
}

// 在设备列表更新时使用
override fun onDeviceListUpdated(deviceList: List<RemoteDevice>) {
    val filteredDevices = filterDevices(deviceList)
    adapter.submitList(filteredDevices)
}
```

### 处理网络变化

```kotlin
// 注册网络变化广播接收器
private val networkReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION == intent.action) {
            if (isNetworkAvailable(context)) {
                // 网络恢复，重新开始设备发现
                dlnaCastManager.startDiscovery()
            } else {
                // 网络断开，停止发现
                dlnaCastManager.stopDiscovery()
            }
        }
    }
}

// 在Activity启动时注册
override fun onStart() {
    super.onStart()
    val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
    registerReceiver(networkReceiver, filter)
}

// 在Activity停止时注销
override fun onStop() {
    super.onStop()
    unregisterReceiver(networkReceiver)
}

// 检查网络是否可用
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.activeNetwork ?: return false
    val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
    return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
```

### 处理应用生命周期

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 可以在应用初始化时做一些全局配置
    }
    
    // 应用退出时确保资源释放
    override fun onTerminate() {
        super.onTerminate()
        DLNACastManager.getInstance(this).release()
    }
}
```
