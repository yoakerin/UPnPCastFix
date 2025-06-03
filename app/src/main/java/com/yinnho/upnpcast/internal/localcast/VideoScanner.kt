package com.yinnho.upnpcast.internal.localcast

import android.content.Context
import android.util.Log
import com.yinnho.upnpcast.DLNACast
import com.yinnho.upnpcast.internal.utils.FileUtils

/**
 * Video scanner
 * Responsible for scanning and managing local video files
 * 
 * Video scanning logic extracted from DLNACastImpl
 */
internal class VideoScanner(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoScanner"
        
        // Scan related constants (moved from DLNACastImpl)
        private const val VIDEO_MIME_TYPE_FILTER = "video/%"
        private const val UNKNOWN_VIDEO_TITLE = "Unknown Video"
        private const val MIN_VALID_FILE_SIZE = 0L
        
        // Memory optimization related constants (moved from DLNACastImpl)
        private const val BATCH_SIZE = 50 // Number of videos processed per batch
        private const val MAX_VIDEO_COUNT = 1000 // Maximum video count limit
    }
    
    /**
     * Scan local video files
     * 
     * Use MediaStore API to scan all video files on device and filter out valid castable files
     * 
     * @param callback Scan completion callback, returns video file list
     */
    fun scanLocalVideos(callback: (videos: List<DLNACast.LocalVideo>) -> Unit) {
        Log.d(TAG, "Starting to scan local video files")
        Thread {
            try {
                val videos = mutableListOf<DLNACast.LocalVideo>()
                
                // Define MediaStore query field projection
                // These fields contain basic video file information: ID, title, path, duration, size, etc.
                val projection = arrayOf(
                    android.provider.MediaStore.Video.Media._ID,
                    android.provider.MediaStore.Video.Media.TITLE,
                    android.provider.MediaStore.Video.Media.DATA,           // File path
                    android.provider.MediaStore.Video.Media.DURATION,      // Duration (milliseconds)
                    android.provider.MediaStore.Video.Media.SIZE,          // File size (bytes)
                    android.provider.MediaStore.Video.Media.DISPLAY_NAME   // Display name
                )
                
                // Build SQL WHERE condition: only query video type media files
                // MIME_TYPE LIKE 'video/%' can match all video formats (mp4, mkv, avi, etc.)
                val selection = "${android.provider.MediaStore.Video.Media.MIME_TYPE} LIKE '$VIDEO_MIME_TYPE_FILTER'"
                Log.d(TAG, "MediaStore query condition: $selection")
                
                // Execute MediaStore query
                // Sort by modification time in descending order, newest video files first
                val cursor = try {
                    context.contentResolver.query(
                        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        null,
                        "${android.provider.MediaStore.Video.Media.DATE_MODIFIED} DESC"
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "MediaStore query permission denied: ${e.message}")
                    callback(emptyList())
                    return@Thread
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "MediaStore query parameter error: ${e.message}")
                    callback(emptyList())
                    return@Thread
                }
                
                cursor?.use {
                    val totalRows = it.count
                    Log.d(TAG, "MediaStore scanned $totalRows video records")
                    
                    // Get column index positions for subsequent data extraction
                    val idColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                    val titleColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.TITLE)
                    val dataColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
                    val durationColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)
                    val sizeColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.SIZE)
                    val displayNameColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
                    
                    // Statistics variables: track processing progress and valid file count
                    var processedCount = 0
                    var validCount = 0
                    var securityErrorCount = 0
                    var ioErrorCount = 0
                    
                    // Iterate through query results, process each video record individually
                    while (it.moveToNext()) {
                        try {
                            processedCount++
                            
                            // Memory protection: limit maximum video count to prevent memory overflow
                            if (validCount >= MAX_VIDEO_COUNT) {
                                Log.w(TAG, "Reached maximum video count limit ($MAX_VIDEO_COUNT), stopping scan")
                                break
                            }
                            
                            // Batch processing: perform garbage collection after processing a certain number
                            if (processedCount % BATCH_SIZE == 0) {
                                Log.d(TAG, "Processed $processedCount records, triggering garbage collection")
                                System.gc() // Suggest garbage collection
                                
                                // Check available memory
                                val runtime = Runtime.getRuntime()
                                val maxMemory = runtime.maxMemory()
                                val totalMemory = runtime.totalMemory()
                                val freeMemory = runtime.freeMemory()
                                val usedMemory = totalMemory - freeMemory
                                val memoryUsagePercent = (usedMemory * 100) / maxMemory
                                
                                Log.d(TAG, "Memory usage: ${memoryUsagePercent}% (${usedMemory / (1024 * 1024)}MB/${maxMemory / (1024 * 1024)}MB)")
                                
                                // If memory usage is too high, pause processing
                                if (memoryUsagePercent > 80) {
                                    Log.w(TAG, "Memory usage too high (${memoryUsagePercent}%), pausing processing to avoid OOM")
                                    Thread.sleep(100) // Brief pause to let GC work
                                }
                            }
                            
                            // Extract video basic information
                            val id = it.getString(idColumn)
                            val title = it.getString(titleColumn) ?: it.getString(displayNameColumn) ?: UNKNOWN_VIDEO_TITLE
                            val path = it.getString(dataColumn)
                            val durationMs = it.getLong(durationColumn)
                            val sizeBytes = it.getLong(sizeColumn)
                            
                            Log.v(TAG, "Processing video $processedCount/$totalRows: $title")
                            Log.v(TAG, "  Path: $path")
                            Log.v(TAG, "  Size: ${FileUtils.formatFileSize(sizeBytes)}, Duration: ${FileUtils.formatDuration(durationMs)}")
                            
                            // File validity verification: filter out invalid or inaccessible files
                            if (!path.isNullOrEmpty()) {
                                try {
                                    val file = java.io.File(path)
                                    // Check if file actually exists and is not empty
                                    // This step is important: MediaStore may contain stale records of deleted files
                                    if (file.exists() && file.length() > MIN_VALID_FILE_SIZE) {
                                        // Additional file validity check
                                        if (file.isFile && file.canRead()) {
                                            // Simple video file extension check as additional verification
                                            val fileName = file.name.lowercase()
                                            val isVideoFile = FileUtils.isVideoFile(fileName)
                                            
                                            if (isVideoFile) {
                                                validCount++
                                                
                                                // Format display information: convert duration and file size to user-friendly format
                                                val duration = FileUtils.formatDuration(durationMs)
                                                val size = FileUtils.formatFileSize(sizeBytes)
                                                
                                                // Create LocalVideo object and add to result list
                                                videos.add(DLNACast.LocalVideo(
                                                    id = id,
                                                    title = title,
                                                    path = path,
                                                    duration = duration,
                                                    size = size,
                                                    durationMs = durationMs
                                                ))
                                                Log.d(TAG, "✅ Added valid video: $title")
                                            } else {
                                                Log.w(TAG, "❌ Unsupported video format: $fileName")
                                            }
                                        } else {
                                            Log.w(TAG, "❌ File not readable or not a regular file: $path")
                                        }
                                    } else {
                                        Log.w(TAG, "❌ File does not exist or is empty: $path")
                                    }
                                } catch (e: SecurityException) {
                                    // Handle permission exceptions: some files may be inaccessible due to permission restrictions
                                    securityErrorCount++
                                    Log.w(TAG, "❌ Permission denied accessing file: $path, reason: ${e.message}")
                                } catch (e: java.io.IOException) {
                                    // Handle IO exceptions: file system errors
                                    ioErrorCount++
                                    Log.w(TAG, "❌ IO error accessing file: $path, reason: ${e.message}")
                                } catch (e: Exception) {
                                    // Handle other file access exceptions
                                    Log.w(TAG, "❌ Unknown error accessing file: $path, reason: ${e.message}")
                                }
                            } else {
                                Log.w(TAG, "❌ Path is empty, skipping record")
                            }
                        } catch (e: Exception) {
                            // Handle single record processing exceptions, don't affect processing of other records
                            Log.w(TAG, "Error processing video record: ${e.message}, continuing with next record")
                        }
                    }
                    
                    // Output detailed statistics, including error statistics
                    Log.i(TAG, "Scan completed: Total records=$totalRows, Processed=$processedCount, Valid=$validCount")
                    if (securityErrorCount > 0) {
                        Log.w(TAG, "Security error count: $securityErrorCount")
                    }
                    if (ioErrorCount > 0) {
                        Log.w(TAG, "IO error count: $ioErrorCount")
                    }
                } ?: run {
                    Log.w(TAG, "MediaStore query returned null cursor")
                }
                
                Log.i(TAG, "Final result: Found ${videos.size} usable video files")
                callback(videos)
                
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Not enough memory to complete video scan: ${e.message}")
                callback(emptyList())
            } catch (e: SecurityException) {
                Log.e(TAG, "Insufficient permissions to access media library: ${e.message}")
                callback(emptyList())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scan local videos: ${e.message}", e)
                callback(emptyList())
            }
        }.start()
    }
} 