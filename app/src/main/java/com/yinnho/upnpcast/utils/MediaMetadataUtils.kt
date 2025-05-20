package com.yinnho.upnpcast.utils

import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.network.SoapHelper
import com.yinnho.upnpcast.utils.DeviceUtils
import com.yinnho.upnpcast.utils.UrlUtils
import com.yinnho.upnpcast.core.EnhancedThreadManager

/**
 * 媒体元数据工具类
 * 提供媒体元数据相关的工具方法
 */
object MediaMetadataUtils {
    private const val TAG = "MediaMetadataUtils"
    
    /**
     * 预处理媒体URL，处理不同设备的URL格式差异
     * @param url 原始URL
     * @param isXiaomiDevice 是否为小米设备
     * @return 处理后的URL
     */
    fun preprocessMediaUrl(
        url: String,
        isXiaomiDevice: Boolean = false
    ): String {
        // 使用UrlUtils处理URL
        return UrlUtils.preprocessMediaUrl(url, isXiaomiDevice)
    }
    
    /**
     * 预处理控制URL，处理不同设备的URL格式差异
     */
    fun preprocessControlURL(controlURL: String, friendlyName: String = ""): String {
        // 使用UrlUtils处理URL
        return UrlUtils.preprocessControlURL(controlURL, friendlyName)
    }
    
    /**
     * 构建媒体元数据
     * @param mediaUrl 媒体URL
     * @param title 标题
     * @param episodeLabel 集数标签
     * @param isXiaomiDevice 是否为小米设备
     * @return DIDL-Lite格式的元数据
     */
    fun buildMetadata(
        mediaUrl: String,
        title: String,
        episodeLabel: String = "",
        isXiaomiDevice: Boolean = false
    ): String {
        EnhancedThreadManager.d(TAG, "构建元数据: $title, $episodeLabel")
        
        val displayTitle = if (episodeLabel.isNotEmpty()) {
            "$title $episodeLabel"
        } else {
            title
        }
        
        // 根据设备类型选择元数据模板
        return if (isXiaomiDevice) {
            buildXiaomiMetadata(mediaUrl, displayTitle)
        } else {
            buildStandardMetadata(mediaUrl, displayTitle)
        }
    }
    
    /**
     * 为特定设备构建媒体元数据
     * @param mediaUrl 媒体URL
     * @param title 标题
     * @param episodeLabel 集数标签
     * @param device 远程设备
     * @return DIDL-Lite格式的元数据
     */
    fun buildMetadataForDevice(
        mediaUrl: String,
        title: String,
        episodeLabel: String = "",
        device: RemoteDevice
    ): String {
        val isXiaomiDevice = DeviceUtils.isXiaomiDevice(device)
        val isSamsungDevice = DeviceUtils.isSamsungDevice(device)
        return buildMetadataInternal(mediaUrl, title, episodeLabel, isXiaomiDevice, isSamsungDevice)
    }
    
    /**
     * 内部构建媒体元数据的方法
     */
    private fun buildMetadataInternal(
        mediaUrl: String,
        title: String,
        episodeLabel: String = "",
        isXiaomiDevice: Boolean = false,
        isSamsungDevice: Boolean = false
    ): String {
        // 构建标题：如果有集数，则格式为"视频标题 - 集数"；否则只使用视频标题
        val fullTitle = when {
            episodeLabel.isNotEmpty() -> "$title - $episodeLabel"
            else -> title
        }

        // 使用UrlUtils获取MIME类型
        val mimeType = UrlUtils.getMimeTypeFromUrl(mediaUrl)

        // 内部转义标题和URL
        val escapedTitle = SoapHelper.safeEscapeXml(fullTitle)
        val escapedUrl = SoapHelper.safeEscapeXml(mediaUrl)

        // 根据设备类型提供不同格式的元数据
        if (isXiaomiDevice) {
            // 小米设备偏好更简单的元数据
            return """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                    <item id="0" parentID="-1" restricted="1">
                        <dc:title>$escapedTitle</dc:title>
                        <upnp:class>object.item.videoItem</upnp:class>
                        <res protocolInfo="http-get:*:$mimeType:*">$escapedUrl</res>
                    </item>
                </DIDL-Lite>
            """.trimIndent()
        } else if (isSamsungDevice) {
            // 三星设备元数据，可能需要额外字段
            return """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">
                    <item id="0" parentID="-1" restricted="1">
                        <dc:title>$escapedTitle</dc:title>
                        <upnp:class>object.item.videoItem</upnp:class>
                        <dc:creator>Video Player</dc:creator>
                        <res protocolInfo="http-get:*:$mimeType:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000">$escapedUrl</res>
                    </item>
                </DIDL-Lite>
            """.trimIndent()
        } else {
            // 通用设备元数据
            return """
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

    /**
     * 构建标准DLNA元数据
     */
    private fun buildStandardMetadata(url: String, title: String): String {
        return """
        <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" 
            xmlns:dc="http://purl.org/dc/elements/1.1/" 
            xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" 
            xmlns:sec="http://www.sec.co.kr/">
            <item id="0" parentID="-1" restricted="1">
                <dc:title>$title</dc:title>
                <upnp:class>object.item.videoItem</upnp:class>
                <res protocolInfo="http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000">$url</res>
            </item>
        </DIDL-Lite>
        """.trimIndent()
    }

    /**
     * 构建小米设备专用元数据
     */
    private fun buildXiaomiMetadata(url: String, title: String): String {
        return """
        <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" 
            xmlns:dc="http://purl.org/dc/elements/1.1/" 
            xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
            <item id="1" parentID="0" restricted="0">
                <dc:title>$title</dc:title>
                <upnp:class>object.item.videoItem</upnp:class>
                <res protocolInfo="http-get:*:video/mp4:*">$url</res>
            </item>
        </DIDL-Lite>
        """.trimIndent()
    }
} 