# UPnPCast 测试指南

本文档提供 UPnPCast 库的测试最佳实践指南，包括 JUnit 5 和 Kotest 测试框架的使用方式。

## 测试框架选择指南

UPnPCast 项目同时使用 JUnit 5 和 Kotest 两种测试框架：

### JUnit 5 适用场景

- 单元测试（测试单个组件）
- 参数化测试（多种输入值测试相同逻辑）
- 与传统 Android 测试工具集成时
- 需要使用 JUnit 5 扩展系统时

### Kotest 适用场景

- 行为驱动测试（BDD风格）
- 数据驱动测试
- 需要更丰富断言库的场景
- 属性测试（生成随机测试数据）

## 测试工具类

我们提供统一的测试工具类来简化测试编写：

```kotlin
import com.yinnho.upnpcast.utils.TestMockUtils
```

### 设备工厂

为了简化测试设备的创建，使用 `DeviceFactory`：

```kotlin
// 创建测试设备
val device = TestMockUtils.DeviceFactory.createTestDevice(
    udn = "uuid:test-device",
    manufacturer = "Test Manufacturer",
    friendlyName = "Test Device"
)

// 基于制造商创建
val samsungDevice = TestMockUtils.DeviceFactory.createDeviceByManufacturer("Samsung")
```

### JUnit 5 扩展

使用我们的自定义扩展可以简化测试编写：

```kotlin
@WithTestDevice
class MyTest {
    
    @Test
    fun testSomething(testDevice: RemoteDevice) {
        // 测试设备会自动注入
    }
}
```

## JUnit 5 测试编写指南

### 基本测试结构

```kotlin
@DisplayName("测试类名称")
class MyTest {

    @BeforeEach
    fun setUp() {
        // 测试前准备
    }
    
    @Test
    @DisplayName("测试方法描述")
    fun testMethod() {
        // 测试代码
    }
    
    @AfterEach
    fun tearDown() {
        // 测试后清理
    }
}
```

### 嵌套测试

使用嵌套测试提高测试组织性：

```kotlin
@DisplayName("外部测试")
class OuterTest {

    @Nested
    @DisplayName("内部测试组")
    inner class InnerTests {
        
        @Test
        @DisplayName("内部测试方法")
        fun innerTest() {
            // 测试代码
        }
    }
}
```

### 参数化测试

使用参数化测试验证多种输入：

```kotlin
@ParameterizedTest
@ValueSource(strings = ["value1", "value2", "value3"])
@DisplayName("多值测试")
fun testWithMultipleValues(value: String) {
    // 使用不同的值测试相同的逻辑
}

@ParameterizedTest
@CsvSource("key1,value1", "key2,value2")
@DisplayName("键值对测试")
fun testWithKeyValuePairs(key: String, value: String) {
    // 使用键值对测试
}
```

## Kotest 测试编写指南

### BDD 风格测试

使用 BehaviorSpec 进行行为驱动测试：

```kotlin
class MyBehaviorSpec : BehaviorSpec({
    given("某个条件") {
        `when`("执行某个操作") {
            then("应该产生期望结果") {
                // 断言
            }
        }
    }
})
```

### 数据驱动测试

```kotlin
class MyDataTest : FunSpec({
    context("数据驱动测试") {
        forAll(
            row("输入1", "期望1"),
            row("输入2", "期望2")
        ) { input, expected ->
            // 测试逻辑
        }
    }
})
```

## 断言最佳实践

### JUnit 5 断言

```kotlin
import org.junit.jupiter.api.Assertions.*

// 基本断言
assertEquals(expected, actual)
assertTrue(condition)
assertFalse(condition)

// 组合断言
assertAll(
    { assertEquals(expected1, actual1) },
    { assertEquals(expected2, actual2) }
)

// 异常断言
assertThrows<IllegalArgumentException> {
    // 执行应抛出异常的代码
}
```

### Kotest 断言

```kotlin
// 基本断言
expected shouldBe actual
condition shouldBe true
value shouldNotBe null

// 集合断言
list shouldContain element
list shouldHaveSize 3

// 异常断言
shouldThrow<IllegalArgumentException> {
    // 执行应抛出异常的代码
}
```

## Mock 对象最佳实践

使用我们的测试工具类简化 mock 创建：

```kotlin
// 创建 mock
val mockObject = TestMockUtils.createMock<MyInterface>()

// 设置行为
TestMockUtils.stub(mockObject) { someMethod() }.thenReturn(result)

// 验证调用
TestMockUtils.verifyCall(mockObject) { someMethod() }
TestMockUtils.verifyCallTimes(mockObject, 2) { someMethod() }
```

## 测试覆盖率

我们的目标是保持以下测试覆盖率：

- 行覆盖率：至少 80%
- 分支覆盖率：至少 70%
- 类覆盖率：至少 90%

定期运行测试覆盖率报告：

```bash
./gradlew jacocoTestReport
```

## 常见问题解决

1. **测试执行超时**：检查异步操作是否正确完成，或者增加超时设置。

2. **设备创建问题**：使用 `DeviceFactory` 而不是手动创建设备对象。

3. **Mock 对象行为不符合预期**：确保正确设置了 mock 行为，使用我们的工具类可减少常见错误。

4. **多线程测试问题**：使用 JUnit 5 的 `@Timeout` 注解，必要时增加同步机制。

## 总结

遵循本指南可以帮助保持测试代码的一致性和高质量。随着项目发展，我们将不断更新这些最佳实践。 