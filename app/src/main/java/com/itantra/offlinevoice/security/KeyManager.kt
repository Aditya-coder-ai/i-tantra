package com.itantra.offlinevoice.security

import com.itantra.offlinevoice.security.models.DeviceIdentity
import com.itantra.offlinevoice.security.models.TrustedDevice
import java.security.KeyPair

/**
 * Manages the device's long-term cryptographic identity, key lifecycle,
 * and trusted peer device store.
 *
 * Enforces key protection: private keys are never returned as plaintext strings
 * or exposed in debug logs.
 */
class KeyManager(
    private val storage: SecureStorage = InMemorySecureStorage()
) {

    private var localKeyPair: KeyPair? = null
    private var localIdentity: DeviceIdentity? = null

    init {
        loadOrInitializeIdentity()
    }

    /**
     * Loads existing identity from secure storage or generates a new EC P-256 key pair.
     */
    @Synchronized
    fun loadOrInitializeIdentity(displayName: String = "VoiceLink Device"): DeviceIdentity {
        val existingKeyPair = storage.loadIdentityKeyPair()
        val existingIdentity = storage.loadDeviceIdentity()

        if (existingKeyPair != null && existingIdentity != null) {
            localKeyPair = existingKeyPair
            localIdentity = existingIdentity
            return existingIdentity
        }

        // Generate fresh EC P-256 key pair
        val newKeyPair = CryptoManager.generateEcKeyPair()
        val pubKeyBase64 = CryptoManager.encodePublicKey(newKeyPair.public)
        val deviceId = CryptoManager.computeDeviceId(newKeyPair.public.encoded)

        val newIdentity = DeviceIdentity(
            deviceId = deviceId,
            publicKeyBase64 = pubKeyBase64,
            displayName = displayName,
            keyAlgorithm = "EC-P256",
            createdAt = System.currentTimeMillis()
        )

        storage.saveIdentityKeyPair(newKeyPair, newIdentity)
        localKeyPair = newKeyPair
        localIdentity = newIdentity
        return newIdentity
    }

    /**
     * Returns the local device's long-term key pair.
     */
    @Synchronized
    fun getIdentityKeyPair(): KeyPair {
        return localKeyPair ?: run {
            loadOrInitializeIdentity()
            localKeyPair ?: throw KeyStorageException("Failed to load or generate local identity key pair")
        }
    }

    /**
     * Returns the local device identity metadata.
     */
    @Synchronized
    fun getDeviceIdentity(): DeviceIdentity {
        return localIdentity ?: run {
            loadOrInitializeIdentity()
        }
    }

    /**
     * Stores or updates a trusted peer device.
     */
    fun saveTrustedDevice(device: TrustedDevice) {
        storage.saveTrustedDevice(device)
    }

    /**
     * Checks if a remote device ID is trusted and verified.
     */
    fun isDeviceTrusted(deviceId: String): Boolean {
        val device = storage.getTrustedDevice(deviceId)
        return device != null && device.isVerified
    }

    /**
     * Retrieves a trusted peer device by ID.
     */
    fun getTrustedDevice(deviceId: String): TrustedDevice? {
        return storage.getTrustedDevice(deviceId)
    }

    /**
     * Returns all trusted peer devices.
     */
    fun getAllTrustedDevices(): List<TrustedDevice> {
        return storage.getAllTrustedDevices()
    }

    /**
     * Revokes trust and deletes a peer device from secure storage.
     */
    fun revokeTrustedDevice(deviceId: String) {
        storage.removeTrustedDevice(deviceId)
    }

    /**
     * Wipes all local keys and pairing records (e.g. on user reset).
     */
    @Synchronized
    fun wipeAllKeys() {
        storage.clearAll()
        localKeyPair = null
        localIdentity = null
    }
}
