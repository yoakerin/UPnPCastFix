# UPnPCast本地视频投屏功能需求

## 📋 功能概述

希望在UPnPCast库中增加本地视频文件投屏功能，让开发者能够直接投屏本地存储的视频文件到DLNA设备，而不仅限于网络URL投屏。

## 🎯 需求背景

当前UPnPCast库只支持网络URL投屏，但在实际应用中，用户经常需要投屏本地视频文件（如相册视频、下载的电影等）。为了实现这个功能，开发者需要自己搭建HTTP文件服务器，增加了开发复杂度。

## ✅ 技术验证

我们已在86Cast项目中成功验证了完整的技术方案：

**核心技术栈：**
- **HTTP服务器**: NanoHTTPD (Android优化，兼容API 24+)
- **MIME类型策略**: `application/octet-stream` (最佳电视兼容性)
- **Range请求支持**: 断点续传，支持大文件
- **设备兼容性**: 完美支持小米电视等主流设备

**测试结果：**
- ✅ 小文件投屏 (11MB): 完美运行
- ✅ 大文件投屏 (300+MB): 稳定播放
- ✅ 小米电视兼容性: 100%成功率

## 🚀 建议的API设计

### 方案1: 简化集成API

```kotlin
// 1. 初始化（现有功能保持不变）
DLNACast.init(context)

// 2. 搜索设备（现有功能保持不变）
DLNACast.search(10000) { devices ->
    // 3. 新增：本地文件投屏 - 一行代码搞定
    DLNACast.castLocalFile("/sdcard/movie.mp4", device) { success, message ->
        if (success) {
            println("投屏成功")
        } else {
            println("投屏失败: $message")
        }
    }
}
```

### 方案2: 高级配置API

```kotlin
// 支持更多配置选项
DLNACast.castLocalFile(
    filePath = "/sdcard/video.mp4",
    device = device,
    options = LocalCastOptions(
        description = "我的视频",
        serverPort = 8081,
        autoPlay = true,
        mimeType = "application/octet-stream"
    )
) { success, message ->
    // 处理结果
}
```

### 方案3: 分离式API

```kotlin
// 高级用户可以分步操作
val fileUrl = DLNACast.getLocalFileUrl("/sdcard/movie.mp4")
DLNACast.cast(fileUrl, device) { success ->
    // 处理结果
}
```

## 🏗️ 实现方案建议

### 依赖添加

```gradle
// 在库的build.gradle中添加
implementation 'org.nanohttpd:nanohttpd:2.3.1'
```

### 核心实现思路

1. **文件服务器管理**
   ```kotlin
   class LocalFileServer {
       companion object {
           private var instance: NanoHTTPD? = null
           
           fun startServer(context: Context): String? {
               // 启动NanoHTTPD服务器
               // 返回服务器地址
           }
           
           fun getFileUrl(filePath: String): String {
               // 生成文件访问URL
               // 支持Base64编码处理特殊字符路径
           }
       }
   }
   ```

2. **MIME类型策略**
   ```kotlin
   private fun getMimeType(filePath: String): String {
       // 统一返回application/octet-stream
       // 确保最佳电视兼容性
       return "application/octet-stream"
   }
   ```

3. **Range请求支持**
   ```kotlin
   // NanoHTTPD内置支持Range请求
   // 自动处理断点续传
   // 支持大文件播放
   ```

### 关键技术细节

**1. MIME类型选择**
- 推荐统一使用 `application/octet-stream`
- 经验证，这是小米电视等设备的最佳选择
- 避免使用具体类型如 `video/mp4`，可能导致兼容性问题

**2. 端口管理**
- 默认端口: 8081
- 支持端口冲突时自动递增
- 端口范围: 8081-8090

**3. 路径编码**
- 支持Base64编码文件路径
- 处理中文文件名和特殊字符
- 确保URL安全性

**4. 生命周期管理**
- 服务器自动启动/停止
- 内存优化，避免泄漏
- 支持多文件并发投屏

## 📱 使用场景

**场景1: 相册视频投屏**
```kotlin
// 用户选择相册中的视频投屏到电视
val videoPath = "/storage/emulated/0/DCIM/video.mp4"
DLNACast.castLocalFile(videoPath, tvDevice) { success, msg ->
    // 处理结果
}
```

**场景2: 下载文件投屏**
```kotlin
// 投屏下载到本地的电影文件
val moviePath = "/sdcard/Download/movie.mkv"
DLNACast.castLocalFile(moviePath, device) { success, msg ->
    // 处理结果  
}
```

**场景3: 应用内资源投屏**
```kotlin
// 投屏应用内的示例视频
val demoPath = context.filesDir.absolutePath + "/demo.mp4"
DLNACast.castLocalFile(demoPath, device) { success, msg ->
    // 处理结果
}
```

## 🔧 兼容性考虑

**Android版本**: 
- 最低支持 API 24 (Android 7.0)
- 与现有UPnPCast保持一致

**设备兼容性**:
- 小米电视 ✅ (已验证)
- Sony电视 ✅ (理论兼容)
- Samsung电视 ✅ (理论兼容)
- 其他DLNA设备 ✅ (标准协议)

**文件格式支持**:
- MP4, AVI, MKV, MOV (主流视频格式)
- MP3, AAC, FLAC (音频格式)
- 依赖目标设备的解码能力

## 📊 性能特征

**文件大小支持**:
- 小文件 (<100MB): 秒开
- 中等文件 (100MB-1GB): 流畅播放
- 大文件 (>1GB): 支持Range请求，渐进加载

**内存占用**:
- NanoHTTPD轻量级实现
- 流式传输，不占用大量内存
- 支持并发连接

**网络性能**:
- 局域网传输，速度快
- 支持WiFi Direct等场景
- 自动处理网络波动

## 🎁 开发者收益

**简化开发流程**:
- 从6-7步操作简化为2-3步
- 无需了解HTTP服务器细节
- 一站式DLNA解决方案

**降低技术门槛**:
- 新手也能快速实现本地投屏
- 减少第三方依赖管理
- 统一的错误处理和日志

**提升用户体验**:
- 更快的投屏响应速度
- 更好的大文件播放体验
- 更稳定的连接质量

## 🏷️ API命名建议

```kotlin
// 核心API
DLNACast.castLocalFile()           // 本地文件投屏
DLNACast.getLocalFileUrl()         // 获取文件URL
DLNACast.startFileServer()         // 启动文件服务器（可选）
DLNACast.stopFileServer()          // 停止文件服务器（可选）

// 配置类
LocalCastOptions                   // 本地投屏配置
ServerConfig                       // 服务器配置

// 回调接口
LocalCastCallback                  // 本地投屏回调
```

## 📈 市场价值

这个功能将显著提升UPnPCast库的竞争力：

1. **功能完整性**: 从URL投屏升级为完整投屏解决方案
2. **开发者友好**: 大幅简化本地投屏的实现复杂度  
3. **用户体验**: 支持相册视频等常见使用场景
4. **技术领先**: 基于实际验证的最佳实践方案

## ⚡ 总结

基于86Cast项目的成功经验，我们建议UPnPCast库添加本地视频投屏功能。这个功能采用成熟的NanoHTTPD + application/octet-stream技术方案，能够完美兼容小米电视等主流设备，为开发者提供真正开箱即用的本地投屏体验。

希望UPnPCast团队能够考虑这个功能请求，让更多开发者受益于简单易用的本地投屏能力！ 