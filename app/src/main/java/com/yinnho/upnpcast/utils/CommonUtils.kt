package com.yinnho.upnpcast.utils

/**
 * 可释放资源接口
 * 所有需要释放资源的组件都应实现此接口
 */
interface Releasable {
    /**
     * 释放所有资源
     * 实现类应该确保此方法可以安全地多次调用
     */
    fun release()
}