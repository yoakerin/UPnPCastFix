package com.yinnho.upnpcast.core.lifecycle

import android.util.Log
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * 弱引用监听器管理器
 * 用于管理各种监听器的注册和释放，防止内存泄漏
 */
class WeakListenerManager {
    companion object {
        private const val TAG = "WeakListenerManager"
    }
    
    // 使用ConcurrentHashMap保证线程安全
    private val listenerCollections = ConcurrentHashMap<String, MutableList<WeakReference<Any>>>()
    
    /**
     * 注册监听器
     * @param type 监听器类型
     * @param listener 监听器实例
     */
    fun <T : Any> registerListener(type: String, listener: T) {
        val listeners = listenerCollections.getOrPut(type) { mutableListOf() }
        
        // 检查是否已存在该监听器
        if (listeners.any { it.get() == listener }) {
            Log.d(TAG, "监听器 $listener 已注册，类型: $type")
            return
        }
        
        // 添加新监听器的弱引用
        listeners.add(WeakReference(listener))
        Log.d(TAG, "已注册新监听器，类型: $type，当前数量: ${listeners.size}")
    }
    
    /**
     * 注销监听器
     * @param type 监听器类型
     * @param listener 监听器实例
     */
    fun <T : Any> unregisterListener(type: String, listener: T) {
        listenerCollections[type]?.let { listeners ->
            // 移除匹配的监听器
            val removed = listeners.removeIf { ref ->
                val instance = ref.get()
                instance == null || instance == listener
            }
            
            if (removed) {
                Log.d(TAG, "已注销监听器，类型: $type，当前数量: ${listeners.size}")
            }
            
            // 如果列表为空，移除整个类型
            if (listeners.isEmpty()) {
                listenerCollections.remove(type)
                Log.d(TAG, "已移除空的监听器类型: $type")
            }
        }
    }
    
    /**
     * 获取指定类型的所有有效监听器
     * @param type 监听器类型
     * @return 有效监听器列表
     */
    fun <T : Any> getListeners(type: String): List<T> {
        val result = mutableListOf<T>()
        
        listenerCollections[type]?.let { listeners ->
            // 移除已回收的弱引用
            listeners.removeIf { it.get() == null }
            
            // 添加有效的监听器到结果列表
            listeners.forEach { ref ->
                @Suppress("UNCHECKED_CAST")
                ref.get()?.let { result.add(it as T) }
            }
        }
        
        return result
    }
    
    /**
     * 通知指定类型的所有监听器
     * @param type 监听器类型
     * @param notify 通知回调函数
     */
    fun <T : Any> notifyListeners(type: String, notify: (T) -> Unit) {
        val listeners = getListeners<T>(type)
        
        if (listeners.isNotEmpty()) {
            Log.d(TAG, "正在通知 ${listeners.size} 个监听器，类型: $type")
            listeners.forEach(notify)
        }
    }
    
    /**
     * 清理指定类型的所有监听器
     * @param type 监听器类型，如果为null则清理所有类型
     */
    fun clearListeners(type: String? = null) {
        if (type != null) {
            listenerCollections.remove(type)
            Log.d(TAG, "已清理监听器类型: $type")
        } else {
            listenerCollections.clear()
            Log.d(TAG, "已清理所有监听器")
        }
    }
    
    /**
     * 清理所有无效的监听器引用
     */
    fun cleanupReferences() {
        var totalRemoved = 0
        
        listenerCollections.forEach { (type, listeners) ->
            val sizeBefore = listeners.size
            listeners.removeIf { it.get() == null }
            val removed = sizeBefore - listeners.size
            totalRemoved += removed
            
            if (listeners.isEmpty()) {
                listenerCollections.remove(type)
            }
        }
        
        if (totalRemoved > 0) {
            Log.d(TAG, "已清理 $totalRemoved 个无效监听器引用")
        }
    }
} 