# UPnPCast项目最佳实践指南

本文档提供了UPnPCast项目代码开发和维护的最佳实践、命名规范和架构指导。

## 代码风格与命名规范

### 1. 命名规范

#### 类名命名

- 使用大驼峰命名法（PascalCase）
- 统一使用`Dlna`前缀（不使用`DLNA`全大写）
- 类名应当表明其职责，如`DlnaDeviceManager`而非简单的`DeviceManager`

```kotlin
// 推荐
class DlnaController
class DlnaDeviceManager

// 不推荐
class dlnaController  // 小写开头
class DLNA_Controller // 使用下划线
class DLNAController  // 不一致的大写风格
```

#### 包名命名

- 全部小写
- 使用单数形式（如`device`而非`devices`）
- 包名应当反映其包含内容的领域

```kotlin
// 推荐
package com.yinnho.upnpcast.device
package com.yinnho.upnpcast.network.ssdp

// 不推荐
package com.yinnho.upnpcast.Devices  // 大写字母和复数
package com.yinnho.upnpcast.networkStuff  // 不专业的命名
```

#### 接口命名

- 不要使用`I`前缀
- 使用能表达行为的名称，如`Playable`而非`PlayerInterface`

```kotlin
// 推荐
interface DeviceListener
interface MediaRenderer

// 不推荐
interface IDeviceListener  // I前缀
interface DeviceListenerInterface  // 冗余的Interface后缀
```

#### 方法命名

- 使用小驼峰命名法（camelCase）
- 动词开头，表示行为
- 布尔返回类型的方法应以`is`、`has`、`can`等开头

```kotlin
// 推荐
fun searchDevices()
fun isDeviceConnected(): Boolean

// 不推荐
fun Search_Devices()  // 大写开头和下划线
fun deviceConnected()  // 布尔返回值的方法没有表示状态的前缀
```

#### 常量命名

- 全大写，使用下划线分隔
- 放在文件顶部或companion object中

```kotlin
// 推荐
const val SSDP_PORT = 1900
companion object {
    const val DEFAULT_TIMEOUT_MS = 5000
}

// 不推荐
const val ssdpPort = 1900  // 小写
const val DefaultTimeout = 5000  // 大小写混合，没有单位说明
```

### 2. 文件结构

每个Kotlin文件应当遵循以下结构：

1. 包声明
2. 导入语句（按字母顺序排列）
3. 文件级注释（如有）
4. 顶层声明（如类、接口、函数等）

类内部结构应当遵循以下顺序：

1. 属性
   - 常量
   - 初始化属性
   - 延迟初始化属性
2. 伴生对象
3. 构造函数
4. 覆盖方法
5. 公共方法
6. 内部和私有方法
7. 内部类

### 3. 注释规范

- 使用KDoc风格的注释
- 类和公共方法必须有文档注释
- 复杂算法需要添加适当注释
- 避免无意义的注释

```kotlin
/**
 * DLNA设备管理器，负责设备的发现、连接和状态管理
 *
 * @property context 应用上下文，用于访问系统服务
 */
class DlnaDeviceManager(private val context: Context) {

    /**
     * 搜索局域网内的DLNA设备
     *
     * @param timeout 搜索超时时间，单位毫秒
     * @return 是否成功启动搜索
     */
    fun searchDevices(timeout: Int = DEFAULT_TIMEOUT_MS): Boolean {
        // 实现...
    }
}
```

## 架构最佳实践

### 1. 分层架构

UPnPCast项目采用分层架构，包括：

- **表示层**：UI组件和视图模型
- **业务逻辑层**：控制器和管理器
- **数据层**：数据模型和数据源
- **网络层**：网络通信和协议处理

各层之间应当通过接口通信，保持松耦合。

### 2. 依赖注入

- 使用构造函数注入依赖
- 对于复杂对象图，考虑使用Hilt或Koin等DI框架
- 避免直接在类内部创建依赖

```kotlin
// 推荐
class DlnaDeviceManager(
    private val registry: Registry,
    private val router: Router,
    private val config: ServiceConfiguration
)

// 不推荐
class DlnaDeviceManager {
    private val registry = RegistryImpl.getInstance()
    private val router = RouterImpl.getInstance()
}
```

### 3. 错误处理

- 使用密封类（sealed class）表示操作结果
- 异常仅用于程序错误，不作为正常流程控制
- 日志记录必须包含足够的上下文信息

