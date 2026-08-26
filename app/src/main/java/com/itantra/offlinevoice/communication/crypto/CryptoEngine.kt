package com.itantra.offlinevoice.communication.crypto

import com.itantra.offlinevoice.communication.model.MeshPacket
import com.itantra.offlinevoice.communication.model.PeerIdentity

/**
 * Result of encrypting a message for an offline recipient.
 */
data class EncryptedEnvelope(
    val ephemeralPublicKey: ByteArray,
    val blindRecipientTag: ByteArray,
    val ciphertext: ByteArray,
    val authTag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedEnvelope
        if (!ephemeralPublicKey.contentEquals(other.ephemeralPublicKey)) return false
        if (!blindRecipientTag.contentEquals(other.blindRecipientTag)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!authTag.contentEquals(other.authTag)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = ephemeralPublicKey.contentHashCode()
        result = 31 * result + blindRecipientTag.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        return result
    }
}

/**
 * End-to-end cryptographic operations interface for iTantra.
 */
interface CryptoEngine {
    /** The local device's long-term identity public key */
    val localPublicKey: ByteArray

    /** Generates or loads the device identity keypair */
    fun initializeIdentity()

    /**
     * Encrypts a plaintext utterance with forward secrecy using an ephemeral keypair
     * and the recipient's public key.
     */
    fun encrypt(
        recipientPublicKey: ByteArray,
        plaintext: ByteArray
    ): EncryptedEnvelope

    /**
     * Checks if this device is the intended recipient using the blind recipient tag.
     */
    fun matchesRecipientTag(
        ephemeralPublicKey: ByteArray,
        blindRecipientTag: ByteArray
    ): Boolean

    /**
     * Decrypts the ciphertext using the local private key and sender's ephemeral public key.
     */
    fun decrypt(
        ephemeralPublicKey: ByteArray,
        ciphertext: ByteArray,
        authTag: ByteArray
    ): ByteArray?
}
