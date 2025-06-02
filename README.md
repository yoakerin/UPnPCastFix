# UPnPCast

[![Build Status](https://github.com/yinnho/UPnPCast/workflows/CI%2FCD/badge.svg)](https://github.com/yinnho/UPnPCast/actions)
[![Maven Central](https://img.shields.io/maven-central/v/com.yinnho/upnpcast.svg)](https://search.maven.org/search?q=g:com.yinnho%20AND%20a:upnpcast)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)

A modern Android DLNA/UPnP casting library as a replacement for the discontinued Cling project.

> **[中文文档](README_zh.md)** | **English Documentation**

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

#### Option 1: JitPack (Recommended)

Add to your root `build.gradle`:
```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Add dependency:
```gradle
dependencies {
    implementation 'com.github.yinnho:UPnPCast:1.0.0'
}
```

#### Option 2: Maven Central
```gradle
dependencies {
    implementation 'com.yinnho:upnpcast:1.0.0'
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
        
        // Search for devices
        DLNACast.search { devices ->
            devices.forEach { device ->
                Log.d("DLNA", "Found: ${device.name}")
            }
        }
        
        // Cast media
        DLNACast.cast("http://your-video.mp4", "Video Title") { success ->
            if (success) {
                Log.d("DLNA", "Casting started!")
            }
        }
        
        // Control playback
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

// Search for devices
DLNACast.search(timeout: Long = 10000, callback: (devices: List<Device>) -> Unit)

// Auto cast to available device
DLNACast.cast(url: String, title: String? = null, callback: (success: Boolean) -> Unit = {})

// Smart cast with device selection
DLNACast.smartCast(url: String, title: String? = null, callback: (success: Boolean) -> Unit = {}, deviceSelector: (devices: List<Device>) -> Device?)

// Cast to specific device
DLNACast.castToDevice(device: Device, url: String, title: String? = null, callback: (success: Boolean) -> Unit = {})

// Control media playback
DLNACast.control(action: MediaAction, value: Any? = null, callback: (success: Boolean) -> Unit = {})

// Get current state
DLNACast.getState(): State

// Release resources
DLNACast.release()
```

### Data Types

```kotlin
data class Device(
    val id: String,
    val name: String,
    val address: String,
    val isTV: Boolean
)

enum class MediaAction {
    PLAY, PAUSE, STOP, VOLUME, MUTE, SEEK, GET_STATE
}

enum class PlaybackState {
    IDLE, PLAYING, PAUSED, STOPPED, BUFFERING, ERROR
}

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

We welcome contributions! Please see our [best practices guide](docs/best_practices.md) for development guidelines.

## Support

- 📖 Detailed usage examples in the [demo app](app-demo/) 
- 🐛 Report issues on [GitHub Issues](https://github.com/yinnho/UPnPCast/issues)
- 💡 Feature requests are welcome!
