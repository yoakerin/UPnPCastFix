# UPnPCast项目迁移指南

本文档提供了将UPnPCast项目代码按照新的包结构进行迁移的详细步骤。

## 迁移准备

### 1. 环境准备

迁移前，确保您的开发环境满足以下条件：

- 最新版本的Android Studio已安装
- 项目能够正常编译和运行
- Git版本控制配置正确
- 已创建迁移专用分支（如`refactor/package-structure`）

### 2. 创建迁移分支

```bash
# 确保从最新的主分支创建
git checkout main
git pull
git checkout -b refactor/package-structure
```

### 3. 备份工作区

```bash
# 可选：创建备份压缩文件
zip -r upnpcast-backup-$(date +%Y%m%d).zip .
```

## 迁移步骤

### 第一阶段：创建目录结构

在开始移动文件之前，先创建新的目录结构：

```bash
# 在app/src/main/java/com/yinnho/upnpcast/目录下创建以下目录
mkdir -p core/player core/media
mkdir -p device/types device/manager
mkdir -p network/ssdp network/router network/soap
mkdir -p wrapper
mkdir -p registry
mkdir -p config
mkdir -p manager
mkdir -p service/impl service/android
```

使用Android Studio也可以直接创建这些目录：
1. 右键点击`com.yinnho.upnpcast`包
2. 选择"New > Package"
3. 输入子包名称（如"core.player"）

### 第二阶段：迁移基础组件

#### 迁移model和utils包中的文件

1. **迁移Icon.kt到model包**：
   - 在Android Studio中，右键点击`Icon.kt`文件
   - 选择"Refactor > Move..."
   - 目标包选择`com.yinnho.upnpcast.model`
   - 勾选"Update package statements"和"Update references"
   - 点击"Refactor"

2. **迁移DeviceUtils.kt和ThreadManager.kt到utils包**：
   - 使用相同方法将这些文件移动到utils包

### 第三阶段：迁移核心组件

#### 迁移核心组件到各自的包

1. **准备迁移DlnaController.kt**：
   ```
   # 使用Android Studio的Refactor > Move功能
   源文件: com.yinnho.upnpcast.DlnaController
   目标包: com.yinnho.upnpcast.core
   ```

2. **迁移DLNAMediaController.kt**：
   ```
   源文件: com.yinnho.upnpcast.DLNAMediaController
   目标包: com.yinnho.upnpcast.core.media
   ```

3. **迁移DLNAPlayer.kt**：
   ```
   源文件: com.yinnho.upnpcast.DLNAPlayer
   目标包: com.yinnho.upnpcast.core.player
   ```

4. **迁移注册表相关文件**：
   ```
   源文件: com.yinnho.upnpcast.RegistryImpl
   目标包: com.yinnho.upnpcast.registry
   
   源文件: com.yinnho.upnpcast.DefaultRegistry
   目标包: com.yinnho.upnpcast.registry
   ```

5. **迁移网络相关文件**：
   ```
   源文件: com.yinnho.upnpcast.SsdpDatagramProcessor
   目标包: com.yinnho.upnpcast.network.ssdp
   
   源文件: com.yinnho.upnpcast.RouterImpl
   目标包: com.yinnho.upnpcast.network.router
   ```

每完成一个文件的迁移，立即编译验证，确保没有引入新的错误。

### 第四阶段：迁移高层组件

1. **迁移DlnaControllerWrapper.kt**：
   ```
   源文件: com.yinnho.upnpcast.DlnaControllerWrapper
   目标包: com.yinnho.upnpcast.wrapper
   ```

2. **迁移配置相关文件**：
   ```
   源文件: com.yinnho.upnpcast.DefaultUpnpServiceConfiguration
   目标包: com.yinnho.upnpcast.config
   ```

3. **迁移管理器相关文件**：
   ```
   源文件: com.yinnho.upnpcast.DlnaManager
   目标包: com.yinnho.upnpcast.manager
   
   源文件: com.yinnho.upnpcast.DLNACastManager
   目标包: com.yinnho.upnpcast.manager
   ```

4. **迁移服务相关文件**：
   ```
   源文件: com.yinnho.upnpcast.DefaultUpnpService
   目标包: com.yinnho.upnpcast.service.impl
   
   源文件: com.yinnho.upnpcast.AndroidUpnpServiceImpl
   目标包: com.yinnho.upnpcast.service.android
   ```

### 第五阶段：迁移设备相关文件

