package com.yinnho.upnpcast.model

/**
 * 扩展了DeviceType，添加了常用的电视品牌常量
 */
val DeviceType.Companion.SAMSUNG_TV: DeviceType
    get() = DeviceType(namespace = "samsung.com", type = "SamsungTV", version = 1)

val DeviceType.Companion.XIAOMI_TV: DeviceType
    get() = DeviceType(namespace = "xiaomi.com", type = "XiaomiTV", version = 1)

val DeviceType.Companion.LG_TV: DeviceType
    get() = DeviceType(namespace = "lg.com", type = "LGTV", version = 1)

val DeviceType.Companion.SONY_TV: DeviceType
    get() = DeviceType(namespace = "sony.com", type = "SonyTV", version = 1)

val DeviceType.Companion.HISENSE_TV: DeviceType
    get() = DeviceType(namespace = "hisense.com", type = "HisenseTV", version = 1)

val DeviceType.Companion.TCL_TV: DeviceType
    get() = DeviceType(namespace = "tcl.com", type = "TCLTV", version = 1)

val DeviceType.Companion.GENERIC: DeviceType
    get() = DeviceType(namespace = "upnp.org", type = "MediaRenderer", version = 1)

/**
 * 传输状态枚举
 * 定义DLNA传输状态常量
 */
enum class TransportState(val value: String) {
    STOPPED("STOPPED"),
    PLAYING("PLAYING"),
    TRANSITIONING("TRANSITIONING"),
    PAUSED_PLAYBACK("PAUSED_PLAYBACK"),
    PAUSED_RECORDING("PAUSED_RECORDING"),
    RECORDING("RECORDING"),
    NO_MEDIA_PRESENT("NO_MEDIA_PRESENT"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromString(value: String?): TransportState {
            if (value == null) return UNKNOWN
            return values().find { it.value == value } ?: UNKNOWN
        }
    }
}

/**
 * 状态变量
 * 表示UPnP服务的状态变量
 */
interface StateVariable {
    /**
     * 变量名称
     */
    val name: String

    /**
     * 数据类型
     */
    val dataType: String

    /**
     * 默认值
     */
    val defaultValue: String?

    /**
     * 允许的值列表
     */
    val allowedValues: List<String>?

    /**
     * 最小值
     */
    val minimum: Number?

    /**
     * 最大值
     */
    val maximum: Number?

    /**
     * 步长
     */
    val step: Number?

    /**
     * 发送事件
     */
    val sendEvents: Boolean

    /**
     * 当前值
     */
    var value: Any?
}

/**
 * 动作参数
 * 表示UPnP动作的参数
 */
interface ActionArgument {
    /**
     * 参数名称
     */
    val name: String

    /**
     * 参数方向
     */
    val direction: Direction

    /**
     * 关联的状态变量
     */
    val relatedStateVariable: StateVariable?

    /**
     * 参数方向枚举
     */
    enum class Direction {
        IN, OUT
    }
} 