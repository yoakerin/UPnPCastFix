# UPnPCast 排错指南

## 常见问题与解决方案

### 1. 设备发现问题

#### 问题：无法发现任何DLNA设备
- **可能原因**：手机与设备不在同一网络
- **解决方案**：确保手机和DLNA设备连接到同一Wi-Fi网络
- **代码检查**：
  ```kotlin
  // 检查网络连接
  if (!EnhancedNetworkManager.getInstance(context).isConnectedToWifi()) {
      // 提示用户连接到Wi-Fi
      showToast("请连接到Wi-Fi网络")
      return
  }
  ```

#### 问题：设备列表为空
- **可能原因**：搜索超时或网络权限缺失
- **解决方案**：
  1. 检查应用是否有网络权限
  2. 增加搜索时间
  3. 重启DLNA设备
- **代码检查**：
  ```kotlin
  // 在AndroidManifest.xml中确保有以下权限
  // <uses-permission android:name="android.permission.INTERNET" />
  // <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  // <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
  // <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
  ```

### 2. 连接问题

#### 问题：连接到设备失败
- **可能原因**：网络不稳定或设备拒绝连接
- **解决方案**：
  1. 确保设备开启了DLNA/UPnP服务
  2. 检查网络状态
  3. 尝试重新连接
- **代码示例**：
  ```kotlin
  // 实现重试逻辑
  private fun connectWithRetry(device: RemoteDevice, maxRetries: Int = 3) {
      var retryCount = 0
      val handler = Handler(Looper.getMainLooper())
      
      fun attemptConnect() {
          dlnaCastManager.connectToDevice(device)
          // 监听连接结果
          dlnaCastManager.setCastListener(object : CastListener {
              override fun onConnected(device: RemoteDevice) {
                  // 连接成功
              }
              
              override fun onError(errorMessage: String) {
                  if (retryCount < maxRetries) {
                      retryCount++
                      // 延迟后重试
                      handler.postDelayed({ attemptConnect() }, 2000)
                  } else {
                      // 重试失败，通知用户
                      showToast("连接失败，请检查网络后重试")
                  }
              }
              
              // 实现其他回调...
          })
      }
      
      attemptConnect()
  }
  ```

#### 问题：连接成功后很快断开
- **可能原因**：设备兼容性问题或网络不稳定
- **解决方案**：
  1. 尝试使用设备特定适配器
  2. 确保Wi-Fi信号强度足够
- **代码检查**：
  ```kotlin
  // 检查是否使用了设备适配器
  override fun onDeviceListUpdated(deviceList: List<RemoteDevice>) {
      // 记录每个设备的制造商信息，帮助调试
      deviceList.forEach { device ->
          Log.d("UPnPCast", "发现设备: ${device.name}, 制造商: ${device.manufacturer}, 型号: ${device.model}")
      }
  }
  ```

### 3. 播放问题

#### 问题：媒体无法播放
- **可能原因**：媒体格式不支持或URL无效
- **解决方案**：
  1. 确保媒体格式是DLNA设备支持的(常见如MP4, AAC等)
  2. 检查媒体URL是否可访问
  3. 确认URL是公网可访问的(DLNA设备需直接访问URL)
- **代码检查**：
  ```kotlin
  // 在播放前验证URL可访问性
  fun checkMediaUrl(url: String, callback: (Boolean) -> Unit) {
      EnhancedThreadManager.getInstance().executeAsync {
          try {
              val connection = URL(url).openConnection() as HttpURLConnection
              connection.requestMethod = "HEAD"
              connection.connectTimeout = 5000
              connection.connect()
              
              val code = connection.responseCode
              callback(code in 200..299)
          } catch (e: Exception) {
              Log.e("UPnPCast", "URL检查错误: ${e.message}", e)
              callback(false)
          }
      }
  }
  
  // 使用示例
  checkMediaUrl(videoUrl) { isAccessible ->
      if (isAccessible) {
          dlnaCastManager.playMedia(videoUrl, title, null, 0)
      } else {
          showToast("媒体URL不可访问，请检查后重试")
      }
  }
  ```

#### 问题：播放控制(暂停/恢复/跳转)不起作用
- **可能原因**：设备不完全支持DLNA控制命令
- **解决方案**：
  1. 检查设备是否完全兼容DLNA协议
  2. 尝试使用设备特定的适配器
- **调试方法**：
  ```kotlin
  // 开启详细日志可帮助发现问题
  // 在Application类中初始化时:
  DLNACastManager.setDebugMode(true) // 如果提供了这样的调试模式开关
  ```

