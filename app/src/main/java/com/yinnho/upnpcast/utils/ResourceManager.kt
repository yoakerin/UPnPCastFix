package com.yinnho.upnpcast.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yinnho.upnpcast.core.EnhancedThreadManager
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue


/**
 * 资源管理器
 * 统一管理资源的关闭和清理
 */
object ResourceManager {
    private const val TAG = "ResourceManager"
    
    // 使用弱引用存储应用上下文
    private var applicationContextRef: WeakReference<Context> = WeakReference(null)
    
    // 跟踪需要清理的资源
    private val resources = ConcurrentLinkedQueue<CleanupResource>()
    
    // 存储需要释放的回调列表
    private val cleanupCallbacks = mutableListOf<() -> Unit>()
    
    /**
     * 设置应用上下文
     */
    fun setApplicationContext(context: Context) {
        applicationContextRef = WeakReference(context.applicationContext)
        Log.d(TAG, "应用上下文已设置")
    }
    
    /**
     * 获取应用上下文
     */
    fun getApplicationContext(): Context? = applicationContextRef.get()
    
    /**
     * 注册需要清理的资源
     */
    fun registerResource(resource: CleanupResource) {
        resources.add(resource)
        Log.d(TAG, "已注册资源: ${resource.javaClass.simpleName}")
    }
    
    /**
     * 注册可关闭资源
     */
    fun registerCloseable(closeable: Closeable, description: String = "未知资源") {
        registerResource(object : CleanupResource {
            override fun cleanup() {
                try {
                    closeable.close()
                    Log.d(TAG, "已关闭资源: $description")
                } catch (e: Exception) {
                    Log.e(TAG, "关闭资源失败: $description", e)
                }
            }
            
            override fun toString(): String = description
        })
    }
    
    /**
     * 注册需要在资源释放时执行的回调
     */
    fun registerCleanup(cleanupCallback: () -> Unit) {
        synchronized(cleanupCallbacks) {
            cleanupCallbacks.add(cleanupCallback)
            Log.d(TAG, "已注册资源清理回调，当前总数: ${cleanupCallbacks.size}")
        }
    }
    
    /**
     * 移除已注册的回调
     */
    fun unregisterCleanup(cleanupCallback: () -> Unit) {
        synchronized(cleanupCallbacks) {
            val removed = cleanupCallbacks.remove(cleanupCallback)
            if (removed) {
                Log.d(TAG, "已移除资源清理回调，当前总数: ${cleanupCallbacks.size}")
            }
        }
    }
    
    /**
     * 释放所有资源
     */
    fun releaseAll() {
        Log.d(TAG, "开始释放所有资源，数量: ${resources.size}")
        
        // 复制一份资源列表，避免并发修改问题
        val resourcesToCleanup = ArrayList(resources)
        resources.clear()
        
        // 逐个清理资源
        for (resource in resourcesToCleanup) {
            try {
                Log.d(TAG, "正在清理资源: $resource")
                resource.cleanup()
            } catch (e: Exception) {
                Log.e(TAG, "清理资源时出错: $resource", e)
            }
        }
        
        // 清除上下文引用
        applicationContextRef.clear()
        
        // 延迟关闭线程池
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                EnhancedThreadManager.shutdown()
            } catch (e: Exception) {
                Log.e(TAG, "关闭线程池失败", e)
            }
        }, 500)
        
        Log.d(TAG, "所有资源已释放")
    }
    
    /**
     * 释放所有已注册的资源
     */
    fun releaseAllCallbacks() {
        synchronized(cleanupCallbacks) {
            Log.d(TAG, "开始释放所有资源，总数: ${cleanupCallbacks.size}")
            
            // 复制列表，避免并发修改
            val callbacks = ArrayList(cleanupCallbacks)
            
            // 执行所有回调
            for (callback in callbacks) {
                try {
                    callback.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "资源释放过程中发生错误", e)
                }
            }
            
            // 清空列表
            cleanupCallbacks.clear()
            Log.d(TAG, "所有资源已释放")
        }
    }
    
    /**
     * 需要清理的资源接口
     */
    interface CleanupResource {
        /**
         * 清理资源
         */
        fun cleanup()
    }
} 