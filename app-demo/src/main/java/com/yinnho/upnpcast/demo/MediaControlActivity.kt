package com.yinnho.upnpcast.demo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.yinnho.upnpcast.DLNACast

/**
 * 🎮 Media Control Interface - Complete playback control functionality
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
        
        supportActionBar?.title = "Media Control"
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
        // Play button
        btnPlay.setOnClickListener {
            val url = urlInput.text.toString().trim()
            val title = titleInput.text.toString().trim().ifEmpty { "Cast Video" }
            
            if (url.isNotEmpty()) {
                playMedia(url, title)
            } else {
                showToast("Please enter video URL")
            }
        }

        // 初始化音量显示
        updateVolumeDisplay()

        // Control buttons
        btnPause.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val success = DLNACast.pause()
                    runOnUiThread {
                        if (success) {
                            playbackStatus.text = "Paused"
                            showToast("Paused")
                        } else {
                            showToast("Pause failed")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { showToast("Pause error: ${e.message}") }
                }
            }
        }

        btnResume.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val success = DLNACast.play()
                    runOnUiThread {
                        if (success) {
                            playbackStatus.text = "Playing"
                            showToast("Resumed")
                        } else {
                            showToast("Resume failed")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { showToast("Resume error: ${e.message}") }
                }
            }
        }

        btnStop.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val success = DLNACast.stop()
                    runOnUiThread {
                        if (success) {
                            playbackStatus.text = "Stopped"
                            currentTime.text = "00:00"
                            seekBar.progress = 0
                            showToast("Stopped")
                        } else {
                            showToast("Stop failed")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { showToast("Stop error: ${e.message}") }
                }
            }
        }

        // Progress bar dragging
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
                    lifecycleScope.launch {
                        try {
                            val success = DLNACast.seek(targetPosition)
                            runOnUiThread {
                                if (success) {
                                    showToast("Seek to ${formatTime(targetPosition)}")
                                } else {
                                    showToast("Seek failed")
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread { showToast("Seek error: ${e.message}") }
                        }
                    }
                }
            }
        })

        // Volume control
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    volumeText.text = "🔊 音量: $progress%"
                    // 不在拖动过程中设置音量，避免重复调用
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 只在用户停止拖动时设置音量
                val volume = seekBar?.progress ?: 0
                setVolume(volume)
            }
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
    }

    private fun playMedia(url: String, title: String) {
        lifecycleScope.launch {
            try {
                val success = DLNACast.cast(url, title)
                runOnUiThread {
                    if (success) {
                        mediaTitle.text = title
                        playbackStatus.text = "Playing"
                        showToast("Playback started")
                    } else {
                        showToast("Playback failed")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { showToast("Playback error: ${e.message}") }
            }
        }
    }

    private fun updateDeviceInfo() {
        val state = DLNACast.getState()
        connectedDeviceName.text = if (state.isConnected) {
            "📺 已连接设备: ${state.currentDevice?.name ?: "未知"}"
        } else {
            "❌ 未连接设备"
        }
        
        // 主动获取实时音量信息
        if (state.isConnected) {
            lifecycleScope.launch {
                try {
                    val volumeInfo = DLNACast.getVolume()
                    runOnUiThread {
                        if (volumeInfo != null) {
                            val (volume, isMuted) = volumeInfo
                            if (volume != null) {
                                volumeSeekBar.progress = volume
                                val muteStatus = if (isMuted == true) " (静音)" else ""
                                volumeText.text = "🔊 音量: ${volume}%$muteStatus"
                            } else {
                                updateVolumeDisplay()
                            }
                        } else {
                            // 获取失败时使用缓存的状态
                            updateVolumeDisplay()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { updateVolumeDisplay() }
                }
            }
        } else {
            // 未连接时显示默认状态
            updateVolumeDisplay()
        }
    }

    private fun startProgressMonitoring() {
        progressHandler = Handler(Looper.getMainLooper())
        progressRunnable = object : Runnable {
            override fun run() {
                if (!isUserDragging) {
                    updateProgress()
                }
                progressHandler?.postDelayed(this, 1000)
            }
        }
        progressHandler?.post(progressRunnable!!)
    }

    private fun stopProgressMonitoring() {
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        progressRunnable = null
    }

    private fun updateProgress() {
        // 获取播放进度
        lifecycleScope.launch {
            try {
                val progressInfo = DLNACast.getProgress()
                runOnUiThread {
                    if (progressInfo != null) {
                        val (currentMs, totalMs) = progressInfo
                        totalDurationMs = totalMs
                        val progressPercent = if (totalMs > 0) (currentMs * 100 / totalMs).toInt() else 0
                        
                        // 只在用户没有拖动进度条时更新
                        if (!isUserDragging) {
                            seekBar.progress = progressPercent
                            currentTime.text = formatTime(currentMs)
                            totalTime.text = formatTime(totalMs)
                        }
                    } else {
                        // 进度获取失败时静默处理，避免过多提示
                    }
                    
                    // 同时更新状态和音量信息
                    updateStateAndVolume()
                }
            } catch (e: Exception) {
                runOnUiThread { updateStateAndVolume() }
            }
        }
    }
    
    private fun updateStateAndVolume() {
        // 获取当前播放状态
        val state = DLNACast.getState()
        
        // 更新播放状态显示
        playbackStatus.text = when (state.playbackState) {
            DLNACast.PlaybackState.PLAYING -> "🎬 播放中"
            DLNACast.PlaybackState.PAUSED -> "⏸️ 已暂停"
            DLNACast.PlaybackState.STOPPED -> "⏹️ 已停止"
            DLNACast.PlaybackState.BUFFERING -> "⏳ 缓冲中"
            DLNACast.PlaybackState.ERROR -> "❌ 错误"
            else -> "空闲"
        }
        
        // 偶尔获取实时音量信息（每10秒一次）
        if (state.isConnected && System.currentTimeMillis() % 10000 < 1000) {
            lifecycleScope.launch {
                try {
                    val volumeInfo = DLNACast.getVolume()
                    runOnUiThread {
                        if (volumeInfo != null) {
                            val (volume, isMuted) = volumeInfo
                            if (volume != null) {
                                volumeSeekBar.progress = volume
                                val muteStatus = if (isMuted == true) " (静音)" else ""
                                volumeText.text = "🔊 音量: ${volume}%$muteStatus"
                            } else {
                                updateVolumeDisplay()
                            }
                        } else {
                            updateVolumeDisplay()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { updateVolumeDisplay() }
                }
            }
        } else {
            // 其他时候使用缓存的音量信息
            updateVolumeDisplay()
        }
    }

    private fun updateVolumeDisplay() {
        val state = DLNACast.getState()
        if (state.volume >= 0) {
            volumeSeekBar.progress = state.volume
            val muteStatus = if (state.isMuted) " (静音)" else ""
            volumeText.text = "🔊 音量: ${state.volume}%$muteStatus"
        } else {
            volumeText.text = "🔊 音量: 未知"
        }
    }

    private fun setVolume(volume: Int) {
        lifecycleScope.launch {
            try {
                val success = DLNACast.setVolume(volume)
                runOnUiThread {
                    if (success) {
                        showToast("音量设置为: $volume%")
                        // 延迟更新显示，等待设备响应
                        Handler(Looper.getMainLooper()).postDelayed({
                            updateVolumeDisplay()
                        }, 500)
                    } else {
                        showToast("音量设置失败")
                        // 恢复原有音量显示
                        updateVolumeDisplay()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("音量设置异常: ${e.message}")
                    updateVolumeDisplay()
                }
            }
        }
    }



    private fun formatTime(timeMs: Long): String {
        val seconds = timeMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
} 