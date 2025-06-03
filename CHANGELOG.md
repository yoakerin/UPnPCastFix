# Changelog

All notable changes to the UPnPCast library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.4] - 2025-01-14

### 🚀 Major Feature Added
- **Local File Casting**: Complete local file casting functionality with automatic HTTP file server
- **NanoHTTPD Integration**: Lightweight HTTP server for serving local files to DLNA devices
- **Range Request Support**: Full support for large file streaming with HTTP Range requests
- **Optimal Device Compatibility**: Uses `application/octet-stream` MIME type for maximum TV compatibility

### 🎯 New APIs
- **`castLocalFile()`**: Two overloads for casting local files directly
  - `castLocalFile(filePath, device, title, callback)` - Cast to specific device
  - `castLocalFile(filePath, title, callback)` - Auto-select best device
- **`getLocalFileUrl()`**: Generate HTTP URL for local files for manual use
- **File Server Management**: Automatic startup/shutdown with port conflict handling

### 🏗️ Technical Improvements
- **Smart Port Selection**: Automatic port selection from 8081-8090 range
- **Token-based Security**: Secure file access using time-based tokens
- **Chinese Path Support**: Full support for Chinese filenames and special characters
- **Memory Optimization**: Streaming file transfer without loading entire files into memory
- **Resource Management**: Automatic cleanup of file server and resources

### 📱 Demo Enhancements
- **ApiDemoActivity**: Added comprehensive local file casting demonstrations
- **MediaControlActivity**: Added local file selection and casting options
- **Interactive UI**: User-friendly file path input with validation and error handling

### 💡 Usage Examples
```kotlin
// Simple local file casting
DLNACast.castLocalFile("/storage/emulated/0/video.mp4", "My Video") { success, message ->
    if (success) {
        println("Local casting successful!")
    } else {
        println("Failed: $message")
    }
}

// Get URL for manual control
val fileUrl = DLNACast.getLocalFileUrl("/path/to/video.mp4")
if (fileUrl != null) {
    DLNACast.cast(fileUrl, "Local Video") { success ->
        // Handle result
    }
}
```

### 🔧 Dependencies
- **Added**: `org.nanohttpd:nanohttpd:2.3.1` for HTTP file server functionality
- **Compatibility**: Maintains backward compatibility with existing APIs

### 📋 Validated Features
- ✅ **File Size Support**: From small files (11MB) to large files (300+MB)
- ✅ **Device Compatibility**: Tested with Xiaomi TV and other DLNA devices
- ✅ **Format Support**: MP4, MKV, AVI, MP3, and other common media formats
- ✅ **Network Performance**: Efficient local network streaming with Range support

## [1.0.3] - 2025-01-14

### Enhanced
- **SEEK Functionality**: Fully implemented MediaAction.SEEK feature for precise playback position control
- **Progress Monitoring**: Added DLNACast.getProgress() API for real-time playback progress tracking
- **API Demo**: Added interactive seek control and progress monitoring demonstrations in ApiDemoActivity
- **Code Quality**: Replaced placeholder implementation with complete DLNA seek protocol support

### Added
- **getProgress() API**: New method to get current playback position and total duration
- **Position Info Parsing**: Complete UPnP GetPositionInfo SOAP action implementation
- **Time Format Support**: Proper parsing of DLNA time formats (HH:MM:SS)

### Fixed
- **Media Control**: SEEK action now properly calls DlnaMediaController.seekTo() method
- **Time Format**: Improved time formatting for DLNA seek operations (HH:MM:SS format)
- **Error Handling**: Enhanced error reporting for seek and progress operations

### Technical Improvements
- Added public `seekTo(positionMs: Long)` method in DlnaMediaController
- Added `getPositionInfo()` method with complete SOAP GetPositionInfo implementation
- Proper SOAP action implementation for UPnP AVTransport seek operations
- XML response parsing for RelTime and TrackDuration fields
- Maintained simple API design while providing complete functionality

