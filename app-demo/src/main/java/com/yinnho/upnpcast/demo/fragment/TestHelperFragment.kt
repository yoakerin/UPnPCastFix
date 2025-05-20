package com.yinnho.upnpcast.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.yinnho.upnpcast.DLNACastManager
import com.yinnho.upnpcast.cache.UPnPCacheManager
import com.yinnho.upnpcast.demo.R
import com.yinnho.upnpcast.helper.CacheStatisticsCapture
import com.yinnho.upnpcast.helper.MemoryMonitor
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule

/**
 * 测试辅助界面
 * 用于测试过程中显示缓存状态和控制测试流程
 */
class TestHelperFragment : Fragment() {
    private lateinit var tvCacheStats: TextView
    private lateinit var tvMemoryStatus: TextView
    private lateinit var tvTestStatus: TextView
    private lateinit var btnStartRecord: Button
    private lateinit var btnStopRecord: Button
    private lateinit var btnRunAutoTest: Button
    
    private var isRecording = false
    private var isTestRunning = false
    private var testTimer: Timer? = null
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_test_helper, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        tvCacheStats = view.findViewById(R.id.tv_cache_stats)
        tvMemoryStatus = view.findViewById(R.id.tv_memory_status)
        tvTestStatus = view.findViewById(R.id.tv_test_status)
        btnStartRecord = view.findViewById(R.id.btn_start_record)
        btnStopRecord = view.findViewById(R.id.btn_stop_record)
        btnRunAutoTest = view.findViewById(R.id.btn_run_auto_test)
        
        btnStartRecord.setOnClickListener {
            startRecording()
        }
        
        btnStopRecord.setOnClickListener {
            stopRecording()
        }
        
        btnRunAutoTest.setOnClickListener {
            if (!isTestRunning) {
                startAutomatedTest()
            } else {
                stopAutomatedTest()
            }
        }
        
        // 初始更新一次状态
        updateStats()
    }
    
    private fun startRecording() {
        if (!isRecording) {
            context?.let { ctx ->
                MemoryMonitor.getInstance(ctx).startMonitoring()
                CacheStatisticsCapture.getInstance(ctx).startCapture()
                
                isRecording = true
                btnStartRecord.isEnabled = false
                btnStopRecord.isEnabled = true
                
                updateStatus("开始记录数据...")
                updateStatus("日志保存在: ${MemoryMonitor.getInstance(ctx).getLogFilePath()}")
            }
        }
    }
    
    private fun stopRecording() {
        if (isRecording) {
            context?.let { ctx ->
                MemoryMonitor.getInstance(ctx).stopMonitoring()
                CacheStatisticsCapture.getInstance(ctx).stopCapture()
                
                isRecording = false
                btnStartRecord.isEnabled = true
                btnStopRecord.isEnabled = false
                
                updateStatus("数据记录已停止")
            }
        }
    }
    
    private fun startAutomatedTest() {
        if (isTestRunning) return
        
        isTestRunning = true
        btnRunAutoTest.text = "停止自动测试"
        updateStatus("自动测试开始...")
        
        // 确保开始记录
        if (!isRecording) {
            startRecording()
        }
        
        // 执行测试序列
        testTimer = Timer()
        
        // 测试序列：搜索-连接-播放-控制-断开
        // 1. 开始搜索
        updateStatus("开始设备搜索...")
        DLNACastManager.getInstance()!!.startDiscovery()
        context?.let { ctx ->
            CacheStatisticsCapture.getInstance(ctx).captureSnapshot("开始搜索")
        }
        
        // 2. 等待10秒，然后停止搜索
        testTimer?.schedule(10000) {
            activity?.runOnUiThread {
                updateStatus("停止设备搜索...")
                DLNACastManager.getInstance()!!.stopDiscovery()
                context?.let { ctx ->
                    CacheStatisticsCapture.getInstance(ctx).captureSnapshot("停止搜索")
                }
            }
        }
        
        // 3. 再次开始搜索（验证缓存效果）
        testTimer?.schedule(15000) {
            activity?.runOnUiThread {
                updateStatus("再次开始设备搜索...")
                DLNACastManager.getInstance()!!.startDiscovery()
                context?.let { ctx ->
                    CacheStatisticsCapture.getInstance(ctx).captureSnapshot("再次搜索")
                }
            }
        }
        
        // 4. 停止所有测试
        testTimer?.schedule(30000) {
            activity?.runOnUiThread {
                updateStatus("自动测试完成")
                stopAutomatedTest()
            }
        }
        
        // 周期性更新状态
        testTimer?.schedule(0, 2000) {
            activity?.runOnUiThread {
                updateStats()
            }
        }
    }
    
    private fun stopAutomatedTest() {
        testTimer?.cancel()
        testTimer = null
        
        DLNACastManager.getInstance()!!.stopDiscovery()
        
        isTestRunning = false
        btnRunAutoTest.text = "开始自动测试"
        updateStatus("自动测试已停止")
    }
    
    private fun updateStats() {
        try {
            // 更新缓存统计
            val cacheStats = UPnPCacheManager.getInstance().getCacheSummary()
            tvCacheStats.text = cacheStats
            
            // 更新内存状态
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            val activityManager = requireContext().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            activityManager.getMemoryInfo(memoryInfo)
            
            val totalMemory = memoryInfo.totalMem / (1024 * 1024) // MB
            val freeMemory = memoryInfo.availMem / (1024 * 1024) // MB
            val usedMemory = totalMemory - freeMemory
            val isLowMemory = memoryInfo.lowMemory
            
            val memoryStatus = """
                内存状态:
                - 总内存: ${totalMemory}MB
                - 可用内存: ${freeMemory}MB
                - 已用内存: ${usedMemory}MB
                - 低内存: ${if (isLowMemory) "是" else "否"}
            """.trimIndent()
            
            tvMemoryStatus.text = memoryStatus
        } catch (e: Exception) {
            tvCacheStats.text = "获取统计信息出错: ${e.message}"
        }
    }
    
    private fun updateStatus(message: String) {
        val timestamp = dateFormat.format(Date())
        val status = "[$timestamp] $message"
        tvTestStatus.text = status
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        stopAutomatedTest()
    }
} 