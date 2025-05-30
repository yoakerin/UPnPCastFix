package com.yinnho.upnpcast

/**
 * Control point interface - Reference Cling ControlPoint design
 * Responsible for device discovery and operation execution
 */
interface ControlPoint {
    
    /**
     * Search for all devices
     */
    fun search()
    
    /**
     * Search for specific device type
     */
    fun search(deviceType: String)
    
    /**
     * Execute action
     */
    fun execute(action: Action)
    
    /**
     * Get currently connected device
     */
    fun getCurrentDevice(): RemoteDevice?
    
    /**
     * Connect to device
     */
    fun connectToDevice(device: RemoteDevice): Boolean
    
    /**
     * Disconnect
     */
    fun disconnect()
}

/**
 * UPnP action interface
 */
interface Action {
    /**
     * Get action name
     */
    fun getName(): String
    
    /**
     * Get target device
     */
    fun getDevice(): RemoteDevice
    
    /**
     * Execute action
     */
    fun execute(): ActionResult
}

/**
 * Action execution result
 */
data class ActionResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val outputValues: Map<String, Any> = emptyMap()
) 