1. **迁移设备相关文件**：
   ```
   源文件: com.yinnho.upnpcast.Device
   目标包: com.yinnho.upnpcast.device
   
   源文件: com.yinnho.upnpcast.DeviceDescriptionRetriever
   目标包: com.yinnho.upnpcast.device
   
   源文件: com.yinnho.upnpcast.UDADeviceType
   目标包: com.yinnho.upnpcast.device.types
   
   源文件: com.yinnho.upnpcast.DeviceListManager
   目标包: com.yinnho.upnpcast.device
   
   源文件: com.yinnho.upnpcast.DLNADeviceManager
   目标包: com.yinnho.upnpcast.device.manager
   ```

## 代码合并与精简步骤

除了文件移动和重组外，本次重构还将合并相似功能、精简冗余代码。以下是详细的合并步骤：

### 第一阶段：核心控制器合并

1. **准备合并分析**：
   ```bash
   # 分析DlnaController、DLNAController和DlnaControllerWrapper中的功能重叠
   diff -u <(grep -r "public\|protected\|private" app/src/main/java/com/yinnho/upnpcast/DlnaController.kt) <(grep -r "public\|protected\|private" app/src/main/java/com/yinnho/upnpcast/DlnaControllerWrapper.kt)
   ```

2. **创建合并后的核心控制器**：
   - 在Android Studio中新建`DlnaController.kt`文件到`com.yinnho.upnpcast.core`包
   - 从旧文件中提取核心功能合并到新文件中
   - 确保保留以下功能：
     * 设备发现
     * 设备连接管理
     * 播放控制
     * 事件监听

3. **迁移单元测试**：
   - 更新相关测试用例指向新的合并类
   - 确保所有功能测试覆盖

### 第二阶段：设备管理合并

1. **分析设备相关类**：
   ```bash
   # 分析DeviceListManager和DLNADeviceManager的功能
   grep -r "public\|protected\|private" app/src/main/java/com/yinnho/upnpcast/DeviceListManager.kt
   grep -r "public\|protected\|private" app/src/main/java/com/yinnho/upnpcast/DLNADeviceManager.kt
   ```

2. **创建合并后的设备管理器**：
   - 新建`DlnaDeviceManager.kt`到`com.yinnho.upnpcast.device.manager`包
   - 实现以下统一功能：
     * 设备列表维护
     * 设备状态监控
     * 设备详情获取
     * 设备分类与过滤

3. **更新控制器引用**：
   - 修改合并后的核心控制器，使用新的设备管理器

### 第三阶段：网络层精简

1. **网络功能审查**：
   ```bash
   # 查找所有网络相关类和方法
   grep -r "Socket\|Datagram\|HTTP\|network" app/src/main/java/com/yinnho/upnpcast/
   ```

2. **创建网络管理器**：
   - 新建`DlnaNetworkManager.kt`到`com.yinnho.upnpcast.network`包
   - 整合以下功能：
     * SSDP搜索
     * 多播管理
     * HTTP请求处理
     * 错误恢复

3. **删除冗余网络代码**：
   - 合并后移除原有分散的网络实现

### 第四阶段：服务层优化

1. **服务类型分组**：
   ```bash
   # 按功能对服务实现分类
   grep -r "ServiceImpl" app/src/main/java/com/yinnho/upnpcast/
   ```

2. **整合相似服务**：
   - 将功能相似的服务实现合并
   - 按照服务类型组织代码

3. **统一服务接口**：
   - 提供统一的服务访问接口
   - 简化服务调用流程

### 第五阶段：工具类精简

1. **收集工具方法**：
   ```bash
   # 查找所有工具方法
   grep -r "static\|companion object" app/src/main/java/com/yinnho/upnpcast/
   ```

2. **按功能分类**：
   - 将工具方法按功能领域分组
   - 移除重复实现

3. **整合到统一工具类**：
   - 创建按功能分类的工具类

## 合并后的验证与清理

### 1. 重构后完整性验证

```bash
# 编译验证
./gradlew clean build

# 单元测试
./gradlew test

# 检查未使用的引用
./gradlew lintDebug
```

### 2. 代码清理

- 移除未使用的导入
- 删除注释掉的代码
- 移除重复的方法实现
- 统一日志输出格式

### 3. 性能基准测试

- 比较重构前后的内存使用
- 比较重构前后的CPU占用
- 比较重构前后的启动时间

## 向后兼容处理

为确保API兼容性，对外部调用者可见的API变化需要做以下处理：

1. **提供兼容性桥接类**：
   - 创建与旧API签名匹配的桥接类
   - 将调用委托给新实现

