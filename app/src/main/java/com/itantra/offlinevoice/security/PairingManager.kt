package com.itantra.offlinevoice.security

import com.itantra.offlinevoice.security.models.PairingOffer
import com.itantra.offlinevoice.security.models.PairingResponse
import com.itantra.offlinevoice.security.models.PairingSession
import com.itantra.offlinevoice.security.models.TrustedDevice
import java.security.PrivateKey
import java.util.Base64

/**
 * Handles device-to-device secure pairing with Short Authentication String (SAS) out-of-band verification.
 *
 * Protocol:
 * 1. Phone A (Initiator) creates a `PairingOffer` with its identity & ephemeral public key.
 * 2. Phone B (Responder) receives the offer, computes the SAS code, and returns a `PairingResponse`.
 * 3. Phone A processes the response and computes the identical SAS code.
 * 4. Both screens display the 6-digit numeric SAS code (e.g. "482 917").
 * 5. Users compare and confirm; keys are stored in `KeyManager` and an initial session is established.
 */
class PairingManager(
    private val keyManager: KeyManager,
    private val sessionManager: SessionManager
) {

    /**
     * Step 1 (Initiator Phone A): Generates a new pairing offer and returns the offer with the ephemeral private key.
     */
    fun createPairingOffer(): Pair<PairingOffer, java.security.KeyPair> {
        val identity = keyManager.getDeviceIdentity()
        val ephemeralKeyPair = CryptoManager.generateEcKeyPair()
        val ephemeralPubBase64 = CryptoManager.encodePublicKey(ephemeralKeyPair.public)
        val nonce = Base64.getEncoder().encodeToString(CryptoManager.generateSecureNonce(12))

        val offer = PairingOffer(
            protocolVersion = "VoiceLink-Pair-v1",
            initiatorDeviceId = identity.deviceId,
            initiatorDisplayName = identity.displayName,
            initiatorIdentityKeyBase64 = identity.publicKeyBase64,
            initiatorEphemeralKeyBase64 = ephemeralPubBase64,
            timestamp = System.currentTimeMillis(),
            nonce = nonce
        )

        return Pair(offer, ephemeralKeyPair)
    }

    /**
     * Step 2 (Responder Phone B): Processes the incoming pairing offer and generates a response with SAS verification code.
     */
    fun processPairingOffer(offer: PairingOffer): Pair<PairingResponse, PairingSession> {
        if (offer.initiatorDeviceId == keyManager.getDeviceIdentity().deviceId) {
            throw PairingFailedException("Cannot pair device with itself")
        }

        val myIdentity = keyManager.getDeviceIdentity()
        val myEphemeralKeyPair = CryptoManager.generateEcKeyPair()
        val myEphemeralPubBase64 = CryptoManager.encodePublicKey(myEphemeralKeyPair.public)
        val nonce = Base64.getEncoder().encodeToString(CryptoManager.generateSecureNonce(12))

        // Decode initiator keys
        val initiatorIdentityPub = CryptoManager.decodePublicKey(offer.initiatorIdentityKeyBase64)
        val initiatorEphemeralPub = CryptoManager.decodePublicKey(offer.initiatorEphemeralKeyBase64)

        // Compute dual ECDH secrets
        val ephemeralSecret = CryptoManager.computeEcdhSharedSecret(myEphemeralKeyPair.private, initiatorEphemeralPub)
        val identitySecret = CryptoManager.computeEcdhSharedSecret(keyManager.getIdentityKeyPair().private, initiatorIdentityPub)

        // Compute mutual SAS verification code
        val salt = offer.nonce.toByteArray(Charsets.UTF_8) + nonce.toByteArray(Charsets.UTF_8)
        val sasCode = CryptoManager.generateSasCode(ephemeralSecret, identitySecret, salt)

        val response = PairingResponse(
            protocolVersion = "VoiceLink-Pair-v1",
            responderDeviceId = myIdentity.deviceId,
            responderDisplayName = myIdentity.displayName,
            responderIdentityKeyBase64 = myIdentity.publicKeyBase64,
            responderEphemeralKeyBase64 = myEphemeralPubBase64,
            timestamp = System.currentTimeMillis(),
            nonce = nonce
        )

        val session = PairingSession(
            peerDeviceId = offer.initiatorDeviceId,
            peerDisplayName = offer.initiatorDisplayName,
            peerPublicKeyBase64 = offer.initiatorIdentityKeyBase64,
            peerEphemeralKeyBase64 = offer.initiatorEphemeralKeyBase64,
            localEphemeralPrivateKey = myEphemeralKeyPair.private,
            sasCode = sasCode
        )

        return Pair(response, session)
    }

    /**
     * Step 3 (Initiator Phone A): Processes the pairing response from Phone B and computes the SAS verification code.
     */
    fun processPairingResponse(
        offer: PairingOffer,
        response: PairingResponse,
        initiatorEphemeralPrivateKey: PrivateKey
    ): PairingSession {
        val responderIdentityPub = CryptoManager.decodePublicKey(response.responderIdentityKeyBase64)
        val responderEphemeralPub = CryptoManager.decodePublicKey(response.responderEphemeralKeyBase64)

        // Compute dual ECDH secrets matching Responder
        val ephemeralSecret = CryptoManager.computeEcdhSharedSecret(initiatorEphemeralPrivateKey, responderEphemeralPub)
        val identitySecret = CryptoManager.computeEcdhSharedSecret(keyManager.getIdentityKeyPair().private, responderIdentityPub)

        // Compute mutual SAS verification code
        val salt = offer.nonce.toByteArray(Charsets.UTF_8) + response.nonce.toByteArray(Charsets.UTF_8)
        val sasCode = CryptoManager.generateSasCode(ephemeralSecret, identitySecret, salt)

        return PairingSession(
            peerDeviceId = response.responderDeviceId,
            peerDisplayName = response.responderDisplayName,
            peerPublicKeyBase64 = response.responderIdentityKeyBase64,
            peerEphemeralKeyBase64 = response.responderEphemeralKeyBase64,
            localEphemeralPrivateKey = initiatorEphemeralPrivateKey,
            sasCode = sasCode
        )
    }

    /**
     * Step 4 (Confirmation): User on both devices verifies SAS code matches and confirms pairing.
     */
    fun confirmPairing(session: PairingSession): TrustedDevice {
        val trustedDevice = TrustedDevice(
            deviceId = session.peerDeviceId,
            publicKeyBase64 = session.peerPublicKeyBase64,
            displayName = session.peerDisplayName,
            pairedAt = System.currentTimeMillis(),
            isVerified = true,
            lastSeenAt = System.currentTimeMillis()
        )

        keyManager.saveTrustedDevice(trustedDevice)
        session.isConfirmed = true

        // Automatically establish initial session key with verified peer
        val peerPublicKey = CryptoManager.decodePublicKey(session.peerPublicKeyBase64)
        sessionManager.establishSession(session.peerDeviceId, peerPublicKey)

        return trustedDevice
    }
}
