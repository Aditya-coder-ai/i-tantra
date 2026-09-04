package com.talkmitra.offlinevoice.security

import com.talkmitra.offlinevoice.security.models.DeviceIdentity
import com.talkmitra.offlinevoice.security.models.TrustedDevice
import java.security.KeyPair
import java.security.PrivateKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface defining secure storage operations for long-term identity keys,
 * trusted peer identities, and session tokens.
 */
interface SecureStorage {
    fun saveIdentityKeyPair(keyPair: KeyPair, deviceIdentity: DeviceIdentity)
    fun loadIdentityKeyPair(): KeyPair?
    fun loadDeviceIdentity(): DeviceIdentity?

    fun saveTrustedDevice(device: TrustedDevice)
    fun getTrustedDevice(deviceId: String): TrustedDevice?
    fun getAllTrustedDevices(): List<TrustedDevice>
    fun removeTrustedDevice(deviceId: String)

    fun clearAll()
}

/**
 * Default in-memory secure storage implementation.
 * Used for testing and fallback when AndroidKeyStore is not initialized.
 */
class InMemorySecureStorage : SecureStorage {
    private var storedKeyPair: KeyPair? = null
    private var storedIdentity: DeviceIdentity? = null
    private val trustedDevices = ConcurrentHashMap<String, TrustedDevice>()

    @Synchronized
    override fun saveIdentityKeyPair(keyPair: KeyPair, deviceIdentity: DeviceIdentity) {
        this.storedKeyPair = keyPair
        this.storedIdentity = deviceIdentity
    }

    @Synchronized
    override fun loadIdentityKeyPair(): KeyPair? = storedKeyPair

    @Synchronized
    override fun loadDeviceIdentity(): DeviceIdentity? = storedIdentity

    override fun saveTrustedDevice(device: TrustedDevice) {
        trustedDevices[device.deviceId] = device
    }

    override fun getTrustedDevice(deviceId: String): TrustedDevice? {
        return trustedDevices[deviceId]
    }

    override fun getAllTrustedDevices(): List<TrustedDevice> {
        return trustedDevices.values.toList()
    }

    override fun removeTrustedDevice(deviceId: String) {
        trustedDevices.remove(deviceId)
    }

    @Synchronized
    override fun clearAll() {
        storedKeyPair = null
        storedIdentity = null
        trustedDevices.clear()
    }
}