2. **使用`@Deprecated`标记旧API**：
   ```kotlin
   @Deprecated("使用DlnaController.searchDevices()代替", ReplaceWith("DlnaController.getInstance().searchDevices()"))
   fun searchDevices() {
       DlnaController.getInstance().searchDevices()
   }
   ```

3. **编写迁移文档**：
   - 详细记录API变化
   - 提供迁移示例

## 提交更改

```bash
# 添加所有更改
git add .

# 提交合并更改
git commit -m "Refactor: Merge similar components and optimize code structure"

# 推送到远程仓库
git push origin refactor/package-structure
```

---

## 附录：合并后的类结构

| 原始类 | 合并到 | 合并原因 |
|-------|-------|---------|
| DlnaController, DLNAController, DlnaControllerWrapper | core/DlnaController | 功能重叠，统一控制逻辑 |
| DeviceListManager, DLNADeviceManager | device/manager/DlnaDeviceManager | 统一设备管理职责 |
| SsdpDatagramProcessor, RouterImpl | network/DlnaNetworkManager | 整合网络通信逻辑 |
| 各服务实现类 | service/DlnaServiceManager | 按服务类型分组并整合 |
| 分散的工具方法 | utils/DlnaUtils | 提高代码复用性 |

## 解决常见问题

### 1. 处理导入语句错误

迁移后，您可能会遇到导入语句错误。使用以下方法解决：

1. 使用Android Studio的"Optimize Imports"功能（Ctrl+Alt+O）
2. 对于更复杂的情况，可使用"Find in Path"功能全局搜索旧的包路径

### 2. 处理循环依赖

如果遇到循环依赖问题：

1. 识别依赖关系：使用工具如`gradle dependencies`查看依赖关系
2. 抽取共享接口：将共享功能提取到接口中
3. 应用依赖反转原则：高层模块不应依赖低层模块，两者都应依赖抽象

### 3. 编译错误处理

常见编译错误及解决方案：

1. **类找不到错误**：检查导入语句和包声明
2. **方法不可访问**：检查访问修饰符，可能需要调整为public
3. **资源找不到**：确保R文件引用正确

## 测试迁移结果

### 1. 编译验证

```bash
./gradlew clean build
```

### 2. 运行单元测试

```bash
./gradlew test
```

### 3. 功能测试

执行完整的功能测试，确保所有核心功能正常工作。

## 提交更改

成功完成迁移后，提交您的更改：

```bash
# 添加所有更改
git add .

# 提交更改
git commit -m "Refactor: Reorganize package structure"

# 推送到远程仓库
git push origin refactor/package-structure
```

## 合并到主分支

创建Pull Request，经过审核后将更改合并到主分支。

---

## 附录：迁移清单

以下是完整的文件迁移清单，可以用于追踪迁移进度：

| 文件名 | 源位置 | 目标位置 | 状态 |
|-------|-------|---------|------|
| DlnaController.kt | 根目录 | core/ | □ 未完成 |
| DLNAMediaController.kt | 根目录 | core/media/ | □ 未完成 |
| DLNAPlayer.kt | 根目录 | core/player/ | □ 未完成 |
| DlnaControllerWrapper.kt | 根目录 | wrapper/ | □ 未完成 |
| RegistryImpl.kt | 根目录 | registry/ | □ 未完成 |
| DefaultRegistry.kt | 根目录 | registry/ | □ 未完成 |
| SsdpDatagramProcessor.kt | 根目录 | network/ssdp/ | □ 未完成 |
| RouterImpl.kt | 根目录 | network/router/ | □ 未完成 |
| Device.kt | 根目录 | device/ | □ 未完成 |
| DeviceDescriptionRetriever.kt | 根目录 | device/ | □ 未完成 |
| UDADeviceType.kt | 根目录 | device/types/ | □ 未完成 |
| DeviceListManager.kt | 根目录 | device/ | □ 未完成 |
| DLNADeviceManager.kt | 根目录 | device/manager/ | □ 未完成 |
| DefaultUpnpServiceConfiguration.kt | 根目录 | config/ | □ 未完成 |
| DefaultUpnpService.kt | 根目录 | service/impl/ | □ 未完成 |
| AndroidUpnpServiceImpl.kt | 根目录 | service/android/ | □ 未完成 |
| DlnaManager.kt | 根目录 | manager/ | □ 未完成 |
| DLNACastManager.kt | 根目录 | manager/ | □ 未完成 |
| Icon.kt | 根目录 | model/ | □ 未完成 |
| DeviceUtils.kt | 根目录 | utils/ | □ 未完成 |
| ThreadManager.kt | 根目录 | utils/ | □ 未完成 | 