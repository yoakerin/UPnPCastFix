package com.yinnho.upnpcast.demo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yinnho.upnpcast.RemoteDevice
import com.yinnho.upnpcast.demo.R
import android.util.Log

/**
 * 设备列表适配器
 * 展示发现的DLNA设备
 */
class DeviceListAdapter(private val onDeviceClick: (RemoteDevice) -> Unit) : 
    RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {
    
    companion object {
        private const val TAG = "DeviceListAdapter"
    }
    
    private var devices: List<RemoteDevice> = emptyList()
    
    /**
     * 更新设备列表 - 增量更新策略
     * @param newDevices 新的设备列表
     */
    fun updateDevices(newDevices: List<RemoteDevice>) {
        // 记录所有接收到的设备
        Log.d(TAG, "收到设备列表更新: ${newDevices.size}个设备")
        newDevices.forEachIndexed { index, device ->
            Log.d(TAG, "收到设备[$index]: ${device.displayName}, ID: ${device.id}, 地址: ${device.address}")
        }
        
        // 如果列表为空但newDevices不为空，直接更新
        if (devices.isEmpty() && newDevices.isNotEmpty()) {
            Log.d(TAG, "初始设备列表，直接添加${newDevices.size}个设备")
            devices = newDevices.distinctBy { "${it.id}@${it.address}" }
            notifyDataSetChanged()
            Log.d(TAG, "设备列表已初始化，当前有${devices.size}个设备")
            return
        }
        
        // 如果新列表为空，保留现有设备（避免闪烁）
        if (newDevices.isEmpty()) {
            Log.d(TAG, "收到空设备列表，保留现有${devices.size}个设备")
            return
        }
        
        // 使用设备ID去重 - 合并现有设备和新设备
        val existingDeviceIds = devices.map { it.id }.toSet()
        val newUniqueDevices = newDevices.filter { device ->
            !existingDeviceIds.contains(device.id)
        }
        
        // 如果有新设备，添加到列表
        if (newUniqueDevices.isNotEmpty()) {
            Log.d(TAG, "发现${newUniqueDevices.size}个新设备")
            newUniqueDevices.forEach { device ->
                Log.d(TAG, "新设备: ${device.displayName}, 制造商: ${device.manufacturer}")
            }
            
            val combinedDevices = (devices + newUniqueDevices).distinctBy { "${it.id}@${it.address}" }
            devices = combinedDevices
            notifyDataSetChanged()
            Log.d(TAG, "设备列表已更新，当前有${devices.size}个设备")
        } else {
            Log.d(TAG, "没有新设备，保持现有${devices.size}个设备")
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
        holder.itemView.setOnClickListener { onDeviceClick(device) }
    }
    
    override fun getItemCount() = devices.size
    
    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(R.id.device_name)
        private val deviceManufacturer: TextView = itemView.findViewById(R.id.device_manufacturer)
        private val deviceIpPort: TextView = itemView.findViewById(R.id.device_ip_port)
        private val deviceUdn: TextView = itemView.findViewById(R.id.device_udn)
        
        /**
         * 绑定设备数据到视图
         * @param device 要展示的设备
         */
        fun bind(device: RemoteDevice) {
            try {
                // 设备名称
                deviceName.text = device.displayName
                
                // 制造商名称
                val manufacturer = device.manufacturer.ifEmpty { "未知厂商" }
                deviceManufacturer.text = manufacturer
                
                // IP地址和端口
                val ipAddress = device.address.ifEmpty { "未知IP" }
                deviceIpPort.text = "IP地址: $ipAddress"
                
                // 设备UDN - 唯一标识符
                val udn = device.id.take(20) + (if (device.id.length > 20) "..." else "")
                deviceUdn.text = "设备ID: $udn"
                
                // 记录设备详情用于调试
                Log.d(TAG, "设备详情 - 名称: ${device.displayName}, 制造商: $manufacturer, IP: $ipAddress")
            } catch (e: Exception) {
                // 处理可能的异常
                Log.e(TAG, "绑定设备数据时出错", e)
                
                // 设置默认值
                deviceName.text = "设备 (无法获取详情)"
                deviceManufacturer.text = "未知"
                deviceIpPort.text = "IP地址: 未知"
                deviceUdn.text = "设备ID: 未知"
            }
        }
    }
}
