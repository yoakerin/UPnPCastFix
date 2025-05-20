# JUnit 4 到 JUnit 5 迁移指南

本文档提供从 JUnit 4 到 JUnit 5 的迁移步骤和最佳实践，帮助开发人员顺利过渡到新的测试框架。

## 主要变化

### 包结构变化

JUnit 5 使用新的包名结构：

| JUnit 4                    | JUnit 5                        |
|----------------------------|--------------------------------|
| `org.junit`                | `org.junit.jupiter.api`        |
| `org.junit.Assert`         | `org.junit.jupiter.api.Assertions` |
| `org.junit.Assume`         | `org.junit.jupiter.api.Assumptions` |

### 注解变化

| JUnit 4            | JUnit 5              | 说明                     |
|--------------------|----------------------|--------------------------|
| `@Test`            | `@Test`              | 包名不同                 |
| `@Before`          | `@BeforeEach`        | 每个测试方法前执行       |
| `@After`           | `@AfterEach`         | 每个测试方法后执行       |
| `@BeforeClass`     | `@BeforeAll`         | 所有测试方法前执行       |
| `@AfterClass`      | `@AfterAll`          | 所有测试方法后执行       |
| `@Ignore`          | `@Disabled`          | 禁用测试                 |
| `@Category`        | `@Tag`               | 测试分组                 |
| `@RunWith`         | `@ExtendWith`        | 扩展机制                 |
| `@Rule` / `@ClassRule` | `@ExtendWith`    | 规则被扩展替代           |

### 断言方法变化

JUnit 5 的断言方法被移动到 `Assertions` 类中，并且增加了 lambda 表达式支持。

```kotlin
// JUnit 4
import org.junit.Assert.*
assertEquals(expected, actual)
assertTrue(condition)

// JUnit 5
import org.junit.jupiter.api.Assertions.*
assertEquals(expected, actual)
assertTrue(condition)
assertThrows<IllegalArgumentException> { /* 代码块 */ }
```

## 迁移过程

### 步骤 1：更新依赖

在 `build.gradle.kts` 中，确保添加 JUnit 5 依赖并配置测试执行：

```kotlin
dependencies {
    // 移除 JUnit 4 依赖
    // testImplementation("junit:junit:4.13.2")
    
    // 添加 JUnit 5 依赖
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### 步骤 2：更新导入语句

将 JUnit 4 导入替换为 JUnit 5 导入：

```kotlin
// 替换这些导入
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.Assert.*

// 使用这些导入
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
```

### 步骤 3：更新注解

将所有 JUnit 4 注解替换为对应的 JUnit 5 注解。

### 步骤 4：更新断言

确保所有断言方法使用 `Assertions` 类。

### 步骤 5：更新规则和运行器

将 JUnit 4 规则和运行器替换为 JUnit 5 扩展。

```kotlin
// JUnit 4
@RunWith(MockitoJUnitRunner::class)
class MyTest {
    @Rule
    @JvmField
    val thrown: ExpectedException = ExpectedException.none()
}

// JUnit 5
@ExtendWith(MockitoExtension::class)
class MyTest {
    // 使用 assertThrows 替代 ExpectedException
}
```

### 步骤 6：使用新特性

升级后，利用 JUnit 5 新特性改进测试：

1. 使用 `@DisplayName` 提供更好的测试描述
2. 使用 `@Nested` 组织相关测试
3. 使用 `@ParameterizedTest` 进行参数化测试
4. 使用 `@TestInstance` 控制测试实例生命周期

## 示例：RemoteDeviceTest 迁移

### JUnit 4 版本

```kotlin
package com.yinnho.upnpcast.model

import org.junit.Assert.*
import org.junit.Test

class RemoteDeviceTest {
    @Test
    fun testDeviceProperties() {
        val device = createTestDevice()
        assertEquals("uuid:test-device-123", device.identity.udn)
    }
}
```

### JUnit 5 版本

```kotlin
package com.yinnho.upnpcast.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

class RemoteDeviceTest {
    @Test
    @DisplayName("设备属性应该按照预期设置")
    fun testDeviceProperties() {
        val device = createTestDevice()
        assertEquals("uuid:test-device-123", device.identity.udn)
    }
}
```

## 常见陷阱和解决方案

### 1. 类型转换和泛型问题

JUnit 5 的泛型处理更严格，需要显式类型声明。

```kotlin
// 问题代码
val serviceType = TestMockUtils.createMock<ServiceType>()
// 可能的解决方法
val serviceType: ServiceType = TestMockUtils.createMock()
```

### 2. 静态方法访问

JUnit 5 不再通过静态导入访问所有断言方法。

```kotlin
// 不再支持
import static org.junit.Assert.*

// 应改为
import org.junit.jupiter.api.Assertions.*
```

### 3. 超时处理

JUnit 5 中的超时处理方式发生了变化。

```kotlin
// JUnit 4
@Test(timeout = 1000)
fun testWithTimeout() { ... }

// JUnit 5
@Test
fun testWithTimeout() {
    assertTimeout(Duration.ofMillis(1000)) {
        // 测试代码
    }
}
```

## 最佳实践

1. **分批迁移**：先修改简单的测试类，再处理复杂的测试类
2. **保留版本控制**：每次迁移一组相关测试，以便在出现问题时回滚
3. **运行测试**：每迁移一个测试类就立即运行，确保正常工作
4. **使用新功能**：不要仅仅更换API，充分利用JUnit 5的新特性提高测试质量
5. **考虑模块化**：将大型测试类分解为多个小型测试类，利用@Nested注解组织 