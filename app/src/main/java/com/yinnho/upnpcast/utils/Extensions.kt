package com.yinnho.upnpcast.utils

import android.content.SharedPreferences

/**
 * 通用扩展函数
 * 提供SharedPreferences相关的扩展功能
 */

/**
 * SharedPreferences扩展函数
 * 简化put操作，链式调用
 */
fun SharedPreferences.Editor.putSafely(key: String, value: String?): SharedPreferences.Editor {
    if (value != null) {
        putString(key, value)
    }
    return this
}

/**
 * SharedPreferences扩展函数
 * 简化put操作，链式调用
 */
fun SharedPreferences.Editor.putSafely(key: String, value: Int?): SharedPreferences.Editor {
    if (value != null) {
        putInt(key, value)
    }
    return this
}

/**
 * SharedPreferences扩展函数
 * 简化put操作，链式调用
 */
fun SharedPreferences.Editor.putSafely(key: String, value: Long?): SharedPreferences.Editor {
    if (value != null) {
        putLong(key, value)
    }
    return this
}

/**
 * SharedPreferences扩展函数
 * 简化put操作，链式调用
 */
fun SharedPreferences.Editor.putSafely(key: String, value: Boolean?): SharedPreferences.Editor {
    if (value != null) {
        putBoolean(key, value)
    }
    return this
}

/**
 * SharedPreferences扩展函数
 * 简化put操作，链式调用
 */
fun SharedPreferences.Editor.putSafely(key: String, value: Float?): SharedPreferences.Editor {
    if (value != null) {
        putFloat(key, value)
    }
    return this
}

/**
 * SharedPreferences扩展函数
 * 简化put操作，链式调用
 */
fun SharedPreferences.Editor.putSafely(key: String, value: Set<String>?): SharedPreferences.Editor {
    if (value != null) {
        putStringSet(key, value)
    }
    return this
} 