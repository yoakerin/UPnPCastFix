package com.yinnho.upnpcast.internal

/**
 * Device registry interface - Reference Cling Registry design
 * Manages all discovered UPnP devices
 */
interface Registry {
    
    /**
     * Get all devices
     */
    fun getDevices(): Collection<RemoteDevice>
    
    /**
     * Get device by UDN
     */
    fun getDevice(udn: String): RemoteDevice?
    
    /**
     * Add device to registry
     */
    fun addDevice(device: RemoteDevice)
    
    /**
     * Remove device from registry
     */
    fun removeDevice(device: RemoteDevice)
    
    /**
     * Add registry listener
     */
    fun addListener(listener: RegistryListener)
    
    /**
     * Remove registry listener
     */
    fun removeListener(listener: RegistryListener)
    
    /**
     * Start device discovery
     */
    fun startDiscovery()
    
    /**
     * Stop device discovery
     */
    fun stopDiscovery()
    
    /**
     * Whether device discovery is in progress
     */
    fun isDiscovering(): Boolean
}

/**
 * Registry listener interface - Reference Cling RegistryListener
 */
interface RegistryListener {
    
    /**
     * Device was added to registry
     */
    fun deviceAdded(registry: Registry, device: RemoteDevice) {}
    
    /**
     * Device was removed from registry
     */
    fun deviceRemoved(registry: Registry, device: RemoteDevice) {}
    
    /**
     * Device was updated
     */
    fun deviceUpdated(registry: Registry, device: RemoteDevice) {}
    
    /**
     * Called before registry maintenance begins
     */
    fun beforeShutdown(registry: Registry) {}
    
    /**
     * Called after registry maintenance completes
     */
    fun afterShutdown() {}
} 