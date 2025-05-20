package com.yinnho.upnpcast.core.lifecycle

import java.lang.ref.WeakReference

/**
 * 通用监听器管理器
 * 封装监听器的添加、移除和通知功能，使用弱引用避免内存泄漏
 * @param T 监听器接口类型
 */
class ListenerManager<T : Any> {
    // 使用弱引用列表存储监听器
    private val listeners = mutableListOf<WeakReference<T>>()
    
    /**
     * 添加监听器
     * @param listener 要添加的监听器
     */
    fun addListener(listener: T) {
        // 检查是否已存在相同监听器
        if (listeners.none { it.get() == listener }) {
            listeners.add(WeakReference(listener))
        }
    }
    
    /**
     * 移除监听器
     * @param listener 要移除的监听器
     */
    fun removeListener(listener: T) {
        listeners.removeAll { it.get() == listener || it.get() == null }
    }
    
    /**
     * 通知所有监听器
     * @param action 要对每个监听器执行的操作
     */
    fun notifyListeners(action: (T) -> Unit) {
        // 先清理失效的弱引用
        listeners.removeAll { it.get() == null }
        
        // 对每个有效的监听器执行操作
        listeners.forEach { weakRef ->
            weakRef.get()?.let(action)
        }
    }
    
    /**
     * 清除所有监听器
     */
    fun clear() {
        listeners.clear()
    }
    
    /**
     * 获取监听器数量
     */
    fun size(): Int {
        // 先清理失效的弱引用
        listeners.removeAll { it.get() == null }
        return listeners.size
    }
    
    /**
     * 判断是否没有监听器
     */
    fun isEmpty(): Boolean = size() == 0
    
    /**
     * 判断是否有监听器
     */
    fun hasListeners(): Boolean = size() > 0
} 