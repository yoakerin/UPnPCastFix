# UPnPCast API 迁移计划

本文档详细描述了将UPnPCast库的核心API从子包移回顶层包的迁移计划，同时保持内部实现的组织结构。

## 1. 迁移目标

- 将核心API类移至顶层包(`com.yinnho.upnpcast`)，提高用户使用体验
- 保持内部实现在子包中，维持良好的代码组织
- 确保向后兼容性，最小化对现有代码的影响
- 遵循业界最佳实践，如Retrofit、OkHttp等库的设计模式

## 2. 核心API移动计划

下表列出了需要移回顶层包的核心API类/接口：

| 当前路径 | 目标路径 | 类型 | 优先级 |
|---------|---------|------|-------|
| `com.yinnho.upnpcast.manager.DLNACastManager` | `com.yinnho.upnpcast.DLNACastManager` | 类 | 高 |
| `com.yinnho.upnpcast.interfaces.CastListener` | `com.yinnho.upnpcast.CastListener` | 接口 | 高 |
| `com.yinnho.upnpcast.model.RemoteDevice` | `com.yinnho.upnpcast.RemoteDevice` | 类 | 高 |
| `com.yinnho.upnpcast.interfaces.PlaybackState` | `com.yinnho.upnpcast.PlaybackState` | 枚举 | 中 |
| `com.yinnho.upnpcast.exception.DLNAException` | `com.yinnho.upnpcast.DLNAException` | 类 | 中 |

## 3. 实施步骤

### 阶段一：准备工作（1-2天）

1. **创建实现类**：
   - 将`DLNACastManager`重命名为`DLNACastManagerImpl`
   - 确保所有内部实现类的接口清晰定义

2. **设计委托模式**：
   - 为每个核心API类设计委托模式的具体实现方式
   - 确定哪些方法需要直接转发，哪些需要额外逻辑

### 阶段二：创建顶层API（2-3天）

1. **创建顶层包装类**：
   ```kotlin
   // 示例：DLNACastManager顶层类
   package com.yinnho.upnpcast
   
   import android.content.Context
   import com.yinnho.upnpcast.manager.DLNACastManagerImpl
   import com.yinnho.upnpcast.model.RemoteDevice
   
   class DLNACastManager private constructor(context: Context) {
       private val impl = DLNACastManagerImpl.getInstance(context)
       
       fun startDiscovery() = impl.startDiscovery()
       fun stopDiscovery() = impl.stopDiscovery()
       fun getAllDevices(): List<RemoteDevice> = impl.getAllDevices()
       // ... 其他方法
       
       companion object {
           @JvmStatic
           fun getInstance(context: Context): DLNACastManager {
               return DLNACastManager(context)
           }
       }
   }
   ```

2. **创建顶层接口**：
   ```kotlin
   // 示例：CastListener顶层接口
   package com.yinnho.upnpcast
   
   import com.yinnho.upnpcast.model.RemoteDevice
   
   interface CastListener {
       fun onDeviceListUpdated(devices: List<RemoteDevice>)
       fun onConnected(device: RemoteDevice)
       fun onDisconnected()
       fun onError(error: DLNAException)
   }
   ```

3. **移动必要的数据模型**：
   - 确保顶层包中的模型与内部实现保持一致
   - 使用类型别名或继承关系维护兼容性

### 阶段三：实现向后兼容（1-2天）

1. **添加过渡性代码**：
   - 使用`@Deprecated`标记旧的子包类/接口
   - 添加引导用户迁移的文档注释

2. **创建适配器**：
   - 确保新旧API之间的互操作性
   - 实现必要的类型转换和数据传递

### 阶段四：更新文档和示例（1-2天）

1. **更新API文档**：
   - 更新所有涉及API的文档
   - 强调新的推荐使用方式

2. **更新示例代码**：
   - 重写demo应用中的示例代码
   - 展示新API的最佳使用方式

3. **编写迁移指南**：
   - 详细说明旧代码如何迁移到新API
   - 提供迁移工具或脚本（如有可能）

## 4. 具体实现示例

### DLNACastManager 实现

```kotlin
// 子包实现类
package com.yinnho.upnpcast.manager

@InternalApi
class DLNACastManagerImpl private constructor(context: Context) {
    // 原有实现...
    
    companion object {
        @Volatile
        private var instance: DLNACastManagerImpl? = null
        
        fun getInstance(context: Context): DLNACastManagerImpl {
            return instance ?: synchronized(this) {
                instance ?: DLNACastManagerImpl(context.applicationContext).also { instance = it }
            }
        }
    }
}

// 顶层API类
package com.yinnho.upnpcast

import android.content.Context
import com.yinnho.upnpcast.manager.DLNACastManagerImpl

class DLNACastManager private constructor(context: Context) {
    private val impl = DLNACastManagerImpl.getInstance(context)
    
    // 简单委托方法
    fun startDiscovery() = impl.startDiscovery()
    
    // 带转换的委托方法
    fun getAllDevices(): List<RemoteDevice> {
        return impl.getAllDevices().map { it.toPublicDevice() }
    }
    
    companion object {
        @JvmStatic
        fun getInstance(context: Context): DLNACastManager {
            return DLNACastManager(context)
        }
    }
}
```

### 数据模型转换示例

```kotlin
// 内部模型扩展函数
package com.yinnho.upnpcast.model

import com.yinnho.upnpcast.RemoteDevice as PublicRemoteDevice

fun RemoteDevice.toPublicDevice(): PublicRemoteDevice {
    return PublicRemoteDevice(
        id = this.identity.udn,
        name = this.details.friendlyName ?: "",
        address = this.identity.descriptorURL?.host ?: "",
        details = this.details
    )
}

// 公共模型扩展函数
package com.yinnho.upnpcast

import com.yinnho.upnpcast.model.RemoteDevice as InternalRemoteDevice

fun RemoteDevice.toInternalDevice(): InternalRemoteDevice {
    // 转换逻辑
}
```

## 5. 测试策略

1. **单元测试**：
   - 为每个顶层API类编写单元测试
   - 确保委托模式正确执行
   - 测试边界条件和异常处理

2. **集成测试**：
   - 测试顶层API与内部实现的交互
   - 确保数据正确传递和转换

3. **迁移测试**：
   - 创建使用旧API和新API的测试案例
   - 验证行为一致性

4. **性能测试**：
   - 测量委托模式带来的性能影响
   - 确保不会有明显的性能下降

## 6. 风险与缓解

| 风险 | 可能性 | 影响 | 缓解策略 |
|------|-------|-----|---------|
| 破坏现有应用 | 中 | 高 | 严格的向后兼容性设计和充分测试 |
| 实现差异 | 中 | 中 | 详细的单元测试确保行为一致 |
| 性能下降 | 低 | 低 | 优化委托实现，减少不必要的转换 |
| 文档不一致 | 高 | 中 | 自动化文档生成，确保同步更新 |

## 7. 时间线

- **第1周**：准备工作和创建顶层API
- **第2周**：实现向后兼容和测试
- **第3周**：文档更新和最终调整
- **发布**：v1.x.0版本发布新API，保留旧API
- **后续**：v2.0.0版本可考虑完全移除旧API

## 8. 结论

通过将核心API移至顶层包，同时保持内部实现的组织结构，我们可以同时实现用户友好性和代码组织性。这种设计参考了业界最佳实践，将为用户提供更好的使用体验，也为后续的维护和扩展奠定了基础。 