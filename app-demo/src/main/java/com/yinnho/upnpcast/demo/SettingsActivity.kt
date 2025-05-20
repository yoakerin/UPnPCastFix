package com.yinnho.upnpcast.demo

import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yinnho.upnpcast.DLNACastManager
import android.widget.SeekBar
import android.widget.TextView
import android.util.Log

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SettingsActivity"
        
        // 默认设置值
        private const val DEFAULT_SEARCH_TIMEOUT = 30 // 搜索超时秒数
        private const val DEFAULT_RETRY_COUNT = 3 // 重试次数
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // 获取DLNA管理器实例
        val dlnaCastManager = DLNACastManager.getInstance(applicationContext)
        
        // 自动连接开关
        val autoConnectSwitch = findViewById<Switch>(R.id.switch_auto_connect)
        autoConnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            // 保存设置
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putBoolean("auto_connect", isChecked)
                .apply()
            
            // 不再调用DLNACastManager的setAutoConnect方法
            
            Toast.makeText(
                this, 
                "自动连接已${if (isChecked) "启用" else "禁用"}", 
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // 启用高级日志开关
        val enableLoggingSwitch = findViewById<Switch>(R.id.switch_enable_logging)
        enableLoggingSwitch.setOnCheckedChangeListener { _, isChecked ->
            // 保存设置
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putBoolean("enable_logging", isChecked)
                .apply()
            
            // 设置日志级别
            dlnaCastManager.setDebugMode(isChecked)
            
            Toast.makeText(
                this, 
                "详细日志已${if (isChecked) "启用" else "禁用"}", 
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // 搜索超时设置
        val timeoutSeekBar = findViewById<SeekBar>(R.id.seekbar_search_timeout)
        val timeoutValueText = findViewById<TextView>(R.id.text_timeout_value)
        
        // 设置进度条范围和初始值
        timeoutSeekBar.max = 60 // 最大60秒
        val savedTimeout = getSharedPreferences("settings", MODE_PRIVATE)
            .getInt("search_timeout", DEFAULT_SEARCH_TIMEOUT)
        timeoutSeekBar.progress = savedTimeout
        timeoutValueText.text = "$savedTimeout 秒"
        
        timeoutSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // 确保最小值为5秒
                val timeout = if (progress < 5) 5 else progress
                timeoutValueText.text = "$timeout 秒"
                
                // 保存设置
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putInt("search_timeout", timeout)
                    .apply()
                
                // 设置超时
                dlnaCastManager.setSearchTimeout(timeout * 1000L) // 转为毫秒
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        
        // 恢复开关状态
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        autoConnectSwitch.isChecked = preferences.getBoolean("auto_connect", false)
        enableLoggingSwitch.isChecked = preferences.getBoolean("enable_logging", false)
        
        // 返回按钮
        findViewById<Button>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        // 清除缓存按钮
        findViewById<Button>(R.id.btn_clear_cache).setOnClickListener {
            clearDeviceCache()
        }
    }
    
    /**
     * 清除设备缓存
     */
    private fun clearDeviceCache() {
        try {
            // 获取DLNA管理器
            val dlnaCastManager = DLNACastManager.getInstance(applicationContext)
            
            // 清除设备缓存
            dlnaCastManager.clearDeviceCache()
            
            // 显示成功消息
            Toast.makeText(this, "设备缓存已清除", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "设备缓存已成功清除")
        } catch (e: Exception) {
            Log.e(TAG, "清除设备缓存失败", e)
            Toast.makeText(this, "清除缓存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
