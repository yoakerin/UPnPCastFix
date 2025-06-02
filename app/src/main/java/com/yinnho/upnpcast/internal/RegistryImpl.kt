package com.yinnho.upnpcast.internal

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/**
 * Device registry implementation
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
    }
    
    override fun removeListener(listener: RegistryListener) {
        listeners.remove(listener)
    }
    
    override fun startDiscovery() {
        if (isDiscovering.compareAndSet(false, true)) {
            ssdpDiscovery.startSearch()
        }
    }
    
    override fun stopDiscovery() {
        if (isDiscovering.compareAndSet(true, false)) {
            ssdpDiscovery.stopSearch()
        }
    }
    
    override fun isDiscovering(): Boolean = isDiscovering.get()
    
    /**
     * Add device to registry
     */
    override fun addDevice(device: RemoteDevice) {
        val existing = devices.put(device.id, device)
        
        if (existing == null) {
            listeners.forEach { listener ->
                try {
                    listener.deviceAdded(this, device)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to notify listener of device addition", e)
                }
            }
        } else {
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
     */
    override fun removeDevice(device: RemoteDevice) {
        val removed = devices.remove(device.id)
        if (removed != null) {
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
     * Shutdown registry
     */
    internal fun shutdown() {
        stopDiscovery()
        ssdpDiscovery.shutdown()
        devices.clear()
        listeners.clear()
    }
    
    /**
     * Clear device cache
     */
    internal fun clearDevices() {
        devices.clear()
    }
} 