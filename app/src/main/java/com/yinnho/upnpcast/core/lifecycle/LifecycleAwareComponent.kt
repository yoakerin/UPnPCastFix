package com.yinnho.upnpcast.core.lifecycle

/**
 * 生命周期感知组件接口
 * 所有需要感知生命周期的组件都应实现此接口
 * 用于统一管理资源释放和防止内存泄漏
 */
interface LifecycleAwareComponent {
    /**
     * 组件初始化时调用
     * 可在此初始化资源和注册监听器
     */
    fun onInit()
    
    /**
     * 组件启动时调用
     * 如启动服务、注册广播等
     */
    fun onStart()
    
    /**
     * 组件停止时调用
     * 如停止服务、解注册广播等
     */
    fun onStop()
    
    /**
     * 组件销毁时调用
     * 必须在此释放所有资源和取消所有监听器
     */
    fun onDestroy()
    
    /**
     * 暂停组件但不销毁
     * 可选实现，处理临时暂停状态
     */
    fun onPause() {
        // 默认空实现
    }
    
    /**
     * 恢复组件运行
     * 可选实现，从暂停状态恢复
     */
    fun onResume() {
        // 默认空实现
    }
} 