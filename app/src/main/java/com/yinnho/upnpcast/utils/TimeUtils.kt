package com.yinnho.upnpcast.utils

/**
 * 时间转换工具类
 * 提供毫秒与HH:MM:SS格式之间的转换
 */
object TimeUtils {
    
    /**
     * 将毫秒转换为HH:MM:SS格式时间字符串
     * @param milliseconds 毫秒时间
     * @return HH:MM:SS格式的时间字符串
     */
    fun millisecondsToTimeString(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
    
    /**
     * 将HH:MM:SS格式的时间字符串转换为毫秒
     * @param timeString HH:MM:SS格式的时间字符串
     * @return 毫秒时间，解析失败则返回0
     */
    fun timeStringToMilliseconds(timeString: String): Long {
        return try {
            // 解析时间格式 HH:MM:SS 或 MM:SS
            val timeParts = timeString.split(":")
            
            when (timeParts.size) {
                3 -> { // HH:MM:SS
                    val hours = timeParts[0].toLongOrNull() ?: 0
                    val minutes = timeParts[1].toLongOrNull() ?: 0
                    val seconds = timeParts[2].toLongOrNull() ?: 0
                    
                    // 转换为毫秒
                    (hours * 3600 + minutes * 60 + seconds) * 1000
                }
                2 -> { // MM:SS
                    val minutes = timeParts[0].toLongOrNull() ?: 0
                    val seconds = timeParts[1].toLongOrNull() ?: 0
                    
                    // 转换为毫秒
                    (minutes * 60 + seconds) * 1000
                }
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
} 