```kotlin
sealed class DeviceSearchResult {
    data class Success(val devices: List<Device>) : DeviceSearchResult()
    data class Error(val exception: Exception) : DeviceSearchResult()
}

fun searchDevices(): DeviceSearchResult {
    return try {
        // 搜索逻辑...
        DeviceSearchResult.Success(foundDevices)
    } catch (e: Exception) {
        Log.e(TAG, "搜索设备失败", e)
        DeviceSearchResult.Error(e)
    }
}
```

### 4. 协程和异步处理

- 使用协程处理异步操作，避免回调地狱
- 对于耗时操作，明确指定调度器
- 妥善处理协程生命周期

```kotlin
class DlnaDeviceManager(
    private val coroutineScope: CoroutineScope,
    // 其他依赖...
) {
    fun searchDevices() {
        coroutineScope.launch(Dispatchers.IO) {
            // 网络搜索操作...
            
            withContext(Dispatchers.Main) {
                // 更新UI或通知监听器
            }
        }
    }
}
```

## DLNA/UPnP特定最佳实践

### 1. 设备发现

- 使用多种搜索机制提高设备发现率
- 实现优雅的失败回退机制
- 设置合理的搜索超时时间
- 使用设备缓存减少重复搜索

```kotlin
fun searchDevices() {
    // 首先尝试使用标准SSDP多播搜索
    if (!standardSsdpSearch()) {
        // 回退到直接搜索已知IP地址范围
        directAddressSearch()
    }
    
    // 应用缓存策略
    applyDeviceCache()
}
```

### 2. 设备连接与控制

- 支持多种设备类型和厂商特定协议
- 实现稳健的命令重试机制
- 监控连接状态并自动恢复
- 合理处理设备断开和重连

```kotlin
fun connectToDevice(device: Device): Boolean {
    // 设备类型适配
    val renderer = deviceAdapterFactory.createAdapter(device)
    
    // 连接并设置重试策略
    return renderer.connect(RetryPolicy(maxRetries = 3, delayMs = 1000))
}
```

### 3. 媒体管理

- 支持多种媒体格式和编解码器
- 处理不同设备的格式兼容性问题
- 实现合理的缓冲策略
- 提供媒体元数据管理

```kotlin
fun playMedia(device: Device, media: MediaItem) {
    // 检查格式兼容性
    val compatibleFormat = formatAdapter.getCompatibleFormat(device, media)
    
    // 设置缓冲策略
    val bufferPolicy = BufferPolicy.forNetworkType(networkType)
    
    // 开始播放
    mediaController.play(device, compatibleFormat, bufferPolicy)
}
```

## 性能优化

### 1. 内存管理

- 避免内存泄漏，特别是长寿命对象引用短寿命对象
- 谨慎使用单例模式
- 及时关闭资源和取消协程

```kotlin
class DlnaController {
    private var deviceListener: WeakReference<DeviceListener>? = null
    
    fun setDeviceListener(listener: DeviceListener) {
        deviceListener = WeakReference(listener)
    }
    
    fun release() {
        deviceListener = null
        coroutineScope.cancel()
        // 释放其他资源...
    }
}
```

### 2. 网络优化

- 使用连接池管理HTTP连接
- 实现合理的请求超时和重试策略
- 减少不必要的网络请求
- 考虑网络条件变化

```kotlin
object NetworkManager {
    private val connectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)
    
    fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
```

### 3. 电池优化

- 减少唤醒锁使用
- 合理调整搜索频率
- 监听网络变化，避免不必要的操作
- 在后台时降低资源使用

```kotlin
fun adjustSearchFrequency(isAppVisible: Boolean) {
    if (isAppVisible) {
        searchInterval = ACTIVE_SEARCH_INTERVAL
    } else {
        searchInterval = BACKGROUND_SEARCH_INTERVAL
    }
}
```

## 测试最佳实践

### 1. 单元测试

- 每个公共方法都应有对应的单元测试
- 使用依赖注入便于模拟依赖
- 测试边界条件和异常情况
- 避免测试内部实现细节

```kotlin
@Test
fun `searchDevices returns devices when search successful`() {
    // 准备
    val mockRouter = mock(Router::class.java)
    whenever(mockRouter.search()).thenReturn(true)
    
    val controller = DlnaController(mockRouter)
    
    // 执行
    val result = controller.searchDevices()
    
    // 验证
    assertTrue(result)
    verify(mockRouter, times(1)).search()
}
```

### 2. 集成测试

- 测试关键组件的交互
- 模拟网络条件变化
- 测试真实设备交互场景
- 包括异常恢复测试

