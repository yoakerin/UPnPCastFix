package com.yinnho.upnpcast.controller

import android.content.Context
import android.content.Intent
import com.yinnho.upnpcast.cache.UPnPCacheManager
import com.yinnho.upnpcast.core.EnhancedThreadManager
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.network.SoapHelper
import com.yinnho.upnpcast.utils.DeviceUtils
import com.yinnho.upnpcast.utils.UrlUtils
import java.lang.ref.WeakReference

/**
 * DLNA控制器工厂类
 * 精简版：合并重复方法，移除冗余代码
 */
object DlnaControllerFactory {
    private const val TAG = "DlnaControllerFactory"
    
    // 上下文引用，用于发送广播
    private var contextRef: WeakReference<Context> = WeakReference(null)
    
    // 使用UPnPCacheManager替代本地缓存
    private val cacheManager = UPnPCacheManager.getInstance()
    
    /**
     * 设置应用上下文
     */
    fun setAppContext(context: Context) {
        contextRef = WeakReference(context.applicationContext)
        EnhancedThreadManager.d(TAG, "设置应用上下文完成")
    }
    
    /**
     * 获取存储的应用上下文
     */
    internal val appContext: Context?
        get() = contextRef.get()
    
    /**
     * 根据设备获取控制器
     */
    fun getController(device: RemoteDevice): DlnaController {
        // 使用设备USN作为缓存键
        val deviceId = device.identity.udn
        
        // 先从缓存获取
        cacheManager.getController(deviceId, DlnaController::class.java)?.let { 
            EnhancedThreadManager.d(TAG, "从缓存获取控制器: $deviceId")
            return it 
        }
        
        // 创建新控制器
        EnhancedThreadManager.d(TAG, "创建新控制器: ${device.details.friendlyName}")
        val controller = DlnaController(device)
        
        // 使用UPnPCacheManager存入缓存
        cacheManager.cacheController(deviceId, controller)
        
        return controller
    }
    
    /**
     * 移除控制器缓存
     */
    fun removeController(deviceId: String) {
        // 尝试获取控制器
        val controller = cacheManager.getController(deviceId, DlnaController::class.java)
        if (controller != null) {
            // 尝试停止和释放控制器
            try {
                // stop是suspend函数，不能直接调用，改用stopSync()
                controller.stopSync()
                controller.release()
            } catch (e: Exception) {
                // 忽略异常
            }
            
            // 从缓存移除（用null值替换）
            cacheManager.cacheController(deviceId, object {})
            EnhancedThreadManager.d(TAG, "移除控制器: $deviceId")
        }
    }
    
    /**
     * 清除所有控制器缓存
     */
    fun clearAll() {
        EnhancedThreadManager.d(TAG, "清除所有控制器缓存")
        cacheManager.clearCache(UPnPCacheManager.CacheType.CONTROLLER)
    }
    
    /**
     * 根据USN获取设备
     */
    fun getDeviceByUSN(usn: String): RemoteDevice? {
        try {
            // 从缓存中查找控制器
            val cachedController = cacheManager.getController(usn, DlnaController::class.java)
            if (cachedController != null) {
                return cachedController.device
            }
            
            // 如果没找到控制器，尝试从设备缓存查找
            return cacheManager.getDevice(usn)
        } catch (e: Exception) {
            EnhancedThreadManager.e(TAG, "获取设备失败: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 构建媒体元数据
     * 整合了多个元数据生成方法
     */
    fun buildMetadata(
        mediaUrl: String,
        title: String,
        episodeLabel: String = "",
        device: RemoteDevice? = null
    ): String {
        // 确定设备类型
        val isXiaomiDevice = device?.let { DeviceUtils.isXiaomiDevice(it) } ?: false
        val isSamsungDevice = device?.let { DeviceUtils.isSamsungDevice(it) } ?: false
        
        // 构建标题
        val fullTitle = if (episodeLabel.isNotEmpty()) "$title - $episodeLabel" else title
        
        // 获取MIME类型
        val mimeType = UrlUtils.getMimeTypeFromUrl(mediaUrl)
        
        // 内部转义标题和URL
        val escapedTitle = SoapHelper.safeEscapeXml(fullTitle)
        val escapedUrl = SoapHelper.safeEscapeXml(mediaUrl)
        
        // 根据设备类型提供不同格式的元数据
        return when {
            isXiaomiDevice -> {
                // 小米设备偏好更简单的元数据
                """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                    <item id="0" parentID="-1" restricted="1">
                        <dc:title>$escapedTitle</dc:title>
                        <upnp:class>object.item.videoItem</upnp:class>
                        <res protocolInfo="http-get:*:$mimeType:*">$escapedUrl</res>
                    </item>
                </DIDL-Lite>
                """.trimIndent()
            }
            isSamsungDevice -> {
                // 三星设备元数据，可能需要额外字段
                """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">
                    <item id="0" parentID="-1" restricted="1">
                        <dc:title>$escapedTitle</dc:title>
                        <upnp:class>object.item.videoItem</upnp:class>
                        <dc:creator>Video Player</dc:creator>
                        <res protocolInfo="http-get:*:$mimeType:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000">$escapedUrl</res>
                    </item>
                </DIDL-Lite>
                """.trimIndent()
            }
            else -> {
                // 通用设备元数据
                """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">
                    <item id="0" parentID="-1" restricted="1">
                        <dc:title>$escapedTitle</dc:title>
                        <upnp:class>object.item.videoItem</upnp:class>
                        <res protocolInfo="http-get:*:$mimeType:*">$escapedUrl</res>
                    </item>
                </DIDL-Lite>
                """.trimIndent()
            }
        }
    }
    
    /**
     * 为特定设备构建媒体元数据
     */
    fun buildMetadataForDevice(
        mediaUrl: String,
        title: String,
        episodeLabel: String = "",
        device: RemoteDevice
    ): String {
        return buildMetadata(mediaUrl, title, episodeLabel, device)
    }
    
    /**
     * 通知DLNA操作状态
     */
    fun notifyDLNAActionStatus(usn: String, status: String) {
        val context = appContext ?: return
        
        val intent = Intent("com.yinnho.upnpcast.STATUS_CHANGED").apply {
            putExtra("usn", usn)
            putExtra("status", status)
        }
        
        context.sendBroadcast(intent)
    }
    
    /**
     * 发送错误广播
     */
    fun sendErrorBroadcast(errorMessage: String) {
        val context = appContext ?: return
        
        val intent = Intent("com.yinnho.upnpcast.ACTION_ERROR").apply {
            putExtra("error", errorMessage)
        }
        
        context.sendBroadcast(intent)
    }
}
