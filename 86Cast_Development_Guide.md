# 86Cast - 投屏应用技术实现文档

## 📋 项目概述

**项目名称**: 86Cast  
**项目类型**: Android投屏应用 (类似乐播投屏)  
**核心库**: UPnPCast (`com.github.yinnho:UPnPCast:v1.0.1`)  
**开发周期**: 2-3个月 (MVP版本)

### 🎯 核心功能
1. **设备发现与管理** - 自动发现DLNA设备，支持收藏和历史记录
2. **媒体投屏** - 支持图片、视频、音频投屏到电视/音响
3. **播放控制** - 远程控制播放、暂停、音量、进度
4. **文件管理** - 本地媒体文件浏览和选择
5. **智能推荐** - 根据使用习惯推荐设备和内容

---

## 🏗️ 技术架构

### **技术栈选择**
```
语言: Kotlin 100%
架构: MVVM + Repository Pattern
UI: Jetpack Compose + Material Design 3
依赖注入: Hilt
数据库: Room + SQLite
网络: UPnPCast库 + OkHttp
异步: Kotlin Coroutines + Flow
测试: JUnit + Mockito + Compose Test
```

### **模块架构**
```
app/                    # 主应用模块
├── ui/                # UI层 (Screens + ViewModels)
├── data/              # 数据层 (Repository + Database)
├── domain/            # 业务逻辑层
├── di/                # 依赖注入配置
└── utils/             # 工具类

feature/               # 功能模块 (可选，用于大型项目)
├── discovery/         # 设备发现
├── cast/              # 投屏功能  
├── media/             # 媒体管理
└── settings/          # 设置功能
```

---

## 📦 核心依赖配置

### **build.gradle关键依赖**
```gradle
// 核心投屏库
implementation 'com.github.yinnho:UPnPCast:v1.0.1'

// UI框架
implementation 'androidx.compose.ui:ui:1.5.4'
implementation 'androidx.compose.material3:material3:1.1.2'
implementation 'androidx.activity:activity-compose:1.8.1'

// 架构组件
implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'
implementation 'androidx.navigation:navigation-compose:2.7.5'
implementation 'com.google.dagger:hilt-android:2.48'

// 数据库
implementation 'androidx.room:room-runtime:2.6.0'
implementation 'androidx.room:room-ktx:2.6.0'

// 图片加载
implementation 'io.coil-kt:coil-compose:2.5.0'
```

### **权限配置重点**
- 网络权限 (INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE)
- 文件访问权限 (READ_MEDIA_*)
- 前台服务权限 (用于后台投屏)

---

## 💾 数据模型设计

### **核心数据实体**
1. **CastDevice** - 投屏设备 (ID, 名称, 地址, 类型, 收藏状态)
2. **MediaFile** - 媒体文件 (URI, 名称, 类型, 大小, 缩略图)
3. **CastHistory** - 投屏历史 (设备, 媒体, 时间, 时长)
4. **AppSettings** - 应用设置 (自动连接, 默认音量等)

### **数据库表设计**
- `cast_devices` - 设备信息和使用统计
- `cast_history` - 投屏历史记录
- `app_settings` - 用户设置

---

## 🔄 业务流程设计

### **设备发现流程**
1. 启动应用 → 初始化UPnPCast → 自动搜索设备
2. 显示已保存设备 + 新发现设备
3. 支持手动刷新 + 设备收藏功能

### **投屏流程**
1. 选择设备 → 选择媒体 → 开始投屏
2. 显示投屏状态 → 提供播放控制
3. 记录投屏历史 → 更新设备使用频率

### **数据流设计**
```
UI Layer (Compose) 
    ↕ StateFlow/Flow
ViewModel (MVVM)
    ↕ Repository
Repository Pattern
    ↕ Room Database / UPnPCast API
Data Sources
```

---

## 🎨 UI设计思路

### **主要页面结构**
1. **MainActivity** - 主界面入口
2. **MainScreen** - 快速投屏 + 设备状态 + 历史记录
3. **DeviceListScreen** - 设备发现和管理
4. **MediaBrowserScreen** - 媒体文件浏览 (图片网格/视频列表)
5. **CastControlScreen** - 投屏控制界面
6. **SettingsScreen** - 应用设置

### **UI组件设计**
- **DeviceCard** - 设备信息展示组件
- **MediaGrid/List** - 媒体文件展示组件  
- **PlaybackControls** - 播放控制组件
- **QuickCastCard** - 快速投屏入口组件

