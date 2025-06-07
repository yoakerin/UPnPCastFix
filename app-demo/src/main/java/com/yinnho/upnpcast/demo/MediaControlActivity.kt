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

        // Control buttons
        btnPause.setOnClickListener {
            DLNACast.control(DLNACast.MediaAction.PAUSE) { success ->
                runOnUiThread {
                    if (success) {
                        playbackStatus.text = "Paused"
                        showToast("Paused")
                    } else {
                        showToast("Pause failed")
                    }
                }
            }
        }

        btnResume.setOnClickListener {
            DLNACast.control(DLNACast.MediaAction.PLAY) { success ->
                runOnUiThread {
                    if (success) {
                        playbackStatus.text = "Playing"
                        showToast("Resumed")
                    } else {
                        showToast("Resume failed")
                    }
                }
            }
        }

        btnStop.setOnClickListener {
            DLNACast.control(DLNACast.MediaAction.STOP) { success ->
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
                    DLNACast.control(DLNACast.MediaAction.SEEK, targetPosition) { success ->
                        runOnUiThread {
                            if (success) {
                                showToast("Seek to ${formatTime(targetPosition)}")
                            } else {
                                showToast("Seek failed")
                            }
                        }
                    }
                }
            }
        })

        // Volume control
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    volumeText.text = "Volume: $progress%"
                    DLNACast.control(DLNACast.MediaAction.VOLUME, progress) { success ->
                        if (!success) {
                            runOnUiThread {
                                showToast("Volume setting failed")
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
    }

    private fun playMedia(url: String, title: String) {
        DLNACast.cast(url, title) { success ->
            runOnUiThread {
                if (success) {
                    mediaTitle.text = title
                    playbackStatus.text = "Playing"
                    showToast("Playback started")
                } else {
                    showToast("Playback failed")
                }
            }
        }
    }

    private fun updateDeviceInfo() {
        val state = DLNACast.getState()
        connectedDeviceName.text = if (state.isConnected) {
            "Connected Device: ${state.currentDevice?.name ?: "Unknown"}"
        } else {
            "No device connected"
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
        DLNACast.getProgress { currentMs, totalMs, success ->
            runOnUiThread {
                if (success && totalMs > 0) {
                    totalDurationMs = totalMs
                    val progress = ((currentMs * 100) / totalMs).toInt()
                    
                    seekBar.progress = progress
                    currentTime.text = formatTime(currentMs)
                    totalTime.text = formatTime(totalMs)
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