# UPnPCast API参考文档

## 核心类

### DLNACastManager
- 描述：应用与库交互的主要入口点
- 方法：
  - `getInstance(context)`: 获取单例实例
  - `startDiscovery()`: 开始搜索设备
  - `stopDiscovery()`: 停止搜索设备
  - `connectToDevice(device)`: 连接到设备
  - `disconnect()`: 断开连接
  - `playMedia(mediaUrl, title, metadata, positionMs)`: 播放媒体
  - `pauseMedia()`: 暂停播放
  - `resumeMedia()`: 恢复播放
  - `seekTo(positionMs)`: 跳转到指定位置
  - `setVolume(volume)`: 设置音量
  - `release()`: 释放资源

### RemoteDevice
- 描述：表示网络中的DLNA设备
- 属性：
  - `id`: 设备唯一标识
  - `name`: 设备名称
  - `address`: 设备地址
  - `manufacturer`: 设备制造商
  - `model`: 设备型号

### CastListener
- 描述：投屏事件监听接口
- 方法：
  - `onDeviceListUpdated(deviceList)`: 设备列表更新回调
  - `onConnected(device)`: 设备连接成功回调
  - `onDisconnected()`: 设备断开连接回调
  - `onError(error)`: 错误处理回调
  - `onPlaybackStateChanged(state)`: 播放状态变更回调

### EnhancedNetworkManager
- 描述：网络管理和连接维护
- 主要功能：
  - 网络状态监测
  - 连接恢复处理
  - 网络请求管理

### DeviceRankingService
- 描述：设备智能排序
- 主要功能：
  - 基于多维度对设备评分和排序
