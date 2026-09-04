package com.talkmitra.offlinevoice.network

/**
 * Lifecycle states of the device-to-device radio connection.
 */
enum class ConnectionState {
    DISCONNECTED,
    DISCOVERING,
    DEVICE_FOUND,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    CONNECTION_LOST,
    FAILED
}

/**
 * Underlying radio transport technologies available for offline communication.
 */
enum class TransportType(val displayName: String, val iconLabel: String) {
    WIFI_DIRECT("Wi‑Fi Direct", "⚡ Wi‑Fi Direct"),
    BLUETOOTH("Bluetooth", "📶 Bluetooth")
}
