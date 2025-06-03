package com.yinnho.upnpcast.demo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.yinnho.upnpcast.DLNACast

/**
 * 🎮 媒体控制界面 - 完整的播放控制功能
 */
class MediaControlActivity : AppCompatActivity() {

    companion object {
        fun start(activity: AppCompatActivity) {
            val intent = Intent(activity, MediaControlActivity::class.java)
            activity.startActivity(intent)
        }
    }

    private lateinit var connectedDeviceName: TextView
    private lateinit var urlInput: EditText
    private lateinit var titleInput: EditText
    private lateinit var mediaTitle: TextView
    private lateinit var playbackStatus: TextView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnResume: Button
    private lateinit var btnStop: Button
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeText: TextView
    
    private var totalDurationMs: Long = 0
    private var isUserDragging = false
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_control)
        
        supportActionBar?.title = "媒体控制"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        initViews()
        setupListeners()
        setupExampleVideos()
        updateDeviceInfo()
        startProgressMonitoring()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressMonitoring()
    }

    private fun initViews() {
        connectedDeviceName = findViewById(R.id.connected_device_name)
        urlInput = findViewById(R.id.url_input)
        titleInput = findViewById(R.id.title_input)
        mediaTitle = findViewById(R.id.media_title)
        playbackStatus = findViewById(R.id.playback_status)
        currentTime = findViewById(R.id.current_time)
        totalTime = findViewById(R.id.total_time)
        seekBar = findViewById(R.id.seek_bar)
        btnPlay = findViewById(R.id.btn_play)
        btnPause = findViewById(R.id.btn_pause)
        btnResume = findViewById(R.id.btn_resume)
        btnStop = findViewById(R.id.btn_stop)
        volumeSeekBar = findViewById(R.id.volume_seekbar)
        volumeText = findViewById(R.id.volume_text)
    }

    private fun setupListeners() {
        // 播放按钮
        btnPlay.setOnClickListener {
            val url = urlInput.text.toString().trim()
            val title = titleInput.text.toString().trim().ifEmpty { "投屏视频" }
            
            if (url.isNotEmpty()) {
                playMedia(url, title)
            } else {
                showToast("请输入视频链接")
            }
        }

        // 控制按钮
        btnPause.setOnClickListener {
            DLNACast.control(DLNACast.MediaAction.PAUSE) { success ->
                runOnUiThread {
                    if (success) {
                        playbackStatus.text = "已暂停"
                        showToast("已暂停")
                    } else {
                        showToast("暂停失败")
                    }
                }
            }
        }

        btnResume.setOnClickListener {
            DLNACast.control(DLNACast.MediaAction.PLAY) { success ->
                runOnUiThread {
                    if (success) {
                        playbackStatus.text = "播放中"
                        showToast("继续播放")
                    } else {
                        showToast("继续播放失败")
                    }
                }
            }
        }

        btnStop.setOnClickListener {
            DLNACast.control(DLNACast.MediaAction.STOP) { success ->
                runOnUiThread {
                    if (success) {
                        playbackStatus.text = "已停止"
                        currentTime.text = "00:00"
                        seekBar.progress = 0
                        showToast("已停止")
                    } else {
                        showToast("停止失败")
                    }
                }
            }
        }

        // 进度条拖拽
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && totalDurationMs > 0) {
                    val targetPosition = (progress * totalDurationMs / 100).toLong()
                    currentTime.text = formatTime(targetPosition)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserDragging = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserDragging = false
                val progress = seekBar?.progress ?: 0
                if (totalDurationMs > 0) {
                    val targetPosition = (progress * totalDurationMs / 100).toLong()
                    DLNACast.control(DLNACast.MediaAction.SEEK, targetPosition) { success ->
                        runOnUiThread {
                            if (success) {
                                showToast("跳转到 ${formatTime(targetPosition)}")
                            } else {
                                showToast("跳转失败")
                            }
                        }
                    }
                }
            }
        })

        // 音量控制
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    volumeText.text = "音量: $progress%"
                    DLNACast.control(DLNACast.MediaAction.VOLUME, progress) { success ->
                        if (!success) {
                            runOnUiThread {
                                showToast("音量设置失败")
                            }
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupExampleVideos() {
        findViewById<Button>(R.id.btn_sample_video1).setOnClickListener {
            urlInput.setText("http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
            titleInput.setText("Big Buck Bunny")
        }

        findViewById<Button>(R.id.btn_sample_video2).setOnClickListener {
            urlInput.setText("http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4")
            titleInput.setText("Elephants Dream")
        }
        
        // 添加本地文件投屏按钮
        addLocalFileButton()
    }
    
    private fun addLocalFileButton() {
        // 在示例按钮区域动态添加本地文件按钮
        val sampleLayout = findViewById<LinearLayout>(R.id.sample_urls_layout)
        
        val localFileButton = Button(this).apply {
            text = "📁 本地文件"
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener { showLocalFileDialog() }
        }
        
        sampleLayout.addView(localFileButton)
    }
    
    private fun showLocalFileDialog() {
        // 提供直接文件路径访问方式
        val options = arrayOf(
            "📹 DCIM/Camera 文件夹",
            "📁 Download 文件夹", 
            "🎵 Music 文件夹",
            "✏️ 手动输入路径"
        )
        
        AlertDialog.Builder(this)
            .setTitle("选择本地文件")
            .setMessage("基于直接文件路径的本地投屏")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> browseFolder("/storage/emulated/0/DCIM/Camera/")
                    1 -> browseFolder("/storage/emulated/0/Download/")
                    2 -> browseFolder("/storage/emulated/0/Music/")
                    3 -> showManualPathInput()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun browseFolder(folderPath: String) {
        try {
            val folder = java.io.File(folderPath)
            if (!folder.exists() || !folder.isDirectory) {
                showToast("文件夹不存在: $folderPath")
                return
            }
            
            val files = folder.listFiles { file ->
                file.isFile && isMediaFile(file.name)
            }?.sortedBy { it.name } ?: emptyList()
            
            if (files.isEmpty()) {
                showToast("该文件夹中没有媒体文件")
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
                    useSelectedFile(selectedFile.absolutePath, selectedFile.name)
                }
                .setNegativeButton("返回", null)
                .show()
                
        } catch (e: Exception) {
            showToast("浏览文件夹失败: ${e.message}")
        }
    }
    
    private fun isMediaFile(fileName: String): Boolean {
        val name = fileName.lowercase()
        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
               name.endsWith(".mov") || name.endsWith(".mp3") || name.endsWith(".aac") ||
               name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".jpg") ||
               name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")
    }
    
    private fun useSelectedFile(filePath: String, fileName: String) {
        AlertDialog.Builder(this)
            .setTitle("使用选中的文件")
            .setMessage("文件: $fileName\n路径: $filePath")
            .setPositiveButton("生成URL") { _, _ ->
                val fileUrl = DLNACast.getLocalFileUrl(filePath)
                if (fileUrl != null) {
                    urlInput.setText(fileUrl)
                    titleInput.setText(fileName)
                    showToast("已生成本地文件URL")
                } else {
                    showToast("无法生成文件URL")
                }
            }
            .setNeutralButton("直接投屏") { _, _ ->
                castLocalFileDirectly(filePath)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun castLocalFileDirectly(filePath: String) {
        playbackStatus.text = "本地文件投屏中..."
        val fileName = java.io.File(filePath).name
        
        DLNACast.castLocalFile(filePath, fileName) { success, message ->
            runOnUiThread {
                if (success) {
                    mediaTitle.text = fileName
                    playbackStatus.text = "播放中"
                    showToast("本地文件投屏成功")
                } else {
                    playbackStatus.text = "投屏失败"
                    showToast("投屏失败: $message")
                }
            }
        }
    }
    
    private fun showManualPathInput() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        
        val pathInput = EditText(this).apply {
            hint = "输入本地文件路径"
            setText("/storage/emulated/0/")
        }
        
        val tipText = TextView(this).apply {
            text = """
                💡 本地文件投屏特性：
                • 自动启动HTTP文件服务器
                • 支持大文件Range请求
                • 最佳设备兼容性
                
                示例路径：
                • /storage/emulated/0/DCIM/Camera/video.mp4
                • /storage/emulated/0/Download/movie.mkv
                • /storage/emulated/0/Music/music.mp3
            """.trimIndent()
            textSize = 12f
            setTextColor("#666666".toColorInt())
            setPadding(0, 10, 0, 0)
        }
        
        layout.addView(TextView(this).apply { 
            text = "本地文件路径:" 
            textSize = 14f
            setPadding(0, 0, 0, 5)
        })
        layout.addView(pathInput)
        layout.addView(tipText)
        
        AlertDialog.Builder(this)
            .setTitle("手动输入文件路径")
            .setView(layout)
            .setPositiveButton("使用此路径") { _, _ ->
                val path = pathInput.text.toString().trim()
                if (path.isNotEmpty()) {
                    val fileName = java.io.File(path).name
                    useSelectedFile(path, fileName)
                } else {
                    showToast("请输入有效路径")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun playMedia(url: String, title: String) {
        playbackStatus.text = "连接中..."
        
        DLNACast.cast(url, title) { success ->
            runOnUiThread {
                if (success) {
                    mediaTitle.text = title
                    playbackStatus.text = "播放中"
                    showToast("投屏成功")
                } else {
                    playbackStatus.text = "投屏失败"
                    showToast("投屏失败，请检查设备连接")
                }
            }
        }
    }

    private fun updateDeviceInfo() {
        val state = DLNACast.getState()
        val currentDevice = state.currentDevice
        if (state.isConnected && currentDevice != null) {
            connectedDeviceName.text = currentDevice.name
        } else {
            connectedDeviceName.text = "未连接设备"
        }
    }

    private fun startProgressMonitoring() {
        progressHandler = Handler(Looper.getMainLooper())
        progressRunnable = object : Runnable {
            override fun run() {
                if (!isUserDragging) {
                    DLNACast.getProgress { currentMs, totalMs, success ->
                        runOnUiThread {
                            if (success && totalMs > 0) {
                                totalDurationMs = totalMs
                                val progressPercent = (currentMs * 100 / totalMs).toInt()
                                
                                currentTime.text = formatTime(currentMs)
                                totalTime.text = formatTime(totalMs)
                                seekBar.progress = progressPercent
                            }
                        }
                    }
                }
                progressHandler?.postDelayed(this, 1000) // 每秒更新一次
            }
        }
        progressHandler?.post(progressRunnable!!)
    }

    private fun stopProgressMonitoring() {
        progressRunnable?.let { runnable ->
            progressHandler?.removeCallbacks(runnable)
        }
        progressHandler = null
        progressRunnable = null
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
} 