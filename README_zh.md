# UPnPCast

[![构建状态](https://github.com/yinnho/UPnPCast/workflows/CI%2FCD/badge.svg)](https://github.com/yinnho/UPnPCast/actions)
[![Maven Central](https://img.shields.io/maven-central/v/com.yinnho/upnpcast.svg)](https://search.maven.org/search?q=g:com.yinnho%20AND%20a:upnpcast)
[![许可证: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)

一个现代化的Android DLNA/UPnP投屏库，作为停止维护的Cling项目的替代品。

> [English Documentation](README.md) | **中文文档**

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

#### 方式一：JitPack（推荐）

在根目录的 `build.gradle` 中添加：
```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

添加依赖：
```gradle
dependencies {
    implementation 'com.github.yinnho:UPnPCast:1.0.0'
}
```

#### 方式二：Maven Central
```gradle
dependencies {
    implementation 'com.yinnho:upnpcast:1.0.0'
}
```

### 基本用法

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var dlnaManager: DLNACastManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化DLNA管理器
        dlnaManager = DLNACastManager.getInstance(this)
        
        // 设置监听器
        dlnaManager.setCastListener(object : CastListener {
            override fun onDeviceListUpdated(devices: List<RemoteDevice>) {
                // 更新UI显示发现的设备
                showDevices(devices)
            }
            
            override fun onConnected(device: RemoteDevice) {
                // 设备连接成功
                Toast.makeText(this@MainActivity, "已连接到 ${device.displayName}", Toast.LENGTH_SHORT).show()
            }
            
            override fun onDisconnected() {
                // 设备断开连接
            }
            
            override fun onError(error: DLNAException) {
                // 处理错误
                Log.e("DLNA", "错误: ${error.message}")
            }
        })
        
        // 开始设备发现
        dlnaManager.startSearch()
    }
    
    private fun castMedia() {
        val mediaUrl = "http://example.com/video.mp4"
        val success = dlnaManager.playMedia(mediaUrl, "我的视频")
        if (success) {
            // 开始投屏
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        dlnaManager.release()
    }
}
```

## API参考

### 核心类

#### DLNACastManager
所有DLNA操作的主要入口点。

```kotlin
// 获取单例实例
val dlnaManager = DLNACastManager.getInstance(context)

// 设备发现
dlnaManager.startSearch(timeoutMs = 30000)
dlnaManager.stopSearch()

// 设备连接
dlnaManager.connectToDevice(device)
dlnaManager.disconnect()

// 媒体播放
dlnaManager.playMedia(url, title)
dlnaManager.pause()
dlnaManager.resume()
dlnaManager.stop()
dlnaManager.setVolume(50)
dlnaManager.setMute(true)

// 获取信息
val devices = dlnaManager.getAllDevices()
val currentDevice = dlnaManager.getCurrentDevice()
val state = dlnaManager.getCurrentState()
```

#### RemoteDevice
表示发现的DLNA设备。

```kotlin
data class RemoteDevice(
    val id: String,           // 设备唯一标识
    val displayName: String,  // 显示名称
    val manufacturer: String, // 制造商
    val address: String,      // IP地址
    val details: Map<String, Any> // 详细信息
)
```

#### 监听器

```kotlin
interface CastListener {
    fun onDeviceListUpdated(devices: List<RemoteDevice>) // 设备列表更新
    fun onConnected(device: RemoteDevice)                // 设备连接成功
    fun onDisconnected()                                 // 设备断开连接
    fun onError(error: DLNAException)                   // 错误回调
}

interface PlaybackStateListener {
    fun onStateChanged(state: PlaybackState)            // 播放状态变化
    fun onPositionChanged(position: Long)               // 播放位置变化
}
```

## 高级用法

### 自定义错误处理

```kotlin
dlnaManager.setCastListener(object : CastListener {
    override fun onError(error: DLNAException) {
        when (error.errorType) {
            DLNAErrorType.DEVICE_NOT_FOUND -> {
                // 没有可用设备
            }
            DLNAErrorType.CONNECTION_FAILED -> {
                // 设备连接失败
            }
            DLNAErrorType.PLAYBACK_ERROR -> {
                // 媒体播放失败
            }
            DLNAErrorType.NETWORK_ERROR -> {
                // 网络连接问题
            }
        }
    }
})
```

### 设备过滤

```kotlin
// 按制造商过滤设备
val xiaomiDevices = dlnaManager.getAllDevices()
    .filter { it.manufacturer.contains("小米", ignoreCase = true) }

// 按设备功能过滤
val mediaRenderers = dlnaManager.getAllDevices()
    .filter { device ->
        val services = device.details["services"] as? List<*>
        services?.any { service ->
            service.toString().contains("MediaRenderer", ignoreCase = true)
        } ?: false
    }
```

## 兼容性

### 已测试设备
- ✅ 小米电视（原生DLNA + 奇异果投屏）
- ✅ 三星智能电视
- ✅ LG智能电视
- ✅ 索尼Bravia电视
- ✅ Android TV盒子
- ✅ Windows Media Player

### Android要求
- **最低SDK**: API 24 (Android 7.0)
- **目标SDK**: API 34 (Android 14)
- **权限**: 
  - `INTERNET`
  - `ACCESS_NETWORK_STATE`
  - `ACCESS_WIFI_STATE`
  - `CHANGE_WIFI_MULTICAST_STATE`

## 贡献

我们欢迎贡献！请查看我们的[贡献指南](CONTRIBUTING.md)了解详情。

### 开发环境设置

1. 克隆仓库：
```bash
git clone https://github.com/yinnho/UPnPCast.git
cd UPnPCast
```

2. 在Android Studio中打开
3. 构建项目：
```bash
./gradlew build
```

4. 运行测试：
```bash
./gradlew test
```

## 更新日志

详细发布说明请查看 [CHANGELOG.md](CHANGELOG.md)。

## 许可证

本项目采用MIT许可证 - 详情请查看 [LICENSE](LICENSE) 文件。

## 致谢

- 作为停止维护的 [Cling](http://4thline.org/projects/cling/) 项目的现代化替代品
- 灵感来自UPnP/DLNA规范和Android媒体框架
- 特别感谢Android社区的测试和反馈

## 支持

- 📚 [API文档](docs/API.md)
- 🐛 [问题跟踪](https://github.com/yinnho/UPnPCast/issues)
- 💬 [讨论区](https://github.com/yinnho/UPnPCast/discussions)

---

**为Android社区用❤️制作** 