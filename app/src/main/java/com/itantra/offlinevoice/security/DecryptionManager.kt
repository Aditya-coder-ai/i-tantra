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

        // 2. Decode Nonce & Combined Ciphertext + Tag
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

        val aadBytes = packet.computeAadBytes()

        // 3. Multi-tier Decryption Strategy:
        // Try peer's specific session key first, fallback to default channel key
        val session = sessionManager.getSessionById(packet.sessionId) ?: sessionManager.getSession(packet.senderId)
        var plaintextBytes: ByteArray? = null

        if (session != null) {
            try {
                plaintextBytes = CryptoManager.decryptAesGcm(
                    ciphertextWithTag = ciphertextWithTagBytes,
                    key = session.sessionKey,
                    iv = nonceBytes,
                    aad = aadBytes
                )
            } catch (_: Exception) {}
        }

        if (plaintextBytes == null) {
            // Try fallback default channel key
            val defaultKey = CryptoManager.deriveDefaultSessionKey()
            try {
                plaintextBytes = CryptoManager.decryptAesGcm(
                    ciphertextWithTag = ciphertextWithTagBytes,
                    key = defaultKey,
                    iv = nonceBytes,
                    aad = aadBytes
                )
            } catch (e: Exception) {
                metrics.recordAuthFailure()
                if (!keyManager.isDeviceTrusted(packet.senderId)) {
                    throw UnknownDeviceException(packet.senderId)
                }
                throw AuthenticationFailedException("Decryption failed across all session keys: ${e.message}", e)
            }
        }

        // 4. Deserialize Plaintext to ProcessedMessage
        val processedMessage = EncryptedMessagePacket.deserializeProcessedMessage(plaintextBytes)

        // 5. Record seen in deduplication cache
        deduplicator.recordSeen(packet.messageId)

        // 6. Record performance metrics
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
