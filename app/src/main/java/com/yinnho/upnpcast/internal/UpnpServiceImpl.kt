package com.yinnho.upnpcast.internal

import android.content.Context
import com.yinnho.upnpcast.*
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log

/**
 * UPnP service implementation - Reference Cling design but keep it simple
 */
internal class UpnpServiceImpl(
    private val context: Context,
    private val configuration: UpnpServiceConfiguration = DefaultUpnpServiceConfiguration()
) : UpnpService {
    
    private val TAG = "UpnpServiceImpl"
    
    private val isRunning = AtomicBoolean(false)
    private val registry: Registry = RegistryImpl()
    private val controlPoint: ControlPoint = ControlPointImpl(registry)
    
    init {
        // Auto start when initialized
        startup()
    }
    
    override fun getRegistry(): Registry = registry
    
    override fun getControlPoint(): ControlPoint = controlPoint
    
    override fun startup() {
        if (isRunning.compareAndSet(false, true)) {
            // Start registry
            registry.startDiscovery()
        }
    }
    
    override fun shutdown() {
        Log.d(TAG, "Shutting down UPnP service")
        
        // Shutdown registry and resources
        (registry as? RegistryImpl)?.shutdown()
        
        // Disconnect control point
        controlPoint.disconnect()
    }
    
    override fun getConfiguration(): UpnpServiceConfiguration = configuration
    
    override fun isRunning(): Boolean = isRunning.get()
} 