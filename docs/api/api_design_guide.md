# UPnPCast API 设计指南

## 实际架构 - 超级简单！

UPnPCast的架构非常简单直接，没有复杂的分层设计，核心就是几个类：

### 🎯 **真实的文件结构**

```
com.yinnho.upnpcast/
├── DLNACastManager.kt     ← 唯一的用户入口，单例模式
├── CastListener.kt        ← 回调接口
├── RemoteDevice.kt        ← 设备数据类
├── PlaybackState.kt       ← 播放状态枚举
├── DLNAException.kt       ← 异常类
├── UpnpService.kt         ← UPnP服务接口
├── ControlPoint.kt        ← 控制点接口
├── Registry.kt            ← 设备注册表接口
├── UpnpServiceConfiguration.kt ← 配置类
└── internal/              ← 内部实现
    ├── UpnpServiceImpl.kt
    ├── ControlPointImpl.kt  
    ├── RegistryImpl.kt
    ├── SsdpDeviceDiscovery.kt
    ├── DlnaMediaController.kt
    └── DeviceDescriptionParser.kt
```

**就这么简单！** 总共才9个公开类 + 6个内部实现类。

## ✨ **设计理念**

### 1. **单一入口原则**
- **`DLNACastManager`** 是唯一的用户入口
- 用户不需要知道其他任何类
- 单例模式，全局使用

### 2. **接口 + 实现分离**
- 公开接口在顶层：`UpnpService`, `ControlPoint`, `Registry`
- 具体实现在 `internal/` 包下
- 用户永远不直接接触 `internal/` 包

### 3. **基于UPnP标准**
- `UpnpService` - UPnP服务核心
- `Registry` - 设备注册表（发现的设备存这里）
- `ControlPoint` - 控制点（连接和控制设备）

## 🚀 **核心API设计**

### **DLNACastManager** - 单一入口
```kotlin
class DLNACastManager {
    // 设备发现
    fun startSearch(timeoutMs: Long = 30000)
    fun stopSearch()
    
    // 设备连接
    fun connectToDevice(device: RemoteDevice): Boolean
    fun disconnect()
    
    // 媒体控制
    fun playMedia(url: String, title: String?): Boolean
    fun pause(): Boolean
    fun resume(): Boolean
    fun stop(): Boolean
    
    // 状态查询
    fun getAllDevices(): List<RemoteDevice>
    fun getCurrentDevice(): RemoteDevice?
    fun getCurrentState(): PlaybackState
    
    // 监听器
    fun setCastListener(listener: CastListener?)
    fun setPlaybackStateListener(listener: PlaybackStateListener?)
}
```

### **CastListener** - 事件回调
```kotlin
interface CastListener {
    fun onDeviceListUpdated(devices: List<RemoteDevice>)
    fun onConnected(device: RemoteDevice)
    fun onDisconnected()
    fun onError(error: DLNAException)
}
```

### **RemoteDevice** - 设备信息
```kotlin
data class RemoteDevice(
    val id: String,           // 设备唯一ID
    val displayName: String,  // 显示名称
    val manufacturer: String, // 制造商
    val address: String,      // IP地址
    val details: Map<String, Any> // 其他信息
)
```

## 🔧 **内部实现说明**

### **不复杂的内部结构**
- **`UpnpServiceImpl`** - 管理整个UPnP服务生命周期
- **`RegistryImpl`** - 维护发现的设备列表
- **`ControlPointImpl`** - 处理设备连接和控制
- **`SsdpDeviceDiscovery`** - 负责SSDP协议的设备发现
- **`DlnaMediaController`** - 处理媒体播放控制
- **`DeviceDescriptionParser`** - 解析设备描述XML

### **数据流向超级简单**
```
用户调用DLNACastManager 
    ↓
委托给UpnpService
    ↓
UpnpService协调Registry和ControlPoint
    ↓
Registry管理设备，ControlPoint处理连接和控制
    ↓
通过CastListener回调给用户
```

## 💡 **为什么这么简单？**

1. **专注核心功能** - 只做DLNA投屏，不做其他
2. **单例模式** - 全局只有一个Manager实例
3. **委托模式** - Manager只是外观，真正工作由内部类完成
4. **基于成熟协议** - UPnP是标准协议，不需要重新发明轮子

## 📝 **使用示例**

### **完整的使用流程**
```kotlin
// 1. 获取Manager（单例）
val manager = DLNACastManager.getInstance(context)

// 2. 设置监听器
manager.setCastListener(object : CastListener {
    override fun onDeviceListUpdated(devices: List<RemoteDevice>) {
        // 更新设备列表UI
    }
    override fun onConnected(device: RemoteDevice) {
        // 连接成功，可以投屏了
    }
    override fun onError(error: DLNAException) {
        // 处理错误
    }
})

// 3. 开始搜索设备
manager.startSearch()

// 4. 连接设备
manager.connectToDevice(selectedDevice)

// 5. 投屏媒体
manager.playMedia("http://example.com/video.mp4", "我的视频")
```

**就这么简单！** 用户不需要了解UPnP协议细节，不需要管理复杂的状态，只要调用几个方法就能实现DLNA投屏。

## 🎉 **总结**

UPnPCast的架构哲学就是：**让复杂的变简单，让简单的更简单**。

- ✅ **对用户简单**：只需要一个Manager类
- ✅ **对开发者简单**：代码结构清晰，职责分明  
- ✅ **对维护简单**：基于标准协议，不重复造轮子

**这就是为什么叫"超级简单"！** 🚀 