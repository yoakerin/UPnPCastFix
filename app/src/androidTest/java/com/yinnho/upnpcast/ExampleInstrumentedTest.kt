package com.yinnho.upnpcast

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assertions.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
// 注：Android测试仍然需要使用@RunWith(AndroidJUnit4::class)，但是内部可以使用JUnit 5的断言和注解
@org.junit.runner.RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    @DisplayName("验证应用程序包名")
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.yinnho.upnpcast", appContext.packageName)
    }
}