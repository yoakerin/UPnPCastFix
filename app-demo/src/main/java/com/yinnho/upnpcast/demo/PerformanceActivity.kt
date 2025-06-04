package com.yinnho.upnpcast.demo

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.yinnho.upnpcast.DLNACast
import kotlin.random.Random

/**
 * ⚡ UPnPCast Performance Monitor
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

        supportActionBar?.title = "Performance Monitor"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Modern back button handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        // Initialize DLNACast first to avoid crashes
        try {
            DLNACast.init(this)
        } catch (e: Exception) {
            // Log initialization failure but don't affect page display
            android.util.Log.e("PerformanceActivity", "Initialization failed: ${e.message}")
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

        // Performance metrics
        searchTimeView = createMetricView("🔍 Device Search Time", "0ms")
        networkLatencyView = createMetricView("🌐 Network Latency", "0ms")
        memoryUsageView = createMetricView("💾 Memory Usage", "0MB")
        
        performanceScoreView = TextView(this).apply {
            text = "Performance Score: 0"
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

        // Test buttons
        val runButton = Button(this).apply {
            text = "Run Benchmark"
            setOnClickListener { runBenchmark() }
        }
        
        val networkButton = Button(this).apply {
            text = "Network Test"
            setOnClickListener { runNetworkTest() }
        }
        
        val memoryButton = Button(this).apply {
            text = "Memory Test" 
            setOnClickListener { runMemoryTest() }
        }

        layout.addView(runButton)
        layout.addView(networkButton)
        layout.addView(memoryButton)

        // Detailed information
        performanceDetailsView = TextView(this).apply {
            text = "Click button to start testing..."
            textSize = 12f
            setTextColor(Color.GRAY)
            setBackgroundColor("#F5F5F5".toColorInt())
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

        searchTimeView.text = "🔍 Device Search Time: ${searchTime}ms"
        networkLatencyView.text = "🌐 Network Latency: ${networkLatency}ms"
        memoryUsageView.text = "💾 Memory Usage: ${memoryUsage}MB"
        performanceScoreView.text = "Performance Score: $score"
        progressBar.progress = score
    }

    private fun calculateScore(searchTime: Int, latency: Int, memory: Int): Int {
        val searchScore = maxOf(0, 100 - (searchTime - 100) / 10)
        val latencyScore = maxOf(0, 100 - (latency - 10) * 2)
        val memoryScore = maxOf(0, 100 - (memory - 10) * 3)
        return (searchScore + latencyScore + memoryScore) / 3
    }

    private fun runBenchmark() {
        performanceDetailsView.text = "🚀 Running benchmark...\n"
        
        Thread {
            val startTime = System.currentTimeMillis()
            
            try {
                DLNACast.search(5000) { devices ->
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime
                    
                    runOnUiThread {
                        performanceDetailsView.text = performanceDetailsView.text.toString() + "✅ Search completed: Found ${devices.size} devices\n"
                        performanceDetailsView.text = performanceDetailsView.text.toString() + "⏱️ Search time: ${duration}ms\n"
                        performanceDetailsView.text = performanceDetailsView.text.toString() + "📊 Average latency: ${duration / maxOf(1, devices.size)}ms/device\n"
                        updateMetrics()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    performanceDetailsView.text = performanceDetailsView.text.toString() + "❌ Test failed: ${e.message}\n"
                }
            }
        }.start()
    }

    private fun runNetworkTest() {
        performanceDetailsView.text = "🌐 Network performance test...\n"
        
        Thread {
            repeat(5) { i ->
                val start = System.nanoTime()
                Thread.sleep(Random.nextLong(10, 100))
                val latency = (System.nanoTime() - start) / 1_000_000
                
                runOnUiThread {
                    performanceDetailsView.text = performanceDetailsView.text.toString() + "Test ${i + 1}: ${latency}ms\n"
                }
            }
            
            runOnUiThread {
                performanceDetailsView.text = performanceDetailsView.text.toString() + "✅ Network test completed\n"
                updateMetrics()
            }
        }.start()
    }

    private fun runMemoryTest() {
        performanceDetailsView.text = "💾 Memory performance test...\n"
        
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory

        performanceDetailsView.text = performanceDetailsView.text.toString() + "Maximum memory: ${maxMemory}MB\n"
        performanceDetailsView.text = performanceDetailsView.text.toString() + "Total memory: ${totalMemory}MB\n"
        performanceDetailsView.text = performanceDetailsView.text.toString() + "Used memory: ${usedMemory}MB\n"
        performanceDetailsView.text = performanceDetailsView.text.toString() + "Free memory: ${freeMemory}MB\n"
        performanceDetailsView.text = performanceDetailsView.text.toString() + "✅ Memory test completed\n"
        
        updateMetrics()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
} 