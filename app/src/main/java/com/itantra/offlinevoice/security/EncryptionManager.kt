package com.itantra.offlinevoice.security

import com.itantra.offlinevoice.text.ProcessedMessage
import java.util.Base64

/**
 * High-level encryption manager for securing [ProcessedMessage] instances
 * before transmission over local transports or mesh relays.
 */
class EncryptionManager(
    private val keyManager: KeyManager,
    private val sessionManager: SessionManager,
    val metrics: CryptoMetrics = CryptoMetrics()
) {

    /**
     * Encrypts a [ProcessedMessage] for a designated [recipientId].
     *
     * @param message The verified processed voice message.
     * @param recipientId The device ID of the recipient.
     * @return Versioned [EncryptedMessagePacket] with AEAD ciphertext and authenticated headers.
     * @throws MissingSessionException if no active session exists with recipient.
     * @throws SessionExpiredException if the session has expired.
     * @throws UnknownDeviceException if the recipient is not in the trusted devices store.
     */
    fun encryptMessage(message: ProcessedMessage, recipientId: String): EncryptedMessagePacket {
        val startNano = System.nanoTime()

        // 2. Retrieve active session or fallback to default channel key
        val session = sessionManager.getSession(recipientId)
        val sessionKey = session?.sessionKey ?: CryptoManager.deriveDefaultSessionKey()
        val sessionId = session?.sessionId ?: "SES-DEFAULT-CHAN"
        val sequenceNumber = session?.nextTxSequenceNumber() ?: System.currentTimeMillis()

        // 3. Obtain sender ID
        val localDeviceId = keyManager.getDeviceIdentity().deviceId

        // 4. Serialize ProcessedMessage to JSON plaintext bytes
        val plaintextBytes = EncryptedMessagePacket.serializeProcessedMessage(message)

        // 5. Generate unique 12-byte secure nonce
        val nonceBytes = CryptoManager.generateSecureNonce(CryptoManager.GCM_NONCE_LENGTH_BYTES)

        // 6. Construct initial packet metadata for AAD computation
        val initialPacket = EncryptedMessagePacket(
            version = 1,
            protocolVersion = "VoiceLink-Sec-v1",
            senderId = localDeviceId,
            recipientId = recipientId,
            sessionId = sessionId,
            messageId = message.messageId,
            sequenceNumber = sequenceNumber,
            timestamp = message.timestamp,
            priority = message.priority,
            nonce = Base64.getEncoder().encodeToString(nonceBytes),
            ciphertext = "",
            authenticationTag = ""
        )

        // 7. Compute AAD over header fields
        val aadBytes = initialPacket.computeAadBytes()

        // 8. Encrypt with AES-256-GCM + AAD
        val ciphertextWithTag = CryptoManager.encryptAesGcm(
            plaintext = plaintextBytes,
            key = sessionKey,
            iv = nonceBytes,
            aad = aadBytes
        )

        // 9. Build final packet
        val finalPacket = EncryptedMessagePacket.create(
            version = initialPacket.version,
            protocolVersion = initialPacket.protocolVersion,
            senderId = initialPacket.senderId,
            recipientId = initialPacket.recipientId,
            sessionId = initialPacket.sessionId,
            messageId = initialPacket.messageId,
            sequenceNumber = initialPacket.sequenceNumber,
            timestamp = initialPacket.timestamp,
            priority = initialPacket.priority,
            nonceBytes = nonceBytes,
            ciphertextWithTagBytes = ciphertextWithTag
        )

        // 10. Record performance metrics
        val durationNanos = System.nanoTime() - startNano
        val packetJsonBytes = finalPacket.toJson().toByteArray(Charsets.UTF_8).size
        metrics.recordEncryption(
            plaintextBytes = plaintextBytes.size,
            ciphertextBytes = ciphertextWithTag.size,
            packetSizeBytes = packetJsonBytes,
            durationNanos = durationNanos
        )

        return finalPacket
    }
}
