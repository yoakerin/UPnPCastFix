# UPnPCast

[![CI/CD](https://github.com/yinnho/UPnPCast/actions/workflows/ci.yml/badge.svg)](https://github.com/yinnho/UPnPCast/actions)
[![Release](https://img.shields.io/github/v/release/yinnho/UPnPCast)](https://github.com/yinnho/UPnPCast/releases)
[![License](https://img.shields.io/github/license/yinnho/UPnPCast)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/yinnho.com/upnpcast)](https://central.sonatype.com/artifact/yinnho.com/upnpcast)

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
import com.yinnho.upnpcast.DLNACast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化
        DLNACast.init(this)
        
        // 搜索设备
        DLNACast.search { devices ->
            devices.forEach { device ->
                Log.d("DLNA", "发现设备: ${device.name}")
            }
        }
        
        // 投屏媒体
        DLNACast.cast("http://your-video.mp4", "视频标题") { success ->
            if (success) {
                Log.d("DLNA", "投屏成功!")
            }
        }
        
        // 控制播放
        DLNACast.control(DLNACast.MediaAction.PAUSE) { success ->
            Log.d("DLNA", "暂停: $success")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        DLNACast.release()
    }
}
```

## API参考

### 核心方法

```kotlin
// 初始化库
DLNACast.init(context: Context)

// 搜索设备
DLNACast.search(timeout: Long = 10000, callback: (devices: List<Device>) -> Unit)

// 自动投屏到可用设备
DLNACast.cast(url: String, title: String? = null, callback: (success: Boolean) -> Unit = {})

// 智能投屏，支持设备选择
DLNACast.smartCast(url: String, title: String? = null, callback: (success: Boolean) -> Unit = {}, deviceSelector: (devices: List<Device>) -> Device?)

// 投屏到指定设备
DLNACast.castToDevice(device: Device, url: String, title: String? = null, callback: (success: Boolean) -> Unit = {})

// 控制媒体播放
DLNACast.control(action: MediaAction, value: Any? = null, callback: (success: Boolean) -> Unit = {})

// 获取当前状态
DLNACast.getState(): State

// 释放资源
DLNACast.release()
```

### 数据类型

```kotlin
data class Device(
    val id: String,           // 设备ID
    val name: String,         // 设备名称
    val address: String,      // IP地址
    val isTV: Boolean         // 是否为电视
)

enum class MediaAction {
    PLAY, PAUSE, STOP, VOLUME, MUTE, SEEK, GET_STATE
}

enum class PlaybackState {
    IDLE, PLAYING, PAUSED, STOPPED, BUFFERING, ERROR
}

data class State(
    val isConnected: Boolean,      // 是否已连接
    val currentDevice: Device?,    // 当前设备
    val playbackState: PlaybackState, // 播放状态
    val volume: Int = -1,          // 音量
    val isMuted: Boolean = false   // 是否静音
)
```

## 文档

- 🎯 **[演示应用](app-demo/)** - 完整的示例程序，包含所有API演示
- 📖 **[API参考](#api参考)** - 上方的完整API文档
- 📋 **[更新日志](CHANGELOG.md)** - 版本历史和更新

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

欢迎贡献！请查看我们的[最佳实践指南](docs/best_practices.md)了解开发指导原则。

## 支持

- 📖 在[演示应用](app-demo/)中查看详细的使用示例
- 🐛 在[GitHub Issues](https://github.com/yinnho/UPnPCast/issues)报告问题
- 💡 欢迎功能请求！ 