package com.yinnho.upnpcast.demo

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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.yinnho.upnpcast.DLNACast
import com.yinnho.upnpcast.R

/**
 * Demo video selector - shows how to implement UI using library APIs
 */
class VideoSelectorActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "VideoSelectorDemo"
        private const val PERMISSION_REQUEST_CODE = 1001
        
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_DEVICE_IS_TV = "device_is_tv"
        
        /**
         * Launch video selector
         */
        fun start(context: Context, device: DLNACast.Device) {
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
        super.onCreate(savedInstanceState)
        
        // Get device information
        targetDevice = getDeviceFromIntent()
        
        if (targetDevice == null) {
            Toast.makeText(this, "设备信息错误", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        createLayout()
        
        // Check permissions
        if (checkPermissions()) {
            startVideoScan()
        }
    }
    
    private fun createLayout() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(20, 20, 20, 20)
        
        // Title
        val titleText = TextView(this)
        titleText.text = "选择视频文件"
        titleText.textSize = 20f
        titleText.setPadding(0, 0, 0, 20)
        layout.addView(titleText)
        
        // Device information
        val deviceText = TextView(this)
        deviceText.text = "目标设备: ${targetDevice?.name ?: ""}"
        deviceText.textSize = 16f
        deviceText.setPadding(0, 0, 0, 20)
        layout.addView(deviceText)
        
        // Status text
        statusText = TextView(this)
        statusText.text = "正在扫描视频文件..."
        statusText.textSize = 14f
        statusText.setPadding(0, 0, 0, 10)
        layout.addView(statusText)
        
        // Scan button
        scanButton = Button(this)
        scanButton.text = "重新扫描"
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
        castButton.text = "请选择视频"
        castButton.isEnabled = false
        castButton.setOnClickListener { startCasting() }
        layout.addView(castButton)
        
        // Back button
        val backButton = Button(this)
        backButton.text = "返回"
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
                Toast.makeText(this, "需要存储权限才能扫描视频文件", Toast.LENGTH_LONG).show()
                statusText.text = "权限被拒绝"
            }
        }
    }
    
    private fun startVideoScan() {
        statusText.text = "正在扫描视频文件..."
        videoListLayout.removeAllViews()
        
        // Add scanning indicator
        val scanningText = TextView(this)
        scanningText.text = "正在扫描设备中的视频文件..."
        scanningText.textSize = 14f
        scanningText.setPadding(10, 10, 10, 10)
        videoListLayout.addView(scanningText)
        
        scanButton.isEnabled = false
        
        // Use library API to scan videos
        lifecycleScope.launch {
            try {
                val videos = DLNACast.scanLocalVideos(this@VideoSelectorActivity)
                runOnUiThread {
                    videoList.clear()
                    videoList.addAll(videos)
                    
                    scanButton.isEnabled = true
                    
                    if (videos.isEmpty()) {
                        statusText.text = "未找到视频文件"
                        displayNoVideos()
                    } else {
                        statusText.text = "找到 ${videos.size} 个视频文件"
                        displayVideoList()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    scanButton.isEnabled = true
                    statusText.text = "扫描失败: ${e.message}"
                    displayNoVideos()
                }
            }
        }
    }
    
    private fun displayVideoList() {
        videoListLayout.removeAllViews()
        
        videoList.forEachIndexed { index, video ->
            val button = Button(this)
            button.text = "${index + 1}. ${video.title}"
            button.setOnClickListener {
                selectedVideo = video
                castButton.isEnabled = true
                castButton.text = "投屏: ${selectedVideo?.title ?: ""}"
            }
            videoListLayout.addView(button)
        }
    }
    
    private fun displayNoVideos() {
        val noVideosText = TextView(this)
        noVideosText.text = "未找到视频文件。请确保设备上有支持的视频文件（MP4、MKV、AVI等）。"
        noVideosText.textSize = 12f
        noVideosText.setPadding(10, 10, 10, 10)
        noVideosText.setBackgroundColor("#F5F5F5".toColorInt())
        videoListLayout.addView(noVideosText)
    }
    
    private fun startCasting() {
        val video = selectedVideo ?: return
        val device = targetDevice ?: return
        
        castButton.isEnabled = false
        castButton.text = "正在投屏..."
        
        lifecycleScope.launch {
            try {
                DLNACast.castLocalFile(video.path, device, video.title)
                runOnUiThread {
                    Toast.makeText(this@VideoSelectorActivity, "投屏成功", Toast.LENGTH_SHORT).show()
                    castButton.text = "投屏成功"
                    
                    // Auto return after 2 seconds
                    castButton.postDelayed({
                        finish()
                    }, 2000)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    val userMessage = when (error) {
                        is com.yinnho.upnpcast.internal.UPnPException.NetworkError -> "网络连接失败，请检查网络"
                        is com.yinnho.upnpcast.internal.UPnPException.FileError -> "文件访问失败，请检查文件"
                        is com.yinnho.upnpcast.internal.UPnPException.DeviceError -> "设备连接失败，请重新选择设备"
                        else -> "投屏失败，请稍后重试"
                    }
                    
                    Toast.makeText(this@VideoSelectorActivity, userMessage, Toast.LENGTH_LONG).show()
                    castButton.isEnabled = true
                    castButton.text = "重新投屏"
                    Log.e(TAG, "Cast failed: ${error.message}")
                }
            }
        }
    }
} 