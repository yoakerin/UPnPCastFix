package com.yinnho.upnpcast.internal.localcast

import android.content.Context
import android.util.Log
import com.yinnho.upnpcast.internal.discovery.RemoteDevice
import com.yinnho.upnpcast.internal.media.MediaPlayer

/**
 * Local casting manager
 * Responsible for unified management of local file casting functionality
 * 
 * Local casting logic extracted from DLNACastImpl
 */
internal class LocalCastManager {
    
    companion object {
        private const val TAG = "LocalCastManager"
        
        /**
         * Cast local file to specified device
         * 
         * @param context Android context
         * @param filePath File path
         * @param device Target device
         * @param title Media title (optional)
         * @param callback Casting result callback
         */
        fun castLocalFile(
            context: Context,
            filePath: String, 
            device: RemoteDevice, 
            title: String?, 
            callback: (success: Boolean, message: String) -> Unit
        ) {
            Thread {
                try {
                    // Verify file exists
                    val file = java.io.File(filePath)
                    if (!file.exists() || !file.isFile) {
                        callback(false, "File not found: $filePath")
                        return@Thread
                    }
                    
                    // Check if file is readable
                    if (!file.canRead()) {
                        callback(false, "File cannot be read, please check permissions: $filePath")
                        return@Thread
                    }
                    
                    // Start file server and get URL
                    val fileUrl = try {
                        LocalFileServer.getInstance(context)
                        LocalFileServer.getFileUrl(filePath)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start local file server: ${e.message}")
                        callback(false, "Failed to start file server: ${e.message}")
                        return@Thread
                    }
                    
                    if (fileUrl == null) {
                        callback(false, "Failed to generate file access URL")
                        return@Thread
                    }
                    
                    Log.i(TAG, "Local file server URL: $fileUrl")
                    
                    // Use MediaPlayer for casting
                    val fileName = file.name
                    val mediaTitle = title ?: fileName
                    
                    MediaPlayer.playMedia(device, fileUrl, mediaTitle) { success ->
                        if (success) {
                            callback(true, "Local file casting successful")
                        } else {
                            callback(false, "Failed to cast to device, please check device connection")
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Local file casting failed: ${e.message}")
                    callback(false, "Local file casting failed: ${e.message}")
                }
            }.start()
        }
        
        /**
         * Get access URL for local file
         * 
         * @param context Android context
         * @param filePath File path
         * @return File access URL, returns null on failure
         */
        fun getLocalFileUrl(context: Context, filePath: String): String? {
            return try {
                val file = java.io.File(filePath)
                
                if (!file.exists() || !file.isFile) {
                    Log.w(TAG, "File not found: $filePath")
                    return null
                }
                
                LocalFileServer.getInstance(context)
                LocalFileServer.getFileUrl(filePath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get local file URL: ${e.message}")
                null
            }
        }
        
        /**
         * Scan local video files
         * 
         * @param context Android context
         * @param callback Scan completion callback
         */
        fun scanLocalVideos(context: Context, callback: (videos: List<com.yinnho.upnpcast.DLNACast.LocalVideo>) -> Unit) {
            val scanner = VideoScanner(context)
            scanner.scanLocalVideos(callback)
        }
        
        /**
         * Clean up local casting resources
         */
        fun cleanup() {
            try {
                LocalFileServer.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing local file server: ${e.message}")
            }
        }
    }
} 