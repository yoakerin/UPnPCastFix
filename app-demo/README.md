# 🚀 UPnPCast Demo - 专业DLNA投屏演示应用

> **展示UPnPCast库的完整功能和专业用法的演示应用**

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg)](https://kotlinlang.org)
[![API](https://img.shields.io/badge/API-21+-green.svg)](https://android-arsenal.com/api?level=21)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📋 目录

- [功能特性](#-功能特性)
- [架构展示](#️-架构展示)
- [快速开始](#-快速开始)
- [页面介绍](#-页面介绍)
- [API演示](#-api演示)
- [性能监控](#-性能监控)
- [技术特点](#-技术特点)

## ✨ 功能特性

### 🎯 核心功能
- **🔍 智能设备发现** - 自动发现网络中的UPnP/DLNA设备
- **🎬 一键投屏** - 支持视频、音频、图片多媒体投屏
- **🎮 实时控制** - 播放、暂停、停止、音量、静音控制
- **📊 状态监控** - 实时显示连接状态和播放信息
- **⚡ 智能选择** - 自动识别设备类型（TV、Box等）

### 🏗️ 专业特性
- **📚 完整API演示** - 展示所有6个核心API的标准用法
- **📊 性能监控** - 网络延迟、内存使用、响应时间分析
- **🎨 现代UI设计** - Material Design + 专业交互体验
- **🔧 开发者友好** - 详细日志、错误处理、调试信息

## 🏗️ 架构展示

### 门面模式实现
```kotlin
// 🎯 单文件导入，功能全覆盖
import com.yinnho.upnpcast.DLNACast

class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化
        DLNACast.init(this)
        
        // 一键投屏
        DLNACast.cast("http://video.mp4") { success -> }
        
        // 智能选择设备投屏
        DLNACast.castTo("http://video.mp4") { devices -> 
            devices.firstOrNull { it.isTV }
        }
        
        // 搜索设备
        DLNACast.search { devices -> }
        
        // 统一控制
        DLNACast.control(DLNACast.MediaAction.PAUSE)
        
        // 获取状态
        val state = DLNACast.getState()
    }
}
```

### API简化对比
| 功能 | 传统方式 | UPnPCast方式 |
|------|----------|-------------|
| **导入** | 多个复杂import | `import com.yinnho.upnpcast.DLNACast` |
| **初始化** | 复杂配置代码 | `DLNACast.init(context)` |
| **设备搜索** | 手动SSDP实现 | `DLNACast.search { devices -> }` |
| **投屏控制** | 分散的15+方法 | 6个核心方法 |
| **状态管理** | 手动状态跟踪 | `DLNACast.getState()` |

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

## 📱 页面介绍

### 1. 🏠 主页面 (MainActivity)
- **设备列表展示** - 实时显示发现的DLNA设备
- **智能设备分类** - 自动识别TV📺、Box📱、音响🔊
- **一键投屏** - 支持多种测试媒体和自定义URL
- **实时控制面板** - 播放控制、音量调节、状态监控

**核心特性:**
```kotlin
// 增量设备发现 - 出现一个显示一个
DLNACast.search { newDevices ->
    devices.addAll(newDevices)
    adapter.notifyDataSetChanged()
}

// 智能设备选择策略
val selectedDevice = devices.find { it.isTV } 
    ?: devices.find { it.isBox } 
    ?: devices.firstOrNull()
```

### 2. 🛠️ API演示页面 (ApiDemoActivity)
- **完整API展示** - 演示所有6个核心API
- **代码示例** - 实时显示API调用代码
- **执行日志** - 详细的执行过程和结果
- **交互式测试** - 可操作的API测试界面

**演示内容:**
- `DLNACast.init()` - 初始化演示
- `DLNACast.search()` - 设备搜索演示  
- `DLNACast.cast()` - 自动投屏演示
- `DLNACast.castTo()` - 智能选择投屏演示
- `DLNACast.control()` - 媒体控制演示
- `DLNACast.getState()` - 状态查询演示

### 3. 📊 性能监控页面 (PerformanceActivity)
- **基准测试** - 初始化、搜索、控制性能测试
- **网络分析** - 延迟测试和连接质量分析
- **内存监控** - 内存使用和泄漏检测
- **性能评分** - 综合性能评分和优化建议

**监控指标:**
```kotlin
📊 性能指标:
• 🔧 初始化耗时: <50ms
• 🔍 设备搜索: <3000ms  
• 📊 状态查询: <10ms
• 🌐 网络延迟: <1000ms
• 💾 内存增长: <100KB
• 🏆 综合评分: 90+ 分
```

## 🛠️ API演示

### 完整API清单
```kotlin
// 🎯 6个核心API，覆盖所有功能

// 1. 初始化
DLNACast.init(context: Context)

// 2. 设备搜索  
DLNACast.search(timeout: Long = 10000, callback: DeviceList)

// 3. 自动投屏
DLNACast.cast(url: String, title: String? = null, callback: Result = {})

// 4. 智能投屏
DLNACast.castTo(url: String, title: String? = null, deviceSelector: DeviceSelector)

// 5. 统一控制
DLNACast.control(action: MediaAction, value: Any? = null, callback: Result = {})

// 6. 状态查询
DLNACast.getState(): State
```

### 控制动作枚举
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

## 📊 性能监控

### 基准测试报告
```
📋 性能基准测试报告:
━━━━━━━━━━━━━━━━━━━━━━━━
🔧 初始化耗时: 45ms
🔍 设备搜索: 2800ms, 发现 3 设备
📊 状态查询: 8.5ms
🌐 网络延迟: 850ms
💾 内存影响: 78KB
━━━━━━━━━━━━━━━━━━━━━━━━
📊 性能评分: 92 分 (🏆 优秀)
⏰ 测试时间: 14:25:33
```

### 优化建议
- **🚀 初始化优化**: 异步初始化，避免阻塞主线程
- **⚡ 搜索优化**: 使用增量回调，提升用户体验
- **💾 内存优化**: 及时释放资源，避免内存泄漏
- **🌐 网络优化**: 连接池复用，减少网络开销

## 💡 技术特点

### 架构设计
- **🎯 门面模式** - 单一入口，简化API复杂度
- **🔒 封装性** - internal包隐藏实现细节
- **🏗️ 组件化** - 模块化设计，职责分离
- **⚡ 异步处理** - 协程 + 回调，性能优化

### 代码质量
- **🔧 类型安全** - 强类型系统，编译时错误检查
- **📝 文档完整** - 详细注释和示例代码
- **🧪 测试覆盖** - 完整的功能和性能测试
- **🛡️ 错误处理** - 健壮的异常处理机制

### 用户体验
- **🎨 现代UI** - Material Design 3.0设计语言
- **📱 响应式** - 适配不同屏幕尺寸
- **⚡ 高性能** - 优化的网络和内存使用
- **🔍 可调试** - 详细的日志和状态信息

---

## 📄 许可证

```
MIT License

Copyright (c) 2024 Yinnho

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

**🌟 如果这个项目对你有帮助，请给一个Star！**

📧 **联系方式**: yinnho@example.com  
🐛 **问题反馈**: [GitHub Issues](https://github.com/yinnho/upnpcast/issues)  
📖 **完整文档**: [Wiki](https://github.com/yinnho/upnpcast/wiki) 