### 4. 性能问题

#### 问题：设备发现过程缓慢
- **可能原因**：网络环境复杂或设备过多
- **解决方案**：
  1. 优化搜索参数
  2. 实现设备缓存机制
- **代码示例**：
  ```kotlin
  // 使用缓存快速加载上次发现的设备
  val cachedDevices = DeviceCache.getInstance().getCachedDevices()
  if (cachedDevices.isNotEmpty()) {
      // 先显示缓存设备，同时开始新搜索更新列表
      adapter.submitList(cachedDevices)
  }
  dlnaCastManager.startDiscovery()
  ```

#### 问题：应用内存占用过高
- **可能原因**：资源未正确释放或内存泄漏
- **解决方案**：
  1. 确保在不需要时停止设备发现
  2. 在Activity/Fragment销毁时释放资源
- **代码检查**：
  ```kotlin
  override fun onDestroy() {
      super.onDestroy()
      // 确保释放资源
      dlnaCastManager.stopDiscovery()
      dlnaCastManager.disconnect()
      dlnaCastManager.release()
  }
  ```

### 5. 兼容性问题

#### 问题：特定品牌设备无法正常工作
- **可能原因**：设备实现了非标准的DLNA协议变种
- **解决方案**：
  1. 使用设备特定适配器
  2. 查看日志确认设备制造商和型号，寻找特定问题
- **调试方法**：
  ```kotlin
  // 记录设备详细信息
  fun logDeviceDetails(device: RemoteDevice) {
      Log.d("UPnPCast", """
          设备详情:
          - 名称: ${device.name}
          - ID: ${device.id}
          - 制造商: ${device.manufacturer}
          - 型号: ${device.model}
          - 地址: ${device.address}
      """.trimIndent())
  }
  ```

### 6. 异常崩溃

#### 问题：应用在投屏过程中崩溃
- **可能原因**：未处理的异常或资源释放问题
- **解决方案**：
  1. 实现全局异常处理
  2. 检查资源释放逻辑
- **代码示例**：
  ```kotlin
  // 在Application类中添加全局异常处理
  class MyApplication : Application() {
      override fun onCreate() {
          super.onCreate()
          
          // 设置全局未捕获异常处理器
          Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
              Log.e("UPnPCast", "未捕获异常: ${throwable.message}", throwable)
              // 可以上报到崩溃收集服务
              // 尝试释放资源
              try {
                  DLNACastManager.getInstance(this).release()
              } catch (e: Exception) {
                  Log.e("UPnPCast", "资源释放异常: ${e.message}", e)
              }
          }
      }
  }
  ```

## 诊断工具

### 网络诊断
```kotlin
// 诊断网络连接
fun diagnoseNetwork(context: Context): String {
    val sb = StringBuilder()
    val networkManager = EnhancedNetworkManager.getInstance(context)
    
    sb.appendLine("网络诊断结果:")
    sb.appendLine("- 网络可用: ${networkManager.isNetworkAvailable()}")
    sb.appendLine("- WiFi连接: ${networkManager.isConnectedToWifi()}")
    sb.appendLine("- 信号强度: ${networkManager.getWifiSignalLevel()} (0-4)")
    sb.appendLine("- 本地IP: ${networkManager.getLocalIpAddress()}")
    
    return sb.toString()
}
```

### 设备兼容性检查
```kotlin
// 检查设备兼容性
fun checkDeviceCompatibility(device: RemoteDevice): String {
    val sb = StringBuilder()
    val rankingService = DeviceRankingService.getInstance()
    
    sb.appendLine("设备兼容性检查:")
    sb.appendLine("- 制造商: ${device.manufacturer}")
    sb.appendLine("- 兼容性评分: ${rankingService.getCompatibilityScore(device)}/100")
    sb.appendLine("- 是否优先设备: ${rankingService.isPriorityDevice(device)}")
    
    return sb.toString()
}
```

## 常见错误代码解释

| 错误代码 | 描述 | 解决方案 |
|---------|------|----------|
| E001 | 设备发现超时 | 检查网络连接，延长搜索时间 |
| E002 | 设备连接失败 | 检查设备状态，尝试重新连接 |
| E003 | 播放失败：不支持的媒体格式 | 使用设备支持的媒体格式 |
| E004 | 播放失败：URL无法访问 | 确保媒体URL是公网可访问的 |
| E005 | 断开连接：网络中断 | 检查网络连接，实现自动重连 |
| E006 | 设备拒绝控制命令 | 检查设备兼容性，使用设备适配器 |
