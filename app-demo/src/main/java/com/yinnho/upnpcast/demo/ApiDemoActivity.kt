package com.yinnho.upnpcast.demo

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.yinnho.upnpcast.DLNACast

/**
 * 📚 API Demo Page - 使用新的协程API
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
        logMessage("Demonstrating all DLNACast API usage with coroutines")
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

        // Title
        val titleView = TextView(this).apply {
            text = "📚 DLNACast API Demo (协程版)"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor("#333333".toColorInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(titleView)

        // API demo buttons
        val buttons = listOf(
            "🔍 搜索设备" to { demoSearch() },
            "🎯 智能投屏" to { demoCast() },
            "🎮 媒体控制" to { demoControl() },
            "📊 获取状态" to { demoGetState() },
            "⏱️ 获取进度" to { demoGetProgress() },
            "🔊 获取音量" to { demoGetVolume() },
            "🔧 缓存管理" to { demoCacheManagement() }
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

        // Log display area
        val logTitle = TextView(this).apply {
            text = "📝 API调用日志:"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 20, 0, 10)
        }
        layout.addView(logTitle)

        logTextView = TextView(this).apply {
            textSize = 12f
            setTextColor("#444444".toColorInt())
            setBackgroundColor("#F8F8F8".toColorInt())
            setPadding(16, 16, 16, 16)
        }
        layout.addView(logTextView)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun demoSearch() {
        logSectionHeader("🔍 设备搜索API演示")
        logApiCall("suspend fun search(timeout: Long): List<Device>")
        
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val devices = DLNACast.search(timeout = 5000)
                val elapsed = System.currentTimeMillis() - startTime
                
                logDetail("⏰ 搜索耗时: ${elapsed}ms")
                logDetail("📱 发现设备: ${devices.size}个")
                logDetail("📺 电视设备: ${devices.filter { it.isTV }.size}个")
                
                devices.forEachIndexed { index, device ->
                    val icon = if (device.isTV) "📺" else "📱"
                    logDetail("  ${index + 1}. $icon ${device.name} (${device.address})")
                }
                
                if (devices.isEmpty()) {
                    logTip("确保同一网络下有DLNA设备")
                } else {
                    logSuccess("搜索完成")
                }
            } catch (e: Exception) {
                logError("搜索失败: ${e.message}")
            }
        }
    }

    private fun demoCast() {
        logSectionHeader("🎯 智能投屏API演示")
        
        val testUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        logDetail("测试URL: $testUrl")
        logApiCall("suspend fun cast(url: String, title: String?): Boolean")
        
        lifecycleScope.launch {
            try {
                val success = DLNACast.cast(testUrl, "API Demo Video")
                if (success) {
                    logSuccess("智能投屏成功")
                    logDetail("自动选择最佳设备并开始播放")
                } else {
                    logError("智能投屏失败")
                    logTip("确保网络中有可用设备")
                }
            } catch (e: Exception) {
                logError("投屏异常: ${e.message}")
            }
        }
    }

    private fun demoControl() {
        logSectionHeader("🎮 媒体控制API演示")
        
        val controls = arrayOf("播放", "暂停", "停止", "跳转(30秒)", "设置音量(50%)")
        
        AlertDialog.Builder(this)
            .setTitle("选择控制操作")
            .setItems(controls) { _, which ->
                when (which) {
                    0 -> demoControlAction(DLNACast.MediaAction.PLAY, "播放")
                    1 -> demoControlAction(DLNACast.MediaAction.PAUSE, "暂停")
                    2 -> demoControlAction(DLNACast.MediaAction.STOP, "停止")
                    3 -> demoSeekControl()
                    4 -> demoVolumeControl()
                }
            }
            .show()
    }

    private fun demoControlAction(action: DLNACast.MediaAction, actionName: String) {
        logDetail("🎮 控制操作: $actionName")
        logApiCall("suspend fun control(action: MediaAction): Boolean")
        
        lifecycleScope.launch {
            try {
                val success = DLNACast.control(action)
                if (success) {
                    logSuccess("$actionName 成功")
                } else {
                    logError("$actionName 失败")
                }
            } catch (e: Exception) {
                logError("$actionName 异常: ${e.message}")
            }
        }
    }

    private fun demoSeekControl() {
        logDetail("🎮 跳转控制: 30秒")
        logApiCall("suspend fun seek(positionMs: Long): Boolean")
        
        lifecycleScope.launch {
            try {
                val success = DLNACast.seek(30000) // 30秒
                if (success) {
                    logSuccess("跳转成功")
                } else {
                    logError("跳转失败")
                }
            } catch (e: Exception) {
                logError("跳转异常: ${e.message}")
            }
        }
    }

    private fun demoVolumeControl() {
        logDetail("🔊 音量控制: 设置为50%")
        logApiCall("suspend fun setVolume(volume: Int): Boolean")
        
        lifecycleScope.launch {
            try {
                val success = DLNACast.setVolume(50)
                if (success) {
                    logSuccess("音量设置成功")
                } else {
                    logError("音量设置失败")
                }
            } catch (e: Exception) {
                logError("音量设置异常: ${e.message}")
            }
        }
    }

    private fun demoGetState() {
        logSectionHeader("📊 状态获取API演示")
        logApiCall("fun getState(): State")
        
        val state = DLNACast.getState()
        logDetail("🔗 连接状态: ${if (state.isConnected) "已连接" else "未连接"}")
        logDetail("📺 当前设备: ${state.currentDevice?.name ?: "无"}")
        logDetail("🎬 播放状态: ${state.playbackState}")
        logDetail("▶️ 正在播放: ${state.isPlaying}")
        logDetail("⏸️ 已暂停: ${state.isPaused}")
        logDetail("🔊 音量: ${if (state.volume >= 0) "${state.volume}%" else "未知"}")
        logDetail("🔇 静音: ${state.isMuted}")
        
        logSuccess("状态获取完成")
    }

    private fun demoGetProgress() {
        logSectionHeader("⏱️ 进度获取API演示")
        logApiCall("suspend fun getProgress(): Pair<Long, Long>?")
        
        lifecycleScope.launch {
            try {
                                 val progressInfo = DLNACast.getProgress()
                 if (progressInfo != null) {
                     val (currentMs, totalMs) = progressInfo
                     val progressPercent = if (totalMs > 0) (currentMs * 100 / totalMs) else 0
                     
                     logDetail("⏱️ 当前时间: ${formatTime(currentMs)}")
                     logDetail("⏱️ 总时长: ${formatTime(totalMs)}")
                     logDetail("📊 进度: $progressPercent%")
                     logSuccess("进度获取成功")
                } else {
                    logError("进度获取失败")
                }
            } catch (e: Exception) {
                logError("进度获取异常: ${e.message}")
            }
        }
    }

    private fun demoGetVolume() {
        logSectionHeader("🔊 音量获取API演示")
        logApiCall("suspend fun getVolume(): Pair<Int?, Boolean?>?")
        
        lifecycleScope.launch {
            try {
                val volumeInfo = DLNACast.getVolume()
                if (volumeInfo != null) {
                    val (volume, isMuted) = volumeInfo
                    logDetail("🔊 音量: ${volume ?: "未知"}%")
                    logDetail("🔇 静音: ${isMuted ?: false}")
                    logSuccess("音量获取成功")
                } else {
                    logError("音量获取失败")
                }
            } catch (e: Exception) {
                logError("音量获取异常: ${e.message}")
            }
        }
    }

    private fun demoCacheManagement() {
        logSectionHeader("🔧 缓存管理API演示")
        
        val options = arrayOf("刷新音量缓存", "刷新进度缓存", "清除进度缓存")
        
        AlertDialog.Builder(this)
            .setTitle("选择缓存操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> demoRefreshVolumeCache()
                    1 -> demoRefreshProgressCache()
                    2 -> demoClearProgressCache()
                }
            }
            .show()
    }

    private fun demoRefreshVolumeCache() {
        logDetail("🔧 刷新音量缓存")
        logApiCall("suspend fun refreshVolumeCache(): Boolean")
        
        lifecycleScope.launch {
            try {
                val success = DLNACast.refreshVolumeCache()
                if (success) {
                    logSuccess("音量缓存刷新成功")
                } else {
                    logError("音量缓存刷新失败")
                }
            } catch (e: Exception) {
                logError("音量缓存刷新异常: ${e.message}")
            }
        }
    }

    private fun demoRefreshProgressCache() {
        logDetail("🔧 刷新进度缓存")
        logApiCall("suspend fun refreshProgressCache(): Boolean")
        
        lifecycleScope.launch {
            try {
                val success = DLNACast.refreshProgressCache()
                if (success) {
                    logSuccess("进度缓存刷新成功")
                } else {
                    logError("进度缓存刷新失败")
                }
            } catch (e: Exception) {
                logError("进度缓存刷新异常: ${e.message}")
            }
        }
    }

    private fun demoClearProgressCache() {
        logDetail("🔧 清除进度缓存")
        logApiCall("fun clearProgressCache()")
        
        DLNACast.clearProgressCache()
        logSuccess("进度缓存已清除")
    }

    // 日志工具方法
    private fun logMessage(message: String) {
        logMessages.add(message)
        updateLogDisplay()
    }

    private fun logSectionHeader(header: String) {
        logMessages.add("\n━━━━━━━━━━━━━━━━━━━━━━━━")
        logMessages.add(header)
        logMessages.add("━━━━━━━━━━━━━━━━━━━━━━━━")
        updateLogDisplay()
    }

    private fun logApiCall(apiCall: String) {
        logMessages.add("📞 API调用: $apiCall")
        updateLogDisplay()
    }

    private fun logDetail(detail: String) {
        logMessages.add("📋 $detail")
        updateLogDisplay()
    }

    private fun logSuccess(message: String) {
        logMessages.add("✅ $message")
        updateLogDisplay()
    }

    private fun logError(message: String) {
        logMessages.add("❌ $message")
        updateLogDisplay()
    }

    private fun logTip(tip: String) {
        logMessages.add("💡 提示: $tip")
        updateLogDisplay()
    }

    private fun updateLogDisplay() {
        logTextView.text = logMessages.takeLast(50).joinToString("\n")
    }

    private fun formatTime(timeMs: Long): String {
        val seconds = timeMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
} 