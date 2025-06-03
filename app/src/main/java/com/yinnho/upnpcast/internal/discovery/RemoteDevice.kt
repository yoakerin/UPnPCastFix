package com.yinnho.upnpcast.internal.discovery

/**
 * Remote device information - Simplified version
 */
data class RemoteDevice(
    val id: String,                    // Unique device identifier (using location URL)
    val displayName: String,           // Display name
    val address: String,              // Device address
    val manufacturer: String = "",     // Manufacturer
    val model: String = "",           // Model
    val details: Map<String, Any> = emptyMap()  // Device details (location, usn, server, etc.)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RemoteDevice) return false
        return id == other.id
    }
    
    override fun hashCode(): Int {
        return id.hashCode()
    }
    
    override fun toString(): String {
        return "RemoteDevice(id='$id', name='$displayName', address='$address')"
    }
} 