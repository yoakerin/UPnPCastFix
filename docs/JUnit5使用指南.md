# JUnit 5使用指南

## 简介

JUnit 5是Java和Kotlin生态系统中流行的测试框架的新一代版本。相比JUnit 4，它提供了更多丰富的特性，使测试代码更具表达力和可维护性。本指南将介绍JUnit 5的主要特性以及在我们项目中的使用方法。

## 基本用法

### 导入和注解

在写测试时，使用以下导入：

```kotlin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
```

主要注解：

- `@Test`: 标记测试方法
- `@DisplayName`: 为测试类或测试方法提供自定义名称
- `@BeforeEach`: 在每个测试方法执行前运行（替代JUnit 4的`@Before`）
- `@AfterEach`: 在每个测试方法执行后运行（替代JUnit 4的`@After`）
- `@BeforeAll`: 在所有测试方法执行前运行一次（替代JUnit 4的`@BeforeClass`）
- `@AfterAll`: 在所有测试方法执行后运行一次（替代JUnit 4的`@AfterClass`）

### 断言

JUnit 5提供了丰富的断言方法：

```kotlin
// 基本断言
assertEquals(expected, actual)
assertNotEquals(unexpected, actual)
assertTrue(condition)
assertFalse(condition)
assertNull(object)
assertNotNull(object)

// 带自定义消息的断言
assertEquals(expected, actual, "自定义错误消息")

// 异常断言
assertThrows<IllegalArgumentException> { 
    // 预期抛出异常的代码
}

// 超时断言
assertTimeout(Duration.ofMillis(100)) {
    // 预期在指定时间内完成的代码
}

// 分组断言
assertAll(
    { assertEquals(2, 1 + 1) },
    { assertTrue(4 > 3) }
)
```

## 高级特性

### 参数化测试

参数化测试允许使用不同的参数多次运行测试。需要添加依赖：

```kotlin
testImplementation("org.junit.jupiter.params:junit-jupiter-params:5.10.1")
```

示例：

```kotlin
@ParameterizedTest
@ValueSource(strings = ["apple", "banana", "orange"])
fun testFruits(fruit: String) {
    assertNotNull(fruit)
    assertTrue(fruit.length > 3)
}

@ParameterizedTest
@MethodSource("provideNumbers")
fun testWithMethodSource(input: Int, expected: Boolean) {
    assertEquals(expected, input > 0)
}

companion object {
    @JvmStatic
    fun provideNumbers(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(1, true),
            Arguments.of(0, false),
            Arguments.of(-1, false)
        )
    }
}
```

参数化测试的来源类型：

- `@ValueSource`: 提供简单的字面值数组
- `@MethodSource`: 引用提供Arguments的方法
- `@CsvSource`: 使用CSV格式提供参数
- `@EnumSource`: 使用枚举值作为参数
- `@ArgumentsSource`: 自定义参数提供者

### 嵌套测试

使用`@Nested`注解可以创建更具表达力的测试组：

```kotlin
@DisplayName("播放服务测试")
class PlaybackServiceTest {

    private lateinit var service: PlaybackService
    
    @BeforeEach
    fun setup() {
        service = PlaybackService()
    }
    
    @Nested
    @DisplayName("初始状态测试")
    inner class InitialStateTests {
        @Test
        fun testInitialPlaybackState() {
            assertEquals(PlaybackState.IDLE, service.state)
        }
    }
    
    @Nested
    @DisplayName("播放控制测试")
    inner class PlaybackControlTests {
        @BeforeEach
        fun initMedia() {
            service.loadMedia(TestMedia())
        }
        
        @Test
        fun testPlay() {
            service.play()
            assertEquals(PlaybackState.PLAYING, service.state)
        }
        
        @Test
        fun testPause() {
            service.play()
            service.pause()
            assertEquals(PlaybackState.PAUSED, service.state)
        }
    }
}
```

### 扩展机制

JUnit 5提供了强大的扩展机制，可以在测试的不同生命周期阶段进行干预：

```kotlin
@ExtendWith(MockitoExtension::class)
class MyServiceTest {
    @Mock
    private lateinit var repository: Repository
    
    @InjectMocks
    private lateinit var service: MyService
    
    @Test
    fun testServiceWithMocks() {
        // 测试代码
    }
}
```

常用扩展：

- `MockitoExtension`: 提供Mockito支持
- `TimingExtension`: 测量测试执行时间
- `TempDirectory`: 提供临时目录

## Android测试中使用JUnit 5

在Android Instrumented Tests中，我们需要同时使用JUnit 4的`@RunWith`注解和JUnit 5的断言和特性：

```kotlin
@RunWith(AndroidJUnit4::class)  // 仍然需要JUnit 4的RunWith
class MyAndroidTest {

    @Test
    @DisplayName("验证上下文")  // 使用JUnit 5的注解
    fun testContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.app", appContext.packageName)  // 使用JUnit 5的断言
    }
}
```

## 与Mockito配合使用

JUnit 5可以与Mockito框架无缝集成：

```kotlin
@ExtendWith(MockitoExtension::class)
class DeviceManagerTest {

    @Mock
    private lateinit var deviceDiscovery: DeviceDiscovery
    
    @InjectMocks
    private lateinit var deviceManager: DeviceManager
    
    @Test
    fun testDeviceFound() {
        // 配置mock
        val device = mock<RemoteDevice>()
        whenever(device.identity.udn).thenReturn("uuid:test")
        
        // 触发被测方法
        deviceManager.onDeviceFound(device)
        
        // 验证预期行为
        verify(deviceDiscovery).addDevice(device)
    }
}
```

## 与Kotest框架的集成

Kotest是一个强大的Kotlin测试框架，已经基于JUnit 5构建，支持多种测试风格：

```kotlin
// BehaviorSpec风格
class DeviceSpec : BehaviorSpec({
    given("一个设备管理器") {
        val manager = DeviceManager()
        
        `when`("发现新设备") {
            val device = createTestDevice()
            manager.onDeviceFound(device)
            
            then("设备应该被添加到列表中") {
                manager.deviceList shouldContain device
            }
        }
    }
})
```

## 最佳实践

1. 使用`@DisplayName`提高可读性
2. 用参数化测试简化重复测试逻辑
3. 使用嵌套测试组织相关测试
4. 优先使用JUnit 5的断言方法
5. 合理使用生命周期注解，避免测试间的状态泄漏
6. 使用`@Tag`注解对测试进行分类（如"快速测试"、"集成测试"等）

## 参考资源

- [JUnit 5官方文档](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito官方文档](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Kotest官方文档](https://kotest.io/docs/) 