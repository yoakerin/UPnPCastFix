package com.yinnho.upnpcast.property

import com.yinnho.upnpcast.model.MediaInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream

/**
 * MediaInfo的属性测试 - 简化版
 * 专注于数据属性测试，使用参数化测试简化重复验证
 */
@DisplayName("MediaInfo属性测试")
class MediaInfoPropertyTest {

    /**
     * 提供测试数据
     */
    companion object {
        fun titleAndArtistProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("标题1", "艺术家1"),
                Arguments.of("很长的标题" + "A".repeat(20), "很长的艺术家名称" + "B".repeat(20)),
                Arguments.of("特殊字符!@#$%^&*()", "特殊字符!@#$%^&*()"),
                Arguments.of("", "")  // 空字符串
            )
        }
        
        fun durationProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(0L),
                Arguments.of(1000L),  // 1秒
                Arguments.of(60000L),  // 1分钟
                Arguments.of(3600000L)  // 1小时
            )
        }
        
        fun urlProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("http://example.com/video.mp4"),
                Arguments.of("https://example.com/video.mp4?param=value&other=123"),
                Arguments.of("rtsp://streaming.example.com/live")
            )
        }
    }

    @ParameterizedTest
    @MethodSource("titleAndArtistProvider")
    @DisplayName("应正确存储各种标题和艺术家")
    fun shouldStoreVariousTitlesAndArtists(title: String, artist: String) {
        val mediaInfo = MediaInfo(title = title, artist = artist)
        assertEquals(title, mediaInfo.title)
        assertEquals(artist, mediaInfo.artist)
    }
    
    @ParameterizedTest
    @MethodSource("durationProvider")
    @DisplayName("应正确处理各种时长值")
    fun shouldHandleVariousDurations(duration: Long) {
        val mediaInfo = MediaInfo(title = "时长测试", duration = duration)
        assertEquals(duration, mediaInfo.duration)
    }
    
    @ParameterizedTest
    @MethodSource("urlProvider")
    @DisplayName("应正确处理各种URL")
    fun shouldHandleVariousUrls(url: String) {
        val mediaInfo = MediaInfo(title = "URL测试", url = url)
        assertEquals(url, mediaInfo.url)
    }
    
    @ParameterizedTest
    @ValueSource(strings = [
        "video/mp4", "audio/mp3", "image/jpeg", "application/octet-stream"
    ])
    @DisplayName("应正确处理不同的MIME类型")
    fun shouldHandleDifferentMimeTypes(mimeType: String) {
        val mediaInfo = MediaInfo(title = "MIME测试", mimeType = mimeType)
        assertEquals(mimeType, mediaInfo.mimeType)
    }
    
    @Test
    @DisplayName("应正确处理元数据映射")
    fun shouldHandleMetadataMap() {
        val sampleMetadata = mapOf(
            "resolution" to "1920x1080",
            "bitrate" to "5000000",
            "codec" to "h264"
        )
        
        val mediaInfo = MediaInfo(title = "元数据测试", metadata = sampleMetadata)
        
        assertEquals(sampleMetadata, mediaInfo.metadata)
        assertEquals("1920x1080", mediaInfo.metadata["resolution"])
        assertEquals("5000000", mediaInfo.metadata["bitrate"])
    }
    
    @RepeatedTest(5)
    @DisplayName("随机数据创建不应抛出异常")
    fun creationShouldNotThrowExceptions() {
        // 准备随机数据
        val titleLength = (1..20).random()
        val title = buildString {
            repeat(titleLength) {
                append((('A'..'Z') + ('a'..'z') + ('0'..'9')).random())
            }
        }
        
        val duration = (0..10000000L).random()
        
        // 创建MediaInfo实例 - 不应抛出异常
        val mediaInfo = MediaInfo(
            title = title,
            duration = duration
        )
        
        // 基本验证
        assertEquals(title, mediaInfo.title)
        assertEquals(duration, mediaInfo.duration)
    }
} 