package com.yinnho.upnpcast.behavior

import com.yinnho.upnpcast.model.MediaInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * MediaInfo的行为测试 - 简化版
 * 专注于特定场景的行为验证，避免与MediaInfoTest重复
 */
@DisplayName("MediaInfo行为测试")
class MediaInfoBehaviorTest {

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
    
    @Nested
    @DisplayName("基本媒体信息场景")
    inner class BasicMediaInfoScenario {
        private val mediaInfo = MediaInfo(
            title = "测试标题",
            artist = "测试艺术家",
            duration = 60000
        )
        
        @Test
        @DisplayName("媒体数据应正确呈现")
        fun shouldPresentMediaDataCorrectly() {
            assertEquals("测试标题", mediaInfo.title)
            assertEquals("测试艺术家", mediaInfo.artist)
            assertEquals("1:00", formatDuration(mediaInfo.duration))
            assertTrue(mediaInfo.toString().contains("测试标题"))
        }
    }
    
    @Nested
    @DisplayName("完整媒体信息场景")
    inner class FullMediaInfoScenario {
        private val mediaInfo = MediaInfo(
            title = "完整测试",
            artist = "测试艺术家",
            albumArt = "http://example.com/art.jpg",
            duration = 3723000,
            size = 2048000,
            mimeType = "video/mp4",
            url = "http://example.com/video.mp4",
            metadata = mapOf("resolution" to "1920x1080")
        )
        
        @Test
        @DisplayName("完整媒体数据应正确呈现")
        fun shouldPresentFullMediaDataCorrectly() {
            with(mediaInfo) {
                assertEquals("完整测试", title)
                assertEquals("测试艺术家", artist)
                assertEquals("http://example.com/art.jpg", albumArt)
                assertEquals(3723000, duration)
                assertEquals(2048000, size)
                assertEquals("video/mp4", mimeType)
                assertEquals("http://example.com/video.mp4", url)
                assertEquals("1920x1080", metadata["resolution"])
                assertEquals("1:02:03", formatDuration(duration))
            }
        }
    }
    
    @Nested
    @DisplayName("最小配置媒体信息场景")
    inner class MinimalMediaInfoScenario {
        private val mediaInfo = MediaInfo(title = "最小配置")
        
        @Test
        @DisplayName("最小配置应使用默认值")
        fun shouldUseDefaultValues() {
            assertEquals("最小配置", mediaInfo.title)
            assertEquals("", mediaInfo.artist)
            assertEquals(0, mediaInfo.duration)
            assertEquals("", mediaInfo.mimeType)
            assertEquals("0:00", formatDuration(mediaInfo.duration))
        }
    }
} 