package com.yinnho.upnpcast.device

import android.util.Log
import com.yinnho.upnpcast.model.RemoteDevice
import com.yinnho.upnpcast.DLNAException
import com.yinnho.upnpcast.DLNAErrorType

/**
 * 设备适配器接口
 * 用于处理不同厂商设备的兼容性差异
 */
interface DeviceAdapter {
    /**
     * 检查是否兼容此设备
     * @param device 远程设备
     * @return 是否兼容
     */
    fun isCompatible(device: RemoteDevice): Boolean
    
    /**
     * 获取设备类型
     * @return 设备类型描述
     */
    fun getDeviceType(): String
    
    /**
     * 格式化媒体URL
     * 针对特定设备格式化URL以提高兼容性
     * @param url 原始媒体URL
     * @return 格式化后的URL
     */
    fun formatMediaUrl(url: String): String
    
    /**
     * 格式化媒体元数据
     * 针对特定设备格式化元数据以提高兼容性
     * @param metadata 原始元数据
     * @return 格式化后的元数据
     */
    fun formatMetadata(metadata: String): String
    
    /**
     * 获取设备优先级
     * 优先级越高，在有多个适配器兼容时优先使用
     * @return 优先级，默认为0
     */
    fun getPriority(): Int = 0
}

/**
 * 设备适配器基类
 */
abstract class BaseDeviceAdapter : DeviceAdapter {
    protected val TAG = "DeviceAdapter"
    
    /**
     * 检测设备是否为特定品牌
     * @param device 远程设备
     * @param brandKeywords 品牌关键词列表，任一关键词匹配即视为匹配
     * @return 是否匹配
     */
    protected fun isBrandMatch(device: RemoteDevice, vararg brandKeywords: String): Boolean {
        val deviceName = device.details.friendlyName ?: ""
        val manufacturer = device.details.manufacturerInfo?.name ?: ""
        val modelName = device.details.modelInfo?.name ?: ""
        
        val combinedText = "$deviceName $manufacturer $modelName".lowercase()
        
        return brandKeywords.any { combinedText.contains(it.lowercase()) }
    }
    
    /**
     * 安全执行设备操作，提供统一的异常处理
     * @param operation 操作描述
     * @param defaultValue 发生异常时的默认返回值
     * @param block 要执行的代码块
     * @return 执行结果或默认值
     */
    protected fun <T> safeExecute(operation: String, defaultValue: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "设备操作失败: $operation", e)
            val errorMessage = "设备操作失败($operation): ${e.message}"
            
            // 由于抛出异常，以下代码不会执行，但是为了保留defaultValue的使用
            // 我们先记录默认值信息，然后再抛出异常
            Log.w(TAG, "使用默认值: $defaultValue")
            throw DLNAException(DLNAErrorType.COMPATIBILITY_ERROR, errorMessage, e)
        }
    }
    
    override fun formatMediaUrl(url: String): String = url
    
    override fun formatMetadata(metadata: String): String = metadata
}

/**
 * 设备适配器工厂
 * 管理和获取适合特定设备的适配器
 */
object DeviceAdapterFactory {
    private val TAG = "DeviceAdapterFactory"
    private val adapters = mutableListOf<DeviceAdapter>()
    
    init {
        // 注册默认适配器
        registerAdapter(GenericDeviceAdapter())
    }
    
    /**
     * 注册设备适配器
     * @param adapter 设备适配器
     */
    fun registerAdapter(adapter: DeviceAdapter) {
        adapters.add(adapter)
        Log.d(TAG, "注册设备适配器: ${adapter.getDeviceType()}, 优先级: ${adapter.getPriority()}")
    }
    
    /**
     * 获取适配器
     * @param device 远程设备
     * @return 适合的设备适配器
     */
    fun getAdapter(device: RemoteDevice): DeviceAdapter {
        val compatibleAdapters = adapters
            .filter { it.isCompatible(device) }
            .sortedByDescending { it.getPriority() }
        
        if (compatibleAdapters.isEmpty()) {
            Log.w(TAG, "没有找到兼容的设备适配器，使用通用适配器")
            return GenericDeviceAdapter()
        }
        
        val adapter = compatibleAdapters.first()
        Log.d(TAG, "为设备 ${device.details.friendlyName} 选择适配器: ${adapter.getDeviceType()}")
        return adapter
    }
}

/**
 * 通用设备适配器
 * 作为默认适配器，适用于大多数标准DLNA设备
 */
class GenericDeviceAdapter : BaseDeviceAdapter() {
    override fun isCompatible(device: RemoteDevice): Boolean = true
    
    override fun getDeviceType(): String = "通用DLNA设备"
    
