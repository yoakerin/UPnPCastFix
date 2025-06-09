package com.yinnho.upnpcast.demo

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.yinnho.upnpcast.DLNACast

/**
 * 🏠 UPnPCast Demo 主页 - 简洁版本
 */
class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"
    private lateinit var deviceListView: TextView
    private lateinit var statusView: TextView
    private val discoveredDevices = mutableListOf<DLNACast.Device>()
    
    // 防止重复显示对话框的标志
    private var isShowingMediaDialog = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        supportActionBar?.title = "UPnPCast Demo"
        
        createLayout()
        
        // 初始化
        DLNACast.init(this)
        
        log("🏠 UPnPCast Demo 启动")
        log("📱 单一导入解决所有问题: import com.yinnho.upnpcast.DLNACast")
    }

    private fun createLayout() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // 标题
        val titleView = TextView(this).apply {
            text = "🎯 UPnPCast Professional Demo"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor("#333333".toColorInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(titleView)

        // 状态显示
        statusView = TextView(this).apply {
            text = "状态: 就绪"
            textSize = 14f
            setTextColor("#666666".toColorInt())
            setPadding(0, 0, 0, 10)
        }
        layout.addView(statusView)

        // 功能按钮
        val buttons = listOf(
            "🔍 搜索设备" to { searchDevices() },
            "🎬 测试投屏" to { testCasting() },
            "📊 获取状态" to { getState() },
            "🎮 媒体控制" to { showMediaControls() }
        )

        buttons.forEach { (text, action) ->
            val button = Button(this).apply {
                this.text = text
                textSize = 16f
                setPadding(20, 15, 20, 15)
                setOnClickListener { action() }
            }
            layout.addView(button)
        }

        // 设备列表
        val deviceTitle = TextView(this).apply {
            text = "发现的设备:"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 20, 0, 10)
        }
        layout.addView(deviceTitle)

        deviceListView = TextView(this).apply {
            text = "尚未搜索设备"
            textSize = 12f
            setTextColor("#666666".toColorInt())
            setBackgroundColor("#F5F5F5".toColorInt())
            setPadding(16, 16, 16, 16)
            // 让设备列表可点击
            setOnClickListener {
                if (discoveredDevices.isNotEmpty()) {
                    showDeviceSelectionDialog()
                }
            }
        }
        layout.addView(deviceListView)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "API演示")
        menu?.add(0, 2, 0, "性能监控")
        menu?.add(0, 3, 0, "关于")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            1 -> {
                startActivity(Intent(this, ApiDemoActivity::class.java))
                true
            }
            2 -> {
                startActivity(Intent(this, PerformanceActivity::class.java))
                true
            }
            3 -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("关于 UPnPCast")
            .setMessage("🎯 专业的DLNA投屏库\n\n✨ 特性:\n• 门面模式设计\n• 类型安全API\n• 高性能异步处理\n• 完整的设备发现\n• 本地文件投屏\n\n🏗️ 架构:\n• 单一入口设计\n• 内部实现隐藏\n• 向后兼容支持")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun searchDevices() {
        log("🔍 开始搜索设备...")
        statusView.text = "状态: 搜索中..."
        discoveredDevices.clear()
        
        // 使用新的协程API
        lifecycleScope.launch {
            try {
                val devices = DLNACast.search(timeout = 5000)
                runOnUiThread {
                    discoveredDevices.clear()
                    discoveredDevices.addAll(devices)
                    log("📱 实时更新: 发现 ${devices.size} 个设备")
                    updateDeviceList()
                    
                    val statusText = "状态: 搜索完成 (${devices.size}个设备)"
                    statusView.text = statusText
                }
            } catch (e: Exception) {
                runOnUiThread {
                    log("❌ 搜索设备失败: ${e.message}")
                    statusView.text = "状态: 搜索失败"
                    deviceListView.text = "搜索失败，请重试"
                }
            }
        }
    }

    private fun updateDeviceList() {
        if (discoveredDevices.isEmpty()) {
            deviceListView.text = "未发现设备"
        } else {
            val deviceText = discoveredDevices.mapIndexed { index: Int, device: DLNACast.Device ->
                val icon = if (device.isTV) "📺" else "📱"
                "${index + 1}. $icon ${device.name}\n   地址: ${device.address}"
            }.joinToString("\n\n")
            deviceListView.text = "$deviceText\n\n💡 点击此处可选择设备进行投屏"
        }
    }

    private fun showDeviceSelectionDialog() {
        val deviceNames = discoveredDevices.map { device ->
            val icon = if (device.isTV) "📺" else "📱"
            "$icon ${device.name} (${device.address})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择投屏设备")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = discoveredDevices[which]
                performCastToDevice(selectedDevice)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun testCasting() {
        if (discoveredDevices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("请先搜索设备再进行投屏测试")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        showDeviceSelectionDialog()
    }

    private fun performCastToDevice(targetDevice: DLNACast.Device) {
        showMediaSelectionDialog(targetDevice)
    }
    
    private fun showMediaSelectionDialog(targetDevice: DLNACast.Device) {
        if (isShowingMediaDialog) {
            return
        }
        
        isShowingMediaDialog = true
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        
        val mediaOptions = listOf(
            "🎬 Big Buck Bunny (经典)" to {
                castMedia(targetDevice, 
                    "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4", 
                    "Big Buck Bunny")
            },
            "🌊 海洋视频 (推荐)" to {
                castMedia(targetDevice, 
                    "http://vjs.zencdn.net/v/oceans.mp4", 
                    "Ocean Video")
            },
            "🎭 Sintel 动画短片" to {
                castMedia(targetDevice, 
                    "https://media.w3.org/2010/05/sintel/trailer.mp4", 
                    "Sintel Trailer")
            },
            "🚗 西瓜视频Demo" to {
                castMedia(targetDevice, 
                    "https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4", 
                    "XiGua Player Demo")
            },
            "📱 选取本地视频" to {
                showLocalFileCastingOptions(targetDevice)
            },
            "✏️ 手动输入网络URL" to {
                showCustomUrlDialog(targetDevice)
            }
        )
        
        mediaOptions.forEach { option ->
            val text = option.first
            val action = option.second
            val button = Button(this).apply {
                this.text = text
                textSize = 14f
                setPadding(20, 15, 20, 15)
                setOnClickListener {
                    isShowingMediaDialog = false
                    action()
                }
            }
            layout.addView(button)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择要投屏的媒体")
            .setMessage("投屏到: ${targetDevice.name}")
            .setView(layout)
            .setNegativeButton("取消") { _, _ ->
                isShowingMediaDialog = false
            }
            .setOnDismissListener {
                isShowingMediaDialog = false
            }
            .create()
            
        dialog.show()
    }
    
    private fun showLocalFileCastingOptions(targetDevice: DLNACast.Device) {
                        VideoSelectorActivity.start(this, targetDevice)
    }
    
    private fun showCustomUrlDialog(targetDevice: DLNACast.Device) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        
        val urlInput = android.widget.EditText(this).apply {
            hint = "请输入媒体URL (http://...)"
            setText("http://")
        }
        
        val titleInput = android.widget.EditText(this).apply {
            hint = "媒体标题 (可选)"
        }
        
        val tipText = TextView(this).apply {
            text = "💡 支持格式: MP4, MP3, JPG, PNG 等\n📱 示例: http://example.com/video.mp4"
            textSize = 12f
            setTextColor("#666666".toColorInt())
            setPadding(0, 10, 0, 0)
        }
        
        layout.addView(TextView(this).apply { 
            text = "媒体URL:" 
            textSize = 14f
            setPadding(0, 0, 0, 5)
        })
        layout.addView(urlInput)
        
        layout.addView(TextView(this).apply { 
            text = "标题:" 
            textSize = 14f 
            setPadding(0, 15, 0, 5)
        })
        layout.addView(titleInput)
        layout.addView(tipText)
        
        AlertDialog.Builder(this)
            .setTitle("输入自定义媒体")
            .setMessage("投屏到: ${targetDevice.name}")
            .setView(layout)
            .setPositiveButton("投屏") { _, _ ->
                val url = urlInput.text.toString().trim()
                val title = titleInput.text.toString().trim().ifEmpty { "自定义媒体" }
                
                if (url.isNotEmpty() && url.startsWith("http")) {
                    castMedia(targetDevice, url, title)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("输入错误")
                        .setMessage("请输入有效的HTTP URL")
                        .setPositiveButton("重新输入") { _, _ -> showCustomUrlDialog(targetDevice) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun castMedia(targetDevice: DLNACast.Device, url: String, title: String) {
        log("🎬 开始投屏: $title 到: ${targetDevice.name}")
        log("📺 URL: $url")
        log("🔍 目标设备ID: ${targetDevice.id}")
        statusView.text = "状态: 投屏中..."
        
        // 显示投屏进度对话框
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("正在投屏")
            .setMessage("正在连接到 ${targetDevice.name}...\n媒体: $title")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        // 使用新的协程API直接向指定设备投屏
        lifecycleScope.launch {
            try {
                val success = DLNACast.castToDevice(targetDevice, url, title)
                runOnUiThread {
                    progressDialog.dismiss()
                    
                    if (success) {
                        log("✅ 投屏成功: $title 到: ${targetDevice.name}")
                        statusView.text = "状态: 正在播放 $title"
                        
                        // 显示成功对话框
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("投屏成功")
                            .setMessage("📺 设备: ${targetDevice.name}\n🎬 媒体: $title\n\n现在可以使用媒体控制功能")
                            .setPositiveButton("确定", null)
                            .setNeutralButton("媒体控制") { _, _ -> showMediaControls() }
                            .show()
                    } else {
                        log("❌ 投屏失败: $title")
                        statusView.text = "状态: 投屏失败"
                        
                        // 显示失败对话框，包含详细错误信息
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("投屏失败")
                            .setMessage("📺 目标设备: ${targetDevice.name}\n🎬 媒体: $title\n\n可能的原因:\n• 设备不在线\n• 媒体格式不支持\n• 网络连接问题")
                            .setPositiveButton("重试") { _, _ -> castMedia(targetDevice, url, title) }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    log("❌ 投屏异常: ${e.message}")
                    statusView.text = "状态: 投屏异常"
                    
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("投屏异常")
                        .setMessage("📺 目标设备: ${targetDevice.name}\n🎬 媒体: $title\n\n错误信息: ${e.message}")
                        .setPositiveButton("重试") { _, _ -> castMedia(targetDevice, url, title) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }
    }

    private fun getState() {
        val state = DLNACast.getState()
        log("📊 当前状态:")
        log("  连接: ${if (state.isConnected) "已连接" else "未连接"}")
        log("  播放状态: ${state.playbackState}")
        log("  当前设备: ${state.currentDevice?.name ?: "无"}")
        log("  播放中: ${state.isPlaying}")
        log("  音量: ${if (state.volume >= 0) "${state.volume}%" else "未知"}")
        
        // 在界面上显示状态信息
        val stateInfo = buildString {
            append("📊 当前DLNA状态\n")
            append("━━━━━━━━━━━━━━━━━━━━━━━━\n")
            append("🔗 连接状态: ${if (state.isConnected) "✅ 已连接" else "❌ 未连接"}\n")
            append("📺 当前设备: ${state.currentDevice?.name ?: "无"}\n")
            append("🎬 播放状态: ${getPlaybackStateDisplay(state.playbackState)}\n")
            append("▶️ 正在播放: ${if (state.isPlaying) "是" else "否"}\n")
            append("⏸️ 已暂停: ${if (state.isPaused) "是" else "否"}\n")
            append("🔊 音量: ${if (state.volume >= 0) "${state.volume}%" else "未知"}\n")
            append("🔇 静音: ${if (state.isMuted) "是" else "否"}\n")
            
            state.currentDevice?.let { device ->
                append("\n📱 设备详情:\n")
                append("  • ID: ${device.id}\n")
                append("  • 地址: ${device.address}\n")
                append("  • 类型: ${if (device.isTV) "电视" else "媒体设备"}\n")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("DLNA状态详情")
            .setMessage(stateInfo)
            .setPositiveButton("确定", null)
            .setNeutralButton("刷新") { _, _ -> getState() }
            .show()
            
        // 更新状态栏
        statusView.text = if (state.isConnected) {
            "状态: 已连接到 ${state.currentDevice?.name} - ${getPlaybackStateDisplay(state.playbackState)}"
        } else {
            "状态: 未连接"
        }
    }
    
    private fun getPlaybackStateDisplay(playbackState: DLNACast.PlaybackState): String {
        return when (playbackState) {
            DLNACast.PlaybackState.IDLE -> "空闲"
            DLNACast.PlaybackState.PLAYING -> "🎬 播放中"
            DLNACast.PlaybackState.PAUSED -> "⏸️ 已暂停"
            DLNACast.PlaybackState.STOPPED -> "⏹️ 已停止"
            DLNACast.PlaybackState.BUFFERING -> "⏳ 缓冲中"
            DLNACast.PlaybackState.ERROR -> "❌ 错误"
        }
    }

    private fun showMediaControls() {
        // 启动专门的媒体控制界面
        MediaControlActivity.start(this)
    }

    private fun log(message: String) {
        Log.d(tag, message)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        DLNACast.cleanup()
    }
}
