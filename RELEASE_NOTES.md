# 🎉 UPnPCast v1.1.2 - Now Available on Maven Central!

## 🚀 Major Milestone: Official Maven Central Release

We're excited to announce that **UPnPCast v1.1.2** is now officially available on **Maven Central**! This marks a significant milestone for the project, making it easier than ever to integrate UPnPCast into your Android applications.

### 📦 Easy Installation

Simply add to your app's `build.gradle`:
```gradle
dependencies {
    implementation 'com.yinnho.upnpcast:upnpcast:1.1.2'
}
```

No additional repositories needed - Maven Central is included by default in all Android projects!

## ✨ What's New in v1.1.2

### 🔊 Enhanced Volume Control System
- **Complete Volume Management**: New `getVolume()`, `setVolume()`, and `setMute()` APIs
- **Intelligent Caching**: 5-second volume cache with automatic refresh
- **Real-time Updates**: Instant volume state synchronization

### ⚡ Millisecond-Level Progress Control
- **Precision Tracking**: Real-time progress with smart interpolation
- **Smart Caching**: 3-second cache duration with async refresh
- **Force Refresh**: `getProgressRealtime()` for immediate updates without cache

### 🚀 Improved Performance & Reliability
- **Enhanced State Management**: Integrated volume and mute status in `getState()`
- **Manual Cache Control**: Exposed cache refresh and clearing methods
- **Better Error Handling**: More robust error recovery mechanisms

## 🔧 Technical Improvements

- **Standardized GPG Signatures**: Improved security with proper code signing
- **Maven Central Compliance**: Full compliance with Maven Central requirements
- **Enhanced Documentation**: Updated README with Maven Central installation instructions
- **Better API Design**: More intuitive method naming and parameter handling

## 📱 Usage Examples

### Quick Start
```kotlin
// Initialize
DLNACast.init(this)

// Smart cast to best available device
lifecycleScope.launch {
    val success = DLNACast.cast("http://your-video.mp4", "My Video")
    if (success) {
        // Control volume
        DLNACast.setVolume(50)
        
        // Get real-time progress
        val progress = DLNACast.getProgressRealtime()
        Log.d("Progress", "${progress.percentage * 100}%")
    }
}
```

### Advanced Volume Control
```kotlin
lifecycleScope.launch {
    // Get current volume
    val volume = DLNACast.getVolume()
    Log.d("Volume", "Level: ${volume.level}, Muted: ${volume.isMuted}")
    
    // Set volume to 75%
    DLNACast.setVolume(75)
    
    // Toggle mute
    DLNACast.setMute(!volume.isMuted)
}
```

## 🎯 Migration Guide

### From JitPack to Maven Central
If you're currently using JitPack, simply update your dependency:

**Old (JitPack):**
```gradle
implementation 'com.github.yinnho:UPnPCast:1.1.2'
```

**New (Maven Central):**
```gradle
implementation 'com.yinnho.upnpcast:upnpcast:1.1.2'
```

The API remains exactly the same - no code changes required!

## 🔗 Links

- **Maven Central**: https://central.sonatype.com/artifact/com.yinnho.upnpcast/upnpcast
- **GitHub Repository**: https://github.com/yinnho/UPnPCast
- **Documentation**: [README.md](README.md)
- **Demo App**: [app-demo/](app-demo/)

## 🙏 Acknowledgments

Special thanks to the community for testing, feedback, and contributions that made this Maven Central release possible!

---

**Happy Casting! 📺✨** 