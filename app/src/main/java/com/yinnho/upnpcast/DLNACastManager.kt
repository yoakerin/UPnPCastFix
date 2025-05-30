package com.yinnho.upnpcast

import android.content.Context
import android.util.Log
import com.yinnho.upnpcast.internal.UpnpServiceImpl
import java.lang.ref.WeakReference

/**
 * DLNA Cast Manager - Optimized based on Cling architecture
 * 
 * This is the single entry point for user API, internally using standard UPnP architecture:
 * - UpnpService: Core service management
 * - Registry: Device registry  
 * - ControlPoint: Control point
 * 
 * Maintains simple and easy-to-use API while hiding complex internal implementation
 */
class DLNACastManager private constructor(context: Context) {

    private val TAG = "DLNACastManager"
    private val contextRef = WeakReference(context.applicationContext)
    
    // Core UPnP service - Based on Cling architecture
    private val upnpService: UpnpService = UpnpServiceImpl(context)
    
    // User listeners
    private var castListener: CastListener? = null
    private var playbackStateListener: PlaybackStateListener? = null
    
    // Internal registry listener - Converts Cling-style events to user API events
    private val registryListener = object : RegistryListener {
        override fun deviceAdded(registry: Registry, device: RemoteDevice) {
            Log.d(TAG, "Device discovered: ${device.displayName}")
            val allDevices = registry.getDevices().toList()
            castListener?.onDeviceListUpdated(allDevices)
        }
        
        override fun deviceRemoved(registry: Registry, device: RemoteDevice) {
            Log.d(TAG, "Device went offline: ${device.displayName}")
            val allDevices = registry.getDevices().toList()
            castListener?.onDeviceListUpdated(allDevices)
            
            // If current connected device goes offline, notify disconnection
            if (upnpService.getControlPoint().getCurrentDevice()?.id == device.id) {
                castListener?.onDisconnected()
            }
        }
        
        override fun deviceUpdated(registry: Registry, device: RemoteDevice) {
            Log.d(TAG, "Device updated: ${device.displayName}")
            val allDevices = registry.getDevices().toList()
            castListener?.onDeviceListUpdated(allDevices)
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: DLNACastManager? = null
        
        fun getInstance(context: Context): DLNACastManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DLNACastManager(context).also { INSTANCE = it }
            }
        }
        
