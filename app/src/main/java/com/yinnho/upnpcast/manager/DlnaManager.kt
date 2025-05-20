package com.yinnho.upnpcast.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.yinnho.upnpcast.wrapper.DlnaControllerWrapper
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.core.EnhancedThreadManager
import java.util.concurrent.ConcurrentHashMap

/**
 * DLNA管理器
 * 处理DLNA命令广播接收和分发
 */
class DlnaManager(private val context: Context) {

    private val TAG = "DlnaManager"
    
    private val deviceMap = ConcurrentHashMap<String, RemoteDevice>()
    private var isReceiverRegistered = false

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 在线程池中处理广播，避免在主线程中执行耗时操作
            EnhancedThreadManager.executeTask {
                handleIntent(intent)
            }
        }
    }
    
    init {
        // 注册广播接收器
        registerBroadcastReceiver()
        
        // 初始化DlnaControllerWrapper
        try {
            DlnaControllerWrapper.setAppContext(context)
            
            // 设置设备监听器
            DlnaControllerWrapper.setDeviceListener(object : DlnaControllerWrapper.DeviceListener {
                override fun onDeviceFound(device: RemoteDevice) {
                    deviceMap[device.identity.udn.toString()] = device
                    EnhancedThreadManager.d(TAG, "发现设备: ${device.details.friendlyName}")
                }
                
                override fun onDeviceLost(device: RemoteDevice) {
                    deviceMap.remove(device.identity.udn.toString())
                    EnhancedThreadManager.d(TAG, "设备丢失: ${device.details.friendlyName}")
                }
            })
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "初始化DlnaManager失败", e)
        }
    }

    /**
     * 注册广播接收器
     */
    private fun registerBroadcastReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction("com.doubanapi.xiaodouplayer.START_SEARCH")
                    addAction("com.doubanapi.xiaodouplayer.STOP_SEARCH")
                    addAction("com.doubanapi.xiaodouplayer.GET_DEVICE_STATUS")
                    addAction("com.doubanapi.xiaodouplayer.PLAY_MEDIA")
                    addAction("com.doubanapi.xiaodouplayer.PAUSE_MEDIA")
                    addAction("com.doubanapi.xiaodouplayer.STOP_MEDIA")
                    addAction("com.doubanapi.xiaodouplayer.SET_VOLUME")
                }
                
                // 使用ContextCompat，并指定RECEIVER_NOT_EXPORTED标志
                ContextCompat.registerReceiver(
                    context,
                    commandReceiver, 
                    filter, 
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                
                isReceiverRegistered = true
                EnhancedThreadManager.d(TAG, "广播接收器注册成功")
            } catch (e: Exception) {
                EnhancedThreadManager.e(TAG, "注册广播接收器失败", e)
            }
        }
    }
    
    /**
     * 处理接收到的Intent命令
     */
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            "com.doubanapi.xiaodouplayer.START_SEARCH" -> {
                EnhancedThreadManager.d(TAG, "收到开始搜索命令")
                startSearch()
            }
            
            "com.doubanapi.xiaodouplayer.STOP_SEARCH" -> {
                EnhancedThreadManager.d(TAG, "收到停止搜索命令")
                stopSearch()
            }
            
            "com.doubanapi.xiaodouplayer.GET_DEVICE_STATUS" -> {
                EnhancedThreadManager.d(TAG, "收到获取设备状态命令")
                getDeviceStatus()
            }
            
            "com.doubanapi.xiaodouplayer.PLAY_MEDIA" -> {
                EnhancedThreadManager.d(TAG, "收到播放媒体命令")
                val mediaUrl = intent.getStringExtra("media_url")
                val deviceId = intent.getStringExtra("device_id")
                playMedia(deviceId, mediaUrl)
            }
            
            "com.doubanapi.xiaodouplayer.PAUSE_MEDIA" -> {
                EnhancedThreadManager.d(TAG, "收到暂停媒体命令")
                val deviceId = intent.getStringExtra("device_id")
                pauseMedia(deviceId)
            }
            
            "com.doubanapi.xiaodouplayer.STOP_MEDIA" -> {
                EnhancedThreadManager.d(TAG, "收到停止媒体命令")
                val deviceId = intent.getStringExtra("device_id")
                stopMedia(deviceId)
            }
            
            "com.doubanapi.xiaodouplayer.SET_VOLUME" -> {
                EnhancedThreadManager.d(TAG, "收到设置音量命令")
                val deviceId = intent.getStringExtra("device_id")
                val volume = intent.getIntExtra("volume", 50)
                setVolume(deviceId, volume)
            }
        }
    }
    
    /**
     * 开始搜索
     */
    private fun startSearch() {
        EnhancedThreadManager.d(TAG, "执行开始搜索")
        // 委托给DlnaControllerWrapper处理
        try {
            DlnaControllerWrapper.searchDevices()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "执行开始搜索失败", e)
        }
    }
    
    /**
     * 停止搜索
     */
    private fun stopSearch() {
        EnhancedThreadManager.d(TAG, "执行停止搜索")
        try {
            DlnaControllerWrapper.stopSearch()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "执行停止搜索失败", e)
        }
    }
    
    /**
     * 获取设备状态
     */
    private fun getDeviceStatus() {
        EnhancedThreadManager.d(TAG, "执行获取设备状态")
        // 发送广播通知设备状态
        val statusIntent = Intent("com.doubanapi.xiaodouplayer.DEVICE_STATUS_RESULT")
        statusIntent.putExtra("devices_count", deviceMap.size)
        context.sendBroadcast(statusIntent)
    }
    
    /**
     * 播放媒体
     */
    private fun playMedia(deviceId: String?, mediaUrl: String?) {
        if (deviceId == null || mediaUrl == null) {
            EnhancedThreadManager.e(TAG, "播放媒体参数不完整: 设备ID=$deviceId, 媒体URL=$mediaUrl")
            return
        }
        
        EnhancedThreadManager.d(TAG, "执行播放媒体: 设备=$deviceId, URL=$mediaUrl")
        try {
            DlnaControllerWrapper.playMedia(
                usn = deviceId,
                mediaUrl = mediaUrl,
                _metadata = null,
                videoTitle = "视频",
                episodeLabel = "",
                positionMs = 0
            )
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "执行播放媒体失败", e)
        }
    }
    
    /**
     * 暂停媒体
     */
    private fun pauseMedia(deviceId: String?) {
        if (deviceId == null) {
            EnhancedThreadManager.e(TAG, "暂停媒体参数不完整: 设备ID为空")
            return
        }
        
        EnhancedThreadManager.d(TAG, "执行暂停媒体: 设备=$deviceId")
        // 通过DlnaController暂停媒体播放
    }
    
    /**
     * 停止媒体
     */
    private fun stopMedia(deviceId: String?) {
        if (deviceId == null) {
            EnhancedThreadManager.e(TAG, "停止媒体参数不完整: 设备ID为空")
            return
        }
        
        EnhancedThreadManager.d(TAG, "执行停止媒体: 设备=$deviceId")
        try {
            DlnaControllerWrapper.stopCasting(deviceId)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "执行停止媒体失败", e)
        }
    }
    
    /**
     * 设置音量
     */
    private fun setVolume(deviceId: String?, volume: Int) {
        if (deviceId == null) {
            EnhancedThreadManager.e(TAG, "设置音量参数不完整: 设备ID为空")
            return
        }
        
        EnhancedThreadManager.d(TAG, "执行设置音量: 设备=$deviceId, 音量=$volume")
        // 通过DlnaController设置音量
    }
    
    /**
     * 释放资源
     */
    fun release() {
        EnhancedThreadManager.d(TAG, "释放DlnaManager资源")
        try {
            // 注销广播接收器
            try {
                if (isReceiverRegistered) {
                    context.unregisterReceiver(commandReceiver)
                    isReceiverRegistered = false
                    EnhancedThreadManager.d(TAG, "广播接收器已注销")
                }
            } catch (e: Exception) {
                EnhancedThreadManager.w(TAG, "注销广播接收器失败")
            }
            
            // 清空设备映射
            deviceMap.clear()
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "释放资源失败", e)
        }
    }
} 