package com.yinnho.upnpcast.internal.core

import kotlinx.coroutines.*

/**
 * Unified coroutine scope management - minimal implementation
 */
internal object ScopeManager {
    
    val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("UPnPCast"))
    val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob() + CoroutineName("UPnPCast-UI"))
    
    fun cleanup() {
        appScope.cancel()
        uiScope.cancel()
    }
} 