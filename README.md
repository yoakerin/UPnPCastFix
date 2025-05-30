# UPnPCast

[![Build Status](https://github.com/yinnho/UPnPCast/workflows/CI%2FCD/badge.svg)](https://github.com/yinnho/UPnPCast/actions)
[![Maven Central](https://img.shields.io/maven-central/v/com.yinnho/upnpcast.svg)](https://search.maven.org/search?q=g:com.yinnho%20AND%20a:upnpcast)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)

A modern Android DLNA/UPnP casting library as a replacement for the discontinued Cling project.

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
class MainActivity : AppCompatActivity() {
    private lateinit var dlnaManager: DLNACastManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize DLNA manager
        dlnaManager = DLNACastManager.getInstance(this)
        
        // Set up listeners
        dlnaManager.setCastListener(object : CastListener {
            override fun onDeviceListUpdated(devices: List<RemoteDevice>) {
                // Update UI with discovered devices
                showDevices(devices)
            }
            
            override fun onConnected(device: RemoteDevice) {
                // Device connected successfully
                Toast.makeText(this@MainActivity, "Connected to ${device.displayName}", Toast.LENGTH_SHORT).show()
            }
            
            override fun onDisconnected() {
                // Device disconnected
            }
            
            override fun onError(error: DLNAException) {
                // Handle errors
                Log.e("DLNA", "Error: ${error.message}")
            }
        })
        
        // Start device discovery
        dlnaManager.startSearch()
    }
    
    private fun castMedia() {
        val mediaUrl = "http://example.com/video.mp4"
        val success = dlnaManager.playMedia(mediaUrl, "My Video")
        if (success) {
            // Media casting started
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        dlnaManager.release()
    }
}
```

## API Reference

### Core Classes

#### DLNACastManager
Main entry point for all DLNA operations.

```kotlin
// Get singleton instance
val dlnaManager = DLNACastManager.getInstance(context)

// Device discovery
dlnaManager.startSearch(timeoutMs = 30000)
dlnaManager.stopSearch()

// Device connection
dlnaManager.connectToDevice(device)
dlnaManager.disconnect()

// Media playback
dlnaManager.playMedia(url, title)
dlnaManager.pause()
dlnaManager.resume()
dlnaManager.stop()
dlnaManager.setVolume(50)
dlnaManager.setMute(true)

// Get information
val devices = dlnaManager.getAllDevices()
val currentDevice = dlnaManager.getCurrentDevice()
val state = dlnaManager.getCurrentState()
```

#### RemoteDevice
Represents a discovered DLNA device.

```kotlin
data class RemoteDevice(
    val id: String,
    val displayName: String,
    val manufacturer: String,
    val address: String,
    val details: Map<String, Any>
)
```

#### Listeners

```kotlin
interface CastListener {
    fun onDeviceListUpdated(devices: List<RemoteDevice>)
    fun onConnected(device: RemoteDevice)
    fun onDisconnected()
    fun onError(error: DLNAException)
}

interface PlaybackStateListener {
    fun onStateChanged(state: PlaybackState)
    fun onPositionChanged(position: Long)
}
```

## Advanced Usage

### Custom Error Handling

```kotlin
dlnaManager.setCastListener(object : CastListener {
    override fun onError(error: DLNAException) {
        when (error.errorType) {
            DLNAErrorType.DEVICE_NOT_FOUND -> {
                // No devices available
            }
            DLNAErrorType.CONNECTION_FAILED -> {
                // Failed to connect to device
            }
            DLNAErrorType.PLAYBACK_ERROR -> {
                // Media playback failed
            }
            DLNAErrorType.NETWORK_ERROR -> {
                // Network connectivity issues
            }
        }
    }
})
```

### Device Filtering

```kotlin
// Filter devices by manufacturer
val xiaomiDevices = dlnaManager.getAllDevices()
    .filter { it.manufacturer.contains("Xiaomi", ignoreCase = true) }

// Filter by device capabilities
val mediaRenderers = dlnaManager.getAllDevices()
    .filter { device ->
        val services = device.details["services"] as? List<*>
        services?.any { service ->
            service.toString().contains("MediaRenderer", ignoreCase = true)
        } ?: false
    }
```

## Compatibility

### Tested Devices
- ✅ Xiaomi TV (Native DLNA + iQiYi Cast)
- ✅ Samsung Smart TV
- ✅ LG Smart TV
- ✅ Sony Bravia TV
- ✅ Android TV boxes
- ✅ Windows Media Player

### Android Requirements
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Permissions**: 
  - `INTERNET`
  - `ACCESS_NETWORK_STATE`
  - `ACCESS_WIFI_STATE`
  - `CHANGE_WIFI_MULTICAST_STATE`

## Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup

1. Clone the repository:
```bash
git clone https://github.com/yinnho/UPnPCast.git
cd UPnPCast
```

2. Open in Android Studio
3. Build the project:
```bash
./gradlew build
```

4. Run tests:
```bash
./gradlew test
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed release notes.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built as a modern replacement for the discontinued [Cling](http://4thline.org/projects/cling/) project
- Inspired by UPnP/DLNA specifications and Android media framework
- Special thanks to the Android community for testing and feedback

## Support

- 📚 [API Documentation](docs/API.md)
- 🐛 [Issue Tracker](https://github.com/yinnho/UPnPCast/issues)
- 💬 [Discussions](https://github.com/yinnho/UPnPCast/discussions)

---

**Made with ❤️ for the Android community**
