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

        findViewById<Button>(R.id.btn_sample_video3).setOnClickListener {
            urlInput.setText("http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4")
            titleInput.setText("Sintel")
        }

        // Add local file cast button
        findViewById<Button>(R.id.btn_local_file).setOnClickListener {
            showLocalFileDialog()
        }
    }

    private fun showLocalFileDialog() {
        val options = arrayOf(
            "Browse Camera folder",
            "Browse Download folder",
            "Browse Music folder",
            "Manual input path"
        )

        AlertDialog.Builder(this)
            .setTitle("Select Local File Cast Method")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> browseFolder("/storage/emulated/0/DCIM/Camera/")
                    1 -> browseFolder("/storage/emulated/0/Download/")
                    2 -> browseFolder("/storage/emulated/0/Music/")
                    3 -> showFilePathInputDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun browseFolder(folderPath: String) {
        try {
            val folder = java.io.File(folderPath)
            if (!folder.exists() || !folder.isDirectory) {
                showToast("Folder does not exist: $folderPath")
                return
            }

            val files = folder.listFiles { file ->
                file.isFile && isMediaFile(file.name)
            }?.sortedBy { it.name } ?: emptyList()

            if (files.isEmpty()) {
                showToast("No media files found in this folder")
                return
            }

            val fileNames = files.map { file ->
                val icon = getFileIcon(file.name.lowercase())
                "$icon ${file.name}"
            }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Select File (${files.size} files)")
                .setItems(fileNames) { _, which ->
                    val selectedFile = files[which]
                    castLocalFile(selectedFile.absolutePath, selectedFile.nameWithoutExtension)
                }
                .setNegativeButton("Back", null)
                .show()

        } catch (e: Exception) {
            showToast("Failed to browse folder: ${e.message}")
        }
    }

    private fun showFilePathInputDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val pathInput = EditText(this).apply {
            hint = "Enter complete file path"
            setText("/storage/emulated/0/")
        }

        val titleInput = EditText(this).apply {
            hint = "File title (optional)"
        }

        layout.addView(TextView(this).apply {
            text = "File path:"
            setPadding(0, 0, 0, 5)
        })
        layout.addView(pathInput)

        layout.addView(TextView(this).apply {
            text = "Title:"
            setPadding(0, 15, 0, 5)
        })
        layout.addView(titleInput)

        AlertDialog.Builder(this)
            .setTitle("Local File Cast")
            .setView(layout)
            .setPositiveButton("Cast") { _, _ ->
                val path = pathInput.text.toString().trim()
                val title = titleInput.text.toString().trim().ifEmpty { "Local File" }

                if (path.isNotEmpty()) {
                    castLocalFile(path, title)
                } else {
                    showToast("Path cannot be empty")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isMediaFile(fileName: String): Boolean {
        val name = fileName.lowercase()
        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
               name.endsWith(".mov") || name.endsWith(".mp3") || name.endsWith(".aac") ||
               name.endsWith(".flac") || name.endsWith(".wav")
    }

    private fun getFileIcon(fileName: String): String {
        return when {
            fileName.endsWith(".mp4") || fileName.endsWith(".mkv") || fileName.endsWith(".avi") || fileName.endsWith(".mov") -> "🎬"
            fileName.endsWith(".mp3") || fileName.endsWith(".aac") || fileName.endsWith(".flac") || fileName.endsWith(".wav") -> "🎵"
            else -> "📄"
        }
    }

    private fun castLocalFile(filePath: String, title: String) {
        val file = java.io.File(filePath)
        if (!file.exists()) {
            showToast("File does not exist: $filePath")
            return
        }

        DLNACast.castLocalFile(filePath, title) { success, message ->
            runOnUiThread {
                if (success) {
                    mediaTitle.text = title
                    playbackStatus.text = "Playing"
                    showToast("Local file cast successful")
                } else {
                    showToast("Cast failed: $message")
                }
            }
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