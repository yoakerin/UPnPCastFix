package com.yinnho.upnpcast.internal

import android.util.Log
import com.yinnho.upnpcast.*

/**
 * Control point implementation - Based on Cling patterns but greatly simplified
 */
internal class ControlPointImpl(private val registry: Registry) : ControlPoint {
    
    private val TAG = "ControlPointImpl"
    
    // Currently connected device
    private var currentDevice: RemoteDevice? = null
    
    override fun search() {
        Log.d(TAG, "Starting search for all devices")
        registry.startDiscovery()
    }
    
    override fun search(deviceType: String) {
        Log.d(TAG, "Searching for device type: $deviceType")
        registry.startDiscovery()
    }
    
    override fun execute(action: Action) {
        Log.d(TAG, "Executing action: ${action.getName()}")
        try {
            executeAction(action)
            Log.d(TAG, "Action executed successfully: ${action.getName()}")
        } catch (e: Exception) {
            Log.w(TAG, "Action execution failed: ${action.getName()}, error: ${e.message}")
        }
    }
    
    override fun connectToDevice(device: RemoteDevice): Boolean {
        return try {
            Log.d(TAG, "Connecting to device: ${device.displayName} (${device.id})")
            
            // Validate basic device information
            val location = device.details["location"] ?: device.details["Location"]
            if (device.address.isBlank() || (location as? String).isNullOrBlank()) {
                Log.w(TAG, "Device information incomplete, cannot connect: address=${device.address}, location=$location")
                return false
            }
            
            // Check for required service information
            val services = device.details["services"] as? List<*>
            if (services.isNullOrEmpty()) {
                Log.w(TAG, "Device service information missing, cannot connect")
                return false
            }
            
            // Create media controller for this device
            val mediaController = DlnaMediaController(device)
            
            // Validate if device has necessary AVTransport service
            val avTransportUrl = mediaController.buildAVTransportUrl()
            if (avTransportUrl == null) {
                Log.w(TAG, "Device lacks AVTransport service, cannot connect")
                return false
            }
            
            // If all checks pass, set as current device
            currentDevice = device
            Log.d(TAG, "Successfully connected to device: ${device.displayName}, service count: ${services.size}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to device: ${device.displayName}", e)
            false
        }
    }
    
    override fun disconnect() {
        currentDevice?.let { device ->
            Log.d(TAG, "Disconnecting from device: ${device.displayName}")
            currentDevice = null
        } ?: Log.d(TAG, "No connected device")
    }
    
    override fun getCurrentDevice(): RemoteDevice? = currentDevice
    
    /**
     * Play media - Direct implementation using DLNA controller
     */
    fun playMedia(url: String, title: String): Boolean {
        val device = currentDevice ?: return false
        
        return try {
            Log.d(TAG, "Playing media: $url, title: $title")
            
            // Create media controller and call play
            val controller = DlnaMediaController(device)
            val success = controller.playMedia(url, title ?: "Unknown")
            
            if (success) {
                Log.d(TAG, "Playback successful: $title")
            } else {
                Log.w(TAG, "Playback failed: $title")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play media", e)
            false
        }
    }
    
    /**
     * Stop playback
     */
    fun stopPlayback(): Boolean {
        val device = currentDevice ?: return false
        
        return try {
            Log.d(TAG, "Stopping playback")
            
            val controller = DlnaMediaController(device)
            val success = controller.stopPlayback()
            
            if (success) {
                Log.d(TAG, "Stop playback successful")
            } else {
                Log.w(TAG, "Stop playback failed")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop playback", e)
            false
        }
    }
    
    /**
     * Pause playback
     */
    fun pausePlayback(): Boolean {
        val device = currentDevice ?: return false
        
        return try {
            Log.d(TAG, "Pausing playback")
            
            val controller = DlnaMediaController(device)
            val success = controller.pausePlayback()
            
            if (success) {
                Log.d(TAG, "Pause playback successful")
            } else {
                Log.w(TAG, "Pause playback failed")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause playback", e)
            false
        }
    }
    
    /**
     * Resume playback
     */
    fun resumePlayback(): Boolean {
        val device = currentDevice ?: return false
        
        return try {
            Log.d(TAG, "Resuming playback")
            
            val controller = DlnaMediaController(device)
            val success = controller.resumePlayback()
            
            if (success) {
                Log.d(TAG, "Resume playback successful")
            } else {
                Log.w(TAG, "Resume playback failed")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume playback", e)
            false
        }
    }
    
    /**
     * Set volume
     */
    fun setVolume(volume: Int): Boolean {
        val device = currentDevice ?: return false
        
        return try {
            Log.d(TAG, "Setting volume: $volume")
            
            val controller = DlnaMediaController(device)
            val success = controller.setVolume(volume)
            
            if (success) {
                Log.d(TAG, "Set volume successful: $volume")
            } else {
                Log.w(TAG, "Set volume failed: $volume")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set volume", e)
            false
        }
    }
    
    /**
     * Seek to position
     */
    fun seekTo(positionMs: Long): Boolean {
        val device = currentDevice ?: return false
        
        return try {
            Log.d(TAG, "Seeking to position: ${positionMs}ms")
            
            val controller = DlnaMediaController(device)
            val success = controller.seekTo(positionMs)
            
            if (success) {
                Log.d(TAG, "Seek successful: ${positionMs}ms")
            } else {
                Log.w(TAG, "Seek failed: ${positionMs}ms")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek", e)
            false
        }
    }
    
    /**
     * Set mute
     */
    fun setMute(mute: Boolean): Boolean {
        val device = currentDevice ?: return false
        
        return try {
            Log.d(TAG, "Setting mute: $mute")
            
            val controller = DlnaMediaController(device)
            val success = controller.setMute(mute)
            
            if (success) {
                Log.d(TAG, "Set mute successful: $mute")
            } else {
                Log.w(TAG, "Set mute failed: $mute")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set mute", e)
            false
        }
    }
    
    // Simple implementation of other methods
    private fun executeAction(action: Action) {
        // Simple implementation - just log the action
        Log.d(TAG, "Action executed: ${action.getName()}")
    }
} 