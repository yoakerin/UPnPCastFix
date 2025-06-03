package com.yinnho.upnpcast.internal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.yinnho.upnpcast.DLNACast
import com.yinnho.upnpcast.R

/**
 * Built-in local video selector - full feature version
 */
class VideoSelectorActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "VideoSelectorLib"
        private const val PERMISSION_REQUEST_CODE = 1001
        
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_IS_TV = "device_is_tv"
        
        /**
         * Launch video selector
         */
        fun start(context: Context, device: DLNACast.Device) {
            Log.d(TAG, "Starting VideoSelectorActivity, device: ${device.name}")
            val intent = Intent(context, VideoSelectorActivity::class.java)
            intent.putExtra(EXTRA_DEVICE_ID, device.id)
            intent.putExtra(EXTRA_DEVICE_NAME, device.name)
            intent.putExtra(EXTRA_DEVICE_ADDRESS, device.address)
            intent.putExtra(EXTRA_DEVICE_IS_TV, device.isTV)
            context.startActivity(intent)
        }
    }
    
    private lateinit var statusText: TextView
    private lateinit var videoListLayout: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var castButton: Button
    
    private val videoList = mutableListOf<DLNACast.LocalVideo>()
    private var selectedVideo: DLNACast.LocalVideo? = null
    private var targetDevice: DLNACast.Device? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "VideoSelectorActivity onCreate started")
        super.onCreate(savedInstanceState)
        
        // Get device information
        targetDevice = getDeviceFromIntent()
        
        if (targetDevice == null) {
            Toast.makeText(this, getString(R.string.device_info_error), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        createLayout()
        
        // Check permissions
        if (checkPermissions()) {
            startVideoScan()
        }
        
        Log.d(TAG, "VideoSelectorActivity onCreate completed")
    }
    
    private fun createLayout() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(20, 20, 20, 20)
        
        // Title
        val titleText = TextView(this)
        titleText.text = getString(R.string.video_selector_title)
        titleText.textSize = 20f
        titleText.setPadding(0, 0, 0, 20)
        layout.addView(titleText)
        
        // Device information
        val deviceText = TextView(this)
        deviceText.text = getString(R.string.target_device, targetDevice?.name ?: "")
        deviceText.textSize = 16f
        deviceText.setPadding(0, 0, 0, 20)
        layout.addView(deviceText)
        
        // Status text
        statusText = TextView(this)
        statusText.text = getString(R.string.scanning_videos)
        statusText.textSize = 14f
        statusText.setPadding(0, 0, 0, 10)
        layout.addView(statusText)
        
        // Scan button
        scanButton = Button(this)
        scanButton.text = getString(R.string.rescan)
        scanButton.setOnClickListener { startVideoScan() }
        layout.addView(scanButton)
        
        // Video list
        videoListLayout = LinearLayout(this)
        videoListLayout.orientation = LinearLayout.VERTICAL
        videoListLayout.setPadding(10, 10, 10, 10)
        videoListLayout.setBackgroundColor("#F5F5F5".toColorInt())
        layout.addView(videoListLayout)
        
        // Cast button
        castButton = Button(this)
        castButton.text = getString(R.string.please_select_video)
        castButton.isEnabled = false
        castButton.setOnClickListener { startCasting() }
        layout.addView(castButton)
        
        // Back button
        val backButton = Button(this)
        backButton.text = getString(R.string.back)
        backButton.setOnClickListener { finish() }
        layout.addView(backButton)
        
        scrollView.addView(layout)
        setContentView(scrollView)
    }
    
    private fun getDeviceFromIntent(): DLNACast.Device? {
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
        val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        val isTV = intent.getBooleanExtra(EXTRA_DEVICE_IS_TV, false)
        
        return if (deviceId != null && deviceName != null && deviceAddress != null) {
            DLNACast.Device(deviceId, deviceName, deviceAddress, isTV)
        } else {
            null
        }
    }
    
    private fun checkPermissions(): Boolean {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
            return false
        }
        
        return true
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startVideoScan()
            } else {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
                statusText.text = getString(R.string.permission_denied)
            }
        }
    }
    
    private fun startVideoScan() {
        statusText.text = getString(R.string.scanning_videos)
        videoListLayout.removeAllViews()
        
        // Add scanning indicator
        val scanningText = TextView(this)
        scanningText.text = getString(R.string.scanning_device_files)
        scanningText.textSize = 14f
        scanningText.setPadding(10, 10, 10, 10)
        videoListLayout.addView(scanningText)
        
        scanButton.isEnabled = false
        
        // Use library API to scan videos
        DLNACast.scanLocalVideos(this) { videos ->
            runOnUiThread {
                videoList.clear()
                videoList.addAll(videos)
                
                scanButton.isEnabled = true
                
                if (videos.isEmpty()) {
                    statusText.text = getString(R.string.no_videos_found)
                    displayNoVideos()
                } else {
                    statusText.text = getString(R.string.found_videos, videos.size)
                    displayVideoList()
                }
            }
        }
    }
    
    private fun displayVideoList() {
        videoListLayout.removeAllViews()
        
        videoList.forEachIndexed { index, video ->
            val button = Button(this)
            button.text = getString(R.string.video_item, index + 1, video.title)
            button.setOnClickListener {
                selectedVideo = video
                castButton.isEnabled = true
                castButton.text = getString(R.string.cast_video, selectedVideo?.title ?: "")
            }
            videoListLayout.addView(button)
        }
    }
    
    private fun displayNoVideos() {
        val noVideosText = TextView(this)
        noVideosText.text = getString(R.string.no_videos_message)
        noVideosText.textSize = 12f
        noVideosText.setPadding(10, 10, 10, 10)
        noVideosText.setBackgroundColor("#F5F5F5".toColorInt())
        videoListLayout.addView(noVideosText)
    }
    
    private fun startCasting() {
        val video = selectedVideo ?: return
        val device = targetDevice ?: return
        
        castButton.isEnabled = false
        castButton.text = getString(R.string.casting)
        
        Log.d(TAG, "Starting to cast local file: ${video.path}")
        
        DLNACast.castLocalFile(video.path, device, video.title) { success, message ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, getString(R.string.cast_successful), Toast.LENGTH_SHORT).show()
                    castButton.text = getString(R.string.cast_successful_button)
                    
                    // Auto return after 2 seconds
                    castButton.postDelayed({
                        finish()
                    }, 2000)
                } else {
                    Toast.makeText(this, getString(R.string.cast_failed, message), Toast.LENGTH_LONG).show()
                    castButton.isEnabled = true
                    castButton.text = getString(R.string.retry_cast)
                    Log.e(TAG, "Cast failed: $message")
                }
            }
        }
    }
} 