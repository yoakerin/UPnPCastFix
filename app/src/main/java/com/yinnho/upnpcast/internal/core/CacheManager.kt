package com.yinnho.upnpcast.internal.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.yinnho.upnpcast.internal.discovery.RemoteDevice
import com.yinnho.upnpcast.internal.media.DlnaMediaController

/**
 * Cache manager for volume and progress caching
 * Simplifies caching logic in CoreManager
 */
internal class CacheManager(
    private val coreScope: CoroutineScope
) {
    companion object {
        private const val TAG = "CacheManager"
        private const val VOLUME_CACHE_DURATION = 10000L // 10 seconds volume cache validity period
        private const val PROGRESS_CACHE_DURATION = 5000L // 5 seconds progress cache validity period
    }
    
    @Volatile
    private var cachedVolume: Int = -1
    @Volatile
    private var cachedMuted: Boolean = false
    @Volatile
    private var lastVolumeUpdate: Long = 0
    
    @Volatile
    private var cachedCurrentMs: Long = 0L
    @Volatile
    private var cachedTotalMs: Long = 0L
    @Volatile
    private var lastProgressUpdate: Long = 0L
    @Volatile
    private var isPlaying: Boolean = false
    
    @Volatile
    private var volumeRefreshJob: Job? = null
    @Volatile
    private var progressRefreshJob: Job? = null
    
    /**
     * Get volume with caching and interpolation
     */
    fun getVolume(
        device: RemoteDevice?,
        callback: (volume: Int?, isMuted: Boolean?, success: Boolean) -> Unit
    ) {
        if (device == null) {
            callback(null, null, false)
            return
        }
        
        val now = System.currentTimeMillis()
        if (now - lastVolumeUpdate < VOLUME_CACHE_DURATION && cachedVolume >= 0) {
            callback(cachedVolume, cachedMuted, true)
            refreshVolumeCacheAsync(device)
        } else {
            refreshVolumeCache(device) { success ->
                if (success) {
                    callback(cachedVolume, cachedMuted, true)
                } else {
                    callback(null, null, false)
                }
            }
        }
    }
    
    /**
     * Get progress with caching and interpolation
     */
    fun getProgress(
        device: RemoteDevice?,
        callback: (currentMs: Long, totalMs: Long, success: Boolean) -> Unit
    ) {
        if (device == null) {
            callback(0L, 0L, false)
            return
        }
        
        val now = System.currentTimeMillis()
        val timeSinceLastUpdate = now - lastProgressUpdate
        
        if (timeSinceLastUpdate < PROGRESS_CACHE_DURATION && cachedTotalMs > 0) {
            val estimatedProgress = if (isPlaying) {
                (cachedCurrentMs + timeSinceLastUpdate).coerceAtMost(cachedTotalMs)
            } else {
                cachedCurrentMs
            }
            callback(estimatedProgress, cachedTotalMs, true)
            refreshProgressCacheAsync(device)
        } else {
            refreshProgressCache(device) { success ->
                callback(cachedCurrentMs, cachedTotalMs, success)
            }
        }
    }
    
    /**
     * Refresh volume cache from device
     */
    fun refreshVolumeCache(device: RemoteDevice, callback: (success: Boolean) -> Unit = {}) {
        coreScope.launch {
            try {
                val controller = DlnaMediaController.getController(device)
                val volume = controller.getVolumeAsync()
                val muted = controller.getMuteAsync()
                
                if (volume != null) {
                    updateVolumeCache(volume, muted ?: false)
                    callback(true)
                } else {
                    callback(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh volume cache: ${e.message}")
                callback(false)
            }
        }
    }
    
    /**
     * Refresh progress cache from device
     */
    fun refreshProgressCache(device: RemoteDevice, callback: (success: Boolean) -> Unit = {}) {
        coreScope.launch {
            try {
                val controller = DlnaMediaController.getController(device)
                val progressInfo = controller.getPositionInfo()
                
                if (progressInfo != null) {
                    val (currentMs, totalMs) = progressInfo
                    updateProgressCache(currentMs, totalMs, System.currentTimeMillis())
                    callback(true)
                } else {
                    callback(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh progress cache: ${e.message}")
                callback(false)
            }
        }
    }
    
    /**
     * Async refresh volume cache (prevent duplicate requests)
     */
    private fun refreshVolumeCacheAsync(device: RemoteDevice) {
        if (volumeRefreshJob?.isActive == true) return
        
        volumeRefreshJob = coreScope.launch {
            try {
                val controller = DlnaMediaController.getController(device)
                val volume = controller.getVolumeAsync()
                val muted = controller.getMuteAsync()
                
                if (volume != null) {
                    updateVolumeCache(volume, muted ?: false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh volume cache async: ${e.message}")
            }
        }
    }
    
    /**
     * Async refresh progress cache (prevent duplicate requests)
     */
    private fun refreshProgressCacheAsync(device: RemoteDevice) {
        if (progressRefreshJob?.isActive == true) return
        
        progressRefreshJob = coreScope.launch {
            try {
                val controller = DlnaMediaController.getController(device)
                val progressInfo = controller.getPositionInfo()
                
                if (progressInfo != null) {
                    val (currentMs, totalMs) = progressInfo
                    updateProgressCache(currentMs, totalMs, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh progress cache async: ${e.message}")
            }
        }
    }
    
    private fun updateVolumeCache(volume: Int, muted: Boolean) {
        cachedVolume = volume
        cachedMuted = muted
        lastVolumeUpdate = System.currentTimeMillis()
    }
    
    private fun updateProgressCache(currentMs: Long, totalMs: Long, timestamp: Long) {
        cachedCurrentMs = currentMs
        cachedTotalMs = totalMs
        lastProgressUpdate = timestamp
        updatePlayingState()
    }
    
    private fun updatePlayingState() {
        isPlaying = cachedTotalMs > 0
    }
    
    /**
     * Clear all cached data
     */
    fun clearAll() {
        cachedVolume = -1
        cachedMuted = false
        lastVolumeUpdate = 0
        
        cachedCurrentMs = 0L
        cachedTotalMs = 0L
        lastProgressUpdate = 0L
        isPlaying = false
        
        volumeRefreshJob?.cancel()
        progressRefreshJob?.cancel()
        volumeRefreshJob = null
        progressRefreshJob = null
    }
    
    /**
     * Get current volume state
     */
    fun getVolumeState(): Pair<Int, Boolean> = Pair(cachedVolume, cachedMuted)
} 