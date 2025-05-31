package com.yinnho.upnpcast.demo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// 直接使用库中的类
import com.yinnho.upnpcast.DLNACastManager
import com.yinnho.upnpcast.PlaybackStateListener
import com.yinnho.upnpcast.PlaybackState

class MediaControlActivity : AppCompatActivity() {
    
    private lateinit var dlnaCastManager: DLNACastManager
    private lateinit var seekBar: SeekBar
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var urlInput: EditText
    private lateinit var titleInput: EditText
    private lateinit var connectedDeviceName: TextView
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var volumeText: TextView
    
    // 示例视频URL
    private val sampleVideo1 = "https://sample-videos.com/video321/mp4/240/big_buck_bunny_240p_1mb.mp4"
    private val sampleVideo2 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
    
    // 当前连接的设备ID
    private var currentDeviceId: String? = null
    
    // 当前音量（0-100）
    private var currentVolume: Int = 50
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_control)
        
        // 从Intent获取设备信息
        currentDeviceId = intent.getStringExtra("device_id")
        val deviceName = intent.getStringExtra("device_name") ?: "未知设备"
        
        android.util.Log.d("MediaControlActivity", "接收到设备ID: $currentDeviceId, 设备名称: $deviceName")
        
        if (currentDeviceId == null) {
            Toast.makeText(this, "没有可用设备ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 初始化视图
        initViews()
        
        // 显示连接的设备名称
        connectedDeviceName.text = deviceName
        
        // 设置默认内容
        urlInput.setText(sampleVideo1)
        titleInput.setText("Big Buck Bunny (示例视频)")
        
        // 获取DLNACastManager实例
        dlnaCastManager = DLNACastManager.getInstance(applicationContext)
        
        // 设置按钮监听器
        setupButtonListeners()
        
        // 设置播放状态监听
        setupPlaybackStateMonitoring()
        
        // 设置进度条监听器
        setupSeekBarListeners()
    }
    
    private fun initViews() {
        titleText = findViewById(R.id.media_title)
        statusText = findViewById(R.id.playback_status)
        seekBar = findViewById(R.id.seek_bar)
        volumeSeekBar = findViewById(R.id.volume_seekbar)
        urlInput = findViewById(R.id.url_input)
        titleInput = findViewById(R.id.title_input)
        connectedDeviceName = findViewById(R.id.connected_device_name)
        currentTimeText = findViewById(R.id.current_time)
        totalTimeText = findViewById(R.id.total_time)
        volumeText = findViewById(R.id.volume_text)
        
        // 初始化音量
        volumeSeekBar.progress = currentVolume
        volumeText.text = "音量: ${currentVolume}%"
    }
    
    private fun setupButtonListeners() {
        // 播放控制按钮
        findViewById<Button>(R.id.btn_play).setOnClickListener {
            playMedia()
        }
        
        findViewById<Button>(R.id.btn_pause).setOnClickListener {
            dlnaCastManager.pausePlayback()
            statusText.text = "已暂停"
        }
        
        findViewById<Button>(R.id.btn_resume).setOnClickListener {
            dlnaCastManager.resumePlayback()
            statusText.text = "正在播放"
        }
        
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            dlnaCastManager.stopPlayback()
            statusText.text = "已停止"
            resetProgress()
        }
        
        // 示例URL按钮
        findViewById<Button>(R.id.btn_sample_video1).setOnClickListener {
            urlInput.setText(sampleVideo1)
            titleInput.setText("Big Buck Bunny (示例视频1)")
        }
        
        findViewById<Button>(R.id.btn_sample_video2).setOnClickListener {
            urlInput.setText(sampleVideo2)
            titleInput.setText("Elephants Dream (示例视频2)")
        }
        
        // 音量控制按钮
        findViewById<Button>(R.id.btn_volume_up).setOnClickListener {
            adjustVolume(10)
        }
        
        findViewById<Button>(R.id.btn_volume_down).setOnClickListener {
            adjustVolume(-10)
        }
    }
    
    private fun setupSeekBarListeners() {
        // 进度条控制
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    dlnaCastManager.seekTo(progress.toLong() * 1000) // 转换为毫秒
                    updateCurrentTime(progress * 1000L)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 音量滑块控制
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentVolume = progress
                    dlnaCastManager.setVolume(currentVolume)
                    volumeText.text = "音量: ${currentVolume}%"
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    /**
     * 设置播放状态监听
     */
    private fun setupPlaybackStateMonitoring() {
        dlnaCastManager.setPlaybackStateListener(object : PlaybackStateListener {
            override fun onPlaybackStateChanged(state: PlaybackState) {
                runOnUiThread {
                    statusText.text = when(state) {
                        PlaybackState.PLAYING -> "正在播放"
                        PlaybackState.PAUSED -> "已暂停"
                        PlaybackState.STOPPED -> "已停止"
                        PlaybackState.BUFFERING -> "缓冲中..."
                        else -> state.toString()
                    }
                }
            }
            
            override fun onPositionChanged(positionMs: Long, durationMs: Long) {
                runOnUiThread {
                    updateProgress(positionMs, durationMs)
                }
            }
        })
    }
    
    private fun updateProgress(positionMs: Long, durationMs: Long) {
        if (durationMs > 0) {
            // 更新进度条
            val durationSec = (durationMs / 1000).toInt()
            val positionSec = (positionMs / 1000).toInt()
            
            if (seekBar.max != durationSec && durationSec > 0) {
                seekBar.max = durationSec
            }
            
            seekBar.progress = positionSec
            
            // 更新时间显示
            updateCurrentTime(positionMs)
            updateTotalTime(durationMs)
        }
    }
    
    private fun updateCurrentTime(positionMs: Long) {
        currentTimeText.text = formatTime(positionMs)
    }
    
    private fun updateTotalTime(durationMs: Long) {
        totalTimeText.text = formatTime(durationMs)
    }
    
    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    private fun adjustVolume(delta: Int) {
        currentVolume = (currentVolume + delta).coerceIn(0, 100)
        volumeSeekBar.progress = currentVolume
        dlnaCastManager.setVolume(currentVolume)
        volumeText.text = "音量: ${currentVolume}%"
        Toast.makeText(this, "音量: ${currentVolume}%", Toast.LENGTH_SHORT).show()
    }
    
    private fun resetProgress() {
        seekBar.progress = 0
        currentTimeText.text = "00:00"
        totalTimeText.text = "00:00"
    }
    
    private fun playMedia() {
        val mediaUrl = urlInput.text.toString().trim()
        val title = titleInput.text.toString().trim()
        
        if (mediaUrl.isEmpty()) {
            Toast.makeText(this, "请输入视频链接", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (title.isEmpty()) {
            Toast.makeText(this, "请输入视频标题", Toast.LENGTH_SHORT).show()
            return
        }
        
        dlnaCastManager.playMedia(url = mediaUrl, title = title)
        
        titleText.text = title
        statusText.text = "正在播放"
        resetProgress()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 保持播放状态，不断开连接
    }
}
