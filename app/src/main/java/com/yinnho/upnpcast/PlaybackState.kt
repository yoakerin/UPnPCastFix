package com.yinnho.upnpcast

/**
 * 播放状态枚举 - 极简版
 */
enum class PlaybackState {
    IDLE,        // 空闲
    PLAYING,     // 播放中
    PAUSED,      // 暂停
    STOPPED,     // 停止
    BUFFERING,   // 缓冲中
    ERROR        // 错误状态
} 