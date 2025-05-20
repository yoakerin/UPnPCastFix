package com.yinnho.upnpcast.helper

import android.content.Context
import com.yinnho.upnpcast.cache.UPnPCacheManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

/**
 * 缓存统计信息捕获工具
 * 用于测试过程中自动记录缓存命中率等统计信息
 */
class CacheStatisticsCapture(private val context: Context) {
    private var timer: Timer? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val logFile: File
    
    init {
        // 使用应用的内部存储空间
        val logDir = File(context.filesDir, "test-logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        logFile = File(logDir, "cache_stats_${dateFormat.format(Date())}.log")
    }
    
    /**
     * 开始周期性记录缓存统计信息
     * @param intervalSeconds 记录间隔（秒）
     */
    fun startCapture(intervalSeconds: Long = 10) {
        timer = Timer().apply {
            scheduleAtFixedRate(0, intervalSeconds * 1000) {
                try {
                    val cacheSummary = UPnPCacheManager.getInstance().getCacheSummary()
                    val timestamp = dateFormat.format(Date())
                    val logEntry = "[$timestamp]\n$cacheSummary\n" +
                            "----------------------------------------\n"
                    
                    logFile.appendText(logEntry)
                } catch (e: Exception) {
                    logFile.appendText("[ERROR] ${e.message}\n")
                }
            }
        }
    }
    
    /**
     * 停止记录
     */
    fun stopCapture() {
        timer?.cancel()
        timer = null
    }
    
    /**
     * 手动记录一次缓存状态，带附加信息
     */
    fun captureSnapshot(label: String) {
        try {
            val cacheSummary = UPnPCacheManager.getInstance().getCacheSummary()
            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] SNAPSHOT: $label\n$cacheSummary\n" +
                    "========================================\n"
            
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            logFile.appendText("[ERROR] ${e.message}\n")
        }
    }
    
    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String {
        return logFile.absolutePath
    }
    
    companion object {
        private var instance: CacheStatisticsCapture? = null
        
        @JvmStatic
        fun getInstance(context: Context): CacheStatisticsCapture {
            if (instance == null) {
                instance = CacheStatisticsCapture(context)
            }
            return instance!!
        }
    }
} 