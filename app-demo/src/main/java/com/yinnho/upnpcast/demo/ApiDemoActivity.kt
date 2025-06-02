package com.yinnho.upnpcast.demo

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yinnho.upnpcast.DLNACast

/**
 * 📚 API Demo Page - Complete Functionality Version
 */
class ApiDemoActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private val logMessages = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        supportActionBar?.title = "API Demo"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        createLayout()
        
        logMessage("📚 API Demo page started")
        logMessage("Demonstrating all DLNACast API usage")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun createLayout() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // 标题
        val titleView = TextView(this).apply {
            text = "📚 DLNACast API 演示"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(titleView)

        // API演示按钮
        val buttons = listOf(
            "🔍 演示搜索API" to { demoSearch() },
            "🎯 演示智能投屏API" to { demoCastTo() },
            "🎮 演示控制API" to { demoControl() },
            "📊 演示状态API" to { demoGetState() },
            "🔊 演示音量控制" to { demoVolumeControl() }
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

        // 日志显示区域
        val logTitle = TextView(this).apply {
            text = "📝 API调用日志:"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 20, 0, 10)
        }
        layout.addView(logTitle)

        logTextView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#444444"))
            setBackgroundColor(Color.parseColor("#F8F8F8"))
            setPadding(16, 16, 16, 16)
        }
        layout.addView(logTextView)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun demoSearch() {
        logMessage("\n🔍 === 搜索设备API演示 ===")
        logMessage("调用: DLNACast.search(timeout = 10000) { devices ->")
        logMessage("参数: timeout = 10秒")
        logMessage("回调: 返回发现的设备列表")
        
        val startTime = System.currentTimeMillis()
        DLNACast.search(timeout = 10000) { devices: List<DLNACast.Device> ->
            runOnUiThread {
                val elapsed = System.currentTimeMillis() - startTime
                logMessage("⏰ 搜索完成，耗时: ${elapsed}ms")
                logMessage("📱 发现设备数量: ${devices.size}")
                
                devices.forEachIndexed { index, device ->
                    val icon = if (device.isTV) "📺" else "📱"
                    logMessage("  ${index + 1}. $icon ${device.name} (${device.address})")
                }
                
                if (devices.isEmpty()) {
                    logMessage("💡 提示: 请确保有DLNA设备在同一网络中")
                }
            }
        }
        
        logMessage("✅ 搜索请求已发送，等待结果...")
    }

    private fun demoCastTo() {
        logMessage("\n🎯 === 智能投屏API演示 ===")
        logMessage("功能: 自动选择最佳设备进行投屏")
        
        val testUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        logMessage("测试URL: $testUrl")
        logMessage("调用: DLNACast.smartCast(url, title, callback) { devices ->")
        
        DLNACast.smartCast(testUrl, "智能选择投屏演示", { success ->
            runOnUiThread {
                logMessage("🎯 投屏结果: ${if (success) "✅ 成功" else "❌ 失败"}")
            }
        }) { devices: List<DLNACast.Device> ->
            runOnUiThread {
                logMessage("📱 可用设备数量: ${devices.size}")
                logMessage("🤖 设备选择逻辑: 优先选择电视设备")
                
                val selectedDevice = devices.find { it.isTV } ?: devices.firstOrNull()
                if (selectedDevice != null) {
                    logMessage("✅ 已选择: ${selectedDevice.name}")
                } else {
                    logMessage("❌ 未找到可用设备")
                }
            }
            
            // 返回选择的设备
            val selectedDevice = devices.find { it.isTV } ?: devices.firstOrNull()
            selectedDevice
        }
    }

    private fun demoControl() {
        logMessage("\n🎮 === 媒体控制API演示 ===")
        
        val controls = arrayOf("播放", "暂停", "停止", "获取状态", "静音", "音量控制")
        
        AlertDialog.Builder(this)
            .setTitle("选择控制动作")
            .setItems(controls) { _, which ->
                when (which) {
                    0 -> demoControlAction(DLNACast.MediaAction.PLAY, "播放")
                    1 -> demoControlAction(DLNACast.MediaAction.PAUSE, "暂停")
                    2 -> demoControlAction(DLNACast.MediaAction.STOP, "停止")
                    3 -> demoControlAction(DLNACast.MediaAction.GET_STATE, "获取状态")
                    4 -> demoControlAction(DLNACast.MediaAction.MUTE, "静音", true)
                    5 -> demoVolumeControl()
                }
            }
            .show()
    }

    private fun demoControlAction(action: DLNACast.MediaAction, actionName: String, value: Any? = null) {
        logMessage("🎮 控制动作: $actionName")
        logMessage("调用: DLNACast.control($action, $value)")
        
        DLNACast.control(action, value) { success ->
            runOnUiThread {
                logMessage("结果: ${if (success) "✅ 成功" else "❌ 失败"}")
            }
        }
    }

    private fun demoVolumeControl() {
        logMessage("\n🔊 === 音量控制API演示 ===")
        logMessage("设置音量到50%")
        
        val volume = 50
        DLNACast.control(DLNACast.MediaAction.VOLUME, volume) { success ->
            runOnUiThread {
                logMessage("音量设置结果: ${if (success) "✅ 成功" else "❌ 失败"}")
                logMessage("目标音量: $volume%")
            }
        }
    }

    private fun demoGetState() {
        logMessage("\n📊 === 状态获取API演示 ===")
        logMessage("调用: DLNACast.getState()")
        
        val state = DLNACast.getState()
        logMessage("连接状态: ${if (state.isConnected) "✅ 已连接" else "❌ 未连接"}")
        logMessage("当前设备: ${state.currentDevice?.name ?: "无"}")
        logMessage("播放状态: ${state.playbackState}")
        logMessage("音量: ${if (state.volume >= 0) "${state.volume}%" else "未知"}")
        logMessage("静音: ${if (state.isMuted) "是" else "否"}")
        
        logMessage("便捷状态:")
        logMessage("  isPlaying: ${state.isPlaying}")
        logMessage("  isPaused: ${state.isPaused}")
        logMessage("  isIdle: ${state.isIdle}")
    }

    private fun logMessage(message: String) {
        logMessages.add(message)
        runOnUiThread {
            if (::logTextView.isInitialized) {
                logTextView.text = logMessages.joinToString("\n")
                
                // 自动滚动到底部
                logTextView.post {
                    val scrollView = logTextView.parent.parent as? ScrollView
                    scrollView?.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }
} 