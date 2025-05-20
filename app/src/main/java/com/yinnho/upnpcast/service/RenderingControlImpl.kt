package com.yinnho.upnpcast.service

/**
 * 渲染控制默认实现
 */
class RenderingControlImpl : RenderingControl {
    private var volume: UInt = 50u
    private var mute: Boolean = false
    private var brightness: UInt = 50u
    private var contrast: UInt = 50u

    override fun setVolume(volume: UInt) {
        if (volume <= 100u) {
            this.volume = volume
        }
    }

    override fun getVolume(): UInt {
        return volume
    }

    override fun setMute(mute: Boolean) {
        this.mute = mute
    }

    override fun getMute(): Boolean {
        return mute
    }

    override fun setBrightness(brightness: UInt) {
        if (brightness <= 100u) {
            this.brightness = brightness
        }
    }

    override fun getBrightness(): UInt {
        return brightness
    }

    override fun setContrast(contrast: UInt) {
        if (contrast <= 100u) {
            this.contrast = contrast
        }
    }

    override fun getContrast(): UInt {
        return contrast
    }
} 