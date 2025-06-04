package com.yinnho.upnpcast.internal.media

import android.util.Log
import com.yinnho.upnpcast.internal.discovery.RemoteDevice
import com.yinnho.upnpcast.types.MediaAction
import kotlinx.coroutines.runBlocking

/**
 * Media player
 * Responsible for unified management of DLNA device media playback control
 * 
 * Media playback logic extracted from DLNACastImpl
 */
internal class MediaPlayer {
    
    companion object {
        private const val TAG = "MediaPlayer"
        
        /**
         * Play media file to specified device
         * 
         * @param device Target device
         * @param url Media URL
         * @param title Media title
         * @param callback Playback result callback
         */
        fun playMedia(device: RemoteDevice, url: String, title: String, callback: (success: Boolean) -> Unit) {
            Thread {
                try {
                    val controller = DlnaMediaController.getController(device)
                    val success = runBlocking { controller.playMediaDirect(url, title) }
                    callback(success)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to play media: ${e.message}")
                    callback(false)
                }
            }.start()
        }
        
        /**
         * Control media playback
         * 
         * @param device Current playback device
         * @param action Control action
         * @param value Control parameter (such as volume value, playback position, etc.)
         * @param callback Control result callback
         */
        fun controlMedia(device: RemoteDevice, action: MediaAction, value: Any?, callback: (success: Boolean) -> Unit) {
            Thread {
                try {
                    val controller = DlnaMediaController.getController(device)
                    val success = runBlocking {
                        when (action) {
                            MediaAction.PLAY -> controller.play()
                            MediaAction.PAUSE -> controller.pause()
                            MediaAction.STOP -> controller.stopDirect()
                            MediaAction.VOLUME -> {
                                val volume = value as? Int ?: return@runBlocking false
                                controller.setVolumeAsync(volume)
                            }
                            MediaAction.MUTE -> {
                                val mute = value as? Boolean ?: true
                                controller.setMuteAsync(mute)
                            }
                            MediaAction.SEEK -> {
                                val position = value as? Long ?: return@runBlocking false
                                controller.seekTo(position)
                            }
                            MediaAction.GET_STATE -> {
                                // State query, return success directly
                                true
                            }
                        }
                    }
                    callback(success)
                } catch (e: Exception) {
                    Log.e(TAG, "Control action failed: $action", e)
                    callback(false)
                }
            }.start()
        }
        
        /**
         * Get playback progress
         * 
         * @param device Current playback device
         * @param callback Progress information callback (currentMs, totalMs, success)
         */
        fun getProgress(device: RemoteDevice, callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit) {
            Thread {
                try {
                    val controller = DlnaMediaController.getController(device)
                    val progressInfo = runBlocking { controller.getPositionInfo() }
                    if (progressInfo != null) {
                        val (currentMs, totalMs) = progressInfo
                        callback(currentMs, totalMs, true)
                    } else {
                        callback(0L, 0L, false)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get progress: ${e.message}")
                    callback(0L, 0L, false)
                }
            }.start()
        }
        
        /**
         * Get current volume
         * 
         * @param device Current playback device
         * @param callback Volume information callback (volume, isMuted, success)
         */
        fun getCurrentVolume(device: RemoteDevice, callback: (volume: Int?, isMuted: Boolean?, success: Boolean) -> Unit) {
            Thread {
                try {
                    val controller = DlnaMediaController.getController(device)
                    val volume = runBlocking { controller.getVolumeAsync() }
                    val isMuted = runBlocking { controller.getMuteAsync() }
                    callback(volume, isMuted, volume != null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get volume: ${e.message}")
                    callback(null, null, false)
                }
            }.start()
        }
        
        /**
         * Clean up all media controllers
         */
        fun cleanup() {
            DlnaMediaController.clearAllControllers()
        }
    }
} 