### **导航结构**
使用Navigation Compose，主要路由：
`main` → `device_list` / `media_browser` → `cast_control`

---

## ⚙️ 核心功能实现要点

### **1. UPnPCast库集成**
```kotlin
// 应用启动时初始化
DLNACast.init(context)

// 设备发现
DLNACast.search(timeout = 10000) { devices -> }

// 媒体投屏
DLNACast.castToDevice(device, url, title) { success -> }

// 播放控制
DLNACast.control(MediaAction.PAUSE) { success -> }
```

### **2. 权限处理策略**
- 使用Accompanist Permissions库
- 运行时动态申请媒体访问权限
- 优雅的权限被拒绝处理

### **3. 媒体文件获取**
- 使用MediaStore API获取本地图片/视频/音频
- 支持外部存储和共享文件
- 生成缩略图用于展示

### **4. 设备状态管理**
- 使用StateFlow管理设备连接状态
- 实现设备自动重连机制
- 处理网络变化和设备离线情况

---

## 🔧 技术实现重点

### **Repository Pattern实现**
- DeviceRepository - 设备发现和管理
- CastRepository - 投屏功能封装
- MediaRepository - 本地媒体文件管理
- SettingsRepository - 用户设置管理

### **ViewModel设计原则**
- 每个Screen对应一个ViewModel
- 使用StateFlow暴露UI状态
- 通过Repository访问数据
- 处理用户交互和业务逻辑

### **错误处理策略**
- 网络错误 - 自动重试 + 用户提示
- 设备不可用 - 状态更新 + 降级处理
- 权限被拒 - 引导用户设置

### **性能优化**
- 图片加载使用Coil + 内存缓存
- 设备列表使用LazyColumn
- 合理使用remember和derivedStateOf

---

## 📱 开发实施步骤

### **Phase 1: 基础架构 (1周)**
1. 创建项目 + 配置依赖
2. 搭建MVVM架构 + Hilt配置
3. 集成UPnPCast库 + 基础权限处理
4. 创建数据模型 + Room数据库

### **Phase 2: 核心功能 (3-4周)**
1. 实现设备发现和列表展示
2. 实现媒体文件浏览功能
3. 实现基础投屏功能
4. 添加播放控制界面

### **Phase 3: 用户体验 (2-3周)**
1. 添加设备收藏和历史记录
2. 实现投屏状态管理和错误处理
3. 优化UI交互和动画效果
4. 添加设置页面和个性化功能

### **Phase 4: 测试和优化 (1-2周)**
1. 单元测试和UI测试
2. 性能优化和内存泄漏检查
3. 多设备兼容性测试
4. 用户体验细节优化

---

## 🧪 测试策略

### **测试层次**
1. **单元测试** - Repository和ViewModel业务逻辑
2. **UI测试** - Compose界面交互测试
3. **集成测试** - UPnPCast库集成测试
4. **手动测试** - 真实设备投屏功能测试

### **关键测试场景**
- 设备发现在不同网络环境下的表现
- 投屏过程中的网络中断恢复
- 应用后台运行时的投屏保持
- 不同媒体格式的兼容性

---

## 📈 后续扩展规划

### **高级功能**
- 云端媒体资源投屏
- 多设备同时投屏
- 投屏质量自适应
- 局域网媒体服务器

### **商业化功能**
- 会员功能和广告集成
- 投屏数据统计和分析
- 设备品牌深度集成
- 企业版功能定制

---

## 💡 关键技术要点

1. **UPnPCast库是核心** - 所有DLNA功能都基于此库
2. **权限处理很重要** - Android 13+的媒体权限变化
3. **状态管理要清晰** - 投屏过程状态复杂，需要明确状态机
4. **用户体验要流畅** - 设备发现和投屏过程要有合适的加载和反馈
5. **错误处理要友好** - 网络问题和设备问题要有明确的用户提示

---

## 🔗 相关资源

- **UPnPCast库**: https://github.com/yinnho/UPnPCast
- **JitPack地址**: https://jitpack.io/#yinnho/UPnPCast/v1.0.1
- **Android官方文档**: https://developer.android.com/
- **Jetpack Compose**: https://developer.android.com/jetpack/compose

这份文档提供了完整的技术实现路径，你可以基于此文档在新的chat中逐步实现具体代码。 

---

# 🚀 86Cast简版开发计划

