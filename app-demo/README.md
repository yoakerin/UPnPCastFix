# 🚀 UPnPCast Demo App

> **UPnPCast库的完整功能演示应用**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-24+-green.svg)](https://android-arsenal.com/api?level=24)

## ✨ 功能特性

- **🔍 智能设备发现** - 自动发现网络中的UPnP/DLNA设备
- **🎬 一键投屏** - 支持视频、音频、图片多媒体投屏
- **🎮 实时控制** - 播放、暂停、停止、音量、静音控制
- **📊 状态监控** - 实时显示连接状态和播放信息
- **📚 完整API演示** - 展示所有DLNACast核心API用法

## 🏗️ 极简API设计

```kotlin
// 🎯 单文件导入，功能全覆盖
import com.yinnho.upnpcast.DLNACast

class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化
        DLNACast.init(this)
        
        // 搜索设备
        DLNACast.search { devices -> }
        
        // 一键投屏
        DLNACast.cast("http://video.mp4") { success -> }
        
        // 智能选择设备投屏
        DLNACast.smartCast("http://video.mp4") { success -> 
        } { devices -> 
            devices.firstOrNull { it.isTV }
        }
        
        // 统一控制
        DLNACast.control(DLNACast.MediaAction.PAUSE)
        
        // 获取状态
        val state = DLNACast.getState()
    }
}
```

## 📱 页面介绍

### 1. 🏠 主页面 (MainActivity)
- **设备列表展示** - 实时显示发现的DLNA设备
- **智能设备分类** - 自动识别TV📺、Box📱、音响🔊
- **一键投屏** - 支持多种测试媒体和自定义URL
- **实时控制面板** - 播放控制、音量调节、状态监控

### 2. 🛠️ API演示页面 (ApiDemoActivity)
- **完整API展示** - 演示所有6个核心API
- **代码示例** - 实时显示API调用代码
- **执行日志** - 详细的执行过程和结果
- **交互式测试** - 可操作的API测试界面

### 3. 📊 性能监控页面 (PerformanceActivity)
- **基准测试** - 初始化、搜索、控制性能测试
- **网络分析** - 延迟测试和连接质量分析
- **内存监控** - 内存使用和泄漏检测
- **性能评分** - 综合性能评分和优化建议

## 🛠️ 完整API清单

```kotlin
// 🎯 6个核心API，覆盖所有功能

// 1. 初始化
DLNACast.init(context: Context)

// 2. 设备搜索  
DLNACast.search(timeout: Long = 10000, callback: DeviceList)

// 3. 自动投屏
DLNACast.cast(url: String, title: String? = null, callback: Result = {})

// 4. 智能投屏
DLNACast.smartCast(url: String, title: String? = null, callback: Result = {}, deviceSelector: DeviceSelector)

// 5. 统一控制
DLNACast.control(action: MediaAction, value: Any? = null, callback: Result = {})

// 6. 状态查询
DLNACast.getState(): State
```

### 控制动作

```kotlin
enum class MediaAction {
    PLAY,           // 播放/恢复
    PAUSE,          // 暂停  
    STOP,           // 停止
    VOLUME,         // 设置音量 (需要value参数: Int 0-100)
    MUTE,           // 静音切换 (可选value参数: Boolean)
    SEEK,           // 跳转到指定位置 (需要value参数: Long 毫秒)
    GET_STATE       // 获取播放状态
}
```

## 🚀 快速开始

### 依赖集成
```gradle
dependencies {
    implementation 'com.yinnho:upnpcast:1.0.0'
}
```

### 基础使用
```kotlin
// 1. 初始化
DLNACast.init(this)

// 2. 搜索设备
DLNACast.search { devices ->
    devices.forEach { device ->
        Log.d("DLNA", "发现设备: ${device.name}")
    }
}

// 3. 投屏媒体
DLNACast.cast("http://your-video.mp4", "视频标题") { success ->
    if (success) {
        Log.d("DLNA", "投屏成功!")
    }
}

// 4. 媒体控制
DLNACast.control(DLNACast.MediaAction.PAUSE) { success ->
    Log.d("DLNA", "暂停: $success")
}
```

## 📊 技术特点

- **🎯 极简API**: 6个方法覆盖所有功能
- **🚀 门面模式**: 单一入口，统一管理
- **📱 现代设计**: Material Design + 专业交互
- **⚡ 高性能**: 协程异步，响应迅速
- **🔧 开发友好**: 详细日志，错误处理完善

---

> **💡 提示**: 这个Demo展示了UPnPCast库的完整能力。所有功能都已经过真实设备测试，可以直接用于生产环境。