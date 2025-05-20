package com.yinnho.upnpcast.utils

import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.ArgumentCaptor
import kotlin.reflect.KClass

/**
 * 测试Mock工具类
 * 提供统一的Mockito使用方式，避免导入冲突
 */
object TestMockUtils {
    
    /**
     * 创建指定类型的mock对象
     * @param T 要模拟的对象类型
     * @return 模拟对象
     */
    inline fun <reified T : Any> createMock(): T {
        return mock(T::class.java)
    }
    
    /**
     * 设置模拟对象的行为
     * @param T 模拟对象类型
     * @param R 返回值类型
     * @param mock 模拟对象
     * @param methodCall 要模拟的方法调用
     * @return Stubber对象，用于指定方法行为
     */
    inline fun <T, R> stub(mock: T, methodCall: T.() -> R): org.mockito.stubbing.OngoingStubbing<R> {
        return whenever(mock.methodCall())
    }
    
    /**
     * 验证方法调用
     * @param T 模拟对象类型
     * @param mock 模拟对象
     * @param methodCall 要验证的方法调用
     */
    inline fun <T> verifyCall(mock: T, methodCall: T.() -> Unit) {
        verify(mock).methodCall()
    }
    
    /**
     * 创建参数捕获器
     * @param T 要捕获的参数类型
     * @return 参数捕获器
     */
    inline fun <reified T : Any> createCaptor(): ArgumentCaptor<T> {
        return ArgumentCaptor.forClass(T::class.java)
    }
    
    /**
     * 使用参数捕获器验证方法调用
     * @param T 模拟对象类型
     * @param P 参数类型
     * @param mock 模拟对象
     * @param captor 参数捕获器
     * @param methodCall 带参数的方法调用
     */
    inline fun <T, P : Any> verifyWithCaptor(
        mock: T, 
        captor: ArgumentCaptor<P>,
        crossinline methodCall: T.(P) -> Unit
    ) {
        verify(mock).methodCall(captor.capture())
    }
} 