## 📱 极简MVP功能清单

### **核心功能（必须）**
1. ✅ **设备发现** - 搜索WiFi内的电视设备
2. ✅ **本地视频投屏** - 选择手机视频投到电视
3. ✅ **播放控制** - 播放/暂停/音量
4. ✅ **简单界面** - 3个主要页面就够了

### **暂时不做（避免复杂化）**
- ❌ 复杂的MVVM架构
- ❌ Room数据库
- ❌ 设备收藏功能
- ❌ 投屏历史记录
- ❌ 云端媒体源
- ❌ 多设备同时投屏

---

## 🏗️ 超简化技术方案

### **技术栈（最小化）**
```
语言: Kotlin
UI: 原生View (LinearLayout + RecyclerView)
核心库: UPnPCast v1.0.1
权限: 运行时申请
数据: SharedPreferences存储简单设置
```

### **项目结构（扁平化）**
```
app/
├── MainActivity.kt           # 主界面 + 设备列表
├── VideoListActivity.kt      # 视频选择
├── CastControlActivity.kt    # 投屏控制
├── DeviceAdapter.kt          # 设备列表适配器
├── VideoAdapter.kt           # 视频列表适配器
└── CastManager.kt            # 投屏业务逻辑封装
```

---

## 📋 开发时间线（2周完成）

### **第1周：基础功能**

**Day 1-2: 项目搭建**
- 创建Android项目
- 集成UPnPCast库
- 添加必要权限
- 简单的启动页

**Day 3-4: 设备发现**
- MainActivity显示设备列表
- 实现设备搜索和展示
- 简单的点击选择设备

**Day 5-7: 视频投屏**
- VideoListActivity显示本地视频
- 实现基础投屏功能
- 处理投屏成功/失败

### **第2周：完善体验**

**Day 8-10: 播放控制**
- CastControlActivity控制界面
- 播放/暂停/音量控制
- 投屏状态显示

**Day 11-12: 优化体验**
- 权限处理优化
- 错误提示优化
- 界面美化

**Day 13-14: 测试打包**
- 真机测试
- 修复关键bug
- 生成APK

---

## 🎨 超简单UI设计

### **参考华为投屏界面设计**
华为投屏界面的优点：
- ✅ **信息清晰**：顶部显示文件名，中间显示目标设备
- ✅ **操作直观**：换设备按钮很醒目，音量控制简单
- ✅ **视觉舒适**：黑色背景，关键信息突出
- ✅ **控制完整**：播放控制 + 进度条 + 时间显示

### **MainActivity（主页）**
```
┌─────────────────────────┐
│     86Cast投屏          │
├─────────────────────────┤
│ 🔍 搜索设备              │
├─────────────────────────┤
│ 📺 客厅电视   [连接]     │
│ 📺 卧室电视   [连接]     │
│ 📺 小米盒子   [连接]     │
├─────────────────────────┤
│      [选择视频投屏]      │
└─────────────────────────┘
```

### **VideoListActivity（视频选择）**
```
┌─────────────────────────┐
│    选择要投屏的视频      │
├─────────────────────────┤
│ 🎬 电影1.mp4            │
│ 🎬 电影2.mp4            │
│ 🎬 家庭视频.mp4         │
│ 🎬 旅游记录.mp4         │
└─────────────────────────┘
```

### **CastControlActivity（投屏控制）**
**参考华为投屏界面布局**：
```
┌─────────────────────────┐
│ ← 01.mp4                │  # 返回 + 文件名
│                         │
│         📺              │  # 电视图标
│                         │
│  视频正在"客厅小米电视"中播放  │  # 状态文字
│                         │
│    [换设备]  -  🔊  +    │  # 设备切换 + 音量
│                         │
│                         │
│  ⏮️  ⏸️  ⏭️              │  # 播放控制
│  ████████████░░░░        │  # 进度条
│  43:50        45:41     │  # 时间
└─────────────────────────┘
```

---

## 💻 核心代码框架

