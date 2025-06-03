# UPnPCast

[![CI/CD](https://github.com/yinnho/UPnPCast/actions/workflows/ci.yml/badge.svg)](https://github.com/yinnho/UPnPCast/actions)
[![Release](https://img.shields.io/github/v/release/yinnho/UPnPCast)](https://github.com/yinnho/UPnPCast/releases)
[![License](https://img.shields.io/github/license/yinnho/UPnPCast)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/yinnho.com/upnpcast)](https://central.sonatype.com/artifact/yinnho.com/upnpcast)
[![Weekly Downloads](https://jitpack.io/v/yinnho/UPnPCast/week.svg)](https://jitpack.io/#yinnho/UPnPCast)
[![Monthly Downloads](https://jitpack.io/v/yinnho/UPnPCast/month.svg)](https://jitpack.io/#yinnho/UPnPCast)

🚀 A modern, clean Android DLNA/UPnP casting library designed as a drop-in replacement for the discontinued Cling project.

> **[中文文档](README_zh.md)** | **English Documentation**

## ✨ What's New in v1.1.0

🎯 **Major Architecture Refactoring & Internationalization**
- **77% Code Reduction**: Streamlined from 644 lines to 148 lines for core functionality
- **Modular Architecture**: Implemented specialized modules for better maintainability
- **Full English Support**: Complete internationalization with English documentation and comments
- **Performance Optimization**: Improved memory usage and response times
- **Enhanced Documentation**: Comprehensive API documentation with clear examples

## Features

- 🔍 **Device Discovery**: Automatic DLNA/UPnP device discovery with SSDP protocol
- 📺 **Media Casting**: Cast photos, videos, and audio to DLNA-compatible devices
- 🎮 **Playback Controls**: Play, pause, stop, seek, volume control, and mute functionality
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
    implementation 'com.github.yinnho:UPnPCast:1.1.0'
}
```

#### Option 2: Maven Central (Coming Soon)
```gradle
dependencies {
    implementation 'yinnho.com:upnpcast:1.1.0'
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
        DLNACast.smartCast("http://your-video.mp4", "Video Title") { success ->
            if (success) {
                Log.d("DLNA", "Smart casting started!")
            }
        } { devices ->
            // Device selector: prefer TV over other devices
            devices.firstOrNull { it.isTV } ?: devices.firstOrNull()
        }
    }
    
    // Control playback
    private fun controlPlayback() {
        DLNACast.control(DLNACast.MediaAction.PAUSE) { success ->
            Log.d("DLNA", "Paused: $success")
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

// Smart cast with device selection strategy
DLNACast.smartCast(
    url: String, 
    title: String? = null, 
    callback: (success: Boolean) -> Unit = {}, 
    deviceSelector: (devices: List<Device>) -> Device?
)

// Cast to specific device
DLNACast.castToDevice(device: Device, url: String, title: String? = null, callback: (success: Boolean) -> Unit = {})

// Cast local files (NEW!)
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

// Get playback progress
DLNACast.getProgress(callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit)

// Get current state
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

// Current casting state
data class State(
    val isConnected: Boolean,
    val currentDevice: Device?,
    val playbackState: PlaybackState,
    val volume: Int = -1,
    val isMuted: Boolean = false
)
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