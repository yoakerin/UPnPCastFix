package com.yinnho.upnpcast.api

/**
 * 播放状态枚举
 * 定义了媒体播放过程中可能的各种状态
 */
enum class PlaybackState {
    // 初始状态，尚未开始播放
    IDLE,
    
    // 正在缓冲数据
    BUFFERING,
    
    // 正在播放
    PLAYING,
    
    // 已暂停
    PAUSED,
    
    // 已停止
    STOPPED,
    
    // 已完成播放
    COMPLETED,
    
    // 状态转换中
    TRANSITIONING,
    
    // 发生错误
    ERROR
} 