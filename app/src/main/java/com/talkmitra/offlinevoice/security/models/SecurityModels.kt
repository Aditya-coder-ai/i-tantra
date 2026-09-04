package com.talkmitra.offlinevoice.security.models

import java.util.Arrays

/**
 * Represents the local device's long-term cryptographic identity.
 */
data class DeviceIdentity(
    val deviceId: String,
    val publicKeyBase64: String,
    val displayName: String,
    val keyAlgorithm: String = "EC-P256",
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun toString(): String {
        return "DeviceIdentity(deviceId='$deviceId', displayName='$displayName', keyAlgorithm='$keyAlgorithm')"
    }
}

/**
 * Represents a remote device that has undergone out-of-band verification and pairing.
 */
data class TrustedDevice(
    val deviceId: String,
    val publicKeyBase64: String,
    val displayName: String,
    val pairedAt: Long = System.currentTimeMillis(),
    val isVerified: Boolean = true,
    var lastSeenAt: Long = System.currentTimeMillis()
) {
    override fun toString(): String {
        return "TrustedDevice(deviceId='$deviceId', displayName='$displayName', isVerified=$isVerified)"
    }
}

/**
 * Represents an active symmetric session established between this device and a peer.
 */
data class SessionInfo(
    val sessionId: String,
    val peerDeviceId: String,
    val sessionKey: ByteArray,
    var txSequenceNumber: Long = 0L,
    var rxSequenceNumber: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000L), // 24 hours default
    var messagesCount: Long = 0L
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt

    fun nextTxSequenceNumber(): Long {
        messagesCount++
        return ++txSequenceNumber
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SessionInfo
        if (sessionId != other.sessionId) return false
        if (peerDeviceId != other.peerDeviceId) return false
        if (!sessionKey.contentEquals(other.sessionKey)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + peerDeviceId.hashCode()
        result = 31 * result + sessionKey.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "SessionInfo(sessionId='$sessionId', peerDeviceId='$peerDeviceId', txSeq=$txSequenceNumber, rxSeq=$rxSequenceNumber, messagesCount=$messagesCount, expired=${isExpired()})"
    }

    /** Clears session key bytes in memory for defense-in-depth. */
    fun wipe() {
        Arrays.fill(sessionKey, 0.toByte())
    }
}

/**
 * Pairing Offer initiated by Phone A to Phone B during out-of-band pairing.
 */
data class PairingOffer(
    val protocolVersion: String = "VoiceLink-Pair-v1",
    val initiatorDeviceId: String,
    val initiatorDisplayName: String,
    val initiatorIdentityKeyBase64: String,
    val initiatorEphemeralKeyBase64: String,
    val timestamp: Long = System.currentTimeMillis(),
    val nonce: String
)

/**
 * Pairing Response returned by Phone B to Phone A.
 */
data class PairingResponse(
    val protocolVersion: String = "VoiceLink-Pair-v1",
    val responderDeviceId: String,
    val responderDisplayName: String,
    val responderIdentityKeyBase64: String,
    val responderEphemeralKeyBase64: String,
    val timestamp: Long = System.currentTimeMillis(),
    val nonce: String
)

/**
 * Pairing Session active state containing mutual SAS verification code.
 */
data class PairingSession(
    val peerDeviceId: String,
    val peerDisplayName: String,
    val peerPublicKeyBase64: String,
    val peerEphemeralKeyBase64: String,
    val localEphemeralPrivateKey: java.security.PrivateKey,
    val sasCode: String,
    val startedAt: Long = System.currentTimeMillis(),
    var isConfirmed: Boolean = false
)
