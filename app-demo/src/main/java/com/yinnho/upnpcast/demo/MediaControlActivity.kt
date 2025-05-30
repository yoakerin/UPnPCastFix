package com.yinnho.upnpcast.demo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// 使用本地定义的简化版DLNACastManager
import com.yinnho.upnpcast.demo.DLNACastManager

class MediaControlActivity : AppCompatActivity() {
    
    private lateinit var dlnaCastManager: DLNACastManager
    private lateinit var seekBar: SeekBar
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var urlInput: EditText
    private lateinit var titleInput: EditText
    
    // 测试视频样本
    private val sampleVideoUrl = "https://sample-videos.com/video321/mp4/240/big_buck_bunny_240p_1mb.mp4"
    
    // 当前连接的设备ID
    private var currentDeviceId: String? = null
    
    // 当前音量（0-100）
    private var currentVolume: Int = 50
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_control)
        
        // 从Intent获取设备ID
        currentDeviceId = intent.getStringExtra("device_id")
        val deviceName = intent.getStringExtra("device_name") ?: "未知设备"
        
        android.util.Log.d("MediaControlActivity", "接收到设备ID: $currentDeviceId, 设备名称: $deviceName")
        
        if (currentDeviceId == null) {
            Toast.makeText(this, "没有可用设备ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 初始化视图
        titleText = findViewById(R.id.media_title)
        statusText = findViewById(R.id.playback_status)
        seekBar = findViewById(R.id.seek_bar)
        urlInput = findViewById(R.id.url_input)
        titleInput = findViewById(R.id.title_input)
        
        // 设置默认链接为测试视频
        urlInput.setText(sampleVideoUrl)
        titleInput.setText("Big Buck Bunny (测试视频)")
        
        // 获取DLNACastManager实例
        dlnaCastManager = DLNACastManager.getInstance(applicationContext)
        
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
            finish()
        }
        
        // 设置播放状态监听
        setupPlaybackStateMonitoring()
        
        // 进度条控制
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 仅当用户操作时才跳转
                if (fromUser) {
                    dlnaCastManager.seekTo(progress.toLong() * 1000) // 转换为毫秒
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 音量控制
        findViewById<Button>(R.id.btn_volume_up).setOnClickListener {
            // 音量增加10，最大100
            currentVolume = (currentVolume + 10).coerceAtMost(100)
            dlnaCastManager.setVolume(currentVolume)
            Toast.makeText(this, "音量: $currentVolume%", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btn_volume_down).setOnClickListener {
            // 音量减少10，最小0
            currentVolume = (currentVolume - 10).coerceAtLeast(0)
            dlnaCastManager.setVolume(currentVolume)
            Toast.makeText(this, "音量: $currentVolume%", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 设置播放状态监听
     * 监控并更新UI中的播放状态和进度
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
                    if (durationMs > 0) {
                        // 动态设置进度条最大值为总时长(秒)
                        val durationSec = (durationMs / 1000).toInt()
                        if (seekBar.max != durationSec && durationSec > 0) {
                            seekBar.max = durationSec
                        }
                        
                        // 更新当前进度
                        seekBar.progress = (positionMs / 1000).toInt()
                    }
                }
            }
        })
    }
    
    private fun playMedia() {
        // 使用输入框中的URL和标题
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
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 不要在这里断开连接，以便返回主界面后保持播放
    }
}
