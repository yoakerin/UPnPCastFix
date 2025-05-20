package com.yinnho.upnpcast.utils

import android.util.Log
import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.model.RemoteDevice

/**
 * 注册表工具类
 * 提供辅助方法简化注册表操作和调试
 */
object RegistryUtils {
    private const val TAG = "RegistryUtils"

    /**
     * 输出注册表状态到日志
     *
     * @param registry 注册表实例
     * @param tag 日志标签，默认为RegistryUtils
     * @param detailLevel 详细程度 (0-最少, 1-基本, 2-详细)
     */
    fun logRegistryState(registry: Registry, tag: String = TAG, detailLevel: Int = 1) {
        val state = registry.dump(detailLevel)
        
        // 将多行输出拆分为单独的日志行
        state.split("\n").forEach { line ->
            if (line.isNotEmpty()) {
                Log.d(tag, line)
            }
        }
    }
    
    /**
     * 查找设备工具方法 - 根据设备名称模糊匹配
     *
     * @param registry 注册表实例
     * @param namePattern 设备名称匹配模式(忽略大小写)
     * @return 匹配的设备列表
     */
    fun findDevicesByName(registry: Registry, namePattern: String): List<RemoteDevice> {
        val devices = registry.getDevices()
        val pattern = namePattern.lowercase()
        
        return devices.filter { device -> 
            device.details.friendlyName?.lowercase()?.contains(pattern) == true
        }
    }
    
    /**
     * 查找设备工具方法 - 根据制造商匹配
     *
     * @param registry 注册表实例
     * @param manufacturer 制造商名称(忽略大小写)
     * @return 匹配的设备列表
     */
    fun findDevicesByManufacturer(registry: Registry, manufacturer: String): List<RemoteDevice> {
        val devices = registry.getDevices()
        val pattern = manufacturer.lowercase()
        
        return devices.filter { device -> 
            device.details.manufacturerInfo?.name?.lowercase()?.contains(pattern) == true
        }
    }
    
    /**
     * 统计注册表中的设备类型分布
     *
     * @param registry 注册表实例
     * @return 设备类型及其数量的映射
     */
    fun getDeviceTypeDistribution(registry: Registry): Map<String, Int> {
        val devices = registry.getDevices()
        val distribution = mutableMapOf<String, Int>()
        
        devices.forEach { device ->
            val type = device.type.toString()
            distribution[type] = (distribution[type] ?: 0) + 1
        }
        
        return distribution
    }
} 