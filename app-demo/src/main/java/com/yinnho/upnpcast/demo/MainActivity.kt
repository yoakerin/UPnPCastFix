package com.yinnho.upnpcast.demo

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.content.ContentUris
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yinnho.upnpcast.DLNACast

/**
 * 🏠 UPnPCast Demo 主页
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var deviceListView: TextView
    private lateinit var statusView: TextView
    private val discoveredDevices = mutableListOf<DLNACast.Device>()
    
    // 防止重复显示对话框的标志
    private var isShowingMediaDialog = false
    
    // 当前等待投屏的设备
    private var currentTargetDevice: DLNACast.Device? = null
    
    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            currentTargetDevice?.let { device ->
                handleSelectedFile(device, selectedUri)
            }
        }
    }

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
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(titleView)

        // 状态显示
        statusView = TextView(this).apply {
            text = "状态: 就绪"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
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
            setTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
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
            .setMessage("🎯 专业的DLNA投屏库\n\n✨ 特性:\n• 门面模式设计\n• 类型安全API\n• 高性能异步处理\n• 完整的设备发现\n\n🏗️ 架构:\n• 单一入口设计\n• 内部实现隐藏\n• 向后兼容支持")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun searchDevices() {
        log("🔍 开始搜索设备...")
        statusView.text = "状态: 搜索中..."
        discoveredDevices.clear()
        
        DLNACast.search(timeout = 10000) { devices: List<DLNACast.Device> ->
            runOnUiThread {
                devices.forEach { device: DLNACast.Device ->
                    if (!discoveredDevices.any { it.id == device.id }) {
                        discoveredDevices.add(device)
                        log("📱 发现设备: ${device.name}")
                    }
                }
                updateDeviceList()
                statusView.text = "状态: 搜索完成 (${discoveredDevices.size}个设备)"
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
        Log.d(TAG, "showDeviceSelectionDialog() called with ${discoveredDevices.size} devices")
        val deviceNames = discoveredDevices.map { device ->
            val icon = if (device.isTV) "📺" else "📱"
            "$icon ${device.name} (${device.address})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择投屏设备")
            .setItems(deviceNames) { _, which ->
                Log.d(TAG, "Device selected: index=$which")
                val selectedDevice = discoveredDevices[which]
                Log.d(TAG, "Selected device: ${selectedDevice.name}")
                performCastToDevice(selectedDevice)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun testCasting() {
        Log.d(TAG, "testCasting() called")
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
        Log.d(TAG, "performCastToDevice() called for device: ${targetDevice.name}")
        // 显示媒体选择对话框
        showMediaSelectionDialog(targetDevice)
    }
    
    private fun showMediaSelectionDialog(targetDevice: DLNACast.Device) {
        Log.d(TAG, "showMediaSelectionDialog() called for device: ${targetDevice.name}")
        
        if (isShowingMediaDialog) {
            Log.w(TAG, "Media dialog is already showing, ignoring duplicate call")
            return
        }
        
        isShowingMediaDialog = true
        Log.d(TAG, "Setting isShowingMediaDialog = true")
        
        // 创建垂直布局，包含8个按钮
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        
        // 8个媒体选项 - 使用在中国可以正常访问的测试URL
        val mediaOptions = listOf(
            "🎬 Big Buck Bunny (经典)" to {
                Log.d(TAG, "Big Buck Bunny selected")
                castMedia(targetDevice, 
                    "http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4", 
                    "Big Buck Bunny")
            },
            "🌊 海洋视频 (推荐)" to {
                Log.d(TAG, "Ocean video selected")
                castMedia(targetDevice, 
                    "http://vjs.zencdn.net/v/oceans.mp4", 
                    "Ocean Video")
            },
            "🎭 Sintel 动画短片" to {
                Log.d(TAG, "Sintel selected")
                castMedia(targetDevice, 
                    "https://media.w3.org/2010/05/sintel/trailer.mp4", 
                    "Sintel Trailer")
            },
            "🚗 西瓜视频Demo" to {
                Log.d(TAG, "XiGua video selected")
                castMedia(targetDevice, 
                    "https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4", 
                    "XiGua Player Demo")
            },
            "📺 W3学校示例" to {
                Log.d(TAG, "W3School example selected")
                castMedia(targetDevice, 
                    "http://www.w3school.com.cn/example/html5/mov_bbb.mp4", 
                    "W3School Example")
            },
            "🎪 测试视频5" to {
                Log.d(TAG, "Test video 5 selected")
                castMedia(targetDevice, 
                    "https://www.w3schools.com/html/movie.mp4", 
                    "W3Schools Movie")
            },
            "📱 本地文件投屏说明" to {
                Log.d(TAG, "Local file info selected")
                selectLocalFile(targetDevice)
            },
            "✏️ 手动输入网络URL" to {
                Log.d(TAG, "Custom URL option selected")
                showCustomUrlDialog(targetDevice)
            }
        )
        
        // 为每个选项创建按钮
        mediaOptions.forEach { (text, action) ->
            val button = Button(this).apply {
                this.text = text
                textSize = 14f
                setPadding(20, 15, 20, 15)
                setOnClickListener {
                    Log.d(TAG, "Button clicked: $text")
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
                Log.d(TAG, "Media selection cancelled")
                isShowingMediaDialog = false
            }
            .setOnDismissListener {
                Log.d(TAG, "Media dialog dismissed")
                isShowingMediaDialog = false
            }
            .create()
            
        Log.d(TAG, "Showing media selection dialog with button list")
        dialog.show()
    }
    
    private fun selectLocalFile(targetDevice: DLNACast.Device) {
        Log.d(TAG, "selectLocalFile() called for device: ${targetDevice.name}")
        
        // 显示DLNA协议说明
        AlertDialog.Builder(this)
            .setTitle("📺 DLNA本地文件投屏说明")
            .setMessage("""
                🔍 DLNA协议工作原理：
                
                ❌ DLNA设备无法直接访问手机本地文件
                ✅ 需要通过网络协议访问文件：
                
                📡 支持的协议：
                • HTTP：需要在手机上启动HTTP服务器
                • SMB/CIFS：网络文件共享协议
                • UPnP媒体服务器
                
                🚫 不支持的协议：
                • file:// (本地文件系统协议)
                
                💡 当前解决方案：
                1. 使用"手动输入URL"功能，输入网络上的媒体地址
                2. 将文件上传到网盘，获取直链地址
                3. 使用局域网文件共享服务
                
                ⚠️ 提示：
                本地文件投屏功能需要实现HTTP媒体服务器，
                目前版本暂不支持，建议使用网络URL。
            """.trimIndent())
            .setPositiveButton("手动输入URL") { _, _ ->
                showCustomUrlDialog(targetDevice)
            }
            .setNeutralButton("了解更多") { _, _ ->
                showDLNAProtocolInfo(targetDevice)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDLNAProtocolInfo(targetDevice: DLNACast.Device) {
        AlertDialog.Builder(this)
            .setTitle("🔬 DLNA技术详解")
            .setMessage("""
                📡 DLNA (Digital Living Network Alliance)
                
                🏗️ 基础架构：
                • 基于UPnP (Universal Plug and Play)
                • 使用HTTP协议传输媒体内容
                • 设备通过SSDP协议发现彼此
                
                🎯 工作流程：
                1. 📱 控制点(手机) 发现 📺 媒体渲染器(电视)
                2. 📱 告诉 📺 要播放的媒体URL
                3. 📺 直接从URL下载并播放媒体
                
                🌐 媒体源要求：
                • 必须是网络可访问的URL
                • 支持HTTP/HTTPS协议
                • 设备能直接下载的格式
                
                📱 本地文件投屏需要：
                • 在手机上运行HTTP服务器
                • 将本地文件通过HTTP提供给设备
                • 处理网络权限和防火墙
                
                💭 这就是为什么：
                直接使用file://协议无法工作，
                因为电视无法访问手机的文件系统。
            """.trimIndent())
            .setPositiveButton("明白了") { _, _ ->
                showCustomUrlDialog(targetDevice)
            }
            .setNegativeButton("返回", null)
            .show()
    }
    
    private fun handleSelectedFile(targetDevice: DLNACast.Device, uri: Uri) {
        Log.d(TAG, "handleSelectedFile() called with uri: $uri")
        
        try {
            // 获取文件信息
            val fileName = getFileName(uri) ?: "选择的文件"
            val fileSize = getFileSize(uri)
            
            // 尝试获取实际文件路径
            val realPath = getRealPathFromURI(uri)
            
            if (realPath != null) {
                // 显示确认对话框
                val message = buildString {
                    append("📱 已选择文件\n\n")
                    append("📁 文件名: $fileName\n")
                    if (fileSize > 0) {
                        append("📊 大小: ${formatFileSize(fileSize)}\n")
                    }
                    append("📍 路径: $realPath\n")
                    append("📺 投屏到: ${targetDevice.name}\n\n")
                    append("⚠️ 提示: 使用file://协议投屏本地文件")
                }
                
                AlertDialog.Builder(this)
                    .setTitle("确认投屏")
                    .setMessage(message)
                    .setPositiveButton("开始投屏") { _, _ ->
                        val fileUrl = "file://$realPath"
                        Log.d(TAG, "Starting cast with file path: $fileUrl")
                        castMedia(targetDevice, fileUrl, fileName)
                    }
                    .setNegativeButton("重新选择") { _, _ ->
                        selectLocalFile(targetDevice)
                    }
                    .setNeutralButton("取消", null)
                    .show()
            } else {
                // 无法获取实际路径，提示用户
                AlertDialog.Builder(this)
                    .setTitle("文件路径问题")
                    .setMessage("""
                        📱 无法获取文件的实际路径
                        
                        🔍 原因：
                        • 文件可能存储在云端或私有目录
                        • Android安全限制
                        
                        💡 解决方案：
                        1. 将文件复制到Download目录
                        2. 使用手动输入路径功能
                        3. 确保文件在SD卡的公共目录中
                        
                        👍 建议：
                        使用"手动输入路径"功能，输入类似：
                        file:///storage/emulated/0/Download/视频.mp4
                    """.trimIndent())
                    .setPositiveButton("手动输入路径") { _, _ ->
                        showLocalFilePathDialog(targetDevice)
                    }
                    .setNeutralButton("重新选择") { _, _ ->
                        selectLocalFile(targetDevice)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
                
        } catch (e: Exception) {
            Log.e(TAG, "Error handling selected file", e)
            AlertDialog.Builder(this)
                .setTitle("文件处理失败")
                .setMessage("无法读取选择的文件，请重新选择或检查文件权限\n\n错误信息: ${e.message}")
                .setPositiveButton("重新选择") { _, _ -> selectLocalFile(targetDevice) }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    private fun getFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) cursor.getString(nameIndex) else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file name", e)
            "未知文件"
        }
    }
    
    private fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                } else -1L
            } ?: -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size", e)
            -1L
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "未知大小"
        
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f %s".format(size, units[unitIndex])
    }
    
    private fun showLocalFileOptions(targetDevice: DLNACast.Device) {
        // 这个方法现在被 selectLocalFile 替代，但保留以防兼容性
        selectLocalFile(targetDevice)
    }
    
    private fun showLocalFilePathDialog(targetDevice: DLNACast.Device) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        
        val pathInput = android.widget.EditText(this).apply {
            hint = "输入本地文件路径"
            setText("file:///storage/emulated/0/")
        }
        
        val titleInput = android.widget.EditText(this).apply {
            hint = "文件标题 (可选)"
        }
        
        val tipText = TextView(this).apply {
            text = """
                💡 路径示例：
                • file:///storage/emulated/0/DCIM/video.mp4
                • file:///storage/emulated/0/Music/song.mp3
                • file:///storage/emulated/0/Pictures/photo.jpg
                
                ⚠️ 注意：需要文件访问权限
            """.trimIndent()
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 10, 0, 0)
        }
        
        layout.addView(TextView(this).apply { 
            text = "本地文件路径:" 
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
            .setTitle("选择本地文件")
            .setMessage("投屏到: ${targetDevice.name}")
            .setView(layout)
            .setPositiveButton("投屏") { _, _ ->
                val path = pathInput.text.toString().trim()
                val title = titleInput.text.toString().trim().ifEmpty { "本地文件" }
                
                if (path.isNotEmpty() && path.startsWith("file://")) {
                    castMedia(targetDevice, path, title)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("路径错误")
                        .setMessage("请输入正确的本地文件路径 (file://...)")
                        .setPositiveButton("重新输入") { _, _ -> showLocalFilePathDialog(targetDevice) }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showFileExamples(targetDevice: DLNACast.Device) {
        val examples = """
            📱 常见文件位置示例
            
            🎬 视频文件：
            • /storage/emulated/0/DCIM/Camera/
            • /storage/emulated/0/Movies/
            • /storage/emulated/0/Download/
            
            🎵 音频文件：
            • /storage/emulated/0/Music/
            • /storage/emulated/0/Ringtones/
            • /storage/emulated/0/Notifications/
            
            📷 图片文件：
            • /storage/emulated/0/DCIM/Camera/
            • /storage/emulated/0/Pictures/
            • /storage/emulated/0/Screenshots/
            
            📄 关于PPT等办公文档：
            由于DLNA协议限制，无法直接投屏PPT、Word等文档。
            
            建议解决方案：
            1. PPT → 导出为图片 → 投屏图片
            2. 使用屏幕镜像功能
            3. 转换为视频格式后投屏
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("文件路径参考")
            .setMessage(examples)
            .setPositiveButton("知道了", null)
            .setNeutralButton("继续选择") { _, _ -> showLocalFilePathDialog(targetDevice) }
            .show()
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
            setTextColor(Color.parseColor("#666666"))
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
        Log.d(TAG, "MainActivity.castMedia called: device=${targetDevice.name}, url=$url, title=$title")
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
        
        // 使用新的API直接向指定设备投屏
        DLNACast.castToDevice(targetDevice, url, title) { success ->
            runOnUiThread {
                progressDialog.dismiss()
                
                if (success) {
                    log("✅ 投屏成功: $title 到: ${targetDevice.name}")
                    statusView.text = "状态: 正在播放 $title"
                    
                    // 显示成功对话框
                    AlertDialog.Builder(this)
                        .setTitle("投屏成功")
                        .setMessage("📺 设备: ${targetDevice.name}\n🎬 媒体: $title\n\n现在可以使用媒体控制功能")
                        .setPositiveButton("确定", null)
                        .setNeutralButton("媒体控制") { _, _ -> showMediaControls() }
                        .show()
                } else {
                    log("❌ 投屏失败: $title")
                    statusView.text = "状态: 投屏失败"
                    
                    // 显示失败对话框
                    AlertDialog.Builder(this)
                        .setTitle("投屏失败")
                        .setMessage("📺 目标设备: ${targetDevice.name}\n🎬 媒体: $title\n\n可能的原因:\n• 设备不在线\n• 媒体格式不支持\n• 网络连接问题")
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
        val controls = arrayOf("播放", "暂停", "停止", "设置音量", "静音")
        
        AlertDialog.Builder(this)
            .setTitle("媒体控制")
            .setItems(controls) { _, which ->
                when (which) {
                    0 -> controlMedia(DLNACast.MediaAction.PLAY, "播放")
                    1 -> controlMedia(DLNACast.MediaAction.PAUSE, "暂停")
                    2 -> controlMedia(DLNACast.MediaAction.STOP, "停止")
                    3 -> controlMedia(DLNACast.MediaAction.VOLUME, "音量", 50)
                    4 -> controlMedia(DLNACast.MediaAction.MUTE, "静音", true)
                }
            }
            .show()
    }

    private fun controlMedia(action: DLNACast.MediaAction, actionName: String, value: Any? = null) {
        DLNACast.control(action, value) { success ->
            runOnUiThread {
                log("🎮 $actionName ${if (success) "成功" else "失败"}")
            }
        }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    private fun getRealPathFromURI(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> {
                    uri.path
                }
                "content" -> {
                    // 尝试通过DocumentsContract获取路径
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        when {
                            android.provider.DocumentsContract.isDocumentUri(this, uri) -> {
                                getPathFromDocumentUri(uri)
                            }
                            uri.authority == "com.android.externalstorage.documents" -> {
                                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                                val split = docId.split(":")
                                if (split.size >= 2) {
                                    val type = split[0]
                                    if ("primary".equals(type, ignoreCase = true)) {
                                        "/storage/emulated/0/${split[1]}"
                                    } else {
                                        null
                                    }
                                } else null
                            }
                            else -> {
                                // 尝试传统方法
                                getDataColumn(uri, null, null)
                            }
                        }
                    } else {
                        getDataColumn(uri, null, null)
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting real path from URI", e)
            null
        }
    }
    
    private fun getPathFromDocumentUri(uri: Uri): String? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                
                when (uri.authority) {
                    "com.android.externalstorage.documents" -> {
                        val split = docId.split(":")
                        if (split.size >= 2) {
                            val type = split[0]
                            if ("primary".equals(type, ignoreCase = true)) {
                                "/storage/emulated/0/${split[1]}"
                            } else {
                                "/storage/$type/${split[1]}"
                            }
                        } else null
                    }
                    "com.android.providers.downloads.documents" -> {
                        val id = docId
                        if (id.startsWith("raw:")) {
                            id.substring(4)
                        } else {
                            val contentUri = android.content.ContentUris.withAppendedId(
                                android.net.Uri.parse("content://downloads/public_downloads"),
                                id.toLongOrNull() ?: return null
                            )
                            getDataColumn(contentUri, null, null)
                        }
                    }
                    "com.android.providers.media.documents" -> {
                        val split = docId.split(":")
                        if (split.size >= 2) {
                            val type = split[0]
                            val contentUri = when (type) {
                                "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                                else -> return null
                            }
                            val selection = "_id=?"
                            val selectionArgs = arrayOf(split[1])
                            getDataColumn(contentUri, selection, selectionArgs)
                        } else null
                    }
                    else -> null
                }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting path from document URI", e)
            null
        }
    }
    
    private fun getDataColumn(uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
        return try {
            val column = "_data"
            val projection = arrayOf(column)
            contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(column)
                    if (columnIndex >= 0) cursor.getString(columnIndex) else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting data column", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DLNACast.release()
    }
}
