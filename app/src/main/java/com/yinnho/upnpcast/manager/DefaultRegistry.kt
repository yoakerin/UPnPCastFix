package com.yinnho.upnpcast.manager

import android.util.Log
import com.yinnho.upnpcast.interfaces.Registry
import com.yinnho.upnpcast.interfaces.RegistryListener
import com.yinnho.upnpcast.model.RemoteDevice

/**
 * 默认注册表实现
 */
class DefaultRegistry : Registry {
    private val TAG = "DefaultRegistry"
    private val listeners = mutableListOf<RegistryListener>()
    private val devices = mutableListOf<RemoteDevice>()

    override fun addDevice(device: RemoteDevice) {
        if (!devices.contains(device)) {
            devices.add(device)
            notifyDeviceAdded(device)
        }
    }

    override fun removeDevice(device: RemoteDevice) {
        if (devices.remove(device)) {
            notifyDeviceRemoved(device)
        }
    }

    override fun removeAllRemoteDevices() {
        val devicesToRemove = devices.toList()
        devices.clear()
        devicesToRemove.forEach { notifyDeviceRemoved(it) }
    }

    override fun getDevices(): List<RemoteDevice> {
        return devices.toList()
    }

    override fun getDevice(udn: String): RemoteDevice? {
        return devices.find { it.identity.udn == udn }
    }

    override fun addListener(listener: RegistryListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    override fun removeListener(listener: RegistryListener) {
        listeners.remove(listener)
    }

    override fun hasListener(listener: RegistryListener): Boolean {
        return listeners.contains(listener)
    }

    override fun shutdown() {
        notifyBeforeShutdown()
        removeAllRemoteDevices()
        listeners.clear()
        notifyAfterShutdown()
    }

    /**
     * 实现dump方法，输出注册表状态
     */
    override fun dump(detailLevel: Int): String {
        val sb = StringBuilder()
        
        sb.appendLine("=== 默认注册表状态 ===")
        sb.appendLine("设备数量: ${devices.size}")
        sb.appendLine("监听器数量: ${listeners.size}")
        
        if (detailLevel >= 1) {
            // 基本信息
            sb.appendLine("\n-- 设备列表 --")
            devices.forEachIndexed { index, device ->
                sb.appendLine("${index + 1}. ${device.details.friendlyName} (${device.identity.udn})")
            }
            
            sb.appendLine("\n-- 监听器列表 --")
            listeners.forEachIndexed { index, listener ->
                sb.appendLine("${index + 1}. ${listener.javaClass.simpleName}")
            }
        }
        
        if (detailLevel >= 2) {
            // 详细信息
            sb.appendLine("\n-- 详细设备信息 --")
            devices.forEachIndexed { index, device ->
                sb.appendLine("${index + 1}. ${device.details.friendlyName}")
                sb.appendLine("   UDN: ${device.identity.udn}")
                sb.appendLine("   类型: ${device.type}")
                sb.appendLine("   制造商: ${device.details.manufacturerInfo?.name}")
                sb.appendLine("   型号: ${device.details.modelInfo?.name}")
                sb.appendLine("   服务数量: ${device.services.size}")
                
                // 输出服务信息
                if (device.services.isNotEmpty()) {
                    sb.appendLine("   -- 服务列表 --")
                    device.services.forEachIndexed { serviceIndex, service ->
                        sb.appendLine("     ${serviceIndex + 1}. ${service.serviceId}")
                        sb.appendLine("        类型: ${service.serviceType}")
                    }
                }
                
                sb.appendLine()
            }
        }
        
        return sb.toString()
    }

    private fun notifyBeforeShutdown() {
        Log.d(TAG, "准备关闭注册表")
        for (listener in listeners) {
            try {
                listener.beforeShutdown(this)
            } catch (e: Exception) {
                Log.e(TAG, "通知关闭前失败", e)
            }
        }
    }

    private fun notifyAfterShutdown() {
        Log.d(TAG, "注册表已关闭")
        for (listener in listeners.toList()) {
            try {
                listener.afterShutdown()
            } catch (e: Exception) {
                Log.e(TAG, "通知关闭后失败", e)
            }
        }
    }

    private fun notifyDeviceAdded(device: RemoteDevice) {
        Log.d(TAG, "设备已添加: ${device.details.friendlyName}")
        for (listener in listeners) {
            try {
                listener.deviceAdded(device)
            } catch (e: Exception) {
                Log.e(TAG, "通知设备添加失败", e)
            }
        }
    }

    private fun notifyDeviceRemoved(device: RemoteDevice) {
        Log.d(TAG, "设备已移除: ${device.details.friendlyName}")
        for (listener in listeners) {
            try {
                listener.deviceRemoved(device)
            } catch (e: Exception) {
                Log.e(TAG, "通知设备移除失败", e)
            }
        }
    }
}