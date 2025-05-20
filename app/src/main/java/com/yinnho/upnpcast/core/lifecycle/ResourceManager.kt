package com.yinnho.upnpcast.core.lifecycle

import android.util.Log
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 资源管理器
 * 用于统一管理需要释放的资源，防止资源泄漏
 */
object ResourceManager {
    private const val TAG = "ResourceManager"
    
    // 资源ID计数器
    private val idCounter = AtomicInteger(0)
    
    // 资源映射表
    private val resources = ConcurrentHashMap<Int, ManagedResource<*>>()
    
    /**
     * 注册资源
     * @param resource 需要管理的资源
     * @param name 资源名称（便于调试）
     * @return 资源ID
     */
    fun <T : Any> registerResource(resource: T, name: String = ""): Int {
        val id = idCounter.incrementAndGet()
        val managedResource = ManagedResource(id, resource, name)
        resources[id] = managedResource
        
        Log.d(TAG, "已注册资源: $name [ID:$id, 类型:${resource.javaClass.simpleName}]")
        return id
    }
    
    /**
     * 获取已注册的资源
     * @param id 资源ID
     * @return 资源实例或null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getResource(id: Int): T? {
        return resources[id]?.resource as? T
    }
    
    /**
     * 释放指定资源
     * @param id 资源ID
     * @return 是否成功释放
     */
    fun releaseResource(id: Int): Boolean {
        val resource = resources.remove(id)
        if (resource != null) {
            closeResource(resource.resource, resource.name)
            Log.d(TAG, "已释放资源: ${resource.name} [ID:$id]")
            return true
        }
        return false
    }
    
    /**
     * 释放所有资源
     */
    fun releaseAll() {
        Log.d(TAG, "正在释放所有资源，数量: ${resources.size}")
        
        resources.values.forEach { 
            closeResource(it.resource, it.name)
        }
        resources.clear()
        
        Log.d(TAG, "所有资源已释放")
    }
    
    /**
     * 根据资源类型释放资源
     * @param type 资源类型
     */
    fun releaseResourcesByType(type: Class<*>) {
        val toRemove = resources.filterValues { it.resource.javaClass == type }
        
        Log.d(TAG, "正在释放类型为 ${type.simpleName} 的资源，数量: ${toRemove.size}")
        
        toRemove.forEach { (id, resource) ->
            closeResource(resource.resource, resource.name)
            resources.remove(id)
        }
    }
    
    /**
     * 获取当前管理的资源数量
     */
    fun getResourceCount(): Int = resources.size
    
    /**
     * 打印当前管理的所有资源信息
     */
    fun dumpResources() {
        Log.d(TAG, "===== 资源管理器状态 =====")
        Log.d(TAG, "当前管理资源数量: ${resources.size}")
        
        resources.values.forEachIndexed { index, resource ->
            Log.d(TAG, "$index. ID:${resource.id}, 名称:${resource.name}, 类型:${resource.resource.javaClass.simpleName}")
        }
        
        Log.d(TAG, "=========================")
    }
    
    /**
     * 根据资源类型安全关闭资源
     */
    private fun closeResource(resource: Any, name: String) {
        try {
            when (resource) {
                // 处理常见的可关闭资源类型
                is Closeable -> resource.close()
                is AutoCloseable -> resource.close()
                is ScheduledExecutorService -> {
                    resource.shutdownNow()
                    resource.awaitTermination(3, TimeUnit.SECONDS)
                }
                is ScheduledFuture<*> -> resource.cancel(true)
                is Thread -> {
                    if (resource.isAlive) {
                        resource.interrupt()
                    }
                }
                // 处理自定义的生命周期组件
                is LifecycleAwareComponent -> resource.onDestroy()
                // 处理其他类型资源
                else -> {
                    // 尝试通过反射调用可能的释放方法
                    tryCallReleaseMethod(resource)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放资源 $name 时出错: ${e.message}", e)
        }
    }
    
    /**
     * 尝试通过反射调用资源的释放方法
     */
    private fun tryCallReleaseMethod(resource: Any) {
        val methods = resource.javaClass.methods
        
        // 尝试调用常见的释放方法名
        val releaseMethodNames = listOf("close", "release", "destroy", "dispose", "shutdown", "cancel")
        
        for (methodName in releaseMethodNames) {
            val method = methods.find { 
                it.name == methodName && it.parameterCount == 0 
            }
            
            if (method != null) {
                try {
                    method.invoke(resource)
                    Log.d(TAG, "通过反射调用 ${resource.javaClass.simpleName}.$methodName() 释放资源")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "反射调用 $methodName 方法失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 内部类，表示一个被管理的资源
     */
    private data class ManagedResource<T : Any>(
        val id: Int,
        val resource: T,
        val name: String,
        val registrationTime: Long = System.currentTimeMillis()
    )
} 