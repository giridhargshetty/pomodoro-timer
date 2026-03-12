package com.wildtribe.drive.model

/**
 * Represents a scanned BLE device shown in the device list.
 */
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isMotoRound: Boolean = name.contains("MotoRound", ignoreCase = true)
) {
    /** Human-readable signal strength label. */
    val signalStrength: String
        get() = when {
            rssi >= -60 -> "Excellent"
            rssi >= -70 -> "Good"
            rssi >= -80 -> "Fair"
            else -> "Weak"
        }
}

/**
 * BLE connection states used across the app.
 */
enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RECONNECTING;

    val displayName: String
        get() = when (this) {
            DISCONNECTED  -> "Disconnected"
            SCANNING      -> "Scanning…"
            CONNECTING    -> "Connecting…"
            CONNECTED     -> "Connected"
            RECONNECTING  -> "Reconnecting…"
        }
}
