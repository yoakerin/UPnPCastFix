# UPnPCast API 文档

## 概述

UPnPCast 是一个现代化的 Android DLNA/UPnP 投屏库，提供简洁易用的 API 来实现媒体投屏功能。

## 核心类

### DLNACastManager

主要的 API 入口点，提供所有投屏功能的控制。

#### 初始化

```kotlin
// 获取实例（单例模式）
val castManager = DLNACastManager.getInstance(context)
```

#### 设备发现

```kotlin
// 开始搜索设备
castManager.startSearch()

// 停止搜索设备
castManager.stopSearch()

// 获取所有发现的设备
val devices = castManager.getAllDevices()
```

#### 监听器设置

```kotlin
// 设置投屏监听器
castManager.setCastListener(object : CastListener {
    override fun onDeviceListUpdated(deviceList: List<RemoteDevice>) {
        // 设备列表更新
    }
    
    override fun onConnected(device: RemoteDevice) {
        // 设备连接成功
    }
    
    override fun onDisconnected() {
        // 设备断开连接
    }
    
    override fun onError(error: DLNAException) {
        // 错误处理
    }
})

// 设置播放状态监听器
castManager.setPlaybackStateListener(object : PlaybackStateListener {
    override fun onPlaybackStateChanged(state: PlaybackState) {
        // 播放状态改变
    }
    
    override fun onPositionChanged(positionMs: Long, durationMs: Long) {
        // 播放位置改变
    }
})
```

#### 设备连接

```kotlin
// 连接到设备
val success = castManager.connectToDevice(device)

// 断开连接
castManager.disconnect()

// 获取当前连接的设备
val currentDevice = castManager.getCurrentDevice()
```

#### 媒体播放

```kotlin
// 播放媒体
val success = castManager.playMedia(
    url = "http://example.com/video.mp4",
    title = "视频标题"
)

// 获取当前播放状态
val state = castManager.getCurrentState()
```

#### 播放控制

```kotlin
// 暂停播放
castManager.pause()

// 恢复播放
castManager.resume()

// 停止播放
castManager.stop()

// 跳转到指定位置
castManager.seekTo(positionMs)
```

#### 音量控制

```kotlin
// 设置音量 (0-100)
castManager.setVolume(50)

// 设置静音
castManager.setMute(true)
```

#### 资源管理

```kotlin
// 清除设备缓存
castManager.clearDeviceCache()

// 释放资源（应用退出时调用）
castManager.release()
```

## 数据类

### RemoteDevice

表示一个远程 DLNA 设备。

```kotlin
data class RemoteDevice(
    val id: String,           // 设备唯一标识
    val displayName: String,  // 设备显示名称
    val manufacturer: String, // 制造商
    val address: String,      // IP地址
    val details: Map<String, Any> // 设备详细信息
)
```

### DLNAException

DLNA 操作的异常类。

```kotlin
class DLNAException(
    val errorType: DLNAErrorType,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
```

## 枚举类

### PlaybackState

播放状态枚举。

```kotlin
enum class PlaybackState {
    IDLE,        // 空闲
    PLAYING,     // 播放中
    PAUSED,      // 暂停
    STOPPED,     // 停止
    BUFFERING    // 缓冲中
}
```

### DLNAErrorType

错误类型枚举。

```kotlin
enum class DLNAErrorType {
    DEVICE_NOT_FOUND,    // 设备未找到
    CONNECTION_FAILED,   // 连接失败
    PLAYBACK_ERROR,      // 播放错误
    NETWORK_ERROR,       // 网络错误
    INVALID_MEDIA_URL,   // 无效的媒体URL
    UNSUPPORTED_FORMAT   // 不支持的格式
}
```

## 接口

### CastListener

投屏监听器接口。

```kotlin
interface CastListener {
    fun onDeviceListUpdated(deviceList: List<RemoteDevice>)
    fun onConnected(device: RemoteDevice)
    fun onDisconnected()
    fun onError(error: DLNAException)
}
```

### PlaybackStateListener

播放状态监听器接口。

```kotlin
interface PlaybackStateListener {
    fun onPlaybackStateChanged(state: PlaybackState)
    fun onPositionChanged(positionMs: Long, durationMs: Long)
}
```

## 使用示例

### 完整的投屏流程

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var castManager: DLNACastManager
    private var connectedDevice: RemoteDevice? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化投屏管理器
        castManager = DLNACastManager.getInstance(this)
        
        // 设置监听器
        setupListeners()
        
        // 开始搜索设备
        castManager.startSearch()
    }
    
    private fun setupListeners() {
        castManager.setCastListener(object : CastListener {
            override fun onDeviceListUpdated(deviceList: List<RemoteDevice>) {
                // 更新UI显示设备列表
                updateDeviceList(deviceList)
            }
            
            override fun onConnected(device: RemoteDevice) {
                connectedDevice = device
                // 显示播放控制界面
                showPlaybackControls()
            }
            
            override fun onDisconnected() {
                connectedDevice = null
                // 隐藏播放控制界面
                hidePlaybackControls()
            }
            
            override fun onError(error: DLNAException) {
                // 显示错误信息
                showError(error.message)
            }
        })
    }
    
    private fun playVideo() {
        val videoUrl = "http://example.com/video.mp4"
        val success = castManager.playMedia(videoUrl, "示例视频")
        if (!success) {
            showError("播放失败")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        castManager.release()
    }
}
```

### 错误处理

```kotlin
castManager.setCastListener(object : CastListener {
    override fun onError(error: DLNAException) {
        when (error.errorType) {
            DLNAErrorType.DEVICE_NOT_FOUND -> {
                showMessage("未找到可用设备")
            }
            DLNAErrorType.CONNECTION_FAILED -> {
                showMessage("连接设备失败，请检查网络")
            }
            DLNAErrorType.PLAYBACK_ERROR -> {
                showMessage("播放失败：${error.message}")
            }
            DLNAErrorType.NETWORK_ERROR -> {
                showMessage("网络错误，请检查网络连接")
            }
            DLNAErrorType.INVALID_MEDIA_URL -> {
                showMessage("无效的媒体链接")
            }
            DLNAErrorType.UNSUPPORTED_FORMAT -> {
                showMessage("不支持的媒体格式")
            }
        }
    }
})
```

## 注意事项

1. **权限要求**：确保在 AndroidManifest.xml 中添加必要的网络权限
2. **线程安全**：所有回调都在主线程中执行
3. **资源管理**：记得在适当的时候调用 `release()` 方法释放资源
4. **网络要求**：设备需要在同一局域网内
5. **媒体格式**：支持大部分常见的视频和音频格式 