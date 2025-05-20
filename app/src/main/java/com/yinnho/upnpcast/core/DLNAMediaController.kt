package com.yinnho.upnpcast.core

import android.util.Log
import com.yinnho.upnpcast.core.lifecycle.BaseLifecycleComponent
import com.yinnho.upnpcast.device.Device
import com.yinnho.upnpcast.model.TransportState
import com.yinnho.upnpcast.model.DLNAUrlImpl
import com.yinnho.upnpcast.model.TransportInfoImpl
import com.yinnho.upnpcast.service.AVTransportService
import com.yinnho.upnpcast.service.RenderingControlService
import com.yinnho.upnpcast.service.AVTransportServiceImpl
import com.yinnho.upnpcast.service.RenderingControlServiceImpl
import java.lang.ref.WeakReference

/**
 * DLNA媒体控制器
 * 负责控制DLNA设备的媒体播放，使用生命周期管理防止内存泄漏
 */
class DLNAMediaController : BaseLifecycleComponent() {
    override val componentName: String = "DLNAMediaController"
    
    private var currentDevice: Device? = null
    private var avTransportService: AVTransportService? = null
    private var renderingControlService: RenderingControlService? = null
    private var currentState: TransportState = TransportState.STOPPED
    
    // 使用WeakReference持有回调，防止内存泄漏
    private var deviceCallbackRef: WeakReference<DeviceCallback>? = null

    companion object {
        private const val TAG = "DLNAMediaController"
        private const val DEFAULT_INSTANCE_ID = "0"
        private const val MASTER_CHANNEL = "Master"
        private const val DEFAULT_SPEED = "1"
    }

    /**
     * 设备回调接口
     */
    interface DeviceCallback {
        fun onDeviceConnected(device: Device)
        fun onDeviceDisconnected(device: Device)
        fun onPlaybackStateChanged(state: TransportState)
        fun onError(error: String)
    }

    /**
     * 设置设备回调
     * 使用WeakReference防止内存泄漏
     */
    fun setDeviceCallback(callback: DeviceCallback) {
        this.deviceCallbackRef = WeakReference(callback)
        Log.d(TAG, "设置了设备回调")
    }

    /**
     * 设置要控制的设备
     */
    fun setDevice(device: Device) {
        try {
            currentDevice = device
            initializeServices(device)
            notifyDeviceConnected(device)
            Log.d(TAG, "已设置设备: ${device.details.friendlyName}")
        } catch (e: Exception) {
            val error = "设备连接失败: ${e.message}"
            Log.e(TAG, error, e)
            notifyError(error)
            throw IllegalStateException(error)
        }
    }

    /**
     * 断开当前设备连接
     */
    fun disconnectDevice() {
        currentDevice?.let { _ ->
            try {
                stopMedia()
                
                // 释放服务资源
                releaseServices()
                
                // 置空设备和服务引用
                val oldDevice = currentDevice
                currentDevice = null
                
                // 通知设备断开连接
                oldDevice?.let { notifyDeviceDisconnected(it) }
                
                Log.d(TAG, "已断开设备连接")
            } catch (e: Exception) {
                val error = "设备断开连接失败: ${e.message}"
                Log.e(TAG, error, e)
                notifyError(error)
            }
        }
    }

    /**
     * 初始化设备服务
     */
    private fun initializeServices(device: Device) {
        avTransportService = findAVTransportService(device)
            ?: throw IllegalStateException("AVTransport服务不可用")
        
        renderingControlService = findRenderingControlService(device)
            ?: throw IllegalStateException("RenderingControl服务不可用")

        currentState = TransportState.STOPPED
        Log.d(TAG, "DLNA服务初始化成功")
    }
    
    /**
     * 释放服务资源
     */
    private fun releaseServices() {
        // 释放AVTransport服务
        val service = avTransportService
        if (service is AVTransportServiceImpl) {
            service.release()
            Log.d(TAG, "已释放AVTransport服务")
        }
        
        // 清空服务引用
        avTransportService = null
        renderingControlService = null
    }
    
