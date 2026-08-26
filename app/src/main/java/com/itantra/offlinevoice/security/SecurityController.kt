package com.itantra.offlinevoice.security

import com.itantra.offlinevoice.security.models.DeviceIdentity
import com.itantra.offlinevoice.security.models.PairingSession
import com.itantra.offlinevoice.security.models.TrustedDevice
import com.itantra.offlinevoice.text.ProcessedMessage

/**
 * Unified Controller / Facade for the VoiceLink offline security subsystem.
 *
 * Coordinates Identity, Pairing, Sessions, Encryption, Decryption, and Metrics
 * for UI presentation and connection integration.
 */
class SecurityController(
    val storage: SecureStorage = InMemorySecureStorage()
) {

    val keyManager = KeyManager(storage)
    val identityManager = IdentityManager(keyManager)
    val sessionManager = SessionManager(keyManager)
    val pairingManager = PairingManager(keyManager, sessionManager)
    val metrics = CryptoMetrics()
    val encryptionManager = EncryptionManager(keyManager, sessionManager, metrics)
    val decryptionManager = DecryptionManager(keyManager, sessionManager, metrics = metrics)

    var currentState: SecurityState = SecurityState.UNPAIRED
        private set

    var activePairingSession: PairingSession? = null
        private set

    /**
     * Initializes device identity with a default name if not already set up.
     */
    fun initializeIdentity(displayName: String = "VoiceLink Device"): DeviceIdentity {
        return keyManager.loadOrInitializeIdentity(displayName)
    }

    /**
     * Simulates or initiates pairing with a peer device.
     */
    fun initiatePairing(): PairingSession {
        currentState = SecurityState.PAIRING
        val (offer, localKeyPair) = pairingManager.createPairingOffer()

        // Create a simulated remote peer for local pairing demo
        val remoteStorage = InMemorySecureStorage()
        val remoteKeyManager = KeyManager(remoteStorage)
        val remoteSessionManager = SessionManager(remoteKeyManager)
        val remotePairingManager = PairingManager(remoteKeyManager, remoteSessionManager)
        remoteKeyManager.loadOrInitializeIdentity("Rescue Team 04")

        val (response, _) = remotePairingManager.processPairingOffer(offer)
        val localSession = pairingManager.processPairingResponse(offer, response, localKeyPair.private)

        activePairingSession = localSession
        currentState = SecurityState.PAIRING_VERIFICATION
        return localSession
    }

    /**
     * Confirms the active pairing after user verifies the SAS code.
     */
    fun confirmActivePairing(): TrustedDevice {
        val session = activePairingSession
            ?: throw PairingFailedException("No active pairing session to confirm")

        val trustedDevice = pairingManager.confirmPairing(session)
        activePairingSession = null
        currentState = SecurityState.PAIRED
        currentState = SecurityState.SESSION_ESTABLISHED
        return trustedDevice
    }

    /**
     * Cancels active pairing.
     */
    fun cancelActivePairing() {
        activePairingSession = null
        currentState = if (keyManager.getAllTrustedDevices().isNotEmpty()) {
            SecurityState.SESSION_ESTABLISHED
        } else {
            SecurityState.UNPAIRED
        }
    }

    /**
     * Encrypts a message for transmission.
     */
    fun encryptOutgoing(message: ProcessedMessage, recipientId: String): EncryptedMessagePacket {
        try {
            val packet = encryptionManager.encryptMessage(message, recipientId)
            currentState = SecurityState.ENCRYPTED
            return packet
        } catch (e: Exception) {
            currentState = when (e) {
                is UnknownDeviceException -> SecurityState.UNKNOWN_DEVICE
                is AuthenticationFailedException -> SecurityState.AUTHENTICATION_FAILED
                is KeyStorageException, is InvalidKeyException -> SecurityState.KEY_ERROR
                else -> SecurityState.KEY_ERROR
            }
            throw e
        }
    }

    /**
     * Decrypts and authenticates an incoming packet.
     */
    fun decryptIncoming(packet: EncryptedMessagePacket): ProcessedMessage {
        try {
            val message = decryptionManager.decryptMessage(packet)
            currentState = SecurityState.DECRYPTED
            return message
        } catch (e: Exception) {
            currentState = when (e) {
                is ReplayAttackException -> SecurityState.REPLAY_DETECTED
                is UnknownDeviceException -> SecurityState.UNKNOWN_DEVICE
                is AuthenticationFailedException -> SecurityState.AUTHENTICATION_FAILED
                else -> SecurityState.KEY_ERROR
            }
            throw e
        }
    }

    /**
     * Pairs a device directly (e.g. for pre-shared trusted devices or demo setups).
     */
    fun pairPreSharedDevice(deviceId: String, displayName: String, publicKeyBase64: String): TrustedDevice {
        val trusted = TrustedDevice(
            deviceId = deviceId,
            publicKeyBase64 = publicKeyBase64,
            displayName = displayName,
            isVerified = true
        )
        keyManager.saveTrustedDevice(trusted)
        val pubKey = CryptoManager.decodePublicKey(publicKeyBase64)
        sessionManager.establishSession(deviceId, pubKey)
        currentState = SecurityState.SESSION_ESTABLISHED
        return trusted
    }

    fun getTrustedDevices(): List<TrustedDevice> = keyManager.getAllTrustedDevices()

    fun revokeDevice(deviceId: String) {
        keyManager.revokeTrustedDevice(deviceId)
        sessionManager.invalidateSession(deviceId)
        if (keyManager.getAllTrustedDevices().isEmpty()) {
            currentState = SecurityState.UNPAIRED
        }
    }
}
