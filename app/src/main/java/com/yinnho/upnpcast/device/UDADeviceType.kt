package com.yinnho.upnpcast.device

import com.yinnho.upnpcast.model.DeviceType

class UDADeviceType(val type: String, val version: Int = 1) {
    private val deviceType = DeviceType(namespace = DEFAULT_NAMESPACE, type = type, version = version)

    init {
        require(type.matches(Regex("[A-Z][a-zA-Z0-9-]*"))) { "Invalid UDA device type: $type" }
    }

    companion object {
        const val DEFAULT_NAMESPACE = "schemas-upnp-org"
        private const val DEVICE_TYPE_PREFIX = "urn:$DEFAULT_NAMESPACE:device:"

        // 标准 UPnP 设备类型
        val MEDIA_SERVER = UDADeviceType("MediaServer", 1)
        val MEDIA_RENDERER = UDADeviceType("MediaRenderer", 1)
        val INTERNET_GATEWAY = UDADeviceType("InternetGatewayDevice", 1)
        val BASIC = UDADeviceType("Basic", 1)
        val PRINTER = UDADeviceType("Printer", 1)
        val SCANNER = UDADeviceType("Scanner", 1)
        val HVAC = UDADeviceType("HVAC", 1)
        val LIGHTING_CONTROLS = UDADeviceType("LightingControls", 1)
        val REMOTE_UI_CLIENT = UDADeviceType("RemoteUIClient", 1)
        val REMOTE_UI_SERVER = UDADeviceType("RemoteUIServer", 1)
        val WAN_CONNECTION = UDADeviceType("WANConnectionDevice", 1)
        val WAN_DEVICE = UDADeviceType("WANDevice", 1)
        val LAN_DEVICE = UDADeviceType("LANDevice", 1)
        val SECURITY_CONSOLE = UDADeviceType("SecurityConsole", 1)
        val DIGITAL_SECURITY_CAMERA = UDADeviceType("DigitalSecurityCamera", 1)
        val HVAC_SYSTEM = UDADeviceType("HVACSystem", 1)
        val MEDIA_SERVER_V2 = UDADeviceType("MediaServer", 2)
        val MEDIA_RENDERER_V2 = UDADeviceType("MediaRenderer", 2)

        fun valueOf(urn: String): UDADeviceType {
            try {
                require(urn.isNotEmpty()) { "Device type URN cannot be empty" }
                require(
                    urn.startsWith(
                        DEVICE_TYPE_PREFIX,
                        ignoreCase = true
                    )
                ) { "Not a UDA device type URN: $urn (must start with $DEVICE_TYPE_PREFIX)" }

                val parts = urn.split(":").filter { it.isNotEmpty() }
                require(parts.size >= 5) { "Invalid device type URN format: $urn (expected format: urn:$DEFAULT_NAMESPACE:device:type:version)" }
                require(parts[0] == "urn") { "Device type URN must start with 'urn': $urn" }
                require(parts[2] == "device") { "Invalid device type URN (missing 'device'): $urn" }
                require(
                    parts[1].equals(
                        DEFAULT_NAMESPACE,
                        ignoreCase = true
                    )
                ) { "Not a UDA device type URN: $urn (namespace must be $DEFAULT_NAMESPACE)" }

                val type = parts[3]
                val version = try {
                    parts[4].toInt().also {
                        require(it > 0) { "Device type version must be greater than 0: $it" }
                    }
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Invalid device type version number: ${parts[4]} (must be a positive integer)")
                }

                require(isValidDeviceType(type)) { "Invalid UDA device type format: $type (must start with uppercase letter and contain only letters, numbers, and hyphens)" }

                return UDADeviceType(type, version)
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to parse UDA device type URN: $urn", e)
            }
        }

        fun valueOfOrNull(urn: String): UDADeviceType? {
            return try {
                if (urn.isEmpty() || !urn.startsWith(DEVICE_TYPE_PREFIX, ignoreCase = true)) {
                    return null
                }
                valueOf(urn)
            } catch (e: Exception) {
                null
            }
        }

        fun isUDADeviceType(deviceType: DeviceType): Boolean {
            return deviceType.namespace.equals(DEFAULT_NAMESPACE, ignoreCase = true)
        }

        fun isValidDeviceType(deviceType: String): Boolean {
            return deviceType.matches(Regex("[A-Z][a-zA-Z0-9-]*"))
        }
    }

    fun toFriendlyString(): String = "$type v$version"

    fun isDeviceType(otherType: String): Boolean = type.equals(otherType, ignoreCase = true)

    fun hasVersion(minimumVersion: Int): Boolean = version >= minimumVersion

    fun toDeviceType(): DeviceType = deviceType

    override fun toString(): String = deviceType.toString()
}