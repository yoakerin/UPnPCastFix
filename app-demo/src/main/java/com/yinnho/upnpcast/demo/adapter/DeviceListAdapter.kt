package com.yinnho.upnpcast.demo.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.yinnho.upnpcast.Device

/**
 * 设备列表适配器
 */
class DeviceListAdapter(private val onDeviceClick: (Device) -> Unit) :
    RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    companion object {
        private const val TAG = "DeviceListAdapter"
    }

    private var devices: List<Device> = emptyList()

    /**
     * 更新设备列表
     */
    fun updateDevices(newDevices: List<Device>) {
        val oldDevices = devices
        devices = newDevices.toList()
        
        // 使用DiffUtil优化列表更新
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldDevices.size
            override fun getNewListSize() = newDevices.size
            
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldDevices[oldItemPosition].id == newDevices[newItemPosition].id
            }
            
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldDevices[oldItemPosition] == newDevices[newItemPosition]
            }
        })
        
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val itemView = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.WHITE)
        }
        return DeviceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView
        private val addressText: TextView
        private val typeIcon: TextView

        init {
            val layout = itemView as LinearLayout
            
            typeIcon = TextView(itemView.context).apply {
                textSize = 24f
                gravity = Gravity.CENTER
            }
            layout.addView(typeIcon)
            
            nameText = TextView(itemView.context).apply {
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#333333"))
            }
            layout.addView(nameText)
            
            addressText = TextView(itemView.context).apply {
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
            }
            layout.addView(addressText)
            
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onDeviceClick(devices[adapterPosition])
                }
            }
        }

        fun bind(device: Device) {
            nameText.text = device.name
            addressText.text = device.address
            typeIcon.text = getDeviceTypeIcon(device)
            setDeviceTypeStyle(device)
        }

        private fun getDeviceTypeIcon(device: Device): String {
            return if (device.isTV) {
                "📺"
            } else {
                "📱"
            }
        }

        private fun setDeviceTypeStyle(device: Device) {
            val backgroundColor = if (device.isTV) {
                Color.parseColor("#E8F5E8")  // 浅绿色背景
            } else {
                Color.parseColor("#F0F8FF")  // 浅蓝色背景
            }
            itemView.setBackgroundColor(backgroundColor)
        }
    }
}
