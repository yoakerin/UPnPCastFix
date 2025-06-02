package com.yinnho.upnpcast.demo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yinnho.upnpcast.DLNACast

/**
 * 🎯 UPnPCast API 完整演示
 */
class ApiDemoActivity : AppCompatActivity() {

    private val TAG = "ApiDemoActivity"
    private lateinit var logOutput: TextView
    private val logs = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.title = "UPnPCast API 演示"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 先初始化DLNACast，避免闪退
        try {
            DLNACast.init(this)
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败: ${e.message}")
        }

        createLayout()
        logMessage("🚀 UPnPCast API Demo 已启动")
        logMessage("📚 演示所有核心API的标准用法")
    }

    private fun createLayout() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // 创建按钮
        val buttons = listOf(
            "init() - 初始化" to { demoInit() },
            "search() - 搜索设备" to { demoSearch() },
            "cast() - 自动投屏" to { demoCast() },
            "castTo() - 智能选择投屏" to { demoCastTo() },
            "control() - 媒体控制" to { demoControl() },
            "getState() - 获取状态" to { demoGetState() },
            "release() - 释放资源" to { demoRelease() },
            "清空日志" to { clearLog() }
        )

        buttons.forEach { (text, action) ->
            val button = Button(this).apply {
                this.text = text
                textSize = 16f
                setOnClickListener { action() }
            }
            layout.addView(button)
        }

        // 日志输出
        logOutput = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(16, 16, 16, 16)
        }
        layout.addView(logOutput)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    // ================ API演示方法 ================

    private fun demoInit() {
        logMessage("\n🔧 API Demo: DLNACast.init()")
        logMessage("```kotlin")
        logMessage("DLNACast.init(context)")
        logMessage("```")
        
        try {
            DLNACast.init(this)
            logMessage("✅ 初始化成功")
        } catch (e: Exception) {
            logMessage("❌ 初始化失败: ${e.message}")
        }
    }

    private fun demoSearch() {
        logMessage("\n🔍 API Demo: DLNACast.search()")
        logMessage("```kotlin")
        logMessage("DLNACast.search(timeout = 10000) { devices: List<DLNACast.Device> ->")
        logMessage("    devices.forEach { device ->")
        logMessage("        Log.d(TAG, \"发现设备: \${device.name}\")")
        logMessage("    }")
        logMessage("}")
        logMessage("```")
        
        DLNACast.search(timeout = 10000) { devices: List<DLNACast.Device> ->
            runOnUiThread {
                logMessage("📱 搜索结果: 发现 ${devices.size} 个设备")
                devices.forEachIndexed { index, device ->
                    val typeIcon = when {
                        device.isTV -> "📺"
                        device.isBox -> "📱"
                        else -> "📲"
                    }
                    logMessage("  [$index] $typeIcon ${device.name} (${device.manufacturer})")
                }
            }
        }
    }

    private fun demoCast() {
        logMessage("\n🎬 API Demo: DLNACast.cast()")
        logMessage("```kotlin")
        logMessage("DLNACast.cast(")
        logMessage("    url = \"http://sample-video.mp4\",")
        logMessage("    title = \"演示视频\"")
        logMessage(") { success ->")
        logMessage("    if (success) Log.d(TAG, \"投屏成功!\")")
        logMessage("}")
        logMessage("```")
        
        val testUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        DLNACast.cast(testUrl, "API演示视频") { success ->
            runOnUiThread {
                if (success) {
                    logMessage("✅ 自动投屏成功!")
                } else {
                    logMessage("❌ 投屏失败 (可能没有可用设备)")
                }
            }
        }
    }

    private fun demoCastTo() {
        logMessage("\n🎯 API Demo: DLNACast.castTo()")
        val testUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
        DLNACast.castTo(testUrl, "智能选择投屏演示") { devices: List<DLNACast.Device> ->
            logMessage("🤖 设备选择器被调用，可用设备: ${devices.size}")
            val selected = devices.find { it.isTV } ?: devices.firstOrNull()
            if (selected != null) {
                logMessage("✅ 智能选择: ${selected.name}")
            } else {
                logMessage("❌ 没有可用设备")
            }
            selected
        }
    }

    private fun demoControl() {
        logMessage("\n🎮 API Demo: DLNACast.control()")
        
        val controlOptions = arrayOf("PLAY", "PAUSE", "STOP", "VOLUME", "MUTE", "GET_STATE")
        
        AlertDialog.Builder(this)
            .setTitle("选择控制操作")
            .setItems(controlOptions) { _, which ->
                when (which) {
                    0 -> demoControlAction(DLNACast.MediaAction.PLAY, "播放")
                    1 -> demoControlAction(DLNACast.MediaAction.PAUSE, "暂停")
                    2 -> demoControlAction(DLNACast.MediaAction.STOP, "停止")
                    3 -> demoVolumeControl()
                    4 -> demoControlAction(DLNACast.MediaAction.MUTE, "静音", true)
                    5 -> demoControlAction(DLNACast.MediaAction.GET_STATE, "获取状态")
                }
            }
            .show()
    }

    private fun demoControlAction(action: DLNACast.MediaAction, actionName: String, value: Any? = null) {
        DLNACast.control(action, value) { success ->
            runOnUiThread {
                logMessage("🎮 $actionName ${if (success) "成功" else "失败"}")
            }
        }
    }

    private fun demoVolumeControl() {
        val volume = 50
        DLNACast.control(DLNACast.MediaAction.VOLUME, volume) { success ->
            runOnUiThread {
                logMessage("🔊 音量设置为 $volume% ${if (success) "成功" else "失败"}")
            }
        }
    }

    private fun demoGetState() {
        logMessage("\n📊 API Demo: DLNACast.getState()")
        
        val state = DLNACast.getState()
        logMessage("📊 当前状态:")
        logMessage("  • 连接状态: ${if (state.isConnected) "✅ 已连接" else "❌ 未连接"}")
        logMessage("  • 播放状态: ${state.playbackState}")
        logMessage("  • 当前设备: ${state.currentDevice?.name ?: "无"}")
        logMessage("  • 是否播放: ${state.isPlaying}")
        logMessage("  • 是否暂停: ${state.isPaused}")
        logMessage("  • 音量: ${if (state.volume >= 0) "${state.volume}%" else "未知"}")
        logMessage("  • 静音: ${state.isMuted}")
    }

    private fun demoRelease() {
        logMessage("\n🧹 API Demo: DLNACast.release()")
        DLNACast.release()
        logMessage("✅ 资源已释放")
    }

    private fun logMessage(message: String) {
        Log.d(TAG, message)
        logs.add(message)
        
        runOnUiThread {
            logOutput.text = logs.takeLast(50).joinToString("\n")
        }
    }

    private fun clearLog() {
        logs.clear()
        logOutput.text = ""
        logMessage("🆕 日志已清空")
        logMessage("🚀 UPnPCast API Demo")
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 