### **CastManager.kt（业务核心）**
```kotlin
object CastManager {
    private var selectedDevice: Device? = null
    
    fun searchDevices(callback: (List<Device>) -> Unit) {
        DLNACast.search { devices ->
            callback(devices)
        }
    }
    
    fun castVideo(videoPath: String, callback: (Boolean) -> Unit) {
        selectedDevice?.let { device ->
            DLNACast.castToDevice(device, videoPath) { success ->
                callback(success)
            }
        }
    }
    
    fun controlPlayback(action: String, callback: (Boolean) -> Unit) {
        when(action) {
            "play" -> DLNACast.control(MediaAction.PLAY, callback = callback)
            "pause" -> DLNACast.control(MediaAction.PAUSE, callback = callback)
            "volume_up" -> DLNACast.control(MediaAction.VOLUME, currentVolume + 1, callback)
            "volume_down" -> DLNACast.control(MediaAction.VOLUME, currentVolume - 1, callback)
        }
    }
}
```

### **权限处理（最简单）**
```kotlin
private fun checkPermissions() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
        != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, 
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
    }
}
```

### **CastControlActivity布局参考**
```xml
<LinearLayout 
    android:background="#000000"
    android:orientation="vertical"
    android:gravity="center"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <!-- 返回按钮 + 文件名 -->
    <LinearLayout android:orientation="horizontal">
        <ImageView android:src="@drawable/ic_back" />
        <TextView android:text="01.mp4" android:textColor="#FFFFFF" />
    </LinearLayout>
    
    <!-- 电视图标 -->
    <ImageView 
        android:src="@drawable/tv_icon"
        android:layout_marginTop="80dp" />
    
    <!-- 状态文字 -->
    <TextView 
        android:id="@+id/statusText"
        android:text="视频正在"客厅电视"中播放"
        android:textColor="#FFFFFF"
        android:layout_marginTop="20dp" />
        
    <!-- 控制按钮：换设备 + 音量 -->
    <LinearLayout 
        android:orientation="horizontal"
        android:layout_marginTop="40dp">
        <Button 
            android:text="换设备" 
            android:background="@color/orange"
            android:textColor="#FFFFFF" />
        <Button android:text="-" />
        <ImageView android:src="@drawable/ic_volume" />
        <Button android:text="+" />
    </LinearLayout>
    
    <!-- 播放控制 -->
    <LinearLayout 
        android:orientation="horizontal"
        android:layout_marginTop="60dp">
        <ImageView android:src="@drawable/ic_previous" />
        <ImageView android:src="@drawable/ic_pause" />
        <ImageView android:src="@drawable/ic_next" />
    </LinearLayout>
    
    <!-- 进度条 + 时间 -->
    <SeekBar 
        android:id="@+id/progressBar"
        android:layout_marginTop="20dp" />
    <LinearLayout android:orientation="horizontal">
        <TextView android:text="43:50" android:textColor="#FFFFFF" />
        <TextView android:text="45:41" android:textColor="#FFFFFF" />
    </LinearLayout>
    
</LinearLayout>
```

---

## 🚀 关键实现要点

### **简化原则**
1. **不用复杂架构** - 直接在Activity里处理业务逻辑
2. **不用数据库** - SharedPreferences存储简单配置
3. **不用依赖注入** - 直接使用单例对象
4. **不用复杂UI** - 标准的LinearLayout + RecyclerView
5. **专注核心功能** - 能投屏、能控制就行

### **用户体验要点**
1. **华为投屏风格** - 黑色背景，信息清晰，操作直观
2. **加载状态** - 搜索设备时显示"正在搜索..."
3. **错误处理** - 投屏失败时显示"连接失败，请重试"
4. **状态反馈** - 实时显示投屏状态和播放进度

### **技术要点**
1. **UPnPCast集成** - 项目启动时初始化，销毁时释放资源
2. **权限处理** - 运行时申请存储和网络权限
3. **文件扫描** - 使用MediaStore API获取本地视频文件
4. **网络状态** - 监听WiFi变化，断网时提示用户

---

## 📦 最终交付物

### **第一版本目标**
- ✅ **可运行的APK**（支持Android 7.0+）
- ✅ **支持主流电视投屏**（小米、华为、三星等）
- ✅ **界面简洁易用**（参考华为投屏设计）
- ✅ **代码结构清晰**（便于后续维护升级）

### **成功标准**
- 能够发现局域网内的DLNA设备
- 能够将本地视频投屏到电视
- 能够远程控制播放和音量
- 界面操作流畅，体验良好

**时间目标**: 2周内完成MVP版本
**代码行数**: 预计1000行以内（保持简洁）
**测试覆盖**: 真机测试 + 主流电视品牌兼容性测试

---

这份简版开发计划专注于**快速实现核心功能**，避免过度设计，确保在2周内能够交付一个可用的投屏应用原型。 