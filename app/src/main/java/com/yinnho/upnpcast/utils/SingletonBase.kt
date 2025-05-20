package com.yinnho.upnpcast.utils

/**
 * 单例基类
 * 提供统一的单例管理和资源释放功能
 */
abstract class SingletonBase<T : SingletonBase<T>> {
    
    /**
     * 释放资源
     * 子类需要实现此方法来释放自己持有的资源
     */
    abstract fun onRelease()
    
    /**
     * 释放资源
     * 最终用户调用的方法，处理异常并调用子类的onRelease方法
     */
    fun release() {
        try {
            onRelease()
        } catch (e: Exception) {
            logError("释放资源失败", e)
        }
    }
    
    /**
     * 记录错误日志的方法
     * 子类可以覆盖此方法使用自己的日志实现
     */
    protected open fun logError(message: String, e: Exception) {
        e.printStackTrace()
    }
    
    /**
     * 单例管理器
     * 通过泛型参数处理不同类型的单例
     */
    abstract class Companion<T : SingletonBase<T>> {
        @Volatile
        private var instance: T? = null
        
        /**
         * 获取单例实例
         * @param creator 创建实例的函数
         */
        protected fun getInstance(creator: () -> T): T {
            return instance ?: synchronized(this) {
                instance ?: creator().also { instance = it }
            }
        }
        
        /**
         * 获取已存在的单例实例
         * 如果实例不存在则抛出异常
         */
        protected fun getExistingInstance(errorMessage: String): T {
            return instance ?: throw IllegalStateException(errorMessage)
        }
        
        /**
         * 释放单例实例
         */
        fun releaseInstance() {
            synchronized(this) {
                instance?.release()
                instance = null
            }
        }
    }
} 