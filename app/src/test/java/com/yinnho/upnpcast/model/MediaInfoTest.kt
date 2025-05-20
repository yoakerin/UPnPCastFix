package com.yinnho.upnpcast.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * MediaInfo的JUnit 5测试
 * 简化版本，减少重复测试
 */
@DisplayName("MediaInfo测试")
class MediaInfoTest {

    @Test
    @DisplayName("应正确存储基本属性")
    fun shouldStoreBasicProperties() {
        val mediaInfo = MediaInfo(
            title = "测试标题",
            artist = "测试艺术家",
            duration = 60000
        )
        
        assertEquals("测试标题", mediaInfo.title)
        assertEquals("测试艺术家", mediaInfo.artist)
        assertEquals(60000, mediaInfo.duration)
    }
    
    @Test
    @DisplayName("toString应包含标题")
    fun toStringShouldContainTitle() {
        val mediaInfo = MediaInfo(title = "特殊标题123")
        assertTrue(mediaInfo.toString().contains("特殊标题123"))
    }
    
    @Test
    @DisplayName("应支持所有可选属性")
    fun shouldSupportAllProperties() {
        val mediaInfo = MediaInfo(
            title = "完整测试",
            artist = "测试艺术家",
            albumArt = "http://example.com/art.jpg",
            duration = 5000,
            size = 2048,
            mimeType = "audio/mp3",
            url = "http://example.com/audio.mp3",
            metadata = mapOf("resolution" to "320x240")
        )
        
        assertEquals("完整测试", mediaInfo.title)
        assertEquals("测试艺术家", mediaInfo.artist)
        assertEquals("http://example.com/art.jpg", mediaInfo.albumArt)
        assertEquals(5000, mediaInfo.duration)
        assertEquals(2048, mediaInfo.size)
        assertEquals("audio/mp3", mediaInfo.mimeType)
        assertEquals("http://example.com/audio.mp3", mediaInfo.url)
        assertEquals("320x240", mediaInfo.metadata["resolution"])
    }
    
    @Nested
    @DisplayName("时长格式化测试")
    inner class DurationFormattingTests {
        
        @Test
        @DisplayName("应正确格式化时长")
        fun shouldFormatDuration() {
            val cases = listOf(
                Triple(0L, "0:00", "零时长"),
                Triple(60000L, "1:00", "分钟格式"),
                Triple(3725000L, "1:02:05", "小时格式")
            )
            
            cases.forEach { (duration, expected, message) ->
                val mediaInfo = MediaInfo(title = "测试", duration = duration)
                assertEquals(expected, formatDuration(mediaInfo.duration), message)
            }
        }
    }
    
    @ParameterizedTest
    @CsvSource(
        "视频标题1, 艺术家1, 30000",
        "视频标题2, 艺术家2, 60000",
        "视频标题3, 艺术家3, 120000"
    )
    @DisplayName("应正确存储各种有效值")
    fun shouldStoreVariousValidValues(title: String, artist: String, duration: Long) {
        val mediaInfo = MediaInfo(
            title = title,
            artist = artist,
            duration = duration
        )
        
        assertEquals(title, mediaInfo.title)
        assertEquals(artist, mediaInfo.artist)
        assertEquals(duration, mediaInfo.duration)
    }
    
    @ParameterizedTest
    @ValueSource(strings = ["video/mp4", "video/avi", "audio/mp3", "audio/aac", "image/jpeg"])
    @DisplayName("应正确处理不同的MIME类型")
    fun shouldHandleDifferentMimeTypes(mimeType: String) {
        val mediaInfo = MediaInfo(
            title = "MIME类型测试",
            mimeType = mimeType
        )
        
        assertEquals(mimeType, mediaInfo.mimeType)
    }
    
    /**
     * 格式化时间工具函数
     */
    private fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
} 