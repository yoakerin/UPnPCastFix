package com.yinnho.upnpcast.device

import com.yinnho.upnpcast.model.RemoteDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.util.concurrent.CopyOnWriteArrayList

/**
 * DLNA设备适配器
 * 
 * 用于在列表中显示DLNA设备
 */
class DeviceListAdapter(private val context: Context) : BaseAdapter() {
    // 线程安全的设备列表
    private val devices = CopyOnWriteArrayList<RemoteDevice>()
    
    // 当前选中的设备索引
    private var selectedIndex = -1
    
    // 设备图标资源ID
    private var deviceIconRes = 0
    
    // 选中设备图标资源ID
    private var selectedDeviceIconRes = 0
    
    /**
     * 设置设备图标
     */
    fun setDeviceIcon(iconRes: Int) {
        this.deviceIconRes = iconRes
    }
    
    /**
     * 设置选中设备图标
     */
    fun setSelectedDeviceIcon(iconRes: Int) {
        this.selectedDeviceIconRes = iconRes
    }
    
    /**
     * 更新设备列表
     */
    fun updateDevices(newDevices: List<RemoteDevice>) {
        // 清空当前列表
        devices.clear()
        
        // 添加新设备
        devices.addAll(newDevices)
        
        // 如果选中索引超出范围，重置为-1
        if (selectedIndex >= devices.size) {
            selectedIndex = -1
        }
        
        // 通知数据变化
        notifyDataSetChanged()
    }
    
    /**
     * 添加设备
     */
    fun addDevice(device: RemoteDevice) {
        // 检查设备是否已存在
        if (!devices.contains(device)) {
            devices.add(device)
            notifyDataSetChanged()
        }
    }
    
    /**
     * 移除设备
     */
    fun removeDevice(device: RemoteDevice) {
        if (devices.remove(device)) {
            // 如果移除的是当前选中的设备，重置选中索引
            if (devices.indexOf(device) == selectedIndex) {
                selectedIndex = -1
            }
            notifyDataSetChanged()
        }
    }
    
    /**
     * 清空设备列表
     */
    fun clearDevices() {
        devices.clear()
        selectedIndex = -1
        notifyDataSetChanged()
    }
    
    /**
     * 选择设备
     */
    fun selectDevice(position: Int) {
        if (position >= 0 && position < devices.size) {
            selectedIndex = position
            notifyDataSetChanged()
        }
    }
    
    /**
     * 获取当前选中的设备
     */
    fun getSelectedDevice(): RemoteDevice? {
        return if (selectedIndex >= 0 && selectedIndex < devices.size) {
            devices[selectedIndex]
        } else {
            null
        }
    }
    
    /**
     * 获取设备列表数量
     */
    override fun getCount(): Int = devices.size
    
    /**
     * 获取指定位置的设备
     */
    override fun getItem(position: Int): RemoteDevice? {
        return if (position >= 0 && position < devices.size) {
            devices[position]
        } else {
            null
        }
    }
    
    /**
     * 获取指定位置的设备ID
     */
    override fun getItemId(position: Int): Long = position.toLong()
    
    /**
     * 创建视图
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // 在实际应用中，这里通常会inflate一个布局文件
        // 简化实现，创建一个基本的布局
        val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false)
        
        // 获取当前位置的设备
        val device = getItem(position)
        
        // 设置设备名称和地址
        val text1 = view.findViewById<TextView>(android.R.id.text1)
        val text2 = view.findViewById<TextView>(android.R.id.text2)
        
        text1.text = device?.displayName ?: "Unknown Device"
        text2.text = device?.displayString ?: ""
        
        // 如果是当前选中的设备，设置特殊样式
        if (position == selectedIndex) {
            view.setBackgroundResource(android.R.color.holo_blue_light)
        } else {
            view.setBackgroundResource(android.R.color.transparent)
        }
        
        return view
    }
} 