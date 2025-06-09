# UPnPCast

[![CI/CD](https://github.com/yinnho/UPnPCast/actions/workflows/ci.yml/badge.svg)](https://github.com/yinnho/UPnPCast/actions)
[![Release](https://img.shields.io/github/v/release/yinnho/UPnPCast)](https://github.com/yinnho/UPnPCast/releases)
[![License](https://img.shields.io/github/license/yinnho/UPnPCast)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/yinnho.com/upnpcast)](https://central.sonatype.com/artifact/yinnho.com/upnpcast)
[![Weekly Downloads](https://jitpack.io/v/yinnho/UPnPCast/week.svg)](https://jitpack.io/#yinnho/UPnPCast)
[![Monthly Downloads](https://jitpack.io/v/yinnho/UPnPCast/month.svg)](https://jitpack.io/#yinnho/UPnPCast)

🚀 A modern, clean Android DLNA/UPnP casting library designed as a drop-in replacement for the discontinued Cling project.

> **[中文文档](README_zh.md)** | **English Documentation**

## ✨ What's New in v1.1.2

🎯 **Enhanced Volume Control & Millisecond-Level Progress Management**
- **🔊 Complete Volume Control System**: Added `getVolume()`, `setVolume()`, and `setMute()` APIs for comprehensive volume management
- **⚡ Millisecond-Level Progress Control**: Intelligent caching with 3-second cache duration and real-time interpolation
- **🚀 Smart Cache Management**: Volume cache (5-second validity) and progress cache with async refresh mechanisms
- **🎯 Real-time Progress Tracking**: `getProgressRealtime()` for force refresh without cache dependency
- **🔄 Manual Cache Control**: Exposed cache refresh and clearing methods for advanced control
- **📊 Enhanced State Management**: Improved `getState()` with integrated volume and mute status

## Features

- 🔍 **Device Discovery**: Automatic DLNA/UPnP device discovery with SSDP protocol
- 📺 **Media Casting**: Cast photos, videos, and audio to DLNA-compatible devices
- 🎮 **Playback Controls**: Play, pause, stop, seek, volume control, and mute functionality
- 🔊 **Advanced Volume Control**: Get/set volume, mute control with intelligent caching
- ⚡ **Millisecond Precision**: Real-time progress tracking with smart interpolation
- 📱 **Easy Integration**: Simple API with intuitive callback mechanisms
- 🚀 **Modern Architecture**: Built with Kotlin, Coroutines, and Android best practices
- 🔧 **Highly Compatible**: Tested with major TV brands (Xiaomi, Samsung, LG, Sony)
- ⚡ **Lightweight**: Minimal dependencies, optimized performance

## Quick Start

### Installation

#### Option 1: JitPack (Recommended - Available Now!)

Add to your root `build.gradle`:
```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Add dependency:
```gradle
dependencies {
    implementation 'com.github.yinnho:UPnPCast:1.1.2'
}
```

#### Option 2: Maven Central (Coming Soon)
```gradle
dependencies {
    implementation 'yinnho.com:upnpcast:1.1.2'
}
```

### Basic Usage

```kotlin
import com.yinnho.upnpcast.DLNACast

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize
        DLNACast.init(this)
        
        // Search for devices - real-time cumulative updates
        searchDevices()
        
        // Or use smart cast with automatic device selection
        performSmartCast()
    }
    
    private fun searchDevices() {
        // Real-time device discovery with cumulative results
        DLNACast.search(timeout = 5000) { devices ->
            // Called with cumulative device list each time new devices are found
            updateDeviceList(devices) // Simply replace the list
            Log.d("DLNA", "Found ${devices.size} devices")
        }
    }
    
    private fun performSmartCast() {
        // Smart cast - automatically finds and selects best device
        DLNACast.cast("http://your-video.mp4", "Video Title") { result ->
            if (result.success) {
                Log.d("DLNA", "Smart casting started!")
            } else {
                Log.e("DLNA", "Cast failed: ${result.message}")
            }
        }
    }
    
    // Control playback
    private fun controlPlayback() {
        DLNACast.control(DLNACast.MediaAction.PAUSE) { success ->
            Log.d("DLNA", "Paused: $success")
        }
    }
    
    // NEW: Volume control examples
    private fun volumeControlExamples() {
        // Get current volume
        DLNACast.getVolume { volume, isMuted, success ->
            if (success) {
                Log.d("DLNA", "Current volume: $volume, Muted: $isMuted")
            }
        }
        
        // Set volume to 50%
        DLNACast.setVolume(50) { success ->
            Log.d("DLNA", "Volume set: $success")
        }
        
        // Toggle mute
        DLNACast.setMute(true) { success ->
            Log.d("DLNA", "Muted: $success")
        }
    }
    
    // NEW: Advanced progress tracking
    private fun progressTrackingExamples() {
        // Cached progress (fast, uses interpolation)
        DLNACast.getProgress { currentMs, totalMs, success ->
            Log.d("DLNA", "Progress: ${currentMs}ms / ${totalMs}ms")
        }
        
        // Real-time progress (accurate, always from device)
        DLNACast.getProgressRealtime { currentMs, totalMs, success ->
            Log.d("DLNA", "Real-time progress: ${currentMs}ms / ${totalMs}ms")
        }
        
        // Manual cache management
        DLNACast.refreshVolumeCache { success ->
            Log.d("DLNA", "Volume cache refreshed: $success")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        DLNACast.release()
    }
}
```

## API Reference

### Core Methods

```kotlin
// Initialize the library
DLNACast.init(context: Context)

// Search for devices with real-time cumulative updates
DLNACast.search(timeout: Long = 5000, callback: (devices: List<Device>) -> Unit)

// Auto cast to available device
DLNACast.cast(url: String, title: String? = null, callback: (success: Boolean) -> Unit = {})

// Removed: Use DLNACast.cast() for automatic device selection

// Cast to specific device
DLNACast.castToDevice(device: Device, url: String, title: String? = null, callback: (success: Boolean) -> Unit = {})

// Cast local files
DLNACast.castLocalFile("/storage/emulated/0/video.mp4", "Local Video") { success, message ->
    if (success) {
        println("Local file cast successful")
    } else {
        println("Cast failed: $message")
    }
}

// Get local file URL for manual use
DLNACast.getLocalFileUrl(filePath: String): String?

// Control media playback
DLNACast.control(action: MediaAction, value: Any? = null, callback: (success: Boolean) -> Unit = {})
```

### 🆕 NEW: Volume Control APIs (v1.1.1)

```kotlin
// Get current volume and mute status
DLNACast.getVolume(callback: (volume: Int?, isMuted: Boolean?, success: Boolean) -> Unit)

// Set volume (0-100)
DLNACast.setVolume(volume: Int, callback: (success: Boolean) -> Unit = {})

// Set mute state
DLNACast.setMute(mute: Boolean, callback: (success: Boolean) -> Unit = {})
```

### 🆕 NEW: Enhanced Progress Management (v1.1.1)

```kotlin
// Get progress with intelligent caching and interpolation (recommended)
DLNACast.getProgress(callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit)

// Get real-time progress (always fetches from device, no cache)
DLNACast.getProgressRealtime(callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit)
```

### 🆕 NEW: Cache Management APIs (v1.1.1)

```kotlin
// Manually refresh volume cache
DLNACast.refreshVolumeCache(callback: (success: Boolean) -> Unit = {})

// Manually refresh progress cache
DLNACast.refreshProgressCache(callback: (success: Boolean) -> Unit = {})

// Clear progress cache (call when switching media)
DLNACast.clearProgressCache()

// Clear volume cache
DLNACast.clearVolumeCache()
```

### Other Core Methods

```kotlin
// Get current state (now includes volume and mute status)
DLNACast.getState(): State

// Release resources
DLNACast.release()
```

### Data Types

```kotlin
// Device information
data class Device(
    val id: String,
    val name: String,
    val address: String,
    val isTV: Boolean
)

// Media control actions
enum class MediaAction {
    PLAY, PAUSE, STOP, VOLUME, MUTE, SEEK, GET_STATE
}

// Playback states
enum class PlaybackState {
    IDLE, PLAYING, PAUSED, STOPPED, BUFFERING, ERROR
}

// Current casting state (enhanced in v1.1.1)
data class State(
    val isConnected: Boolean,
    val currentDevice: Device?,
    val playbackState: PlaybackState,
    val volume: Int = -1,        // Current volume (0-100, -1 if unknown)
    val isMuted: Boolean = false // Current mute status
)
```

## 🆕 Advanced Usage Examples (v1.1.1)

### Smart Volume Control
```kotlin
// Check current volume before adjusting
DLNACast.getVolume { currentVolume, isMuted, success ->
    if (success && currentVolume != null) {
        when {
            isMuted == true -> {
                // Unmute first
                DLNACast.setMute(false) { 
                    DLNACast.setVolume(50) // Then set to 50%
                }
            }
            currentVolume < 30 -> {
                DLNACast.setVolume(currentVolume + 10) // Increase by 10
            }
            else -> {
                DLNACast.setVolume(currentVolume - 10) // Decrease by 10
            }
        }
    }
}
```

### Progress Tracking with Caching Strategy
```kotlin
class ProgressTracker {
    private var useRealtime = false
    
    fun trackProgress() {
        val progressMethod = if (useRealtime) {
            DLNACast::getProgressRealtime
        } else {
            DLNACast::getProgress
        }
        
        progressMethod { currentMs, totalMs, success ->
            if (success) {
                updateUI(currentMs, totalMs)
                
                // Switch to real-time if user is seeking
                if (isUserSeeking()) {
                    useRealtime = true
                    DLNACast.refreshProgressCache() // Refresh cache after seeking
                }
            }
        }
    }
}
```

### Optimized State Management
```kotlin
fun getEnhancedState() {
    val state = DLNACast.getState()
    
    // The state now includes volume and mute information
    println("Connected: ${state.isConnected}")
    println("Device: ${state.currentDevice?.name}")
    println("Playing: ${state.playbackState}")
    println("Volume: ${state.volume}% (Muted: ${state.isMuted})")
    
    // Refresh caches if needed
    if (state.isConnected) {
        DLNACast.refreshVolumeCache()
        DLNACast.refreshProgressCache()
    }
}
```

## Documentation

- 🎯 **[Demo App](app-demo/)** - Working example application with complete API demonstration
- 📖 **[API Reference](#api-reference)** - Complete API documentation above
- 📋 **[Changelog](CHANGELOG.md)** - Version history and updates
- 🤔 **[FAQ](docs/FAQ.md)** - Frequently asked questions and troubleshooting
- 🎯 **[Best Practices](docs/BEST_PRACTICES.md)** - Async callbacks, device management, and optimization guides

## Device Compatibility

- ✅ Xiaomi TV (Native DLNA + Mi Cast)
- ✅ Samsung Smart TV
- ✅ LG Smart TV  
- ✅ Sony Bravia TV
- ✅ Android TV boxes
- ✅ Windows Media Player

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for development guidelines and how to get started.

## Support

- 📖 Detailed usage examples in the [demo app](app-demo/) 
- 🐛 Report issues on [GitHub Issues](https://github.com/yinnho/UPnPCast/issues)
- 💡 Feature requests are welcome!

// Cast media to specific device
DLNACast.castToDevice(device, "http://example.com/video.mp4", "My Video") { success ->
    // Handle result
}

// Cast local files
DLNACast.castLocalFile("/storage/emulated/0/video.mp4", "Local Video") { success, message ->
    if (success) {
        println("Local file cast successful")
    } else {
        println("Cast failed: $message")
    }
}

// Get local file URL for manual use
val fileUrl = DLNACast.getLocalFileUrl("/storage/emulated/0/video.mp4")
if (fileUrl != null) {
    DLNACast.cast(fileUrl, "My Local Video") { success ->
        // Handle result  
    }
}

// Control media playback