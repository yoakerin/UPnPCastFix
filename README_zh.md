# UPnPCast

[![CI/CD](https://github.com/yinnho/UPnPCast/actions/workflows/ci.yml/badge.svg)](https://github.com/yinnho/UPnPCast/actions)
[![Release](https://img.shields.io/github/v/release/yinnho/UPnPCast)](https://github.com/yinnho/UPnPCast/releases)
[![License](https://img.shields.io/github/license/yinnho/UPnPCast)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/yinnho.com/upnpcast)](https://central.sonatype.com/artifact/yinnho.com/upnpcast)
[![Weekly Downloads](https://jitpack.io/v/yinnho/UPnPCast/week.svg)](https://jitpack.io/#yinnho/UPnPCast)
[![Monthly Downloads](https://jitpack.io/v/yinnho/UPnPCast/month.svg)](https://jitpack.io/#yinnho/UPnPCast)

🚀 现代化、简洁的Android DLNA/UPnP投屏库，专为替代已停止维护的Cling项目而设计。

> **中文文档** | **[English Documentation](README.md)**

## 功能特性

- 🔍 **设备发现**: 基于SSDP协议的自动DLNA/UPnP设备发现
- 📺 **媒体投屏**: 支持图片、视频、音频投屏到DLNA兼容设备
- 🎮 **播放控制**: 播放、暂停、停止、拖拽、音量控制、静音等功能
- 📱 **简单集成**: 简洁的API接口和直观的回调机制
- 🚀 **现代架构**: 使用Kotlin、协程和Android最佳实践构建
- 🔧 **高度兼容**: 经过主流电视品牌测试（小米、三星、LG、索尼）
- ⚡ **轻量级**: 最小依赖，性能优化

## 快速开始

### 安装

#### 方式一：JitPack（推荐 - 立即可用！）

在根目录的 `build.gradle` 中添加：
```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

添加依赖：
```gradle
dependencies {
    implementation 'com.github.yinnho:UPnPCast:1.1.2'
}
```

#### 方式二：Maven Central（即将推出）
```gradle
dependencies {
    implementation 'yinnho.com:upnpcast:1.1.2'
}
```

### 基本用法

```kotlin
import com.yinnho.upnpcast.DLNACast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化
        DLNACast.init(this)
        
        // 使用协程进行所有操作
        lifecycleScope.launch {
            searchDevices()
            performSmartCast()
        }
    }
    
    private suspend fun searchDevices() {
        try {
            // 设备发现（带超时）
            val devices = DLNACast.search(timeout = 5000)
            Log.d("DLNA", "发现 ${devices.size} 个设备")
            
            // 显示设备
            devices.forEach { device ->
                val icon = if (device.isTV) "📺" else "📱"
                Log.d("DLNA", "$icon ${device.name} (${device.address})")
            }
        } catch (e: Exception) {
            Log.e("DLNA", "搜索失败: ${e.message}")
        }
    }
    
    private suspend fun performSmartCast() {
        try {
            // 智能投屏 - 自动查找并选择最佳设备
            val success = DLNACast.cast("http://your-video.mp4", "视频标题")
            if (success) {
                Log.d("DLNA", "智能投屏开始!")
                controlPlayback()
            } else {
                Log.e("DLNA", "投屏失败")
            }
        } catch (e: Exception) {
            Log.e("DLNA", "投屏错误: ${e.message}")
        }
    }
    
    private suspend fun controlPlayback() {
        try {
            // 控制播放
            val pauseSuccess = DLNACast.control(DLNACast.MediaAction.PAUSE)
            Log.d("DLNA", "暂停: $pauseSuccess")
            
            // 获取当前状态
            val state = DLNACast.getState()
            Log.d("DLNA", "已连接: ${state.isConnected}, 正在播放: ${state.isPlaying}")
            
            // 跳转到30秒
            val seekSuccess = DLNACast.seek(30000)
            Log.d("DLNA", "跳转到30秒: $seekSuccess")
        } catch (e: Exception) {
            Log.e("DLNA", "控制错误: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        DLNACast.cleanup()
    }
}
```

## API参考

### 🚀 核心方法（所有挂起函数）

```kotlin
// 初始化库（在onCreate中调用一次）
DLNACast.init(context: Context)

// 搜索设备（返回发现的设备列表）
suspend fun DLNACast.search(timeout: Long = 5000): List<Device>

// 智能投屏 - 自动选择最佳可用设备
suspend fun DLNACast.cast(url: String, title: String? = null): Boolean

// 投屏到指定设备
suspend fun DLNACast.castToDevice(device: Device, url: String, title: String): Boolean

// 投屏本地视频文件
suspend fun DLNACast.castLocalFile(device: Device, video: LocalVideo): Boolean

// 扫描本地视频文件
suspend fun DLNACast.scanLocalVideos(): List<LocalVideo>

// 媒体控制操作
suspend fun DLNACast.control(action: MediaAction): Boolean

// 跳转到指定位置（毫秒）
suspend fun DLNACast.seek(positionMs: Long): Boolean
```

### 📊 状态管理

```kotlin
// 获取当前投屏状态（同步）
fun DLNACast.getState(): State

// 获取播放进度（同步）
fun DLNACast.getProgress(): Progress

// 获取音量信息（同步）
fun DLNACast.getVolume(): Volume

// 清理资源（在onDestroy中调用）
fun DLNACast.cleanup()
```

### 📋 数据类型

```kotlin
// 设备信息
data class Device(
    val id: String,           // 唯一设备标识符
    val name: String,         // 显示名称（如"客厅电视"）
    val address: String,      // IP地址
    val isTV: Boolean         // 是否为电视设备
)

// 本地视频文件信息
data class LocalVideo(
    val path: String,         // 文件完整路径
    val name: String,         // 显示名称
    val size: Long,           // 文件大小（字节）
    val duration: Long        // 时长（毫秒）
)

// 媒体控制操作
enum class MediaAction {
    PLAY, PAUSE, STOP
}

// 播放状态
enum class PlaybackState {
    IDLE,                     // 未连接或无媒体
    PLAYING,                  // 正在播放
    PAUSED,                   // 已暂停
    STOPPED,                  // 已停止
    BUFFERING,                // 加载/缓冲中
    ERROR                     // 错误状态
}

// 当前投屏状态
data class State(
    val isConnected: Boolean,     // 是否连接到设备
    val currentDevice: Device?,   // 当前目标设备
    val playbackState: PlaybackState,  // 当前播放状态
    val isPlaying: Boolean,       // 是否正在播放媒体
    val isPaused: Boolean,        // 是否已暂停媒体
    val volume: Int,              // 当前音量（0-100）
    val isMuted: Boolean          // 是否静音
)

// 播放进度信息
data class Progress(
    val currentMs: Long,          // 当前位置（毫秒）
    val totalMs: Long,            // 总时长（毫秒）
    val percentage: Float         // 进度百分比（0.0-1.0）
)

// 音量信息
data class Volume(
    val level: Int,               // 音量级别（0-100）
    val isMuted: Boolean          // 静音状态
)
```

## 🔥 高级用法示例

### 投屏到指定设备
```kotlin
lifecycleScope.launch {
    try {
        // 首先，搜索设备
        val devices = DLNACast.search(timeout = 5000)
        
        // 找到您偏好的设备
        val targetDevice = devices.firstOrNull { it.name.contains("客厅") }
        
        if (targetDevice != null) {
            // 投屏到指定设备
            val success = DLNACast.castToDevice(
                device = targetDevice,
                url = "http://your-video.mp4",
                title = "我的电影"
            )
            
            if (success) {
                Log.d("DLNA", "成功投屏到 ${targetDevice.name}")
            }
        }
    } catch (e: Exception) {
        Log.e("DLNA", "投屏失败: ${e.message}")
    }
}
```

### 本地文件投屏
```kotlin
lifecycleScope.launch {
    try {
        // 扫描本地视频文件
        val localVideos = DLNACast.scanLocalVideos()
        
        // 找到要播放的视频
        val videoToPlay = localVideos.firstOrNull { it.name.contains("电影") }
        
        if (videoToPlay != null) {
            // 获取可用设备
            val devices = DLNACast.search()
            val device = devices.firstOrNull()
            
            if (device != null) {
                // 投屏本地文件
                val success = DLNACast.castLocalFile(device, videoToPlay)
                Log.d("DLNA", "本地投屏成功: $success")
            }
        }
    } catch (e: Exception) {
        Log.e("DLNA", "本地投屏失败: ${e.message}")
    }
}
```

### 媒体控制和状态监控
```kotlin
lifecycleScope.launch {
    try {
        // 控制播放
        DLNACast.control(DLNACast.MediaAction.PAUSE)
        
        // 监控状态
        val state = DLNACast.getState()
        Log.d("DLNA", "设备: ${state.currentDevice?.name}")
        Log.d("DLNA", "播放中: ${state.isPlaying}")
        Log.d("DLNA", "音量: ${state.volume}")
        
        // 获取进度
        val progress = DLNACast.getProgress()
        Log.d("DLNA", "进度: ${progress.percentage * 100}%")
        
        // 跳转到指定位置（2分钟）
        DLNACast.seek(120000)
        
    } catch (e: Exception) {
        Log.e("DLNA", "控制失败: ${e.message}")
    }
}
```

## 文档

- 🎯 **[演示应用](app-demo/)** - 完整的示例程序，包含所有API演示
- 📖 **[API参考](#api参考)** - 上方的完整API文档
- 📋 **[更新日志](CHANGELOG.md)** - 版本历史和更新
- 🤔 **[常见问题](docs/FAQ.md)** - 常见问题解答和故障排除
- 🎯 **[最佳实践](docs/BEST_PRACTICES.md)** - 异步回调、设备管理和优化指南

## 设备兼容性

- ✅ 小米电视 (原生DLNA + 小米投屏)
- ✅ 三星智能电视
- ✅ LG智能电视  
- ✅ 索尼Bravia电视
- ✅ Android TV盒子
- ✅ Windows Media Player

## 许可证

本项目采用MIT许可证 - 详见 [LICENSE](LICENSE) 文件。

## 贡献

欢迎贡献！请查看我们的[贡献指南](CONTRIBUTING.md)了解开发指导原则和如何开始。

## 支持

- 📖 在[演示应用](app-demo/)中查看详细的使用示例
- 🐛 在[GitHub Issues](https://github.com/yinnho/UPnPCast/issues)报告问题
- 💡 欢迎功能请求！