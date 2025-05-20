package com.yinnho.upnpcast.helper

import android.app.ActivityManager
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

/**
 * 内存监控工具
 * 用于测试过程中记录内存使用情况
 */
class MemoryMonitor(private val context: Context) {
    private var timer: Timer? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val logFile: File
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    init {
        // 使用应用的内部存储空间
        val logDir = File(context.filesDir, "test-logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        logFile = File(logDir, "memory_usage_${dateFormat.format(Date())}.csv")
        
        // 创建CSV表头
        logFile.writeText("Timestamp,Total Memory(MB),Free Memory(MB),Used Memory(MB),App Memory(MB),Is Low Memory\n")
    }
    
    /**
     * 开始周期性记录内存使用情况
     * @param intervalSeconds 记录间隔（秒）
     */
    fun startMonitoring(intervalSeconds: Long = 5) {
        timer = Timer().apply {
            scheduleAtFixedRate(0, intervalSeconds * 1000) {
                try {
                    captureMemorySnapshot("periodic")
                } catch (e: Exception) {
                    logFile.appendText("ERROR,${e.message}\n")
                }
            }
        }
    }
    
    /**
     * 停止监控
     */
    fun stopMonitoring() {
        timer?.cancel()
        timer = null
    }
    
    /**
     * 手动记录一次内存状态，带标签
     */
    fun captureMemorySnapshot(label: String) {
        try {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val timestamp = dateFormat.format(Date())
            val totalMemory = memoryInfo.totalMem / (1024 * 1024) // MB
            val freeMemory = memoryInfo.availMem / (1024 * 1024) // MB
            val usedMemory = totalMemory - freeMemory
            
            // 获取应用内存使用
            val pid = android.os.Process.myPid()
            val pInfo = activityManager.runningAppProcesses.find { it.pid == pid }
            val appMemory = if (pInfo != null) {
                val pMemInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid))
                pMemInfo[0].totalPss / 1024 // MB
            } else {
                0
            }
            
            val isLowMemory = memoryInfo.lowMemory
            
            val logEntry = "$timestamp,$totalMemory,$freeMemory,$usedMemory,$appMemory,$isLowMemory,$label\n"
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            logFile.appendText("$dateFormat.format(Date()),ERROR,${e.message}\n")
        }
    }
    
    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String {
        return logFile.absolutePath
    }
    
    companion object {
        private var instance: MemoryMonitor? = null
        
        @JvmStatic
        fun getInstance(context: Context): MemoryMonitor {
            if (instance == null) {
                instance = MemoryMonitor(context)
            }
            return instance!!
        }
    }
} 