package com.yinnho.upnpcast.api

import com.yinnho.upnpcast.cache.UPnPCacheManager

/**
 * 远程DLNA设备类
 * 简化版本，代表DLNA设备
 */
data class RemoteDevice(
    val id: String,                    // 设备唯一标识符
    val displayName: String,           // 显示名称
    val address: String,               // 设备地址
    val manufacturer: String = "",     // 厂商名称
    val model: String = "",            // 型号
    val details: Any? = null           // 原始详细信息，仅内部使用
) {
    /**
     * 获取显示字符串
     */
    val displayString: String
        get() = if (manufacturer.isNotEmpty() && model.isNotEmpty()) {
            "$displayName ($manufacturer $model)"
        } else {
            displayName
        }

    /**
     * 转换为内部RemoteDevice（仅内部使用）
     */
    internal fun toInternalDevice(): com.yinnho.upnpcast.model.RemoteDevice? {
        return details as? com.yinnho.upnpcast.model.RemoteDevice
    }

    companion object {
        // 使用UPnPCacheManager作为缓存
        private val cacheManager = UPnPCacheManager.getInstance()
        
        /**
         * 清理设备缓存
         */
        internal fun clearCache() {
            // 使用UPnPCacheManager清除缓存
            cacheManager.clearCache(UPnPCacheManager.CacheType.DEVICE)
        }
        
        /**
         * 从缓存中移除设备
         */
        internal fun removeFromCache(deviceId: String) {
            // 尝试获取原有设备，以便参数完整
            val cachedDevice = getFromCache(deviceId)
            if (cachedDevice != null) {
                // 创建"已移除"标记的设备，仅保留ID
                val removedDevice = com.yinnho.upnpcast.model.RemoteDevice(
                    com.yinnho.upnpcast.model.DeviceType("", "", 0),
                    com.yinnho.upnpcast.model.DeviceIdentity(
                        "uuid:$deviceId-removed",
                        java.net.URL("http://127.0.0.1/removed")
                    ),
                    com.yinnho.upnpcast.model.DeviceDetails(
                        cachedDevice.displayName, 
                        null, null
                    ),
                    emptyList()
                )
                
                // 通过更新缓存实现"移除"操作
                cacheManager.cacheDevice(deviceId, removedDevice)
            }
        }
        
        /**
         * 从缓存获取设备
         */
        private fun getFromCache(deviceId: String): RemoteDevice? {
            // 从UPnPCacheManager的deviceCache中获取
            val internalDevice = cacheManager.getDevice(deviceId) ?: return null
            
            // 检查是否是已移除的设备
            if (internalDevice.identity.udn.contains("-removed")) {
                return null
            }
            
            return RemoteDevice(
                id = internalDevice.identity.udn,
                displayName = internalDevice.details.friendlyName ?: internalDevice.identity.udn,
                address = internalDevice.identity.descriptorURL.host ?: "",
                manufacturer = internalDevice.details.manufacturerInfo?.name ?: "",
                model = internalDevice.details.modelInfo?.name ?: "",
                details = internalDevice
            )
        }
        
        /**
         * 创建或获取缓存的RemoteDevice实例
         */
        internal fun getOrCreate(
            id: String,
            displayName: String,
            address: String,
            manufacturer: String = "",
            model: String = "",
            details: Any? = null
        ): RemoteDevice {
            // 尝试从缓存获取
            getFromCache(id)?.let { return it }
            
            // 缓存未命中，创建新设备
            val newDevice = RemoteDevice(
                id = id,
                displayName = displayName,
                address = address,
                manufacturer = manufacturer,
                model = model,
                details = details
            )
            
            // 如果有内部设备对象，缓存到UPnPCacheManager
            (details as? com.yinnho.upnpcast.model.RemoteDevice)?.let { internalDevice ->
                cacheManager.cacheDevice(id, internalDevice)
            }
            
            return newDevice
        }
    }
} 