### 3. UI测试

- 测试设备列表显示和选择
- 测试媒体播放控制
- 测试错误提示和恢复流程

## 文档最佳实践

### 1. 代码文档

- 关键类和方法必须有文档注释
- 文档应解释"为什么"而不仅仅是"做什么"
- 保持文档与代码同步更新

### 2. 架构文档

- 提供整体架构图
- 说明核心组件及其职责
- 记录设计决策和权衡考虑

### 3. 使用文档

- 提供完整的API使用示例
- 说明常见错误和解决方案
- 包括性能和兼容性指南

## 代码合并与精简最佳实践

在进行代码合并和精简的过程中，遵循以下最佳实践可以确保重构的质量和效率。

### 1. 合并前的准备工作

- **功能地图绘制**：在合并类之前，先绘制各个类的功能地图，明确哪些方法具有相似功能
- **依赖分析**：分析待合并类的内部和外部依赖，避免合并后产生循环依赖
- **测试覆盖检查**：确保待合并代码有足够的测试覆盖，以验证合并后的功能完整性

```kotlin
// 例如，在合并DlnaController和DlnaControllerWrapper前
// 1. 列出两个类的所有公共方法
// 2. 分析方法签名和实现的相似性
// 3. 找出可以合并的方法和必须保留的独特功能
```

### 2. 合并策略

- **保留最清晰的实现**：当两个方法实现相似功能时，保留设计更清晰、性能更好的那个
- **提取共同代码到基类**：对于类似但不完全相同的功能，考虑提取共同部分到基类或工具方法
- **接口向后兼容**：确保合并后的类保持与原API的兼容性，或提供明确的迁移路径

```kotlin
// 合并两个相似方法时的策略
fun searchDevices() {
    // 合并DlnaController.searchDevices()和DlnaControllerWrapper.searchDevices()的核心逻辑
    // 保留最有效的实现方式
    // 确保包含两者的所有关键功能
}
```

### 3. 代码精简技术

- **去除重复代码**：合并相似的方法实现，消除重复逻辑
- **简化条件判断**：使用更简洁的条件表达式，如`when`表达式或elvis操作符
- **利用Kotlin特性**：使用扩展函数、作用域函数等Kotlin特性简化代码
- **数据类的应用**：对纯数据持有对象使用数据类减少样板代码

```kotlin
// 精简前
fun getDevice(deviceId: String): Device? {
    if (deviceId.isEmpty()) {
        return null
    }
    for (device in deviceList) {
        if (device.id == deviceId) {
            return device
        }
    }
    return null
}

// 精简后
fun getDevice(deviceId: String): Device? {
    if (deviceId.isEmpty()) return null
    return deviceList.find { it.id == deviceId }
}
```

### 4. 合并后验证

- **编译时检查**：确保合并后的代码能够正确编译，解决所有语法和类型错误
- **单元测试执行**：运行所有相关测试，确保功能正确性
- **集成测试验证**：进行端到端测试，验证合并后的组件在真实场景中工作正常
- **代码评审**：邀请其他开发者评审合并后的代码，检查潜在问题

### 5. 性能优化

- **减少对象创建**：合并后检查并减少不必要的对象创建
- **避免冗余操作**：消除重复的计算和检查
- **优化循环和集合操作**：使用更高效的集合操作替代手动循环
- **惰性初始化**：对耗资源的对象使用惰性初始化

```kotlin
// 性能优化示例
// 优化前
val devices = getAllDevices()
val filteredDevices = devices.filter { it.isActive }
val sortedDevices = filteredDevices.sortedBy { it.name }
return sortedDevices

// 优化后
return getAllDevices()
    .asSequence()
    .filter { it.isActive }
    .sortedBy { it.name }
    .toList()
```

### 6. 文档和注释

- **更新类注释**：合并后更新类的KDoc注释，说明合并的来源和目的
- **标记API变化**：使用`@Deprecated`标记被替换的API，并提供迁移建议
- **添加内部注释**：对复杂的合并逻辑添加解释性注释
- **维护变更日志**：记录所有API变更，方便其他开发者了解变化

```kotlin
/**
 * DLNA控制器核心类，负责设备发现和媒体控制
 * 
 * 注意：此类合并了原DlnaController和DlnaControllerWrapper的功能
 */
class DlnaController {
    // 实现...
}
```

---

遵循以上最佳实践将帮助团队维护一个高质量、可扩展的UPnPCast项目，提高代码可读性和可维护性，同时减少错误和技术债务。 