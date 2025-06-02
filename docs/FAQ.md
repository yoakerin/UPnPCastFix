# 🤔 常见问题解答 (FAQ)

## 📋 目录
- [设备发现问题](#设备发现问题)
- [投屏失败问题](#投屏失败问题)
- [网络相关问题](#网络相关问题)
- [性能优化问题](#性能优化问题)
- [API使用问题](#API使用问题)

## 🔍 设备发现问题

### Q: 为什么搜索不到设备？
**A:** 常见原因和解决方案：
1. **网络权限检查**
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
   ```

2. **设备确保在同一网络**
   - 手机和电视必须连接到相同的WiFi网络
   - 确保路由器没有开启AP隔离功能

3. **电视DLNA功能已开启**
   - 小米电视：设置 → 账号与安全 → 投屏接收
   - 三星电视：源 → 连接指南 → 屏幕镜像
   - LG电视：设置 → 网络 → Screen Share

### Q: 设备列表显示重复设备怎么办？
**A:** 
```kotlin
// 使用智能去重搜索
DLNACast.search(timeout = 15000) { devices ->
    val uniqueDevices = devices.distinctBy { it.id }
    // 处理去重后的设备列表
}
```

## 📺 投屏失败问题

### Q: 投屏时提示"连接失败"？
**A:** 排查步骤：
1. **检查媒体URL可访问性**
   ```kotlin
   // 确保URL可访问
   val testUrl = "http://your-server.com/video.mp4"
   // 可以先在浏览器测试URL是否可访问
   ```

2. **使用HTTPS时的证书问题**
   ```kotlin
   // 对于自签名证书，可能需要设置信任
   // 建议使用HTTP协议进行测试
   ```

3. **媒体格式兼容性**
   - 推荐格式：MP4 (H.264), MP3, JPEG
   - 避免使用专有格式或DRM保护的内容

### Q: 投屏成功但没有声音？
**A:** 
```kotlin
// 检查设备音量状态
DLNACast.control(MediaAction.GET_STATE) { success ->
    val state = DLNACast.getState()
    if (state.isMuted) {
        // 取消静音
        DLNACast.control(MediaAction.MUTE, false)
    }
}
```

## 🌐 网络相关问题

### Q: 在移动网络下能使用吗？
**A:** 不建议，原因：
- DLNA协议基于局域网设计
- 移动网络延迟较高，影响体验
- 会消耗大量流量

### Q: 支持IPv6网络吗？
**A:** 当前版本主要支持IPv4，IPv6支持在规划中。

## ⚡ 性能优化问题

### Q: 如何提高设备发现速度？
**A:** 
```kotlin
// 调整搜索超时时间
DLNACast.search(timeout = 5000) { devices ->
    // 较短的超时时间可以更快获得结果
    // 但可能遗漏响应较慢的设备
}

// 使用智能搜索
DLNACast.smartCast(url, title, { success ->
    // 自动选择最佳设备，跳过手动选择过程
}) { devices ->
    devices.firstOrNull { it.isTV } // 优先选择电视
}
```

### Q: 如何避免内存泄漏？
**A:** 
```kotlin
class MyActivity : AppCompatActivity() {
    override fun onDestroy() {
        super.onDestroy()
        // 必须调用release释放资源
        DLNACast.release()
    }
}
```

## 🔧 API使用问题

### Q: 如何同步等待搜索结果？
**A:** 
```kotlin
// 方案1：使用协程
suspend fun searchDevicesSync(): List<Device> {
    return suspendCoroutine { continuation ->
        DLNACast.search { devices ->
            continuation.resume(devices)
        }
    }
}

// 方案2：使用CountDownLatch
fun searchDevicesBlocking(): List<Device> {
    val latch = CountDownLatch(1)
    var result: List<Device> = emptyList()
    
    DLNACast.search { devices ->
        result = devices
        latch.countDown()
    }
    
    latch.await(10, TimeUnit.SECONDS)
    return result
}
```

### Q: 如何实现批量设备操作？
**A:** 
```kotlin
// 同时投屏到多个设备
fun castToMultipleDevices(url: String, devices: List<Device>) {
    devices.forEach { device ->
        DLNACast.castToDevice(device, url) { success ->
            Log.d("DLNA", "设备 ${device.name}: $success")
        }
    }
}
```

### Q: 如何实现播放进度监控？
**A:** 
```kotlin
// 定期查询播放状态
private fun startProgressMonitoring() {
    val handler = Handler(Looper.getMainLooper())
    val runnable = object : Runnable {
        override fun run() {
            DLNACast.control(MediaAction.GET_STATE) { success ->
                if (success) {
                    val state = DLNACast.getState()
                    // 更新UI显示进度
                    updateProgress(state)
                }
            }
            handler.postDelayed(this, 1000) // 每秒查询一次
        }
    }
    handler.post(runnable)
}
```

## 🐛 错误处理最佳实践

### Q: 如何处理网络超时？
**A:** 
```kotlin
DLNACast.search(timeout = 10000) { devices ->
    if (devices.isEmpty()) {
        // 处理没有发现设备的情况
        showErrorMessage("未发现可用设备，请检查网络连接")
    } else {
        // 正常处理设备列表
    }
}
```

### Q: 如何实现重试机制？
**A:** 
```kotlin
fun castWithRetry(url: String, maxRetries: Int = 3) {
    var attempts = 0
    
    fun attemptCast() {
        DLNACast.cast(url) { success ->
            if (success) {
                // 投屏成功
                onCastSuccess()
            } else if (++attempts < maxRetries) {
                // 重试
                Handler().postDelayed({ attemptCast() }, 2000)
            } else {
                // 最终失败
                onCastFailed("投屏失败，已重试 $maxRetries 次")
            }
        }
    }
    
    attemptCast()
}
```

## 💡 更多问题？

如果你遇到其他问题：
1. 📖 查看[完整API文档](API.md)
2. 🎯 参考[Demo应用](../app-demo/)  
3. 🐛 在[GitHub Issues](https://github.com/yinnho/UPnPCast/issues)提交问题
4. 💬 加入技术交流群获得支持 