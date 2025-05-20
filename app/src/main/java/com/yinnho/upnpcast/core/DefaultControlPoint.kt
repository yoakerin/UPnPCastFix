package com.yinnho.upnpcast.core

import android.util.Log
import com.yinnho.upnpcast.interfaces.ControlPoint
import com.yinnho.upnpcast.interfaces.UpnpServiceConfiguration
import com.yinnho.upnpcast.model.RemoteDevice

class DefaultControlPoint(
    private val configuration: UpnpServiceConfiguration
) : ControlPoint {
    private var searching = false
    private var currentDevice: RemoteDevice? = null
    private var connected = false
    private val devices = mutableListOf<RemoteDevice>()
    private val TAG = "DefaultControlPoint"

    override fun search() {
        configuration.datagramProcessor.sendSearchRequest(
            configuration.multicastAddress,
            configuration.multicastPort
        )
        searching = true
        Log.d(TAG, "开始搜索DLNA设备")
    }

    override fun stopSearch() {
        if (!searching) return
        searching = false
        Log.d(TAG, "停止搜索DLNA设备")
    }

    override fun setSearchTimeout(timeoutMs: Long) {
        Log.d(TAG, "设置搜索超时时间: ${timeoutMs}ms")
        // 将超时时间传递给配置
        configuration.setSearchTimeout(timeoutMs)
    }

    override fun getDevices(): List<RemoteDevice> = devices.toList()

    override fun connect(device: RemoteDevice) {
        currentDevice = device
        connected = true
        Log.d(TAG, "已连接到设备: ${device.details.friendlyName}")
    }

    override fun disconnect() {
        currentDevice = null
        connected = false
        Log.d(TAG, "已断开设备连接")
    }

    override fun isSearching(): Boolean = searching

    override fun isConnected(): Boolean = connected

    override fun getCurrentDevice(): RemoteDevice? = currentDevice

    fun addDevice(device: RemoteDevice) {
        if (!devices.contains(device)) {
            devices.add(device)
            Log.d(TAG, "发现新设备: ${device.details.friendlyName}")
        }
    }

    fun removeDevice(device: RemoteDevice) {
        if (devices.remove(device)) {
            Log.d(TAG, "设备已移除: ${device.details.friendlyName}")
            if (currentDevice == device) {
                disconnect()
            }
        }
    }

    override fun shutdown() {
        stopSearch()
        disconnect()
        devices.clear()
        Log.d(TAG, "控制点已关闭")
    }
} 