### Usage Examples
```kotlin
// Seek to 30 seconds position
DLNACast.control(DLNACast.MediaAction.SEEK, 30 * 1000L) { success ->
    Log.d("DLNA", "Seek result: $success")
}

// Get playback progress
DLNACast.getProgress { currentMs, totalMs, success ->
    if (success) {
        val progress = (currentMs * 100 / totalMs).toInt()
        Log.d("DLNA", "Progress: $progress%")
    }
}
```

## [1.0.2] - 2025-01-14

### Fixed
- **JitPack Build Issues**: Simplified build configuration to ensure reliable JitPack builds
- **Maven Publishing**: Streamlined publishing configuration for better JitPack compatibility
- **Build System**: Added jitpack.yml configuration file to specify Java 17 environment
- **Dependencies**: Removed complex Maven publishing and signing configurations that interfered with JitPack

### Changed
- Simplified `app/build.gradle.kts` configuration for JitPack compatibility
- Removed unnecessary ProGuard obfuscation in release builds
- Updated documentation to reference v1.0.2

### Technical Improvements
- Added JitPack-specific build configuration (`jitpack.yml`)
- Optimized Gradle build scripts for external repository builds
- Cleaner dependency management for library consumers

## [1.0.0] - 2024-12-XX

### Added
- Complete DLNA device discovery and connection functionality using SSDP protocol
- Media playback control API (play, pause, resume, seek, volume control, mute)
- Device adapter layer supporting mainstream DLNA devices (Xiaomi, Samsung, LG, etc.)
- Intelligent device sorting algorithm based on usage frequency and connection success rate
- Memory monitoring and error monitoring system for improved stability
- Comprehensive error handling with custom exception types and unified error flow
- Modern Kotlin-based architecture with coroutines and Android best practices

### Technical Improvements
- Thread-safe singleton pattern implementation for core management classes
- Network resource optimization with request pool management
- SAX parser replacing DOM for improved XML parsing performance
- Lazy loading mechanism to reduce startup resource consumption
- ProGuard configuration for release builds with code obfuscation
- Complete Maven publishing configuration for GitHub Packages and Maven Central
- GitHub Actions CI/CD workflow with automated testing and releases

### Documentation
- Comprehensive API reference documentation with examples
- Detailed usage guides and troubleshooting documentation
- Internationalization: English and Chinese documentation
- Complete publishing setup with JitPack, GitHub Packages, and Maven Central support

### Device Compatibility
- ✅ Xiaomi TV (Native DLNA + iQiYi Cast service)
- ✅ Samsung Smart TV
- ✅ LG Smart TV  
- ✅ Sony Bravia TV
- ✅ Android TV boxes
- ✅ Windows Media Player

### Bug Fixes
- Fixed device list stability issues with duplicate device handling
- Resolved resource cleanup problems after device disconnection
- Fixed network change handling that caused connection failures
- Improved SSDP response deduplication mechanism
- Enhanced device timeout management (60-second timeout)

### Breaking Changes
- Migrated from Cling architecture to custom UPnP implementation
- API redesigned for simplicity and modern Android development patterns
- Minimum SDK requirement raised to API 24 (Android 7.0)

### Performance
- Reduced memory footprint by 40% compared to legacy Cling-based solutions
- Improved device discovery speed with optimized SSDP implementation
- Enhanced network efficiency with connection pooling and resource reuse

---

## Release Planning

### [1.1.0] - Planned Features
- [ ] Enhanced error recovery mechanisms
- [ ] Support for additional media formats
- [ ] Background service mode for persistent connections
- [ ] Device connection persistence across app restarts
- [ ] Advanced logging and debugging tools

### [1.2.0] - Advanced Features
- [ ] Multi-device casting support
- [ ] Custom device discovery filters
- [ ] WebRTC integration for low-latency streaming
- [ ] Plugin architecture for custom device adapters

---

**Note**: This is the first stable release of UPnPCast as a modern replacement for the discontinued Cling project. The library has been extensively tested and is ready for production use. 