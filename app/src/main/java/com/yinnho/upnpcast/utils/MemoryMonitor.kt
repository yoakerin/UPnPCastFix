package com.yinnho.upnpcast.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import com.yinnho.upnpcast.core.EnhancedThreadManager

/**
 * 内存监控工具
 * 用于监控应用内存使用情况，提供内存使用统计和内存优化建议
 */
object MemoryMonitor {
    private val TAG = "MemoryMonitor"
    
    // 默认内存监控间隔
    private const val DEFAULT_MONITOR_INTERVAL_MS = 60_000L // 1分钟
    
    // 内存警告阈值（占总内存的百分比）
    private const val MEMORY_WARNING_THRESHOLD = 0.75f
    
    // 内存危险阈值（占总内存的百分比）
    private const val MEMORY_DANGER_THRESHOLD = 0.85f
    
    // 内存使用记录
    private val memoryUsageRecords = mutableListOf<MemoryUsageRecord>()
    
    // 最大记录数量
    private const val MAX_RECORD_COUNT = 100
    
    // 内存监控任务
    private var monitorTask: ScheduledFuture<*>? = null
    
    // 内存监控状态
    private var isMonitoring = false
    
    /**
     * 内存使用记录
     */
    data class MemoryUsageRecord(
        val timestamp: Long,           // 记录时间戳
        val totalMemory: Long,         // 总内存（字节）
        val usedMemory: Long,          // 已使用内存（字节）
        val nativeHeapSize: Long,      // 原生堆大小（字节）
        val nativeHeapUsed: Long,      // 已用原生堆（字节）
        val vmHeapSize: Long,          // VM堆大小（字节）
        val vmHeapUsed: Long,          // 已用VM堆（字节）
        val isLowMemory: Boolean       // 是否低内存
    )
    
    /**
     * 内存监听器接口
     */
    interface MemoryListener {
        /**
         * 当内存使用率达到警告阈值时调用
         */
        fun onMemoryWarning(usage: Float, record: MemoryUsageRecord)
        
        /**
         * 当内存使用率达到危险阈值时调用
         */
        fun onMemoryDanger(usage: Float, record: MemoryUsageRecord)
        
        /**
         * 当内存记录更新时调用
         */
        fun onMemoryRecordUpdated(record: MemoryUsageRecord)
    }
    
    // 内存监听器列表
    private val memoryListeners = mutableListOf<MemoryListener>()
    
    /**
     * 添加内存监听器
     */
    fun addMemoryListener(listener: MemoryListener) {
        synchronized(memoryListeners) {
            memoryListeners.add(listener)
        }
    }
    
    /**
     * 移除内存监听器
     */
    fun removeMemoryListener(listener: MemoryListener) {
        synchronized(memoryListeners) {
            memoryListeners.remove(listener)
        }
    }
    
    /**
     * 开始内存监控
     * 
     * @param context 上下文
     * @param intervalMs 监控间隔（毫秒）
     */
    fun startMonitoring(context: Context, intervalMs: Long = DEFAULT_MONITOR_INTERVAL_MS) {
        if (isMonitoring) {
            EnhancedThreadManager.d(TAG, "内存监控已在运行中")
            return
        }
        
        EnhancedThreadManager.d(TAG, "开始内存监控，间隔: ${intervalMs}ms")
        
        // 创建监控任务
        monitorTask = EnhancedThreadManager.scheduleWithFixedDelay(
            {
                try {
                    val record = collectMemoryInfo(context)
                    addMemoryRecord(record)
                    
                    // 检查内存使用率
                    val memoryUsage = record.usedMemory.toFloat() / record.totalMemory
                    
                    // 通知监听器
                    notifyListeners(record, memoryUsage)
                    
                } catch (e: Exception) {
                    EnhancedThreadManager.e(TAG, "内存监控异常", e)
                }
            },
            0,
            intervalMs,
            TimeUnit.MILLISECONDS
        )
        
        isMonitoring = true
    }
    
