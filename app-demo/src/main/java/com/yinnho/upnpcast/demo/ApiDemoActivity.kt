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
import com.yinnho.upnpcast.DLNACast

/**
 * 📚 API Demo Page - Complete Functionality Version
 */
class ApiDemoActivity : AppCompatActivity() {

    companion object {
    }

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
            setTextColor("#333333".toColorInt())
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
            "⏱️ 演示播放进度API" to { demoGetProgress() },
            "📁 演示本地文件投屏API" to { demoLocalFileCast() },
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
            setTextColor("#444444".toColorInt())
            setBackgroundColor("#F8F8F8".toColorInt())
            setPadding(16, 16, 16, 16)
        }
        layout.addView(logTextView)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun demoSearch() {
        logMessage("\n🔍 === 搜索设备API演示 ===")
        logMessage("调用: DLNACast.search(timeout = 5000) { devices ->")
        logMessage("参数: timeout = 5秒")
        logMessage("回调: 实时返回累积的全部设备列表")
        
        val startTime = System.currentTimeMillis()
        DLNACast.search(timeout = 5000) { devices: List<DLNACast.Device> ->
            runOnUiThread {
                val elapsed = System.currentTimeMillis() - startTime
                logMessage("⏰ 实时更新，耗时: ${elapsed}ms")
                logMessage("📱 当前设备总数: ${devices.size}")
                
                devices.forEachIndexed { index, device ->
                    val icon = if (device.isTV) "📺" else "📱"
                    logMessage("  ${index + 1}. $icon ${device.name} (${device.address})")
                }
                
                if (devices.isEmpty()) {
                    logMessage("💡 提示: 请确保有DLNA设备在同一网络中")
                }
            }
        }
        
        logMessage("✅ 搜索请求已发送，等待实时更新...")
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
        
        val controls = arrayOf("播放", "暂停", "停止", "跳转(30秒)", "获取状态", "静音", "音量控制")
        
        AlertDialog.Builder(this)
            .setTitle("选择控制动作")
            .setItems(controls) { _, which ->
                when (which) {
                    0 -> demoControlAction(DLNACast.MediaAction.PLAY, "播放")
                    1 -> demoControlAction(DLNACast.MediaAction.PAUSE, "暂停")
                    2 -> demoControlAction(DLNACast.MediaAction.STOP, "停止")
                    3 -> demoSeekControl()
                    4 -> demoControlAction(DLNACast.MediaAction.GET_STATE, "获取状态")
                    5 -> demoControlAction(DLNACast.MediaAction.MUTE, "静音", true)
                    6 -> demoVolumeControl()
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
    
    private fun demoSeekControl() {
        logMessage("\n⏩ === 跳转控制API演示 ===")
        logMessage("跳转到30秒位置")
        
        val positionMs = 30 * 1000L // 30秒转换为毫秒
        DLNACast.control(DLNACast.MediaAction.SEEK, positionMs) { success ->
            runOnUiThread {
                logMessage("跳转结果: ${if (success) "✅ 成功" else "❌ 失败"}")
                logMessage("目标位置: 30秒")
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

    private fun demoGetProgress() {
        logMessage("\n⏱️ === 播放进度API演示 ===")
        logMessage("调用: DLNACast.getProgress { currentMs, totalMs, success ->")
        
        DLNACast.getProgress { currentMs, totalMs, success ->
            runOnUiThread {
                if (success) {
                    val currentSec = currentMs / 1000
                    val totalSec = totalMs / 1000
                    val progressPercent = if (totalMs > 0) (currentMs * 100 / totalMs) else 0
                    
                    logMessage("✅ 获取成功:")
                    logMessage("  当前时间: ${formatSeconds(currentSec)}")
                    logMessage("  总时长: ${formatSeconds(totalSec)}")
                    logMessage("  播放进度: $progressPercent%")
                } else {
                    logMessage("❌ 获取播放进度失败")
                    logMessage("💡 提示: 需要先投屏内容")
                }
            }
        }
    }

    private fun formatSeconds(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, remainingSeconds)
    }

    private fun demoLocalFileCast() {
        logMessage("\n📁 === 本地文件投屏API演示 ===")
        logMessage("演示UPnPCast的本地文件投屏功能")
        logMessage("💡 说明: 本功能基于直接文件路径访问")
        
        // 提供简单实用的选择方式
        val options = arrayOf(
            "📹 浏览 DCIM/Camera 文件夹",
            "📁 浏览 Download 文件夹", 
            "🎵 浏览 Music 文件夹",
            "🎬 测试示例文件",
            "✏️ 手动输入文件路径"
        )
        
        AlertDialog.Builder(this)
            .setTitle("选择本地文件投屏方式")
            .setMessage("基于直接文件路径的本地投屏功能")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> browseFolder("/storage/emulated/0/DCIM/Camera/")
                    1 -> browseFolder("/storage/emulated/0/Download/")
                    2 -> browseFolder("/storage/emulated/0/Music/")
                    3 -> showTestFiles()
                    4 -> showCustomFilePathDialog()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun browseFolder(folderPath: String) {
        logMessage("🔍 浏览文件夹: $folderPath")
        
        try {
            val folder = java.io.File(folderPath)
            if (!folder.exists() || !folder.isDirectory) {
                logMessage("❌ 文件夹不存在: $folderPath")
                return
            }
            
            val files = folder.listFiles { file ->
                file.isFile && isMediaFile(file.name)
            }?.sortedBy { it.name } ?: emptyList()
            
            logMessage("📊 发现 ${files.size} 个媒体文件")
            
            if (files.isEmpty()) {
                logMessage("💡 该文件夹中没有媒体文件")
                return
            }
            
            val fileNames = files.map { file ->
                val name = file.name.lowercase()
                val icon = when {
                    name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov") -> "🎬"
                    name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".flac") || name.endsWith(".wav") -> "🎵"
                    name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp") -> "🖼️"
                    else -> "📄"
                }
                "$icon ${file.name}"
            }.toTypedArray()
            
            AlertDialog.Builder(this)
                .setTitle("选择文件 (${files.size}个媒体文件)")
                .setItems(fileNames) { _, which ->
                    val selectedFile = files[which]
                    logMessage("✅ 选中文件: ${selectedFile.name}")
                    demoLocalFileFromPath(selectedFile.absolutePath)
                }
                .setNegativeButton("返回", null)
                .show()
                
        } catch (e: Exception) {
            logMessage("❌ 浏览文件夹失败: ${e.message}")
        }
    }
    
    private fun isMediaFile(fileName: String): Boolean {
        val name = fileName.lowercase()
        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
               name.endsWith(".mov") || name.endsWith(".mp3") || name.endsWith(".aac") ||
               name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".jpg") ||
               name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")
    }
    
    private fun showTestFiles() {
        logMessage("🎬 提供常见路径示例")
        logMessage("💡 请根据设备实际情况选择存在的文件")
        
        val testFiles = arrayOf(
            "📹 /storage/emulated/0/DCIM/Camera/ (相机录制)",
            "🎵 /storage/emulated/0/Music/ (音乐文件)",
            "📁 /storage/emulated/0/Download/ (下载文件)",
            "🖼️ /storage/emulated/0/Pictures/ (图片文件)"
        )
        
        AlertDialog.Builder(this)
            .setTitle("常用文件路径示例")
            .setMessage("请先确保对应路径下有媒体文件")
            .setItems(testFiles) { _, which ->
                val basePath = when (which) {
                    0 -> "/storage/emulated/0/DCIM/Camera/"
                    1 -> "/storage/emulated/0/Music/"
                    2 -> "/storage/emulated/0/Download/"
                    3 -> "/storage/emulated/0/Pictures/"
                    else -> "/storage/emulated/0/"
                }
                logMessage("🎯 选择路径: $basePath")
                browseFolder(basePath)
            }
            .setNegativeButton("返回", null)
            .show()
    }
    
    private fun demoLocalFileFromPath(filePath: String) {
        logMessage("📁 测试文件路径: $filePath")
        logMessage("调用: DLNACast.castLocalFile(filePath, title) { success, message ->")
        
        val fileName = java.io.File(filePath).name
        logMessage("📂 文件名: $fileName")
        
        // 先检查文件是否存在
        val file = java.io.File(filePath)
        if (!file.exists()) {
            logMessage("⚠️ 文件不存在，演示getLocalFileUrl功能")
            val fileUrl = DLNACast.getLocalFileUrl(filePath)
            logMessage("🔗 生成的URL: ${fileUrl ?: "null (文件不存在)"}")
            return
        }
        
        // 自动选择设备投屏
        DLNACast.castLocalFile(filePath, fileName) { success, message ->
            runOnUiThread {
                if (success) {
                    logMessage("✅ 本地文件投屏成功")
                    logMessage("📺 消息: $message")
                    logMessage("🎬 现在可以在电视上看到播放内容")
                } else {
                    logMessage("❌ 本地文件投屏失败")
                    logMessage("📺 错误信息: $message")
                    logMessage("💡 提示: 确保文件存在且有读取权限")
                }
            }
        }
        
        logMessage("🚀 本地文件投屏请求已发送...")
        logMessage("ℹ️ 说明: 自动启动HTTP文件服务器")
        logMessage("ℹ️ 说明: 生成临时访问URL")
        logMessage("ℹ️ 说明: 投屏到最佳设备")
    }
    
    private fun showCustomFilePathDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        
        val pathInput = android.widget.EditText(this).apply {
            hint = "输入完整文件路径"
            setText("/storage/emulated/0/")
        }
        
        val titleInput = android.widget.EditText(this).apply {
            hint = "文件标题 (可选)"
        }
        
        val tipText = TextView(this).apply {
            text = """
                💡 路径示例：
                • /storage/emulated/0/DCIM/Camera/video.mp4
                • /storage/emulated/0/Download/movie.mkv
                • /storage/emulated/0/Music/song.mp3
                
                ✨ 本地文件投屏特性：
                • 基于直接文件路径访问
                • 自动启动HTTP文件服务器
                • 支持Range请求，大文件流式传输
                • 统一MIME类型，确保设备兼容性
                
                ⚠️ 注意：需要输入完整的文件系统路径
            """.trimIndent()
            textSize = 12f
            setTextColor("#666666".toColorInt())
            setPadding(0, 10, 0, 0)
        }
        
        layout.addView(TextView(this).apply { 
            text = "文件路径:" 
            textSize = 14f
            setPadding(0, 0, 0, 5)
        })
        layout.addView(pathInput)
        
        layout.addView(TextView(this).apply { 
            text = "标题:" 
            textSize = 14f 
            setPadding(0, 15, 0, 5)
        })
        layout.addView(titleInput)
        layout.addView(tipText)
        
        AlertDialog.Builder(this)
            .setTitle("本地文件投屏")
            .setView(layout)
            .setPositiveButton("投屏") { _, _ ->
                val path = pathInput.text.toString().trim()
                val title = titleInput.text.toString().trim().ifEmpty { null }
                
                if (path.isNotEmpty()) {
                    logMessage("📁 用户输入路径: $path")
                    logMessage("📝 用户输入标题: ${title ?: "(自动生成)"}")
                    demoLocalFileFromPath(path)
                } else {
                    logMessage("❌ 路径不能为空")
                }
            }
            .setNegativeButton("取消", null)
            .show()
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