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

        // Title
        val titleView = TextView(this).apply {
            text = "📚 DLNACast API Demo"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor("#333333".toColorInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(titleView)

        // API demo buttons
        val buttons = listOf(
            "🔍 Demo Search API" to { demoSearch() },
            "🎯 Demo Smart Cast API" to { demoCastTo() },
            "🎮 Demo Control API" to { demoControl() },
            "📊 Demo State API" to { demoGetState() },
            "⏱️ Demo Progress API" to { demoGetProgress() },
            "🔊 Demo Volume Get API" to { demoGetVolume() },
            "⚡ Demo Cache Management API" to { demoCacheManagement() },
            "📁 Demo Local File Cast API" to { demoLocalFileCast() },
            "🔊 Demo Volume Control" to { demoVolumeControl() }
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

        // Log display area
        val logTitle = TextView(this).apply {
            text = "📝 API Call Logs:"
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
        logSectionHeader("🔍 Device Search API Demo")
        logApiCall("DLNACast.search(timeout = 5000) { devices ->")
        logDetail("Parameters: timeout = 5 seconds")
        logDetail("Callback: Real-time return of cumulative full device list")
        
        val startTime = System.currentTimeMillis()
        DLNACast.search(timeout = 5000) { devices: List<DLNACast.Device> ->
            runOnUiThread {
                val elapsed = System.currentTimeMillis() - startTime
                logDetail("⏰ Real-time update, elapsed: ${elapsed}ms")
                logDetail("📱 Current total devices: ${devices.size}")
                
                devices.forEachIndexed { index, device ->
                    val icon = if (device.isTV) "📺" else "📱"
                    logDetail("  ${index + 1}. $icon ${device.name} (${device.address})")
                }
                
                if (devices.isEmpty()) {
                    logTip("Make sure there are DLNA devices on the same network")
                }
            }
        }
        
        logSuccess("Search request sent, waiting for real-time updates...")
    }

    private fun demoCastTo() {
        logSectionHeader("🎯 Smart Cast API Demo")
        logDetail("Function: Automatically select best device for casting")
        
        val testUrl = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        logDetail("Test URL: $testUrl")
        logApiCall("DLNACast.smartCast(url, title, callback) { devices ->")
        
        DLNACast.smartCast(testUrl, "Smart Cast Demo", { success ->
            runOnUiThread {
                logResult("🎯 Cast", success)
            }
        }) { devices: List<DLNACast.Device> ->
            runOnUiThread {
                logDetail("📱 Available devices: ${devices.size}")
                logDetail("🤖 Device selection logic: Prefer TV devices")
                
                val selectedDevice = devices.find { it.isTV } ?: devices.firstOrNull()
                if (selectedDevice != null) {
                    logSuccess("Selected: ${selectedDevice.name}")
                } else {
                    logError("No available devices found")
                }
            }
            
            // Return selected device
            devices.find { it.isTV } ?: devices.firstOrNull()
        }
    }

    private fun demoControl() {
        logSectionHeader("🎮 Media Control API Demo")
        
        val controls = arrayOf("Play", "Pause", "Stop", "Seek (30s)", "Get State", "Mute", "Volume Control")
        
        AlertDialog.Builder(this)
            .setTitle("Select Control Action")
            .setItems(controls) { _, which ->
                when (which) {
                    0 -> demoControlAction(DLNACast.MediaAction.PLAY, "Play")
                    1 -> demoControlAction(DLNACast.MediaAction.PAUSE, "Pause")
                    2 -> demoControlAction(DLNACast.MediaAction.STOP, "Stop")
                    3 -> demoSeekControl()
                    4 -> demoControlAction(DLNACast.MediaAction.GET_STATE, "Get State")
                    5 -> demoControlAction(DLNACast.MediaAction.MUTE, "Mute", true)
                    6 -> demoVolumeControl()
                }
            }
            .show()
    }

    private fun demoControlAction(action: DLNACast.MediaAction, actionName: String, value: Any? = null) {
        logDetail("🎮 Control action: $actionName")
        logApiCall("DLNACast.control($action, $value)")
        
        DLNACast.control(action, value) { success ->
            runOnUiThread {
                logResult(actionName, success)
            }
        }
    }

    private fun demoVolumeControl() {
        logSectionHeader("🔊 Volume Control API Demo")
        logDetail("Set volume to 50%")
        
        val volume = 50
        DLNACast.control(DLNACast.MediaAction.VOLUME, volume) { success ->
            runOnUiThread {
                logResult("Volume Setting", success)
                logDetail("Target volume: $volume%")
            }
        }
    }
    
    private fun demoSeekControl() {
        logSectionHeader("⏩ Seek Control API Demo")
        logDetail("Seek to 30 seconds position")
        
        val positionMs = 30 * 1000L // Convert 30 seconds to milliseconds
        DLNACast.control(DLNACast.MediaAction.SEEK, positionMs) { success ->
            runOnUiThread {
                logResult("Seek", success)
                logDetail("Target position: 30 seconds")
            }
        }
    }

    private fun demoGetState() {
        logSectionHeader("📊 State Get API Demo")
        logApiCall("DLNACast.getState()")
        
        val state = DLNACast.getState()
        logStatus("Connection Status", state.isConnected)
        logDetail("Current device: ${state.currentDevice?.name ?: "None"}")
        logDetail("Playback state: ${state.playbackState}")
        logVolumeInfo("Volume", state.volume)
        logMuteInfo("Mute", state.isMuted)
        
        logDetail("Convenience state:")
        logDetail("  isPlaying: ${state.isPlaying}")
        logDetail("  isPaused: ${state.isPaused}")
        logDetail("  isIdle: ${state.isIdle}")
    }

    private fun demoGetProgress() {
        logSectionHeader("⏱️ Progress Get API Demo")
        logApiCall("DLNACast.getProgress { currentMs, totalMs, success ->")
        
        DLNACast.getProgress { currentMs, totalMs, success ->
            runOnUiThread {
                if (success) {
                    logProgressInfo(currentMs, totalMs)
                } else {
                    logError("Failed to get playback progress")
                    logTip("Need to cast content first")
                }
            }
        }
    }

    private fun demoGetVolume() {
        logSectionHeader("🔊 Volume Get API Demo")
        logApiCall("DLNACast.getVolume { volume, isMuted, success ->")
        
        DLNACast.getVolume { volume, isMuted, success ->
            runOnUiThread {
                if (success) {
                    logSuccess("Get successful:")
                    logVolumeInfo("  Current volume", volume)
                    logMuteInfo("  Mute status", isMuted)
                } else {
                    logError("Failed to get volume information")
                    logTip("Device may not support volume query or no device connected")
                }
            }
        }
    }

    private fun demoCacheManagement() {
        logSectionHeader("⚡ Cache Management API Demo")
        logDetail("Demonstrate smart cache and manual cache management features")
        
        val options = arrayOf(
            "📊 Get Real-time Progress (No Cache)",
            "🔄 Manually Refresh Volume Cache",
            "🔄 Manually Refresh Progress Cache", 
            "🧹 Clear Progress Cache",
            "📈 Compare Cache vs Real-time Data"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Select Cache Management Operation")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> demoGetProgressRealtime()
                    1 -> demoRefreshVolumeCache()
                    2 -> demoRefreshProgressCache()
                    3 -> demoClearProgressCache()
                    4 -> demoCompareCacheAndRealtime()
                }
            }
            .show()
    }
    
    private fun demoGetProgressRealtime() {
        logSectionHeader("📊 Real-time Progress Get Demo")
        logApiCall("DLNACast.getProgressRealtime { currentMs, totalMs, success ->")
        logTip("Force fetch from device, no cache used")
        
        DLNACast.getProgressRealtime { currentMs, totalMs, success ->
            runOnUiThread {
                if (success) {
                    logSuccess("Real-time get successful:")
                    logProgressInfo(currentMs, totalMs)
                    logDetail("🔧 Cache data also updated")
                } else {
                    logError("Failed to get real-time progress")
                }
            }
        }
    }
    
    private fun demoRefreshVolumeCache() {
        logSectionHeader("🔄 Manual Volume Cache Refresh Demo")
        logApiCall("DLNACast.refreshVolumeCache { success ->")
        
        DLNACast.refreshVolumeCache { success ->
            runOnUiThread {
                if (success) {
                    logSuccess("Volume cache refresh successful")
                    logTip("Now getState() will return latest volume information")
                    
                    // Show refreshed state
                    val state = DLNACast.getState()
                    logDetail("📊 Latest state:")
                    logVolumeInfo("  Volume", state.volume)
                    logMuteInfo("  Mute", state.isMuted)
                } else {
                    logError("Volume cache refresh failed")
                }
            }
        }
    }
    
    private fun demoRefreshProgressCache() {
        logSectionHeader("🔄 Manual Progress Cache Refresh Demo")
        logApiCall("DLNACast.refreshProgressCache { success ->")
        
        DLNACast.refreshProgressCache { success ->
            runOnUiThread {
                if (success) {
                    logSuccess("Progress cache refresh successful")
                    logTip("Now getProgress() will interpolate based on latest data")
                } else {
                    logError("Progress cache refresh failed")
                }
            }
        }
    }
    
    private fun demoClearProgressCache() {
        logSectionHeader("🧹 Clear Progress Cache Demo")
        logApiCall("DLNACast.clearProgressCache()")
        logTip("Use case: When switching media files")
        
        DLNACast.clearProgressCache()
        logSuccess("Progress cache cleared")
        logDetail("Next getProgress() call will re-fetch device data")
    }
    
    private fun demoCompareCacheAndRealtime() {
        logSectionHeader("📈 Cache vs Real-time Data Comparison Demo")
        logDetail("Simultaneously get cache data and real-time data for comparison")
        
        logDetail("\n1️⃣ Get cached progress data:")
        DLNACast.getProgress { currentMs, totalMs, success ->
            runOnUiThread {
                if (success) {
                    logDetail("📊 Cached data: ${formatDuration(currentMs)} / ${formatDuration(totalMs)}")
                    
                    // Get real-time data after 1 second for comparison
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        logDetail("\n2️⃣ Get real-time progress data:")
                        DLNACast.getProgressRealtime { realtimeCurrentMs, realtimeTotalMs, realtimeSuccess ->
                            runOnUiThread {
                                if (realtimeSuccess) {
                                    logDetail("📡 Real-time data: ${formatDuration(realtimeCurrentMs)} / ${formatDuration(realtimeTotalMs)}")
                                    
                                    val timeDiff = kotlin.math.abs(realtimeCurrentMs - currentMs)
                                    logDetail("⏱️ Time difference: ${timeDiff}ms")
                                    logTip("Cached data uses interpolation calculation, real-time data is fetched directly from device")
                                }
                            }
                        }
                    }, 1000L)
                }
            }
        }
    }

    private fun demoLocalFileCast() {
        logSectionHeader("📁 Local File Cast API Demo")
        logDetail("Demonstrate UPnPCast local file casting functionality")
        logTip("This function is based on direct file path access")
        
        // Provide simple and practical selection options
        val options = arrayOf(
            "📹 Browse DCIM/Camera folder",
            "📁 Browse Download folder", 
            "🎵 Browse Music folder",
            "🎬 Test example files",
            "✏️ Manually input file path"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Select Local File Cast Method")
            .setMessage("Local casting functionality based on direct file paths")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> browseFolder("/storage/emulated/0/DCIM/Camera/")
                    1 -> browseFolder("/storage/emulated/0/Download/")
                    2 -> browseFolder("/storage/emulated/0/Music/")
                    3 -> showTestFiles()
                    4 -> showCustomFilePathDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun browseFolder(folderPath: String) {
        logDetail("🔍 Browse folder: $folderPath")
        
        try {
            val folder = java.io.File(folderPath)
            if (!folder.exists() || !folder.isDirectory) {
                logError("Folder does not exist: $folderPath")
                return
            }
            
            val files = folder.listFiles { file ->
                file.isFile && isMediaFile(file.name)
            }?.sortedBy { it.name } ?: emptyList()
            
            logDetail("📊 Found ${files.size} media files")
            
            if (files.isEmpty()) {
                logTip("No media files in this folder")
                return
            }
            
            val fileNames = files.map { file ->
                val name = file.name.lowercase()
                val icon = getFileIcon(name)
                "$icon ${file.name}"
            }.toTypedArray()
            
            AlertDialog.Builder(this)
                .setTitle("Select File (${files.size} media files)")
                .setItems(fileNames) { _, which ->
                    val selectedFile = files[which]
                    logSuccess("Selected file: ${selectedFile.name}")
                    demoLocalFileFromPath(selectedFile.absolutePath)
                }
                .setNegativeButton("Back", null)
                .show()
                
        } catch (e: Exception) {
            logError("Failed to browse folder: ${e.message}")
        }
    }
    
    private fun isMediaFile(fileName: String): Boolean {
        val name = fileName.lowercase()
        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
               name.endsWith(".mov") || name.endsWith(".mp3") || name.endsWith(".aac") ||
               name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".jpg") ||
               name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp")
    }
    
    private fun getFileIcon(fileName: String): String {
        return when {
            fileName.endsWith(".mp4") || fileName.endsWith(".mkv") || fileName.endsWith(".avi") || fileName.endsWith(".mov") -> "🎬"
            fileName.endsWith(".mp3") || fileName.endsWith(".aac") || fileName.endsWith(".flac") || fileName.endsWith(".wav") -> "🎵"
            fileName.endsWith(".jpg") || fileName.endsWith(".png") || fileName.endsWith(".gif") || fileName.endsWith(".webp") -> "🖼️"
            else -> "📄"
        }
    }
    
    private fun showTestFiles() {
        logDetail("🎬 Provide common path examples")
        logTip("Please select existing files based on actual device situation")
        
        val testFiles = arrayOf(
            "📹 /storage/emulated/0/DCIM/Camera/ (Camera recordings)",
            "🎵 /storage/emulated/0/Music/ (Music files)",
            "📁 /storage/emulated/0/Download/ (Download files)",
            "🖼️ /storage/emulated/0/Pictures/ (Picture files)"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Common File Path Examples")
            .setMessage("Please ensure there are media files in the corresponding paths")
            .setItems(testFiles) { _, which ->
                val basePath = when (which) {
                    0 -> "/storage/emulated/0/DCIM/Camera/"
                    1 -> "/storage/emulated/0/Music/"
                    2 -> "/storage/emulated/0/Download/"
                    3 -> "/storage/emulated/0/Pictures/"
                    else -> "/storage/emulated/0/"
                }
                logDetail("🎯 Selected path: $basePath")
                browseFolder(basePath)
            }
            .setNegativeButton("Back", null)
            .show()
    }
    
    private fun demoLocalFileFromPath(filePath: String) {
        logDetail("📁 Test file path: $filePath")
        logApiCall("DLNACast.castLocalFile(filePath, title) { success, message ->")
        
        val fileName = java.io.File(filePath).name
        logDetail("📂 File name: $fileName")
        
        // First check if file exists
        val file = java.io.File(filePath)
        if (!file.exists()) {
            logDetail("⚠️ File does not exist, demo getLocalFileUrl function")
            val fileUrl = DLNACast.getLocalFileUrl(filePath)
            logDetail("🔗 Generated URL: ${fileUrl ?: "null (file does not exist)"}")
            return
        }
        
        // Auto-select device for casting
        DLNACast.castLocalFile(filePath, fileName) { success, message ->
            runOnUiThread {
                if (success) {
                    logSuccess("Local file cast successful")
                    logDetail("📺 Message: $message")
                    logTip("You can now see the playback content on TV")
                } else {
                    logError("Local file cast failed")
                    logDetail("📺 Error message: $message")
                    logTip("Ensure file exists and has read permissions")
                }
            }
        }
        
        logDetail("🚀 Local file cast request sent...")
        logDetail("ℹ️ Description: Auto-start HTTP file server")
        logDetail("ℹ️ Description: Generate temporary access URL")
        logDetail("ℹ️ Description: Cast to best device")
    }
    
    private fun showCustomFilePathDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        
        val pathInput = android.widget.EditText(this).apply {
            hint = "Enter complete file path"
            setText("/storage/emulated/0/")
        }
        
        val titleInput = android.widget.EditText(this).apply {
            hint = "File title (optional)"
        }
        
        val tipText = TextView(this).apply {
            text = """
                💡 Path examples:
                • /storage/emulated/0/DCIM/Camera/video.mp4
                • /storage/emulated/0/Download/movie.mkv
                • /storage/emulated/0/Music/song.mp3
                
                ✨ Local file casting features:
                • Based on direct file path access
                • Auto-start HTTP file server
                • Support Range requests, large file streaming
                • Unified MIME types, ensure device compatibility
                
                ⚠️ Note: Need to input complete file system path
            """.trimIndent()
            textSize = 12f
            setTextColor("#666666".toColorInt())
            setPadding(0, 10, 0, 0)
        }
        
        layout.addView(TextView(this).apply { 
            text = "File path:" 
            textSize = 14f
            setPadding(0, 0, 0, 5)
        })
        layout.addView(pathInput)
        
        layout.addView(TextView(this).apply { 
            text = "Title:" 
            textSize = 14f 
            setPadding(0, 15, 0, 5)
        })
        layout.addView(titleInput)
        layout.addView(tipText)
        
        AlertDialog.Builder(this)
            .setTitle("Local File Cast")
            .setView(layout)
            .setPositiveButton("Cast") { _, _ ->
                val path = pathInput.text.toString().trim()
                val title = titleInput.text.toString().trim().ifEmpty { null }
                
                if (path.isNotEmpty()) {
                    logDetail("📁 User input path: $path")
                    logDetail("📝 User input title: ${title ?: "(auto-generated)"}")
                    demoLocalFileFromPath(path)
                } else {
                    logError("Path cannot be empty")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Log formatting methods - Extract duplicate logic
    private fun logSectionHeader(title: String) = logMessage("\n=== $title ===")
    private fun logApiCall(call: String) = logMessage("Call: $call")
    private fun logDetail(detail: String) = logMessage(detail)
    private fun logSuccess(message: String) = logMessage("✅ $message")
    private fun logError(message: String) = logMessage("❌ $message")
    private fun logTip(tip: String) = logMessage("💡 Tip: $tip")
    
    private fun logResult(action: String, success: Boolean) {
        logMessage("${action} result: ${if (success) "✅ Success" else "❌ Failed"}")
    }
    
    private fun logStatus(label: String, status: Boolean) {
        logMessage("$label: ${if (status) "✅ Connected" else "❌ Disconnected"}")
    }
    
    private fun logVolumeInfo(label: String, volume: Int?) {
        logMessage("$label: ${volume?.let { "${it}%" } ?: "Unknown"}")
    }
    
    private fun logMuteInfo(label: String, muted: Boolean?) {
        logMessage("$label: ${muted?.let { if (it) "Yes" else "No" } ?: "Unknown"}")
    }
    
    private fun logProgressInfo(currentMs: Long, totalMs: Long) {
        val progressPercent = if (totalMs > 0) (currentMs * 100 / totalMs) else 0
        
        logSuccess("Get successful:")
        logDetail("  Current time: ${formatDuration(currentMs)}")
        logDetail("  Total duration: ${formatDuration(totalMs)}")
        logDetail("  Progress: $progressPercent%")
    }
    
    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, remainingSeconds)
    }

    private fun logMessage(message: String) {
        logMessages.add(message)
        runOnUiThread {
            if (::logTextView.isInitialized) {
                logTextView.text = logMessages.joinToString("\n")
                
                // Auto-scroll to bottom
                logTextView.post {
                    val scrollView = logTextView.parent.parent as? ScrollView
                    scrollView?.fullScroll(ScrollView.FOCUS_DOWN)
                }
            }
        }
    }
} 