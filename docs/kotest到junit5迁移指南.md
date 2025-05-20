# Kotest到JUnit 5迁移指南

## 1. 概述

本文档提供从Kotest框架迁移到JUnit 5的详细指导。作为UPnPCast优化计划的一部分，我们决定简化测试框架，只使用JUnit 5进行测试，移除Kotest依赖以减少复杂性。

## 2. 迁移策略

### 2.1 依赖变更

将Kotest依赖替换为JUnit 5依赖：

```kotlin
// 移除Kotest依赖
// testImplementation("io.kotest:kotest-runner-junit5:5.x.x")
// testImplementation("io.kotest:kotest-assertions-core:5.x.x")
// testImplementation("io.kotest:kotest-property:5.x.x")

// 添加JUnit 5依赖
testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1") // 用于参数化测试
```

### 2.2 测试风格迁移对照表

| Kotest风格 | JUnit 5对应方式 |
|------------|---------------|
| StringSpec  | 标准的@Test方法 |
| FunSpec     | @Test方法 + @DisplayName注解 |
| BehaviorSpec (Given/When/Then) | @Nested嵌套类 + @Test方法 |
| DescribeSpec | @Nested嵌套类 + @Test方法 |
| Property Testing | @ParameterizedTest + 参数源 |

### 2.3 断言迁移对照表

| Kotest断言 | JUnit 5断言 |
|-----------|------------|
| value shouldBe expected | assertEquals(expected, value) |
| value shouldNotBe expected | assertNotEquals(expected, value) |
| value shouldContain substring | assertTrue(value.contains(substring)) |
| value.shouldBeNull() | assertNull(value) |
| value.shouldNotBeNull() | assertNotNull(value) |
| shouldThrow<Exception> { code } | assertThrows<Exception> { code } |

## 3. 具体迁移示例

### 3.1 StringSpec迁移

**Kotest代码：**
```kotlin
class MediaInfoTest : StringSpec({
    "MediaInfo should store title correctly" {
        val mediaInfo = MediaInfo(title = "测试")
        mediaInfo.title shouldBe "测试"
    }
})
```

**JUnit 5代码：**
```kotlin
class MediaInfoTest {
    @Test
    @DisplayName("MediaInfo should store title correctly")
    fun shouldStoreTitle() {
        val mediaInfo = MediaInfo(title = "测试")
        assertEquals("测试", mediaInfo.title)
    }
}
```

### 3.2 BehaviorSpec迁移

**Kotest代码：**
```kotlin
class MediaInfoBehaviorSpec : BehaviorSpec({
    given("一个MediaInfo对象") {
        val mediaInfo = MediaInfo(title = "测试", duration = 60000)
        
        `when`("访问其属性") {
            val title = mediaInfo.title
            
            then("应返回正确的值") {
                title shouldBe "测试"
            }
        }
    }
})
```

**JUnit 5代码：**
```kotlin
class MediaInfoTest {
    @Nested
    @DisplayName("给定一个MediaInfo对象")
    inner class GivenMediaInfo {
        private val mediaInfo = MediaInfo(title = "测试", duration = 60000)
        
        @Nested
        @DisplayName("当访问其属性时")
        inner class WhenAccessingProperties {
            private val title = mediaInfo.title
            
            @Test
            @DisplayName("应返回正确的值")
            fun thenReturnCorrectValues() {
                assertEquals("测试", title)
            }
        }
    }
}
```

### 3.3 属性测试迁移

**Kotest代码：**
```kotlin
"MediaInfo should handle different durations" {
    checkAll(
        Arb.long(min = 0, max = 100000)
    ) { duration ->
        val mediaInfo = MediaInfo(title = "测试", duration = duration)
        mediaInfo.duration shouldBe duration
    }
}
```

**JUnit 5代码：**
```kotlin
@ParameterizedTest
@ValueSource(longs = [0, 1000, 10000, 60000, 100000])
@DisplayName("MediaInfo should handle different durations")
fun shouldHandleDifferentDurations(duration: Long) {
    val mediaInfo = MediaInfo(title = "测试", duration = duration)
    assertEquals(duration, mediaInfo.duration)
}
```

## 4. 迁移检查清单

- [x] 更新build.gradle.kts，移除Kotest依赖，添加JUnit 5依赖
- [x] 将所有StringSpec测试迁移为标准JUnit 5测试
- [x] 将所有BehaviorSpec/DescribeSpec测试迁移为嵌套测试
- [x] 将所有Property测试迁移为参数化测试
- [x] 更新所有断言，从Kotest风格改为JUnit 5风格
- [ ] 确保所有测试仍能正确运行
- [ ] 移除Kotest相关导入和引用

## 5. 注意事项

1. JUnit 5的参数化测试功能没有Kotest的属性测试那么强大，因此可能需要更明确地指定测试数据。
2. 嵌套类和@DisplayName可以达到类似BehaviorSpec的效果，但结构会有所不同。
3. 确保在build.gradle.kts中正确配置JUnit 5运行器。

## 6. 参考资源

- [JUnit 5官方文档](https://junit.org/junit5/docs/current/user-guide/)
- [JUnit 5参数化测试指南](https://junit.org/junit5/docs/current/user-guide/#writing-tests-parameterized-tests)
- [Kotest文档](https://kotest.io/docs/) 