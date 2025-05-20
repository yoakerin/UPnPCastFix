# UPnPCast API 迁移步骤指南

本指南提供了将应用从当前子包API迁移到新的顶层API的具体步骤和代码示例。

## 导入语句迁移

以下是从子包导入迁移到顶层包导入的对照表：

| 旧导入 | 新导入 |
|-------|-------|
| `import com.yinnho.upnpcast.manager.DLNACastManager` | `import com.yinnho.upnpcast.DLNACastManager` |
| `import com.yinnho.upnpcast.interfaces.CastListener` | `import com.yinnho.upnpcast.CastListener` |
| `import com.yinnho.upnpcast.model.RemoteDevice` | `import com.yinnho.upnpcast.RemoteDevice` |
| `import com.yinnho.upnpcast.interfaces.PlaybackState` | `import com.yinnho.upnpcast.PlaybackState` |
| `import com.yinnho.upnpcast.exception.DLNAException` | `import com.yinnho.upnpcast.DLNAException` |

## 迁移示例

### 示例1：基本初始化和设备发现

**旧代码**:
```kotlin
import com.yinnho.upnpcast.manager.DLNACastManager
import com.yinnho.upnpcast.interfaces.CastListener
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.exception.DLNAException

class MainActivity : AppCompatActivity() {
    private lateinit var castManager: DLNACastManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化
        castManager = DLNACastManager.getInstance(applicationContext)
        
        // 开始设备发现
        castManager.startDiscovery()
    }
}
```

**新代码**:
```kotlin
import com.yinnho.upnpcast.DLNACastManager
import com.yinnho.upnpcast.CastListener
import com.yinnho.upnpcast.RemoteDevice
import com.yinnho.upnpcast.DLNAException

class MainActivity : AppCompatActivity() {
    private lateinit var castManager: DLNACastManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化 (API保持不变，只有导入路径变化)
        castManager = DLNACastManager.getInstance(applicationContext)
        
        // 开始设备发现
        castManager.startDiscovery()
    }
}
```

### 示例2：实现设备监听器

**旧代码**:
```kotlin
import com.yinnho.upnpcast.manager.DLNACastManager
import com.yinnho.upnpcast.interfaces.CastListener
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.exception.DLNAException

castManager.setCastListener(object : CastListener {
    override fun onDeviceListUpdated(devices: List<RemoteDevice>) {
        // 处理设备列表更新
        deviceAdapter.updateDevices(devices)
    }
    
    override fun onConnected(device: RemoteDevice) {
        Toast.makeText(this@MainActivity, "已连接到: ${device.details.friendlyName}", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDisconnected() {
        Toast.makeText(this@MainActivity, "设备已断开连接", Toast.LENGTH_SHORT).show()
    }
    
    override fun onError(error: DLNAException) {
        Toast.makeText(this@MainActivity, "错误: ${error.message}", Toast.LENGTH_SHORT).show()
    }
})
```

**新代码**:
```kotlin
import com.yinnho.upnpcast.DLNACastManager
import com.yinnho.upnpcast.CastListener
import com.yinnho.upnpcast.RemoteDevice
import com.yinnho.upnpcast.DLNAException

castManager.setCastListener(object : CastListener {
    override fun onDeviceListUpdated(devices: List<RemoteDevice>) {
        // 处理设备列表更新
        deviceAdapter.updateDevices(devices)
    }
    
    override fun onConnected(device: RemoteDevice) {
        Toast.makeText(this@MainActivity, "已连接到: ${device.displayName}", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDisconnected() {
        Toast.makeText(this@MainActivity, "设备已断开连接", Toast.LENGTH_SHORT).show()
    }
    
    override fun onError(error: DLNAException) {
        Toast.makeText(this@MainActivity, "错误: ${error.message}", Toast.LENGTH_SHORT).show()
    }
})
```

### 示例3：播放控制

**旧代码**:
```kotlin
import com.yinnho.upnpcast.manager.DLNACastManager
import com.yinnho.upnpcast.interfaces.PlaybackState

// 播放媒体
castManager.playMedia(
    deviceId = "设备ID",
    mediaUrl = "https://example.com/video.mp4",
    title = "示例视频"
)

// 设置播放状态监听器
castManager.setPlaybackStateListener(object : DLNACastManager.PlaybackStateListener {
    override fun onPlaybackStateChanged(state: String) {
        val statusText = when(state) {
            PlaybackState.PLAYING.name -> "正在播放"
            PlaybackState.PAUSED.name -> "已暂停"
            PlaybackState.STOPPED.name -> "已停止"
            else -> state
        }
        statusTextView.text = statusText
    }
    
    override fun onPositionChanged(positionMs: Long, durationMs: Long) {
        // 更新进度条
        seekBar.max = (durationMs / 1000).toInt()
        seekBar.progress = (positionMs / 1000).toInt()
    }
})
```

**新代码**:
```kotlin
import com.yinnho.upnpcast.DLNACastManager
import com.yinnho.upnpcast.PlaybackState

// 播放媒体 (API保持不变)
castManager.playMedia(
    deviceId = "设备ID",
    mediaUrl = "https://example.com/video.mp4",
    title = "示例视频"
)

// 设置播放状态监听器
castManager.setPlaybackStateListener(object : DLNACastManager.PlaybackStateListener {
    override fun onPlaybackStateChanged(state: String) {
        val statusText = when(state) {
            PlaybackState.PLAYING.name -> "正在播放"
            PlaybackState.PAUSED.name -> "已暂停"
            PlaybackState.STOPPED.name -> "已停止"
            else -> state
        }
        statusTextView.text = statusText
    }
    
    override fun onPositionChanged(positionMs: Long, durationMs: Long) {
        // 更新进度条
        seekBar.max = (durationMs / 1000).toInt()
        seekBar.progress = (positionMs / 1000).toInt()
    }
})
```

## 数据模型变化

在新的API中，RemoteDevice类可能有一些变化：

**旧版RemoteDevice使用**:
```kotlin
// 访问设备详情
val deviceName = device.details.friendlyName ?: "未命名设备"
val deviceAddress = device.identity.descriptorURL?.host ?: ""
val deviceId = device.identity.udn
```

**新版RemoteDevice使用**:
```kotlin
// 使用简化属性
val deviceName = device.displayName  // 直接使用显示名称
val deviceAddress = device.address   // 直接使用地址
val deviceId = device.id             // 直接使用ID
```

## 自动迁移工具

为了简化迁移过程，我们提供了一个简单的IDE插件，可以自动更新导入语句。您可以在[这里](https://github.com/yinnho/upnpcast-migration-plugin)下载。

使用方法：
1. 在Android Studio中安装插件
2. 选择 Tools → UPnPCast Migration
3. 选择您的项目目录
4. 点击"开始迁移"

## 常见问题解答

### Q: 新旧API能同时使用吗？
A: 是的，我们设计了向后兼容层，可以同时使用新旧API。但我们建议尽快完全迁移到新API。

### Q: 迁移会影响性能吗？
A: 不会，新API通过委托模式直接调用原有实现，性能开销可以忽略不计。

### Q: 需要更新库的版本吗？
A: 是的，请更新到v1.x.0或更高版本以支持新API。

## 迁移检查清单

- [ ] 更新UPnPCast库到最新版本
- [ ] 更新所有导入语句
- [ ] 检查RemoteDevice对象的使用方式
- [ ] 测试应用的所有DLNA功能
- [ ] 移除对旧API类的所有引用

如果您在迁移过程中遇到任何问题，请在[GitHub Issues](https://github.com/yinnho/upnpcast/issues)报告，或联系我们的支持团队。 