    /**
     * 停止内存监控
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }
        
        monitorTask?.cancel(false)
        monitorTask = null
        isMonitoring = false
        
        EnhancedThreadManager.d(TAG, "停止内存监控")
    }
    
    /**
     * 收集当前内存信息
     */
    fun collectMemoryInfo(context: Context): MemoryUsageRecord {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        // 获取原生堆信息
        val nativeHeapSize = Debug.getNativeHeapSize()
        val nativeHeapUsed = Debug.getNativeHeapAllocatedSize()
        
        // 获取Java堆信息
        val runtime = Runtime.getRuntime()
        val vmHeapSize = runtime.totalMemory()
        val vmHeapUsed = vmHeapSize - runtime.freeMemory()
        
        return MemoryUsageRecord(
            timestamp = System.currentTimeMillis(),
            totalMemory = memoryInfo.totalMem,
            usedMemory = memoryInfo.totalMem - memoryInfo.availMem,
            nativeHeapSize = nativeHeapSize,
            nativeHeapUsed = nativeHeapUsed,
            vmHeapSize = vmHeapSize,
            vmHeapUsed = vmHeapUsed,
            isLowMemory = memoryInfo.lowMemory
        )
    }
    
    /**
     * 添加内存记录
     */
    private fun addMemoryRecord(record: MemoryUsageRecord) {
        synchronized(memoryUsageRecords) {
            memoryUsageRecords.add(record)
            
            // 保持记录列表在最大大小以内
            if (memoryUsageRecords.size > MAX_RECORD_COUNT) {
                memoryUsageRecords.removeAt(0)
            }
        }
    }
    
    /**
     * 获取内存使用记录
     */
    fun getMemoryRecords(): List<MemoryUsageRecord> {
        synchronized(memoryUsageRecords) {
            return memoryUsageRecords.toList()
        }
    }
    
    /**
     * 获取当前内存使用率
     */
    fun getCurrentMemoryUsage(context: Context): Float {
        val record = collectMemoryInfo(context)
        return record.usedMemory.toFloat() / record.totalMemory
    }
    
    /**
     * 获取最近一次内存记录
     */
    fun getLatestMemoryRecord(): MemoryUsageRecord? {
        synchronized(memoryUsageRecords) {
            return memoryUsageRecords.lastOrNull()
        }
    }
    
    /**
     * 通知所有监听器
     */
    internal fun notifyListeners(record: MemoryUsageRecord, memoryUsage: Float) {
        synchronized(memoryListeners) {
            // 首先通知记录更新
            for (listener in memoryListeners) {
                listener.onMemoryRecordUpdated(record)
            }
            
            // 检查是否达到警告阈值
            if (memoryUsage >= MEMORY_DANGER_THRESHOLD) {
                EnhancedThreadManager.w(TAG, "内存使用率达到危险阈值: ${memoryUsage * 100}%")
                
                for (listener in memoryListeners) {
                    listener.onMemoryDanger(memoryUsage, record)
                }
            } else if (memoryUsage >= MEMORY_WARNING_THRESHOLD) {
                EnhancedThreadManager.w(TAG, "内存使用率达到警告阈值: ${memoryUsage * 100}%")
                
                for (listener in memoryListeners) {
                    listener.onMemoryWarning(memoryUsage, record)
                }
            }
        }
    }
    
    /**
     * 清理内存
     * 释放缓存和不必要的资源
     */
    fun cleanupMemory() {
        EnhancedThreadManager.d(TAG, "执行内存清理")
        System.gc()
        System.runFinalization()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopMonitoring()
        
        synchronized(memoryUsageRecords) {
            memoryUsageRecords.clear()
        }
        
        synchronized(memoryListeners) {
            memoryListeners.clear()
        }
        
        EnhancedThreadManager.d(TAG, "内存监控资源已释放")
    }
    
    /**
     * 测试用：触发内存警告通知
     */
    internal fun notifyMemoryWarning(usage: Float, record: MemoryUsageRecord) {
        synchronized(memoryListeners) {
            for (listener in memoryListeners) {
                listener.onMemoryWarning(usage, record)
            }
        }
    }
    
    /**
     * 测试用：触发内存危险通知
     */
    internal fun notifyMemoryDanger(usage: Float, record: MemoryUsageRecord) {
        synchronized(memoryListeners) {
            for (listener in memoryListeners) {
                listener.onMemoryDanger(usage, record)
            }
        }
    }
    
    /**
     * 测试用：触发内存记录更新通知
     */
    internal fun notifyMemoryRecordUpdated(record: MemoryUsageRecord) {
        synchronized(memoryListeners) {
            for (listener in memoryListeners) {
                listener.onMemoryRecordUpdated(record)
            }
        }
    }
} 