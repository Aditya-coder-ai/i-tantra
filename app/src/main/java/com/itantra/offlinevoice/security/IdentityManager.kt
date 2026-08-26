package com.itantra.offlinevoice.security

import com.itantra.offlinevoice.security.models.DeviceIdentity

/**
 * High-level manager for the local device's cryptographic identity and public discovery profile.
 */
class IdentityManager(
    private val keyManager: KeyManager
) {

    /** Returns the active local device identity. */
    fun getLocalIdentity(): DeviceIdentity = keyManager.getDeviceIdentity()

    /** Returns the local device ID (e.g. `VL-7F3A92`). */
    fun getDeviceId(): String = getLocalIdentity().deviceId

    /** Returns the local device Base64-encoded public identity key. */
    fun getPublicKeyBase64(): String = getLocalIdentity().publicKeyBase64

    /** Returns human-readable display name. */
    fun getDisplayName(): String = getLocalIdentity().displayName

    /** Updates device display name and saves new profile. */
    fun updateDisplayName(newDisplayName: String): DeviceIdentity {
        return keyManager.loadOrInitializeIdentity(newDisplayName)
    }

    /**
     * Produces a formatted discovery / QR code payload string.
     */
    fun exportPublicQrPayload(): String {
        val identity = getLocalIdentity()
        return "voicelink://pair?id=${identity.deviceId}&name=${identity.displayName}&key=${identity.publicKeyBase64}"
    }
}
