package com.yinnho.upnpcast.controller.sync

import com.yinnho.upnpcast.controller.device.DeviceInfoManager
import com.yinnho.upnpcast.controller.playback.PlaybackController
import com.yinnho.upnpcast.controller.volume.VolumeController
import kotlinx.coroutines.runBlocking

/**
 * 同步操作适配器
 * 为异步协程操作提供同步版本的包装器
 */
class SyncOperationsAdapter(
    private val deviceInfoManager: DeviceInfoManager,
    private val playbackController: PlaybackController,
    private val volumeController: VolumeController
) {
    /**
     * 同步版本 - 播放媒体
     */
    fun playMediaSync(mediaUrl: String, title: String, episodeLabel: String = "", positionMs: Long = 0): Boolean {
        return runBlocking {
            playbackController.playMedia(mediaUrl, title, episodeLabel, positionMs)
        }
    }
    
    /**
     * 同步版本 - 暂停播放
     */
    fun pauseSync(): Boolean {
        return runBlocking {
            playbackController.pause()
        }
    }
    
    /**
     * 同步版本 - 开始/恢复播放
     */
    fun playSync(): Boolean {
        return runBlocking {
            playbackController.play()
        }
    }
    
    /**
     * 同步版本 - 停止播放
     */
    fun stopSync(): Boolean {
        return runBlocking {
            playbackController.stop()
        }
    }
    
    /**
     * 同步版本 - 跳转到指定位置
     */
    fun seekToSync(positionMs: Long): Boolean {
        return runBlocking {
            playbackController.seekTo(positionMs)
        }
    }
    
    /**
     * 同步版本 - 设置音量
     */
    fun setVolumeSync(volume: UInt): Boolean {
        return runBlocking {
            volumeController.setVolume(volume)
        }
    }
    
    /**
     * 同步版本 - 获取音量
     */
    fun getVolumeSync(): UInt? {
        return runBlocking {
            volumeController.getVolume()
        }
    }
    
    /**
     * 同步版本 - 设置静音状态
     */
    fun setMuteSync(mute: Boolean): Boolean {
        return runBlocking {
            volumeController.setMute(mute)
        }
    }
    
    /**
     * 同步版本 - 获取静音状态
     */
    fun getMuteSync(): Boolean? {
        return runBlocking {
            volumeController.getMute()
        }
    }
    
    /**
     * 同步版本 - 获取播放位置信息
     */
    fun getPositionInfoSync(): Any? {
        return runBlocking {
            playbackController.getPositionInfo()
        }
    }
    
    /**
     * 同步版本 - 获取传输状态信息
     */
    fun getTransportInfoSync(): Any? {
        return runBlocking {
            playbackController.getTransportInfo()
        }
    }
    
    /**
     * 同步版本 - 增加音量
     */
    fun increaseVolumeSync(step: UInt = 5u): Boolean {
        return runBlocking {
            volumeController.increaseVolume(step)
        }
    }
    
    /**
     * 同步版本 - 减少音量
     */
    fun decreaseVolumeSync(step: UInt = 5u): Boolean {
        return runBlocking {
            volumeController.decreaseVolume(step)
        }
    }
    
    /**
     * 同步版本 - 切换静音状态
     */
    fun toggleMuteSync(): Boolean {
        return runBlocking {
            volumeController.toggleMute()
        }
    }
} 