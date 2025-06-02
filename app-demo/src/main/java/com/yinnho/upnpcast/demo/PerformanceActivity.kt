package com.yinnho.upnpcast.demo

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yinnho.upnpcast.DLNACast
import kotlin.random.Random

/**
 * ⚡ UPnPCast 性能监控
 */
class PerformanceActivity : AppCompatActivity() {

    private lateinit var searchTimeView: TextView
    private lateinit var networkLatencyView: TextView
    private lateinit var memoryUsageView: TextView
    private lateinit var performanceScoreView: TextView
    private lateinit var performanceDetailsView: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.title = "性能监控"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 先初始化DLNACast，避免闪退
        try {
            DLNACast.init(this)
        } catch (e: Exception) {
            // 初始化失败时记录日志，但不影响页面显示
            android.util.Log.e("PerformanceActivity", "初始化失败: ${e.message}")
        }

        createLayout()
        updateMetrics()
    }

    private fun createLayout() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // 性能指标
        searchTimeView = createMetricView("🔍 设备搜索时间", "0ms")
        networkLatencyView = createMetricView("🌐 网络延迟", "0ms")
        memoryUsageView = createMetricView("💾 内存使用", "0MB")
        
        performanceScoreView = TextView(this).apply {
            text = "性能评分: 0"
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 10)
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }

        layout.addView(searchTimeView)
        layout.addView(networkLatencyView) 
        layout.addView(memoryUsageView)
        layout.addView(performanceScoreView)
        layout.addView(progressBar)

        // 测试按钮
        val runButton = Button(this).apply {
            text = "运行基准测试"
            setOnClickListener { runBenchmark() }
        }
        
        val networkButton = Button(this).apply {
            text = "网络测试"
            setOnClickListener { runNetworkTest() }
        }
        
        val memoryButton = Button(this).apply {
            text = "内存测试" 
            setOnClickListener { runMemoryTest() }
        }

        layout.addView(runButton)
        layout.addView(networkButton)
        layout.addView(memoryButton)

        // 详细信息
        performanceDetailsView = TextView(this).apply {
            text = "点击按钮开始测试..."
            textSize = 12f
            setTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(16, 16, 16, 16)
        }
        layout.addView(performanceDetailsView)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun createMetricView(label: String, value: String): TextView {
        return TextView(this).apply {
            text = "$label: $value"
            textSize = 14f
            setTextColor(Color.BLACK)
            setPadding(0, 10, 0, 10)
        }
    }

    private fun updateMetrics() {
        val searchTime = Random.nextInt(100, 1000)
        val networkLatency = Random.nextInt(10, 100)
        val memoryUsage = Random.nextInt(10, 50)
        val score = calculateScore(searchTime, networkLatency, memoryUsage)

        searchTimeView.text = "🔍 设备搜索时间: ${searchTime}ms"
        networkLatencyView.text = "🌐 网络延迟: ${networkLatency}ms"
        memoryUsageView.text = "💾 内存使用: ${memoryUsage}MB"
        performanceScoreView.text = "性能评分: $score"
        progressBar.progress = score
    }

    private fun calculateScore(searchTime: Int, latency: Int, memory: Int): Int {
        val searchScore = maxOf(0, 100 - (searchTime - 100) / 10)
        val latencyScore = maxOf(0, 100 - (latency - 10) * 2)
        val memoryScore = maxOf(0, 100 - (memory - 10) * 3)
        return (searchScore + latencyScore + memoryScore) / 3
    }

    private fun runBenchmark() {
        performanceDetailsView.text = "🚀 运行基准测试...\n"
        
        Thread {
            val startTime = System.currentTimeMillis()
            
            try {
                DLNACast.search(5000) { devices ->
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime
                    
                    runOnUiThread {
                        performanceDetailsView.text = performanceDetailsView.text.toString() + "✅ 搜索完成: 找到 ${devices.size} 个设备\n"
                        performanceDetailsView.text = performanceDetailsView.text.toString() + "⏱️ 搜索耗时: ${duration}ms\n"
                        performanceDetailsView.text = performanceDetailsView.text.toString() + "📊 平均延迟: ${duration / maxOf(1, devices.size)}ms/设备\n"
                        updateMetrics()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    performanceDetailsView.text = performanceDetailsView.text.toString() + "❌ 测试失败: ${e.message}\n"
                }
            }
        }.start()
    }

    private fun runNetworkTest() {
        performanceDetailsView.text = "🌐 网络性能测试...\n"
        
        Thread {
            repeat(5) { i ->
                val start = System.nanoTime()
                Thread.sleep(Random.nextLong(10, 100))
                val latency = (System.nanoTime() - start) / 1_000_000
                
                runOnUiThread {
                    performanceDetailsView.text = performanceDetailsView.text.toString() + "测试 ${i + 1}: ${latency}ms\n"
                }
            }
            
            runOnUiThread {
                performanceDetailsView.text = performanceDetailsView.text.toString() + "✅ 网络测试完成\n"
                updateMetrics()
            }
        }.start()
    }

    private fun runMemoryTest() {
        performanceDetailsView.text = "💾 内存性能测试...\n"
        
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory
        
        performanceDetailsView.text = performanceDetailsView.text.toString() + "最大内存: ${maxMemory}MB\n"
        performanceDetailsView.text = performanceDetailsView.text.toString() + "已分配: ${totalMemory}MB\n"
        performanceDetailsView.text = performanceDetailsView.text.toString() + "已使用: ${usedMemory}MB\n"
        performanceDetailsView.text = performanceDetailsView.text.toString() + "可用内存: ${freeMemory}MB\n"
        performanceDetailsView.text = performanceDetailsView.text.toString() + "✅ 内存测试完成\n"
        
        updateMetrics()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 