    override fun getPriority(): Int = 0
}

/**
 * 小米设备适配器
 */
class XiaomiDeviceAdapter : BaseDeviceAdapter() {
    override fun isCompatible(device: RemoteDevice): Boolean {
        return isBrandMatch(device, "xiaomi", "小米", "mi")
    }
    
    override fun getDeviceType(): String = "小米设备"
    
    override fun getPriority(): Int = 10
    
    override fun formatMediaUrl(url: String): String {
        Log.d(TAG, "小米设备格式化URL: 原始URL: $url")
        
        // 处理逻辑：
        // 1. 处理m3u8文件特殊处理
        // 2. 长URL签名处理 - 去除多余的签名参数
        // 3. 腾讯云COS等特殊链接处理
        
        val formattedUrl = when {
            // m3u8格式且有参数
            url.contains("m3u8") && url.contains("?") -> {
                // 移除所有参数
                url.split("?")[0].also {
                    Log.d(TAG, "小米设备: 移除m3u8 URL参数: $it")
                }
            }
            
            // 处理带有大量参数的复杂URL(如带签名的URL)
            url.contains("?sign=") || url.contains("&sign=") -> {
                // 尝试简化URL
                val result = try {
                    // 对于腾讯云COS链接特殊处理
                    if (url.contains("myqcloud.com") || url.contains("cos.ap-")) {
                        // 不再完全移除签名参数，保留主要URL并添加简化的签名参数
                        val mainPart = url.split("?").firstOrNull() ?: url
                        val simplifiedUrl = "$mainPart?sign=simple&t=${System.currentTimeMillis()/1000}"
                        Log.d(TAG, "小米设备: 简化腾讯云COS URL: $simplifiedUrl")
                        simplifiedUrl
                    } else {
                        // 一般链接只保留必要参数
                        val urlParts = url.split("?")
                        val baseUrl = urlParts[0]
                        
                        // 仅保留重要参数(不包含长签名)
                        if (urlParts.size > 1) {
                            val params = urlParts[1].split("&")
                                .filter { !it.startsWith("sign=") && it.length < 50 }
                                .joinToString("&")
                            
                            if (params.isNotEmpty()) {
                                "$baseUrl?$params".also {
                                    Log.d(TAG, "小米设备: 简化URL参数: $it")
                                }
                            } else {
                                baseUrl.also {
                                    Log.d(TAG, "小米设备: 移除所有URL参数: $it")
                                }
                            }
                        } else {
                            url
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "小米设备: URL处理异常，使用原URL", e)
                    url
                }
                result
            }
            
            else -> url
        }
        
        Log.d(TAG, "小米设备格式化URL: 结果: $formattedUrl")
        return formattedUrl
    }
    
    override fun formatMetadata(metadata: String): String {
        // 小米设备可能需要简化的元数据
        // 移除多余的属性，只保留基本信息
        return try {
            if (metadata.length > 500) {
                // 如果元数据太长，尝试简化
                val simplifiedMetadata = metadata
                    .replace(Regex("<upnp:.*?>.*?</upnp:.*?>"), "")
                    .replace(Regex("<dlna:.*?>.*?</dlna:.*?>"), "")
                
                Log.d(TAG, "小米设备: 简化元数据，原长度: ${metadata.length}, 新长度: ${simplifiedMetadata.length}")
                simplifiedMetadata
            } else {
                metadata
            }
        } catch (e: Exception) {
            Log.e(TAG, "小米设备: 元数据处理异常，使用原元数据", e)
            metadata
        }
    }
}

/**
 * 三星设备适配器
 */
class SamsungDeviceAdapter : BaseDeviceAdapter() {
    override fun isCompatible(device: RemoteDevice): Boolean {
        return isBrandMatch(device, "samsung", "三星")
    }
    
    override fun getDeviceType(): String = "三星设备"
    
    override fun getPriority(): Int = 10
    
    override fun formatMediaUrl(url: String): String {
        // 三星设备特殊URL处理
        return url
    }
    
    override fun formatMetadata(metadata: String): String {
        // 三星设备可能需要特定格式的元数据
        return metadata
    }
}

/**
 * LG设备适配器
 */
class LGDeviceAdapter : BaseDeviceAdapter() {
    override fun isCompatible(device: RemoteDevice): Boolean {
        return isBrandMatch(device, "lg")
    }
    
    override fun getDeviceType(): String = "LG设备"
    
    override fun getPriority(): Int = 10
    
    override fun formatMediaUrl(url: String): String {
        // LG设备特殊URL处理
        return url
    }
    
    override fun formatMetadata(metadata: String): String {
        // LG设备可能需要特定格式的元数据
        return metadata
    }
} 