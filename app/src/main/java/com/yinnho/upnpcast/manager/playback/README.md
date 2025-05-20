# PlayerManager重构文档

## 重构目标

将臃肿的PlayerManager (397行)拆分为多个职责单一的组件，实现更好的代码组织和可维护性。

## 组件结构

### 主要组件

1. **PlayerManager** - 门面类
   - 对外提供统一的API
   - 处理异常和错误传递
   - 将请求委托给专门的子组件

2. **ControllerManager** - 控制器管理器
   - 管理设备控制器的创建
   - 负责控制器缓存
   - 提供控制器生命周期管理

3. **PlaybackOperationsManager** - 播放操作管理器
   - 处理播放、暂停、停止等操作
   - 封装异步操作执行
   - 提供统一的播放控制接口

4. **VolumeManager** - 音量管理器
   - 负责音量和静音控制
   - 提供音量调节辅助功能
   - 处理音量边界校验

## 职责划分

### 控制器管理
- **之前**: 混杂在PlayerManager中，充斥着大量缓存和创建逻辑
- **现在**: 由ControllerManager专门处理，接口更清晰

### 播放控制
- **之前**: 直接在PlayerManager中包含多个播放控制方法，代码重复
- **现在**: 委托给PlaybackOperationsManager，减少重复代码

### 音量控制
- **之前**: 混合在其他播放控制中，职责不清晰
- **现在**: 由VolumeManager专门处理，并新增了音量增减等便捷功能

## 重构优势

1. **单一职责**: 每个组件只关注一个主要功能领域
2. **代码精简**: 主类PlayerManager从397行减少到约200行
3. **功能增强**: 添加了新功能如音量增减、静音切换
4. **错误处理统一**: 通过错误转发机制统一处理错误
5. **边界条件**: 更好地处理了边界条件和异常情况

## 性能和内存改进

1. **延迟初始化**: 子组件创建时不会立即执行重量级操作
2. **资源管理**: 更好的生命周期管理和资源释放
3. **异步优化**: 统一异步任务处理，减少线程消耗

## 代码结构

```
com.yinnho.upnpcast.manager
 ├── PlayerManager.kt (门面类)
 ├── controller
 │   └── ControllerManager.kt (控制器管理)
 ├── playback
 │   └── PlaybackOperationsManager.kt (播放操作)
 └── volume
     └── VolumeManager.kt (音量控制)
```

## 使用示例

```kotlin
// 播放管理器提供统一的外部接口
val playerManager = PlayerManager()

// 播放媒体时，内部会通过ControllerManager获取控制器
// 然后委托给PlaybackOperationsManager执行
playerManager.playMedia(deviceId, mediaUrl, title)

// 音量操作委托给VolumeManager处理
playerManager.setVolume(deviceId, 50)
playerManager.increaseVolume(deviceId, 5) // 新增功能
``` 