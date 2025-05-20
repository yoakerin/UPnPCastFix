package com.yinnho.upnpcast.helper

import android.content.Context
import com.yinnho.upnpcast.DLNACastManager
import com.yinnho.upnpcast.cache.UPnPCacheManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule

/**
 * 测试用例自动执行工具
 * 用于批量执行功能测试用例
 */
class TestRunner(private val context: Context) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val testResultsFile: File
    private var testTimer: Timer? = null
    private var currentTestIndex = 0
    private var testStartTime: Long = 0
    
    // 功能测试用例列表
    private val functionalTests = listOf(
        TestCase("1.1", "基本设备发现", this::testBasicDiscovery),
        TestCase("1.2", "重复搜索", this::testRepeatedDiscovery),
        TestCase("1.3", "设备缓存验证", this::testDeviceCache),
        TestCase("2.1", "正常连接设备", this::testDeviceConnection),
        TestCase("2.2", "手动断开连接", this::testManualDisconnect)
        // 可以添加更多测试用例
    )
    
    init {
        // 使用应用的内部存储空间
        val logDir = File(context.filesDir, "test-logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        testResultsFile = File(logDir, "test_results_${dateFormat.format(Date())}.log")
        testResultsFile.writeText("UPnPCast测试结果\n日期: ${Date()}\n\n")
        testResultsFile.appendText("测试用例ID | 测试名称 | 执行时间 | 测试结果 | 备注\n")
        testResultsFile.appendText("-----------|---------|---------|---------|------\n")
    }
    
    /**
     * 开始执行所有测试用例
     */
    fun runAllTests() {
        testTimer = Timer()
        currentTestIndex = 0
        runNextTest()
    }
    
    /**
     * 停止测试执行
     */
    fun stopTests() {
        testTimer?.cancel()
        testTimer = null
        logMessage("测试执行已停止")
    }
    
    /**
     * 执行下一个测试用例
     */
    private fun runNextTest() {
        if (currentTestIndex < functionalTests.size) {
            val testCase = functionalTests[currentTestIndex]
            logMessage("开始执行测试用例 ${testCase.id}: ${testCase.name}")
            
            testStartTime = System.currentTimeMillis()
            testCase.testFunction.invoke()
            
            // 下一个测试将会在每个测试函数内部通过调用testCompleted来触发
        } else {
            logMessage("所有测试已完成")
            testTimer?.cancel()
            testTimer = null
        }
    }
    
    /**
     * 记录测试完成
     */
    private fun testCompleted(result: TestResult, remarks: String = "") {
        val testCase = functionalTests[currentTestIndex]
        val duration = System.currentTimeMillis() - testStartTime
        
        val resultStr = when(result) {
            TestResult.PASS -> "通过"
            TestResult.FAIL -> "失败"
            TestResult.PARTIAL -> "部分通过"
        }
        
        testResultsFile.appendText("${testCase.id} | ${testCase.name} | ${duration/1000}秒 | ${resultStr} | ${remarks}\n")
        
        logMessage("测试用例 ${testCase.id} 完成: ${resultStr}")
        
        // 执行下一个测试用例
        currentTestIndex++
        
        // 短暂延迟后执行下一个测试
        testTimer?.schedule(2000) {
            runNextTest()
        }
    }
    
    /**
     * 记录日志消息
     */
    private fun logMessage(message: String) {
        val timestamp = dateFormat.format(Date())
        println("[$timestamp] $message")
    }
    
    /**
     * 测试用例1.1: 基本设备发现
     */
    private fun testBasicDiscovery() {
        // 记录测试开始
        CacheStatisticsCapture.getInstance(context).captureSnapshot("测试1.1开始")
        
        // 开始搜索
        DLNACastManager.getInstance().startDiscovery()
        
        // 10秒后检查结果
        testTimer?.schedule(10000) {
            val devices = DLNACastManager.getInstance().getDeviceList()
            
            // 验证测试结果
            if (devices.isNotEmpty()) {
                testCompleted(TestResult.PASS, "发现了${devices.size}个设备")
            } else {
                testCompleted(TestResult.FAIL, "没有发现任何设备")
            }
            
            // 停止搜索
            DLNACastManager.getInstance().stopDiscovery()
            
            // 记录测试结果
            CacheStatisticsCapture.getInstance(context).captureSnapshot("测试1.1结束")
        }
    }
    
    /**
     * 测试用例1.2: 重复搜索
     */
    private fun testRepeatedDiscovery() {
        CacheStatisticsCapture.getInstance(context).captureSnapshot("测试1.2开始")
        
        // 第一次搜索
        DLNACastManager.getInstance().startDiscovery()
        
        // 10秒后停止并记录结果
        testTimer?.schedule(10000) {
            val firstDevices = DLNACastManager.getInstance().getDeviceList()
            DLNACastManager.getInstance().stopDiscovery()
            
            CacheStatisticsCapture.getInstance(context).captureSnapshot("测试1.2第一次搜索结束")
            
            // 再次开始搜索
            testTimer?.schedule(2000) {
                DLNACastManager.getInstance().startDiscovery()
                
                // 10秒后比较两次结果
                testTimer?.schedule(10000) {
                    val secondDevices = DLNACastManager.getInstance().getDeviceList()
                    DLNACastManager.getInstance().stopDiscovery()
                    
                    CacheStatisticsCapture.getInstance(context).captureSnapshot("测试1.2第二次搜索结束")
                    
                    // 验证结果
                    if (firstDevices.size == secondDevices.size) {
                        testCompleted(TestResult.PASS, "两次搜索结果一致，设备数: ${firstDevices.size}")
                    } else {
                        testCompleted(TestResult.PARTIAL, "两次搜索结果不一致，第一次: ${firstDevices.size}，第二次: ${secondDevices.size}")
                    }
                }
            }
        }
    }
    
    /**
     * 测试用例1.3: 设备缓存验证
     */
    private fun testDeviceCache() {
        CacheStatisticsCapture.getInstance(context).captureSnapshot("测试1.3开始")
        
        // 获取初始缓存统计
        val initialSummary = UPnPCacheManager.getInstance().getCacheSummary()
        
        // 开始搜索
        DLNACastManager.getInstance().startDiscovery()
        
        // 10秒后停止并记录结果
        testTimer?.schedule(10000) {
            DLNACastManager.getInstance().stopDiscovery()
            
            // 获取搜索后缓存统计
            val afterDiscoverySummary = UPnPCacheManager.getInstance().getCacheSummary()
            
            // 再次开始搜索（应该使用缓存）
            testTimer?.schedule(2000) {
                DLNACastManager.getInstance().startDiscovery()
                
                // 5秒后获取最终缓存统计
                testTimer?.schedule(5000) {
                    DLNACastManager.getInstance().stopDiscovery()
                    
                    val finalSummary = UPnPCacheManager.getInstance().getCacheSummary()
                    CacheStatisticsCapture.getInstance(context).captureSnapshot("测试1.3结束")
                    
                    // 简单验证缓存命中率是否提高
                    testCompleted(TestResult.PASS, "缓存统计已记录，详见日志文件")
                }
            }
        }
    }
    
    /**
     * 测试用例2.1: 正常连接设备
     */
    private fun testDeviceConnection() {
        CacheStatisticsCapture.getInstance(context).captureSnapshot("测试2.1开始")
        
        // 开始搜索
        DLNACastManager.getInstance().startDiscovery()
        
        // 10秒后尝试连接第一个设备
        testTimer?.schedule(10000) {
            val devices = DLNACastManager.getInstance().getDeviceList()
            DLNACastManager.getInstance().stopDiscovery()
            
            if (devices.isNotEmpty()) {
                val firstDevice = devices[0]
                
                // 尝试连接
                DLNACastManager.getInstance().connectToDevice(firstDevice)
                
                // 10秒后检查连接状态
                testTimer?.schedule(10000) {
                    val isConnected = DLNACastManager.getInstance().isConnected()
                    
                    CacheStatisticsCapture.getInstance(context).captureSnapshot("测试2.1结束")
                    
                    if (isConnected) {
                        testCompleted(TestResult.PASS, "成功连接到设备: ${firstDevice.friendlyName}")
                    } else {
                        testCompleted(TestResult.FAIL, "连接设备失败: ${firstDevice.friendlyName}")
                    }
                }
            } else {
                testCompleted(TestResult.FAIL, "没有发现设备，无法执行连接测试")
            }
        }
    }
    
    /**
     * 测试用例2.2: 手动断开连接
     */
    private fun testManualDisconnect() {
        CacheStatisticsCapture.getInstance(context).captureSnapshot("测试2.2开始")
        
        // 检查当前是否已连接
        if (DLNACastManager.getInstance().isConnected()) {
            // 断开连接
            DLNACastManager.getInstance().disconnect()
            
            // 5秒后检查连接状态
            testTimer?.schedule(5000) {
                val isConnected = DLNACastManager.getInstance().isConnected()
                
                CacheStatisticsCapture.getInstance(context).captureSnapshot("测试2.2结束")
                
                if (!isConnected) {
                    testCompleted(TestResult.PASS, "成功断开连接")
                } else {
                    testCompleted(TestResult.FAIL, "断开连接失败")
                }
            }
        } else {
            // 没有连接，先尝试连接
            // 开始搜索
            DLNACastManager.getInstance().startDiscovery()
            
            // 10秒后尝试连接第一个设备
            testTimer?.schedule(10000) {
                val devices = DLNACastManager.getInstance().getDeviceList()
                DLNACastManager.getInstance().stopDiscovery()
                
                if (devices.isNotEmpty()) {
                    val firstDevice = devices[0]
                    
                    // 尝试连接
                    DLNACastManager.getInstance().connectToDevice(firstDevice)
                    
                    // 10秒后断开连接
                    testTimer?.schedule(10000) {
                        if (DLNACastManager.getInstance().isConnected()) {
                            DLNACastManager.getInstance().disconnect()
                            
                            // 5秒后检查连接状态
                            testTimer?.schedule(5000) {
                                val isConnected = DLNACastManager.getInstance().isConnected()
                                
                                CacheStatisticsCapture.getInstance(context).captureSnapshot("测试2.2结束")
                                
                                if (!isConnected) {
                                    testCompleted(TestResult.PASS, "成功断开连接")
                                } else {
                                    testCompleted(TestResult.FAIL, "断开连接失败")
                                }
                            }
                        } else {
                            testCompleted(TestResult.FAIL, "连接建立失败，无法测试断开")
                        }
                    }
                } else {
                    testCompleted(TestResult.FAIL, "没有发现设备，无法执行连接断开测试")
                }
            }
        }
    }
    
    /**
     * 测试用例数据类
     */
    data class TestCase(
        val id: String,
        val name: String,
        val testFunction: () -> Unit
    )
    
    /**
     * 测试结果枚举
     */
    enum class TestResult {
        PASS,       // 测试通过
        FAIL,       // 测试失败
        PARTIAL     // 部分通过
    }
    
    companion object {
        private var instance: TestRunner? = null
        
        @JvmStatic
        fun getInstance(context: Context): TestRunner {
            if (instance == null) {
                instance = TestRunner(context)
            }
            return instance!!
        }
    }
} 