        fun getInstance(): DLNACastManager {
            return INSTANCE ?: throw IllegalStateException("Must call getInstance(Context) first to initialize")
        }
    }
    
    init {
        // Register internal listener
        upnpService.getRegistry().addListener(registryListener)
        Log.d(TAG, "DLNA Cast Manager initialized successfully")
    }
    
    /**
     * Set cast listener
     */
    fun setCastListener(listener: CastListener?) {
        this.castListener = listener
        Log.d(TAG, "Cast listener set: ${listener != null}")
    }
    
    /**
     * Set playback state listener
     */
    fun setPlaybackStateListener(listener: PlaybackStateListener?) {
        this.playbackStateListener = listener
        Log.d(TAG, "Playback state listener set: ${listener != null}")
    }
    
    /**
     * Start searching for devices
     */
    fun startSearch(timeoutMs: Long = 30000) {
        Log.d(TAG, "Starting device search, timeout: ${timeoutMs}ms")
        upnpService.getControlPoint().search()
    }
    
    /**
     * Stop searching for devices
     */
    fun stopSearch() {
        Log.d(TAG, "Stopping device search")
        upnpService.getRegistry().stopDiscovery()
    }
    
    /**
     * Connect to device
     */
    fun connectToDevice(device: RemoteDevice): Boolean {
        Log.d(TAG, "Attempting to connect to device: ${device.displayName}")
        
        val success = upnpService.getControlPoint().connectToDevice(device)
        if (success) {
            castListener?.onConnected(device)
        } else {
            val error = DLNAException(DLNAErrorType.CONNECTION_FAILED, "Failed to connect to device: ${device.displayName}")
            castListener?.onError(error)
        }
        return success
    }
    
    /**
     * Disconnect from current device
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from device")
        upnpService.getControlPoint().disconnect()
        castListener?.onDisconnected()
    }
    
    /**
     * Play media - Using existing implementation
     */
    fun playMedia(url: String, title: String? = null): Boolean {
        val currentDevice = upnpService.getControlPoint().getCurrentDevice()
        if (currentDevice == null) {
            Log.w(TAG, "No connected device")
            val error = DLNAException(DLNAErrorType.DEVICE_NOT_FOUND, "No connected device")
            castListener?.onError(error)
            return false
        }
        
        return try {
            Log.d(TAG, "Playing media: $url, title: $title")
            
            // Use ControlPointImpl internal method directly to avoid complex Action pattern
            val controlPointImpl = upnpService.getControlPoint() as? com.yinnho.upnpcast.internal.ControlPointImpl
            val success = controlPointImpl?.playMedia(url, title ?: "Unknown") ?: false
            
            if (!success) {
                val error = DLNAException(DLNAErrorType.PLAYBACK_ERROR, "Playback failed")
                castListener?.onError(error)
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play media", e)
            val error = DLNAException(DLNAErrorType.PLAYBACK_ERROR, "Playback failed: ${e.message}", e)
            castListener?.onError(error)
            false
        }
    }
    
    /**
     * Get current playback state
     */
    fun getCurrentState(): PlaybackState {
        val device = upnpService.getControlPoint().getCurrentDevice()
        return if (device != null) PlaybackState.PLAYING else PlaybackState.IDLE
    }
    
    /**
     * Pause playback
     */
    fun pause(): Boolean {
        return executeMediaControl("pause") {
            val controlPointImpl = upnpService.getControlPoint() as? com.yinnho.upnpcast.internal.ControlPointImpl
            controlPointImpl?.pausePlayback() ?: false
        }
    }
    
    /**
     * Resume playback
     */
    fun resume(): Boolean {
        return executeMediaControl("resume") {
            val controlPointImpl = upnpService.getControlPoint() as? com.yinnho.upnpcast.internal.ControlPointImpl
            controlPointImpl?.resumePlayback() ?: false
        }
    }
    
    /**
     * Stop playback
     */
    fun stop(): Boolean {
        return executeMediaControl("stop") {
            val controlPointImpl = upnpService.getControlPoint() as? com.yinnho.upnpcast.internal.ControlPointImpl
            controlPointImpl?.stopPlayback() ?: false
        }
    }
    
    /**
     * Set volume
     */
    fun setVolume(volume: Int): Boolean {
        val clampedVolume = volume.coerceIn(0, 100)
        return executeMediaControl("set volume to $clampedVolume") {
            val controlPointImpl = upnpService.getControlPoint() as? com.yinnho.upnpcast.internal.ControlPointImpl
            controlPointImpl?.setVolume(clampedVolume) ?: false
        }
    }
    
    /**
     * Get all discovered devices
     */
    fun getAllDevices(): List<RemoteDevice> {
        return upnpService.getRegistry().getDevices().toList()
    }
    
    /**
     * Get currently connected device
     */
    fun getCurrentDevice(): RemoteDevice? {
        return upnpService.getControlPoint().getCurrentDevice()
    }
    
    /**
     * Release resources
     */
    fun release() {
        Log.d(TAG, "Releasing resources")
        upnpService.getRegistry().removeListener(registryListener)
        upnpService.shutdown()
        castListener = null
        playbackStateListener = null
        INSTANCE = null
    }
    
    /**
     * Execute media control operation - Generic method
     */
    private fun executeMediaControl(operation: String, action: () -> Boolean): Boolean {
        val currentDevice = upnpService.getControlPoint().getCurrentDevice()
        if (currentDevice == null) {
            Log.w(TAG, "No connected device")
            return false
        }

        return try {
            Log.d(TAG, "Executing operation: $operation")
            val success = action()
            if (!success) {
                Log.w(TAG, "Operation failed: $operation")
            }
            success

        } catch (e: Exception) {
            Log.e(TAG, "Operation exception: $operation", e)
            val error = DLNAException(DLNAErrorType.PLAYBACK_ERROR, "Operation failed: $operation", e)
            castListener?.onError(error)
            false
        }
    }
    
    /**
     * Set mute
     */
    fun setMute(mute: Boolean): Boolean {
        return executeMediaControl("set mute: $mute") {
            val controlPointImpl = upnpService.getControlPoint() as? com.yinnho.upnpcast.internal.ControlPointImpl
            controlPointImpl?.setMute(mute) ?: false
        }
    }
    
    /**
     * Clear device cache
     */
    fun clearDeviceCache() {
        Log.d(TAG, "Clearing device cache")
        (upnpService.getRegistry() as? com.yinnho.upnpcast.internal.RegistryImpl)?.clearDevices()
    }
    
    /**
     * Pause playback - Alias for pause()
     */
    fun pausePlayback(): Boolean {
        return pause()
    }
    
    /**
     * Resume playback - Alias for resume()
     */
    fun resumePlayback(): Boolean {
        return resume()
    }
    
    /**
     * Stop playback - Alias for stop()
     */
    fun stopPlayback(): Boolean {
        return stop()
    }
    
    /**
     * Seek to position
     */
    fun seekTo(positionMs: Long): Boolean {
        return executeMediaControl("seek to ${positionMs}ms") {
            val controlPointImpl = upnpService.getControlPoint() as? com.yinnho.upnpcast.internal.ControlPointImpl
            controlPointImpl?.seekTo(positionMs) ?: false
        }
    }
    
    /**
     * Set debug mode
     */
    fun setDebugMode(enabled: Boolean) {
        Log.d(TAG, "Debug mode set to: $enabled")
        // This is a placeholder - in a real implementation you might configure logging levels
    }
    
    /**
     * Set search timeout
     */
    fun setSearchTimeout(timeoutMs: Long) {
        Log.d(TAG, "Search timeout set to: ${timeoutMs}ms")
        // This is a placeholder - in a real implementation you might configure discovery timeout
    }
} 