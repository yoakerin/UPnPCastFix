# UPnPCast

UPnPCast是一个用于Android平台的DLNA/UPnP投屏库，旨在替代不再维护的[Cling项目](https://github.com/4thline/cling)。该库提供简单易用的API，使Android应用能够发现、连接和控制本地网络中的DLNA设备，实现媒体内容投屏功能。

## 1. 项目概述

UPnPCast库的核心目标是：
- 提供简洁明了的API接口
- 确保稳定的DLNA设备发现和连接
- 支持多种媒体内容投屏
- 优化性能和内存使用
- 降低集成难度

## 2. 技术架构

### 2.1 总体架构

UPnPCast采用分层架构设计：

```
┌─────────────────────────────────────┐
│           应用层接口                │
│         DLNACastManager            │
├─────────────────────────────────────┤
│           设备控制层                │
│  DLNAPlayer    DLNAMediaController  │
├─────────────────────────────────────┤
│           协议实现层                │
│ DlnaController  DeviceManager 等   │
├─────────────────────────────────────┤
│           网络通信层                │
│        OkHttp    SOAP协议           │
└─────────────────────────────────────┘
```

### 2.2 核心组件

- **DLNACastManager**: 单例模式，是应用与库交互的主要入口点
- **DLNAPlayer**: 处理与DLNA设备的基本交互
- **DLNAMediaController**: 负责媒体内容控制
- **核心协议实现**: 实现DLNA协议功能和设备管理

### 2.3 技术栈

- **语言**: Kotlin
- **构建工具**: Gradle (Kotlin DSL)
- **网络库**: OkHttp 4.12.0
- **JSON处理**: Gson 2.10.1
- **最低Android API**: 24 (Android 7.0)
- **目标Android API**: 34

## 3. 功能特性

### 3.1 设备管理
- 自动发现局域网内DLNA设备
- 设备上线/下线监控
- 设备连接管理

### 3.2 媒体控制
- 视频/音频内容投屏
- 播放控制（播放、暂停、停止）
- 进度控制和跳转
- 音量控制

### 3.3 事件回调
- 设备发现事件
- 连接状态变更通知
- 播放状态变更通知
- 错误处理接口

## 4. 使用指南

### 4.1 添加依赖

```gradle
dependencies {
    implementation 'com.yinnho:upnpcast:1.0.0'
}
```

### 4.2 基本用法

```kotlin
// 初始化
val dlnaCastManager = DLNACastManager.getInstance(context)

// 设置监听器
dlnaCastManager.setCastListener(object : CastListener {
    override fun onDeviceListUpdated(deviceList: List<RemoteDevice>) {
        // 处理设备列表更新
    }
    
    override fun onConnected(device: RemoteDevice) {
        // 设备连接成功
    }
    
    override fun onDisconnected() {
        // 设备断开连接
    }
    
    override fun onError(errorMessage: String) {
        // 处理错误
    }
})

// 开始搜索设备
dlnaCastManager.startDiscovery()

// 连接设备
dlnaCastManager.connectToDevice(device)

// 播放媒体
dlnaCastManager.playMedia(
    mediaUrl = "http://example.com/video.mp4",
    title = "视频标题",
    metadata = "额外元数据",
    positionMs = 0
)

// 断开连接
dlnaCastManager.disconnect()

// 释放资源
dlnaCastManager.release()
```

## 5. 开发计划

### 5.1 短期计划

1. **优化代码结构**
   - 清理冗余代码
   - 修复已知Bug
   - 完善Java/Kotlin版本一致性

2. **增强稳定性**
   - 增加异常处理机制
   - 增加连接超时处理
   - 增加设备兼容性测试

3. **完善文档**
   - 编写详细的使用指南
   - 添加示例代码
   - 创建API参考文档

### 5.2 中期计划

1. **功能扩展**
   - 增加图片投屏支持
   - 增加字幕支持
   - 增加媒体信息元数据处理

2. **性能优化**
   - 减少内存使用
   - 优化设备发现速度
   - 减少电量消耗

### 5.3 长期计划

1. **平台扩展**
   - 增加更多DLNA协议变种支持
   - 支持不同厂商的特殊实现

2. **技术创新**
   - 研究并实现DLNA协议优化
   - 加入AI辅助设备选择

## 6. 优化计划

基于代码检查，我们制定了以下分阶段优化计划：

### 6.1 第一阶段：核心功能稳定性与安全性（高优先级）

#### 6.1.1 线程安全优化
- **问题**：DLNACastManager单例实现不是线程安全的
- **解决方案**：
  ```kotlin
  companion object {
      @Volatile
      private var instance: DLNACastManager? = null
      
      fun getInstance(context: Context): DLNACastManager {
          return instance ?: synchronized(this) {
              instance ?: DLNACastManager(context.applicationContext).also { instance = it }
          }
      }
  }
  
  fun release() {
      synchronized(DLNACastManager::class.java) {
          player.release()
          castListener = null
          if (instance === this) {
              instance = null
          }
      }
  }
  ```

#### 6.1.2 错误处理机制完善
- **建议**：实现统一的错误处理策略，区分不同类型的异常
- **步骤**：
  1. 创建错误类型枚举和错误基类
  2. 统一异常处理流程
  3. 在关键方法中使用自定义异常

#### 6.1.3 资源管理完善
- **建议**：确保所有资源在初始化有对应的释放机制
- **步骤**：
  1. 修改release方法，确保清理所有初始化的资源
  2. 添加DlnaController的释放机制
  3. 实现完整的生命周期管理

### 6.2 第二阶段：API完整性与功能扩展（中优先级）

#### 6.2.1 播放控制API补充
- **建议**：添加暂停、恢复、进度控制等API
- **步骤**：
  1. 在DLNACastManager中添加播放控制方法
  2. 在相应的底层实现类中实现这些功能

#### 6.2.2 设备适配层实现
- **建议**：添加针对不同厂商设备的适配机制
- **步骤**：
  1. 创建设备适配接口
  2. 实现常见厂商的适配器（如三星、小米、华为等）
  3. 在设备连接时自动选择适配器

### 6.3 第三阶段：代码结构优化与测试（后期优化）

#### 6.3.1 代码结构重构
- **建议**：拆分过大的类文件，优化类的职责划分
- **步骤**：
  1. 将DLNADeviceManager拆分为多个职责单一的类
  2. 将DlnaController拆分为功能模块

#### 6.3.2 测试覆盖
- **建议**：添加单元测试和集成测试
- **步骤**：
  1. 为核心类编写单元测试
  2. 创建测试专用模拟设备
  3. 编写端到端测试流程

#### 6.3.3 性能优化
- **建议**：优化内存使用和提高设备发现速度
- **步骤**：
  1. 使用内存分析工具找出内存使用瓶颈
  2. 优化设备发现的网络请求策略
  3. 实现设备缓存机制减少重复搜索

### 6.4 实施建议

1. **第一阶段优先实施**：线程安全和错误处理是基础，影响库的稳定性
2. **循序渐进**：每个阶段完成后进行测试，确保不引入新问题
3. **版本管理**：每个阶段的改动对应一个版本号，遵循语义化版本规范
4. **文档同步**：代码改动后及时更新README和API文档

## 7. 发布计划

### 7.1 发布准备

1. **版本管理**
   - 遵循语义化版本规范 (SemVer)
   - 主版本号：不兼容的API修改
   - 次版本号：向后兼容的功能性新增
   - 修订号：向后兼容的问题修正

2. **文档完善**
   - README.md 文件
   - 使用示例
   - API说明文档
   - 版本历史

### 7.2 发布方式

#### 7.2.1 Maven Central 发布

1. **准备工作**
   - 注册Sonatype OSSRH账户
   - 配置GPG签名
   - 设置Maven发布脚本

2. **配置build.gradle**
   ```kotlin
   plugins {
       id("com.android.library")
       id("org.jetbrains.kotlin.android")
       id("maven-publish")
       id("signing")
   }
   
   // 版本和组ID信息
   group = "com.yinnho"
   version = "1.0.0"
   
   // 配置发布
   publishing {
       publications {
           create<MavenPublication>("release") {
               from(components["release"])
               
               groupId = "com.yinnho"
               artifactId = "upnpcast"
               version = "1.0.0"
               
               // POM配置...
           }
       }
       
       repositories {
           maven {
               name = "OSSRH"
               url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
               // 凭证配置...
           }
       }
   }
   ```

#### 7.2.2 GitHub Packages 发布

配置GitHub Packages作为发布仓库。

#### 7.2.3 JitPack 发布

使用JitPack简化发布流程，只需要创建GitHub Release。

### 7.3 持续集成/持续部署

使用GitHub Actions设置CI/CD流程自动构建和测试。

## 8. 技术挑战与解决方案

### 8.1 挑战
- DLNA协议复杂度高
- 不同厂商设备实现差异
- 网络环境不稳定性
- 资源占用和性能平衡

### 8.2 解决方案
- 模块化设计降低复杂度
- 厂商特定适配层
- 强健的错误处理和重试机制
- 性能优化和缓存策略

## 9. 贡献指南

我们欢迎社区贡献。如果您想参与贡献，请遵循以下步骤：

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交您的更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启一个 Pull Request

## 10. 许可协议

该项目采用 Apache License 2.0 许可证 - 详情请查看 [LICENSE](LICENSE) 文件。

## 11. 详细文档

为了方便开发者更好地使用UPnPCast库，我们提供了以下详细文档：

- [API参考文档](docs/api/api_reference.md)：详细的API说明和参数解释
- [使用示例](docs/api/usage_examples.md)：常见使用场景的代码示例
- [排错指南](docs/api/troubleshooting.md)：常见问题的解决方案和调试技巧

这些文档会随着库的更新而持续完善。如果您在使用过程中遇到任何未在文档中提及的问题，欢迎提交Issue。

## 优化进度

- [x] 1. 移除jacoco插件和代码覆盖率配置
- [x] 2. 简化测试配置并暂时禁用测试
- [x] 3. 精简build.gradle.kts文件
- [x] 4. 将DLNAErrorType从类型别名改为直接枚举
- [x] 5. 合并多个错误处理相关类为一个ErrorService
- [x] 6. 简化设备转换逻辑，移除不必要的转换方法
- [x] 7. 简化连接重试机制
- [x] 8. 将DLNACastManagerImpl的方法移动到DLNACastManager
- [x] 9. 增加工具方法简化异常处理
- [x] 10. 直接实现播放控制相关功能，减少委托层级
- [x] 11. 移除Kotest依赖，保留JUnit 5
- [x] 12. 实现JUnit 5测试替代Kotest测试
- [x] 13. 简化测试代码，清理冗余测试目录

### 第五阶段计划（API现代化与代码精简）
- [ ] 14. 移除Java兼容性代码（@JvmStatic、@JvmOverloads等）
- [ ] 15. 包名重构，简化包结构
- [ ] 16. API简化，使用Kotlin高阶函数
- [ ] 17. 代码精简，合并相似功能
- [ ] 18. 构建优化


通过对代码的分析，我发现了以下几个主要问题：
类体积过大：
DlnaController (722行)、DLNACastManager (467行)、DlnaControllerFactory (352行)、AVTransportServiceImpl (317行)等文件都过大，违反了单一职责原则。
这些大文件应该被拆分为更小的、职责单一的类。
内存泄漏风险：
虽然使用了WeakReference来持有Context引用，但一些位置可能存在未释放的监听器和回调。
在DlnaController中，回调监听器可能没有适当的清理机制。
异常处理不统一：
有些方法使用返回布尔值表示成功/失败，有些抛出异常，有些返回特殊值（如null）。
缺乏统一的错误处理策略，没有明确区分可恢复错误和不可恢复错误。
代码重复：
URL处理、时间格式转换等逻辑在多个类中重复出现。
设备类型识别逻辑在多个地方重复实现。
单例模式实现不完善：
DlnaControllerFactory使用了companion object但未实现真正的线程安全单例。
多个工厂类之间的关系复杂，存在初始化顺序依赖问题。
配置硬编码：
服务类型、协议常量等直接硬编码在实现中。
应该提取为配置常量或者使用依赖注入方式提供。
缺乏测试友好性：
大多数类与其依赖紧密耦合，难以进行单元测试。
没有适当的接口抽象和依赖注入，使测试替身难以实现。
并发安全隐患：
使用了ConcurrentHashMap等线程安全集合，但其他共享状态可能存在并发问题。
回调处理中可能存在线程安全问题。
建议在下一步优化中，可以着重处理这些问题，特别是将大类拆分、统一错误处理、消除代码重复、完善内存管理和提高线程安全性。
