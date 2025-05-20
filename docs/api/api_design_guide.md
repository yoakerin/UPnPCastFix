# UPnPCast API 设计指南

## 设计理念

UPnPCast库遵循"核心API在顶层，实现细节在子包"的设计原则，这一原则有以下优势：

1. **用户友好性**：简化了用户的引用路径，只需导入少量顶层类即可使用
2. **实现隐藏**：内部实现细节被合理组织在子包中，避免用户直接依赖
3. **代码组织**：保持良好的内部代码组织结构
4. **易于维护**：接口与实现分离，便于独立更新
5. **向后兼容**：保持API稳定的同时允许内部实现变化

这种设计方法参考了许多成熟Java/Kotlin库的最佳实践，如Retrofit、OkHttp、Gson等。

## 核心API结构

### 顶层API（直接位于`com.yinnho.upnpcast`包下）

以下核心类和接口应位于顶层包中，作为用户的主要入口点：

| 类/接口 | 描述 | 用途 |
|---------|------|------|
| `DLNACastManager` | 主要管理类 | 用户的主要入口点，管理设备发现和控制 |
| `CastListener` | 回调接口 | 用户实现以接收事件通知 |
| `RemoteDevice` | 设备数据模型 | 表示DLNA设备的核心数据结构 |
| `PlaybackState` | 播放状态枚举 | 定义媒体播放状态 |
| `DLNAException` | 异常类 | 处理库中的错误情况 |

### 实现子包

库的内部实现应组织在以下子包中：

| 子包 | 内容 | 说明 |
|------|------|------|
| `core/` | 核心功能实现 | 基础功能和通用实现 |
| `device/` | 设备管理实现 | 设备发现、缓存和状态管理 |
| `network/` | 网络相关实现 | 网络请求、SSDP处理等 |
| `registry/` | 注册表实现 | 设备注册和管理 |
| `interfaces/` | 内部接口 | 非核心接口定义 |
| `utils/` | 工具类 | 辅助功能和实用工具 |
| `wrapper/` | 包装器 | 对外部库或旧API的包装 |

## 实现策略

核心API类应该使用委托模式，将实际实现委托给子包中的具体实现类，例如：

```kotlin
// 顶层API类
package com.yinnho.upnpcast

class DLNACastManager private constructor(context: Context) {
    // 委托给实现类
    private val impl = com.yinnho.upnpcast.manager.DLNACastManagerImpl(context)
    
    fun startDiscovery() = impl.startDiscovery()
    
    // 其他方法...
    
    companion object {
        // 工厂方法
        fun getInstance(context: Context): DLNACastManager = ...
    }
}
```

## 迁移计划

为了实现这一设计理念，我们计划：

1. 在顶层包中创建核心API类和接口
2. 将现有实现重构为内部实现类
3. 让顶层API委托给内部实现
4. 更新文档和示例代码

## 向后兼容性考虑

为了保持向后兼容性，我们将：

1. 保留旧的包结构一段时间，使用`@Deprecated`注解标记
2. 提供迁移指南和代码示例
3. 使用适配器模式连接新旧API
4. 在主要版本更新时才完全移除旧API

## 用户使用示例

使用顶层API的示例代码：

```kotlin
import com.yinnho.upnpcast.DLNACastManager
import com.yinnho.upnpcast.CastListener
import com.yinnho.upnpcast.RemoteDevice
import com.yinnho.upnpcast.DLNAException

class MyActivity : AppCompatActivity() {
    private lateinit var castManager: DLNACastManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化管理器
        castManager = DLNACastManager.getInstance(this)
        
        // 设置监听器
        castManager.setCastListener(object : CastListener {
            override fun onDeviceListUpdated(devices: List<RemoteDevice>) {
                // 处理设备列表更新
            }
            
            override fun onError(error: DLNAException) {
                // 处理错误
            }
            
            // 其他回调...
        })
        
        // 开始设备发现
        castManager.startDiscovery()
    }
}
```

## 结论

这种"核心API在顶层，实现细节在子包"的设计方法在保持代码组织性的同时，也提供了更好的用户体验。它使得库更易于使用，同时不牺牲内部代码的清晰度和可维护性。 