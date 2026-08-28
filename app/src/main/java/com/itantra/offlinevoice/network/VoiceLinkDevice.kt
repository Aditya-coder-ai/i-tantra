package com.itantra.offlinevoice.network

/**
 * Represents a discovered or connected peer running the VoiceLink app.
 *
 * Provides user-friendly identification (Display Name) as well as
 * cryptographic identity (Device ID & Public Key Fingerprint).
 */
data class VoiceLinkDevice(
    val deviceId: String,
    val displayName: String,
    val transportType: TransportType,
    val nativeAddress: String, // MAC address or IP address
    val signalStrength: Int = 4, // 0 to 4 rating
    val isPaired: Boolean = false,
    val publicKeyFingerprint: String = "",
    val isGroupOwner: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val formattedId: String
        get() = if (deviceId.startsWith("VL-")) deviceId else "VL-${deviceId.takeLast(6).uppercase()}"
}
