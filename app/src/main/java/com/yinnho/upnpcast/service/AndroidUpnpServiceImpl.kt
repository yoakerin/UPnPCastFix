package com.yinnho.upnpcast.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.yinnho.upnpcast.R
import com.yinnho.upnpcast.interfaces.AndroidUpnpService
import com.yinnho.upnpcast.interfaces.ControlPoint
import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.interfaces.Router
import com.yinnho.upnpcast.interfaces.UpnpServiceConfiguration
import com.yinnho.upnpcast.manager.RegistryImpl
import com.yinnho.upnpcast.network.RouterImpl
import com.yinnho.upnpcast.core.DefaultUpnpServiceConfiguration
import java.net.MulticastSocket
import java.util.concurrent.ScheduledExecutorService

/**
 * Android UPnP服务实现
 * 提供UPnP功能的服务
 */
class AndroidUpnpServiceImpl : Service(), AndroidUpnpService {
    private val TAG = "AndroidUpnpServiceImpl"
    private val binder = LocalBinder()
    private val registryImpl = RegistryImpl.getInstance()

    private var multicastSocket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var isRunning = false
    private var receiveThread: Thread? = null

    private var searchScheduler: ScheduledExecutorService? = null
    
    // 广播接收器
    @Deprecated("广播机制已废弃，请使用直接方法调用", level = DeprecationLevel.WARNING)
    private val searchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.yinnho.upnpcast.ACTION_STOP_SEARCH" -> {
                    Log.d(TAG, "收到停止搜索广播")
                    // 停止搜索逻辑
                }
            }
        }
    }

    override val registry: Registry
        get() = registryImpl

    override val router: Router by lazy {
        RouterImpl.getInstance(configuration)
    }

    // 简化控制点实现，直接使用RouterImpl作为ControlPoint的委托
    override val controlPoint: ControlPoint
        get() = router as ControlPoint

    override val configuration: UpnpServiceConfiguration by lazy {
        DefaultUpnpServiceConfiguration.getInstance(this)
    }

    inner class LocalBinder : Binder() {
        val service: AndroidUpnpServiceImpl
            get() = this@AndroidUpnpServiceImpl
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务已创建")

        // 创建前台服务通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        startForegroundService()

        // 启动路由器
        router.startup()
        
        // 广播注册已移除 - 2025/05/12优化
        Log.d(TAG, "UPnP服务已初始化")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        // 仅在Android 8.0-12版本创建通知渠道
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val channelId = "dlna_service_channel"
            val channelName = "DLNA Service"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "DLNA服务通知"
                setShowBadge(false)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        // 在Android 13及以上版本使用替代方案
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 仅启动服务，不显示通知
            Log.d(TAG, "Android 13+: 启动服务但不显示通知")
            return
        }

        val channelId = "dlna_service_channel"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("DLNA服务")
            .setContentText("正在运行DLNA服务")
            .setSmallIcon(R.drawable.ic_cast)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        // 广播注销已移除 - 2025/05/12优化
        
        // 关闭所有组件
        router.shutdown()
        configuration.shutdown()
        registry.shutdown()

        // 停止前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        super.onDestroy()
        Log.d(TAG, "服务已销毁")
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "服务已绑定")
        return binder
    }
} 