    /**
     * 查找AVTransport服务
     */
    private fun findAVTransportService(device: Device): AVTransportService? {
        // 直接宽松匹配查找服务
        val foundService = device.services.firstOrNull { 
            it.serviceType.contains("AVTransport", ignoreCase = true)
        } ?: return null
        
        Log.d(TAG, "找到AVTransport服务: ${foundService.serviceType}")
        Log.d(TAG, "- 控制URL: ${foundService.controlURL}")
        Log.d(TAG, "- 事件订阅URL: ${foundService.eventSubURL}")
        Log.d(TAG, "- 描述URL: ${foundService.descriptorURL}")
        
        try {
            val service = AVTransportServiceImpl(
                controlURL = DLNAUrlImpl.fromJavaUrl(foundService.controlURL),
                eventSubURL = DLNAUrlImpl.fromJavaUrl(foundService.eventSubURL),
                SCPDURL = DLNAUrlImpl.fromJavaUrl(foundService.descriptorURL),
                descriptorURL = DLNAUrlImpl.fromJavaUrl(device.identity.descriptorURL)
            )
            return service
        } catch (e: Exception) {
            Log.e(TAG, "创建AVTransportService失败: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 查找RenderingControl服务
     */
    private fun findRenderingControlService(device: Device): RenderingControlService? {
        // 直接宽松匹配查找服务
        val foundService = device.services.firstOrNull { 
            it.serviceType.contains("RenderingControl", ignoreCase = true)
        } ?: return null
        
        Log.d(TAG, "找到RenderingControl服务: ${foundService.serviceType}")
        Log.d(TAG, "- 控制URL: ${foundService.controlURL}")
        Log.d(TAG, "- 事件订阅URL: ${foundService.eventSubURL}")
        Log.d(TAG, "- 描述URL: ${foundService.descriptorURL}")
        
        try {
            val service = RenderingControlServiceImpl(
                controlURL = DLNAUrlImpl.fromJavaUrl(foundService.controlURL),
                eventSubURL = DLNAUrlImpl.fromJavaUrl(foundService.eventSubURL),
                SCPDURL = DLNAUrlImpl.fromJavaUrl(foundService.descriptorURL)
            )
            return service
        } catch (e: Exception) {
            Log.e(TAG, "创建RenderingControlService失败: ${e.message}", e)
            return null
        }
    }

    /**
     * 播放媒体
     */
    fun playMedia(uri: String, metadata: String? = null) {
        executeTransportAction("播放媒体") {
            // 检查当前设备是否为小米设备
            val isXiaomiDevice = isXiaomiDevice(currentDevice)
            
            // 根据设备类型决定使用什么元数据
            val finalMetadata = if (isXiaomiDevice) {
                // 对小米设备使用极简元数据
                Log.d(TAG, "检测到小米设备，使用极简元数据格式")
                "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"><item id=\"0\"><dc:title>视频</dc:title><res>$uri</res></item></DIDL-Lite>"
            } else {
                // 对其他设备使用提供的元数据
                metadata ?: ""
            }
            
            // 执行Stop、SetAVTransportURI、Play操作
            Log.d(TAG, "执行播放流程: 1. 先停止当前播放")
            avTransportService?.stop(DEFAULT_INSTANCE_ID)
            
            // 短暂延迟，确保Stop命令被处理
            Thread.sleep(100)
            
            Log.d(TAG, "执行播放流程: 2. 设置媒体URI")
            Log.d(TAG, "使用元数据长度: ${finalMetadata.length}字符")
            avTransportService?.setAVTransportURI(DEFAULT_INSTANCE_ID, uri, finalMetadata)
            
            // 短暂延迟，确保SetAVTransportURI命令被处理
            Thread.sleep(100)
            
            Log.d(TAG, "执行播放流程: 3. 开始播放")
            avTransportService?.play(DEFAULT_INSTANCE_ID, DEFAULT_SPEED)
            updatePlaybackState(TransportState.PLAYING)
        }
    }
    
    /**
     * 判断设备是否为小米设备
     */
    private fun isXiaomiDevice(device: Device?): Boolean {
        return device?.details?.let { details ->
            details.manufacturerInfo?.name?.contains("xiaomi", ignoreCase = true) == true ||
            details.manufacturerInfo?.name?.contains("mi", ignoreCase = true) == true ||
            details.friendlyName?.contains("小米", ignoreCase = true) == true ||
            details.friendlyName?.contains("xiaomi", ignoreCase = true) == true
        } ?: false
    }

    /**
     * 暂停播放
     */
    fun pauseMedia() {
        executeTransportAction("暂停播放") {
            avTransportService?.pause(DEFAULT_INSTANCE_ID)
            updatePlaybackState(TransportState.PAUSED_PLAYBACK)
        }
    }

    /**
     * 停止播放
     */
    fun stopMedia() {
        executeTransportAction("停止播放") {
            avTransportService?.stop(DEFAULT_INSTANCE_ID)
            updatePlaybackState(TransportState.STOPPED)
        }
    }

    /**
     * 跳转到指定位置
     */
    fun seekTo(position: String) {
        executeTransportAction("跳转进度") {
            avTransportService?.seek(DEFAULT_INSTANCE_ID, position)
        }
    }

    /**
     * 设置音量
     */
    fun setVolume(volume: UInt) {
        executeRenderingAction("设置音量") {
            renderingControlService?.setVolume(DEFAULT_INSTANCE_ID, MASTER_CHANNEL, volume)
        }
    }

    /**
     * 设置静音
     */
    fun setMute(mute: Boolean) {
        executeRenderingAction("设置静音") {
            renderingControlService?.setMute(DEFAULT_INSTANCE_ID, MASTER_CHANNEL, mute)
        }
    }

    /**
     * 获取播放信息
     */
    fun getPlaybackInfo(): PlaybackInfo {
        return try {
            val positionInfo = avTransportService?.getPositionInfo(DEFAULT_INSTANCE_ID)
            val transportInfo = TransportInfoImpl(
                currentTransportState = currentState.toString(),
                currentTransportStatus = "OK",
                currentSpeed = "1"
            )
            val volume =
                renderingControlService?.getVolume(DEFAULT_INSTANCE_ID, MASTER_CHANNEL) ?: 0u
            val mute =
                renderingControlService?.getMute(DEFAULT_INSTANCE_ID, MASTER_CHANNEL) ?: false

            PlaybackInfo(
                currentTime = positionInfo?.relTime ?: "00:00:00",
                duration = positionInfo?.trackDuration ?: "00:00:00",
                state = transportInfo.currentTransportState,
                volume = volume,
                mute = mute
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取播放信息失败", e)
            PlaybackInfo(state = currentState.toString())
        }
    }

    /**
     * 更新播放状态
     */
    private fun updatePlaybackState(newState: TransportState) {
        currentState = newState
        notifyPlaybackStateChanged(newState)
    }

    /**
     * 执行传输操作
     */
    private inline fun executeTransportAction(actionName: String, action: () -> Unit) {
        try {
            if (avTransportService == null) {
                throw IllegalStateException("AVTransport服务不可用")
            }
            action()
            Log.d(TAG, "$actionName 成功")
        } catch (e: Exception) {
            val error = "$actionName 失败: ${e.message}"
            Log.e(TAG, error, e)
            notifyError(error)
        }
    }

    /**
     * 执行渲染控制操作
     */
    private inline fun executeRenderingAction(actionName: String, action: () -> Unit) {
        try {
            if (renderingControlService == null) {
                throw IllegalStateException("RenderingControl服务不可用")
            }
            action()
            Log.d(TAG, "$actionName 成功")
        } catch (e: Exception) {
            val error = "$actionName 失败: ${e.message}"
            Log.e(TAG, error, e)
            notifyError(error)
        }
    }

    /**
     * 通知设备已连接
     */
    private fun notifyDeviceConnected(device: Device) {
        deviceCallbackRef?.get()?.onDeviceConnected(device)
    }
    
    /**
     * 通知设备已断开连接
     */
    private fun notifyDeviceDisconnected(device: Device) {
        deviceCallbackRef?.get()?.onDeviceDisconnected(device)
    }
    
    /**
     * 通知播放状态已改变
     */
    private fun notifyPlaybackStateChanged(state: TransportState) {
        deviceCallbackRef?.get()?.onPlaybackStateChanged(state)
    }
    
    /**
     * 通知错误
     */
    private fun notifyError(error: String) {
        deviceCallbackRef?.get()?.onError(error)
        }
    
    /**
     * 生命周期管理 - 销毁时调用
     * 释放所有资源并断开连接
     */
    override fun onDestroy() {
        Log.d(TAG, "开始执行DLNAMediaController销毁")
        
        try {
            // 断开当前设备连接
            disconnectDevice()
            
            // 清空回调引用
            deviceCallbackRef = null
        } catch (e: Exception) {
            Log.e(TAG, "DLNAMediaController销毁失败: ${e.message}", e)
        }
        
        // 调用父类方法完成资源释放
        super.onDestroy()
    }

    /**
     * 播放信息数据类
     */
    data class PlaybackInfo(
        val currentTime: String = "00:00:00",
        val duration: String = "00:00:00",
        val state: String = TransportState.STOPPED.toString(),
        val volume: UInt = 0u,
        val mute: Boolean = false
    )
} 