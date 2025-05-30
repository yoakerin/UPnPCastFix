package com.yinnho.upnpcast.internal

import android.util.Log
import com.yinnho.upnpcast.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Device registry implementation - Using actual SSDP discovery functionality
 */
internal class RegistryImpl : Registry {
    
    private val TAG = "RegistryImpl"
    
    // Device storage - Using UDN as key
    private val devices = ConcurrentHashMap<String, RemoteDevice>()
    
    // Listener collection
    private val listeners = CopyOnWriteArraySet<RegistryListener>()
    
    // Discovery state
    private val isDiscovering = AtomicBoolean(false)
    
    // Actual SSDP discoverer - From backup real code
    private val ssdpDiscovery = SsdpDeviceDiscovery(this)
    
    override fun getDevices(): Collection<RemoteDevice> {
        return devices.values.toList()
    }
    
    override fun getDevice(udn: String): RemoteDevice? {
        return devices[udn]
    }
    
    override fun addListener(listener: RegistryListener) {
        listeners.add(listener)
        Log.d(TAG, "Registry listener added, current listener count: ${listeners.size}")
    }
    
    override fun removeListener(listener: RegistryListener) {
        listeners.remove(listener)
        Log.d(TAG, "Registry listener removed, current listener count: ${listeners.size}")
    }
    
    override fun startDiscovery() {
        if (isDiscovering.compareAndSet(false, true)) {
            Log.d(TAG, "Starting device discovery - Using actual SSDP")
            ssdpDiscovery.startSearch()
        }
    }
    
    override fun stopDiscovery() {
        if (isDiscovering.compareAndSet(true, false)) {
            Log.d(TAG, "Stopping device discovery")
            ssdpDiscovery.stopSearch()
        }
    }
    
    override fun isDiscovering(): Boolean = isDiscovering.get()
    
    /**
     * Add device to registry
     * Called by SsdpDeviceDiscovery
     */
    override fun addDevice(device: RemoteDevice) {
        val existing = devices.put(device.id, device)
        
        if (existing == null) {
            Log.d(TAG, "New device discovered: ${device.displayName} (${device.id})")
            // Notify listeners
            listeners.forEach { listener ->
                try {
                    listener.deviceAdded(this, device)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to notify listener of device addition", e)
                }
            }
        } else {
            Log.d(TAG, "Device updated: ${device.displayName} (${device.id})")
            // Notify listeners of device update
            listeners.forEach { listener ->
                try {
                    listener.deviceUpdated(this, device)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to notify listener of device update", e)
                }
            }
        }
    }
    
    /**
     * Remove device from registry
     * Called by SsdpDeviceDiscovery
     */
    override fun removeDevice(device: RemoteDevice) {
        val removed = devices.remove(device.id)
        if (removed != null) {
            Log.d(TAG, "Device went offline: ${device.displayName} (${device.id})")
            // Notify listeners
            listeners.forEach { listener ->
                try {
                    listener.deviceRemoved(this, device)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to notify listener of device removal", e)
                }
            }
        }
    }
    
    /**
     * Shutdown registry and release resources
     */
    internal fun shutdown() {
        Log.d(TAG, "Shutting down device registry")
        stopDiscovery()
        ssdpDiscovery.shutdown()
        devices.clear()
        listeners.clear()
    }
    
    /**
     * Clear all device cache
     */
    internal fun clearDevices() {
        Log.d(TAG, "Clearing all device cache, current device count: ${devices.size}")
        devices.clear()
        Log.d(TAG, "Device cache cleared")
    }
} 