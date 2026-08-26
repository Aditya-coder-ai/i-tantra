package com.itantra.offlinevoice.security

import com.itantra.offlinevoice.text.ProcessedMessage
import java.util.Base64

/**
 * High-level decryption and verification manager for incoming [EncryptedMessagePacket] instances.
 *
 * Enforces mesh deduplication, sender authentication, replay protection window,
 * AEAD ciphertext and AAD integrity validation before producing a verified [ProcessedMessage].
 */
class DecryptionManager(
    private val keyManager: KeyManager,
    private val sessionManager: SessionManager,
    private val replayProtection: ReplayProtection = ReplayProtection(),
    private val deduplicator: SecureMessageDeduplicator = SecureMessageDeduplicator(),
    val metrics: CryptoMetrics = CryptoMetrics()
) {

    /**
     * Decrypts and authenticates an incoming [EncryptedMessagePacket].
     *
     * @param packet The received encrypted message packet.
     * @return Fully restored, authenticated [ProcessedMessage].
     * @throws DuplicateMessageException if messageId has already been seen (in mesh relay).
     * @throws UnknownDeviceException if sender is not in trusted devices store.
     * @throws MissingSessionException if session key is unknown.
     * @throws SessionExpiredException if session is expired.
     * @throws ReplayAttackException if packet sequence or timestamp is replayed or expired.
     * @throws AuthenticationFailedException if ciphertext or headers were tampered with.
     */
    fun decryptMessage(packet: EncryptedMessagePacket): ProcessedMessage {
        val startNano = System.nanoTime()
        val packetJsonBytes = packet.toJson().toByteArray(Charsets.UTF_8).size

        // 1. Mesh Message Deduplication Check
        if (deduplicator.isDuplicate(packet.messageId)) {
            metrics.recordDuplicateDropped()
            throw DuplicateMessageException(packet.messageId)
        }

        // 2. Sender Authentication Check
        if (!keyManager.isDeviceTrusted(packet.senderId)) {
            metrics.recordAuthFailure()
            throw UnknownDeviceException(packet.senderId)
        }

        // 3. Session Retrieval
        val session = sessionManager.getSessionById(packet.sessionId)
            ?: sessionManager.getSession(packet.senderId)
            ?: run {
                metrics.recordAuthFailure()
                throw MissingSessionException(packet.senderId)
            }

        if (session.isExpired()) {
            metrics.recordAuthFailure()
            throw SessionExpiredException(session.sessionId)
        }

        // 4. Replay Protection Verification (Sliding Window & Timestamp Freshness)
        try {
            replayProtection.validateAndRecord(
                sessionId = session.sessionId,
                sequenceNumber = packet.sequenceNumber,
                timestampStr = packet.timestamp
            )
        } catch (e: ReplayAttackException) {
            metrics.recordReplayBlocked()
            throw e
        }

        // 5. Decode Nonce & Combined Ciphertext + Tag
        val nonceBytes = try {
            Base64.getDecoder().decode(packet.nonce)
        } catch (e: Exception) {
            metrics.recordAuthFailure()
            throw CorruptedPacketException("Corrupted nonce in packet", e)
        }

        val ciphertextWithTagBytes = try {
            packet.getCiphertextWithTagBytes()
        } catch (e: Exception) {
            metrics.recordAuthFailure()
            throw CorruptedPacketException("Corrupted ciphertext or tag in packet", e)
        }

        // 6. Compute Canonical AAD from Header
        val aadBytes = packet.computeAadBytes()

        // 7. Decrypt & Authenticate via AES-256-GCM
        val plaintextBytes = try {
            CryptoManager.decryptAesGcm(
                ciphertextWithTag = ciphertextWithTagBytes,
                key = session.sessionKey,
                iv = nonceBytes,
                aad = aadBytes
            )
        } catch (e: AuthenticationFailedException) {
            metrics.recordAuthFailure()
            throw e
        } catch (e: Exception) {
            metrics.recordAuthFailure()
            throw AuthenticationFailedException("Decryption failed: ${e.message}", e)
        }

        // 8. Deserialize Plaintext to ProcessedMessage
        val processedMessage = EncryptedMessagePacket.deserializeProcessedMessage(plaintextBytes)

        // 9. Record in Deduplication Cache & Update Sequence Counter
        deduplicator.recordSeen(packet.messageId)
        session.rxSequenceNumber = maxOf(session.rxSequenceNumber, packet.sequenceNumber)

        // 10. Record Performance Metrics
        val durationNanos = System.nanoTime() - startNano
        metrics.recordDecryption(
            packetSizeBytes = packetJsonBytes,
            plaintextBytes = plaintextBytes.size,
            durationNanos = durationNanos
        )

        return processedMessage
    }

    /**
     * Resets deduplication and replay protection state.
     */
    fun reset() {
        deduplicator.clear()
        replayProtection.resetAll()
    }
}
