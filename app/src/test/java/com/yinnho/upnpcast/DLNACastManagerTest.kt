package com.yinnho.upnpcast

import com.yinnho.upnpcast.api.DLNAErrorType
import com.yinnho.upnpcast.model.DeviceStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * DLNACastManager单元测试
 * 测试状态转换和错误处理
 */
@DisplayName("DLNA投屏管理器测试")
class DLNACastManagerTest {

    /**
     * 测试DLNAErrorType枚举
     */
    @Test
    @DisplayName("测试错误类型枚举")
    fun testErrorTypes() {
        // 验证错误类型和代码
        val errorTypes = mapOf(
            DLNAErrorType.NETWORK_ERROR to 1001,
            DLNAErrorType.NETWORK_TIMEOUT to 1002, 
            DLNAErrorType.DISCOVERY_ERROR to 1003,
            DLNAErrorType.CONNECTION_ERROR to 2001,
            DLNAErrorType.DEVICE_CONNECTION_ERROR to 2002,
            DLNAErrorType.COMMUNICATION_ERROR to 2003,
            DLNAErrorType.DEVICE_ERROR to 2004,
            DLNAErrorType.PLAYBACK_ERROR to 3001,
            DLNAErrorType.CONTROL_ERROR to 3002,
            DLNAErrorType.INVALID_PARAMETER to 4001,
            DLNAErrorType.RESOURCE_ERROR to 4002,
            DLNAErrorType.PARSING_ERROR to 4003,
            DLNAErrorType.SECURITY_ERROR to 5001,
            DLNAErrorType.COMPATIBILITY_ERROR to 6001,
            DLNAErrorType.UNKNOWN_ERROR to 9999
        )
        
        errorTypes.forEach { (errorType, expectedCode) ->
            assertEquals(expectedCode, errorType.code)
            assertNotNull(errorType.message)
        }
    }
    
    /**
     * 提供设备状态测试数据
     */
    companion object {        
        fun deviceStatusTestData(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(DeviceStatus.UNKNOWN, 0),
                Arguments.of(DeviceStatus.CONNECTING, 1),
                Arguments.of(DeviceStatus.CONNECTED, 2),
                Arguments.of(DeviceStatus.PLAYING, 3),
                Arguments.of(DeviceStatus.PAUSED, 4),
                Arguments.of(DeviceStatus.STOPPED, 5),
                Arguments.of(DeviceStatus.TRANSITIONING, 6),
                Arguments.of(DeviceStatus.ERROR, 7)
            )
        }
    }
    
    /**
     * 测试设备状态枚举
     */
    @ParameterizedTest
    @MethodSource("deviceStatusTestData")
    @DisplayName("测试设备状态枚举值")
    fun testDeviceStatusValues(status: DeviceStatus, expectedValue: Int) {
        assertEquals(expectedValue, status.value)
        assertEquals(status, DeviceStatus.fromValue(expectedValue))
    }
    
    /**
     * 测试未知的设备状态值
     */
    @Test
    @DisplayName("测试未知的设备状态值")
    fun testUnknownDeviceStatus() {
        assertEquals(DeviceStatus.UNKNOWN, DeviceStatus.fromValue(999))
    }
    
    /**
     * 测试状态转换有效性
     */
    @Test
    @DisplayName("测试状态转换有效性")
    fun testDeviceStatusTransitions() {
        val validTransitions = listOf(
            DeviceStatus.UNKNOWN to DeviceStatus.CONNECTING,
            DeviceStatus.CONNECTING to DeviceStatus.CONNECTED,
            DeviceStatus.CONNECTED to DeviceStatus.PLAYING,
            DeviceStatus.PLAYING to DeviceStatus.PAUSED,
            DeviceStatus.PAUSED to DeviceStatus.PLAYING,
            DeviceStatus.PLAYING to DeviceStatus.STOPPED,
            DeviceStatus.PLAYING to DeviceStatus.TRANSITIONING,
            DeviceStatus.TRANSITIONING to DeviceStatus.PLAYING
        )
        
        val invalidTransitions = listOf(
            DeviceStatus.CONNECTING to DeviceStatus.PLAYING,
            DeviceStatus.CONNECTING to DeviceStatus.PAUSED,
            DeviceStatus.CONNECTED to DeviceStatus.PAUSED,
            DeviceStatus.STOPPED to DeviceStatus.PAUSED
        )
        
        // 验证转换逻辑
        val validator = createTransitionValidator()
        
        // 测试有效转换
        validTransitions.forEach { (from, to) ->
            assertTrue(validator.isValidTransition(from, to), "从 $from 到 $to 应该是有效转换")
        }
        
        // 测试无效转换
        invalidTransitions.forEach { (from, to) ->
            assertFalse(validator.isValidTransition(from, to), "从 $from 到 $to 应该是无效转换")
        }
    }
    
    /**
     * 创建状态转换验证器
     */
    private fun createTransitionValidator(): TransitionValidator {
        return object : TransitionValidator {
            override fun isValidTransition(from: DeviceStatus, to: DeviceStatus): Boolean {
                return when (from) {
                    DeviceStatus.UNKNOWN -> true // 从UNKNOWN可以转为任何状态
                    DeviceStatus.CONNECTING -> to == DeviceStatus.CONNECTED || to == DeviceStatus.ERROR
                    DeviceStatus.CONNECTED -> to == DeviceStatus.PLAYING || to == DeviceStatus.STOPPED || to == DeviceStatus.ERROR
                    DeviceStatus.PLAYING -> to == DeviceStatus.PAUSED || to == DeviceStatus.STOPPED || to == DeviceStatus.TRANSITIONING || to == DeviceStatus.ERROR
                    DeviceStatus.PAUSED -> to == DeviceStatus.PLAYING || to == DeviceStatus.STOPPED || to == DeviceStatus.ERROR
                    DeviceStatus.STOPPED -> to == DeviceStatus.PLAYING || to == DeviceStatus.ERROR
                    DeviceStatus.TRANSITIONING -> to == DeviceStatus.PLAYING || to == DeviceStatus.PAUSED || to == DeviceStatus.STOPPED || to == DeviceStatus.ERROR
                    DeviceStatus.ERROR -> true // 从ERROR可以尝试恢复到任何状态
                }
            }
        }
    }
    
    /**
     * 状态转换验证器接口
     */
    private interface TransitionValidator {
        fun isValidTransition(from: DeviceStatus, to: DeviceStatus): Boolean
    }
} 