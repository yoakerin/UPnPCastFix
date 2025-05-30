package com.yinnho.upnpcast.demo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// 使用真实的DLNACastManager
import com.yinnho.upnpcast.DLNACastManager
import com.yinnho.upnpcast.RemoteDevice
import com.yinnho.upnpcast.CastListener
import com.yinnho.upnpcast.DLNAException
import com.yinnho.upnpcast.demo.adapter.DeviceListAdapter
import android.view.View
import android.widget.Button
import android.net.wifi.WifiManager
import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentTransaction

class MainActivity : AppCompatActivity() {
    private lateinit var dlnaCastManager: DLNACastManager
    private lateinit var deviceListAdapter: DeviceListAdapter
    private lateinit var recyclerView: RecyclerView
    private var multicastLock: WifiManager.MulticastLock? = null
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化视图
        recyclerView = findViewById(R.id.device_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // 初始化适配器
        deviceListAdapter = DeviceListAdapter { device ->
            connectToDevice(device)
        }
        recyclerView.adapter = deviceListAdapter
        
        // 初始化DLNA管理器
        dlnaCastManager = DLNACastManager.getInstance(applicationContext)
        Log.d(TAG, "真实DLNA管理器已初始化")
        
        // 添加监听器设置日志
        Log.d(TAG, "准备设置CastListener")
        dlnaCastManager.setCastListener(object : CastListener {
            override fun onDeviceListUpdated(devices: List<RemoteDevice>) {
                Log.d(TAG, "收到设备列表更新，共${devices.size}个设备")
                
                // 记录设备详情
                devices.forEachIndexed { index, device ->
                    Log.d(TAG, "接收到设备[$index]: ${device.displayName}, 制造商: ${device.manufacturer}, ID: ${device.id}")
                }
                
                runOnUiThread {
                    // 先对设备列表去重
                    val uniqueDevices = devices.distinctBy { device ->
                        // 使用设备ID和地址组合作为唯一标识
                        "${device.id}@${device.address}"
                    }
                    
                    if (uniqueDevices.size != devices.size) {
                        Log.d(TAG, "设备去重: 从${devices.size}个设备减少到${uniqueDevices.size}个唯一设备")
                    }
                    
                    // 过滤并只展示MediaRenderer类型设备
                    val rendererDevices = uniqueDevices
                    
                    // 不过滤，显示所有设备 - 调试用
                    val deviceCount = rendererDevices.size
                    if (deviceCount > 0) {
                        val deviceNames = rendererDevices.joinToString { 
                            it.displayName
                        }
                        Log.d(TAG, "展示${deviceCount}个设备: $deviceNames")
                    } else {
                        Log.w(TAG, "设备列表为空")
                    }
                    
                    // 更新UI
                    deviceListAdapter.updateDevices(rendererDevices)
                }
            }
            
            override fun onConnected(device: RemoteDevice) {
                Log.d(TAG, "MainActivity.onConnected被调用: ${device.displayName}, ID: ${device.id}")
                
                try {
                    runOnUiThread {
                        Log.d(TAG, "设备连接成功: ${device.displayName}, ID: ${device.id}")
                        Toast.makeText(this@MainActivity, "已连接到: ${device.displayName}", Toast.LENGTH_SHORT).show()
                        
                        try {
                            // 创建Intent并传递设备ID
                            val intent = Intent(this@MainActivity, MediaControlActivity::class.java).apply {
                                putExtra("device_id", device.id)
                                // 添加更多设备信息作为备份
                                putExtra("device_name", device.displayName)
                            }
                            Log.d(TAG, "准备启动MediaControlActivity，传递设备ID: ${device.id}")
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "启动MediaControlActivity失败", e)
                            Toast.makeText(this@MainActivity, "打开投屏页面失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "onConnected回调处理失败", e)
                }
            }
            
            override fun onDisconnected() {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "设备已断开连接", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onError(error: DLNAException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "错误: ${error.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "DLNA错误", error)
                }
            }
        })
        
        // 获取多播锁
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("multicastLock")
        multicastLock?.acquire()
        
        // 搜索设备
        startDeviceDiscovery()
        
        // 设置按钮
        findViewById<View>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    /**
     * 判断设备是否为MediaRenderer类型
     */
    private fun isMediaRendererDevice(device: RemoteDevice): Boolean {
        // 日志所有设备信息，方便调试
        Log.d(TAG, "检查设备: ${device.displayName}, 制造商: ${device.manufacturer}, ID: ${device.id}")
        
        // 常见电视和投屏设备制造商
        val knownManufacturers = listOf(
            "samsung", "lg", "sony", "xiaomi", "mi", "hisense", "tcl", "philips",
            "panasonic", "sharp", "vizio", "roku", "google", "chromecast"
        )
        
        // 设备名称中常见的关键词
        val rendererKeywords = listOf(
            "tv", "电视", "播放器", "renderer", "player", "cast", "投屏", "dlna", "media"
        )
        
        // 检查制造商
        val isKnownManufacturer = device.manufacturer.isNotEmpty() && knownManufacturers.any {
            device.manufacturer.lowercase().contains(it)
        }
        
        // 检查设备名称
        val hasRendererKeyword = rendererKeywords.any {
            device.displayName.lowercase().contains(it)
        }
        
        // 小米设备特殊处理 - 小米电视经常不带特征关键字
        val isXiaomiTV = device.displayName.contains("小米") && device.displayName.contains("电视")
        
        // 结果
        val result = isKnownManufacturer || hasRendererKeyword || isXiaomiTV || true // 强制显示所有设备
        
        if (result) {
            Log.d(TAG, "接受设备: ${device.displayName}")
        } else {
            Log.d(TAG, "过滤掉设备: ${device.displayName}")
        }
        
        return result
    }
    
    /**
     * 开始设备发现
     */
    private fun startDeviceDiscovery() {
        try {
            dlnaCastManager.startSearch()
            Toast.makeText(this, "正在搜索设备...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "开始搜索设备失败", e)
            Toast.makeText(this, "搜索设备失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 连接到设备
     */
    private fun connectToDevice(device: RemoteDevice) {
        try {
            dlnaCastManager.connectToDevice(device)
            Toast.makeText(this, "正在连接到: ${device.displayName}...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "连接设备失败", e)
            Toast.makeText(this, "连接设备失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            dlnaCastManager.stopSearch()
            multicastLock?.release()
        } catch (e: Exception) {
            Log.e(TAG, "清理资源失败", e)
        }
    }
}
