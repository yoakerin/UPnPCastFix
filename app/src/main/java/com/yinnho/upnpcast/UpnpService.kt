package com.yinnho.upnpcast

/**
 * UPnP service core interface - Reference Cling design
 * This is the core entry point for the entire UPnP protocol stack
 */
interface UpnpService {
    
    /**
     * Get device registry
     */
    fun getRegistry(): Registry
    
    /**
     * Get control point
     */
    fun getControlPoint(): ControlPoint
    
    /**
     * Start UPnP service
     */
    fun startup()
    
    /**
     * Shutdown UPnP service and release resources
     */
    fun shutdown()
    
    /**
     * Get service configuration
     */
    fun getConfiguration(): UpnpServiceConfiguration
    
    /**
     * Whether service is running
     */
    fun isRunning(): Boolean
} 