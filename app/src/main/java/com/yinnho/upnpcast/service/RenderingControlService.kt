package com.yinnho.upnpcast.service

import com.yinnho.upnpcast.interfaces.DLNAService

/**
 * 渲染控制服务接口
 * 继承DLNAService接口，提供渲染控制功能
 */
interface RenderingControlService : DLNAService {
    // 渲染控制特有方法
    fun setVolume(instanceId: String, channel: String, desiredVolume: UInt)
    fun getVolume(instanceId: String, channel: String): UInt
    fun setMute(instanceId: String, channel: String, desiredMute: Boolean)
    fun getMute(instanceId: String, channel: String): Boolean
    fun setBrightness(instanceId: String, desiredBrightness: UInt)
    fun getBrightness(instanceId: String): UInt
    fun setContrast(instanceId: String, desiredContrast: UInt)
    fun getContrast(instanceId: String): UInt
    fun getRenderingControlInfo(instanceId: String): RenderingControlInfo
}

/**
 * 渲染控制简化接口
 * 用于本地实现渲染控制功能
 */
interface RenderingControl {
    fun setVolume(volume: UInt)
    fun getVolume(): UInt
    fun setMute(mute: Boolean)
    fun getMute(): Boolean
    fun setBrightness(brightness: UInt)
    fun getBrightness(): UInt
    fun setContrast(contrast: UInt)
    fun getContrast(): UInt
}

/**
 * 渲染控制信息数据类
 */
data class RenderingControlInfo(
    val currentVolume: UInt = 0u,
    val currentMute: Boolean = false,
    val currentBrightness: UInt = 50u,
    val currentContrast: UInt = 50u,
    val minVolume: UInt = 0u,
    val maxVolume: UInt = 100u,
    val minBrightness: UInt = 0u,
    val maxBrightness: UInt = 100u,
    val minContrast: UInt = 0u,
    val maxContrast: UInt = 100u
) 