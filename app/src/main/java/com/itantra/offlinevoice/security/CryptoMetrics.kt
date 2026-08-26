package com.itantra.offlinevoice.security

import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks real-time cryptographic performance metrics, payload expansion,
 * and security event counters.
 *
 * All values are measured live and exposed to developer/debug interfaces.
 */
class CryptoMetrics {

    private val _totalEncryptedMessages = AtomicLong(0)
    private val _totalDecryptedMessages = AtomicLong(0)
    private val _totalReplaysBlocked = AtomicLong(0)
    private val _totalAuthFailures = AtomicLong(0)
    private val _totalDuplicatesDropped = AtomicLong(0)

    @Volatile
    var lastEncryptionTimeMs: Double = 0.0
        private set

    @Volatile
    var lastDecryptionTimeMs: Double = 0.0
        private set

    @Volatile
    var lastPlaintextBytes: Int = 0
        private set

    @Volatile
    var lastCiphertextBytes: Int = 0
        private set

    @Volatile
    var lastPacketSizeBytes: Int = 0
        private set

    fun recordEncryption(plaintextBytes: Int, ciphertextBytes: Int, packetSizeBytes: Int, durationNanos: Long) {
        _totalEncryptedMessages.incrementAndGet()
        lastPlaintextBytes = plaintextBytes
        lastCiphertextBytes = ciphertextBytes
        lastPacketSizeBytes = packetSizeBytes
        lastEncryptionTimeMs = durationNanos / 1_000_000.0
    }

    fun recordDecryption(packetSizeBytes: Int, plaintextBytes: Int, durationNanos: Long) {
        _totalDecryptedMessages.incrementAndGet()
        lastPlaintextBytes = plaintextBytes
        lastPacketSizeBytes = packetSizeBytes
        lastDecryptionTimeMs = durationNanos / 1_000_000.0
    }

    fun recordReplayBlocked() {
        _totalReplaysBlocked.incrementAndGet()
    }

    fun recordAuthFailure() {
        _totalAuthFailures.incrementAndGet()
    }

    fun recordDuplicateDropped() {
        _totalDuplicatesDropped.incrementAndGet()
    }

    fun getSnapshot(): CryptoMetricsSnapshot {
        return CryptoMetricsSnapshot(
            totalEncryptedMessages = _totalEncryptedMessages.get(),
            totalDecryptedMessages = _totalDecryptedMessages.get(),
            totalReplaysBlocked = _totalReplaysBlocked.get(),
            totalAuthFailures = _totalAuthFailures.get(),
            totalDuplicatesDropped = _totalDuplicatesDropped.get(),
            lastEncryptionTimeMs = lastEncryptionTimeMs,
            lastDecryptionTimeMs = lastDecryptionTimeMs,
            lastPlaintextBytes = lastPlaintextBytes,
            lastCiphertextBytes = lastCiphertextBytes,
            lastPacketSizeBytes = lastPacketSizeBytes
        )
    }

    fun reset() {
        _totalEncryptedMessages.set(0)
        _totalDecryptedMessages.set(0)
        _totalReplaysBlocked.set(0)
        _totalAuthFailures.set(0)
        _totalDuplicatesDropped.set(0)
        lastEncryptionTimeMs = 0.0
        lastDecryptionTimeMs = 0.0
        lastPlaintextBytes = 0
        lastCiphertextBytes = 0
        lastPacketSizeBytes = 0
    }
}

/**
 * Immutable snapshot of cryptographic performance metrics for UI presentation.
 */
data class CryptoMetricsSnapshot(
    val totalEncryptedMessages: Long,
    val totalDecryptedMessages: Long,
    val totalReplaysBlocked: Long,
    val totalAuthFailures: Long,
    val totalDuplicatesDropped: Long,
    val lastEncryptionTimeMs: Double,
    val lastDecryptionTimeMs: Double,
    val lastPlaintextBytes: Int,
    val lastCiphertextBytes: Int,
    val lastPacketSizeBytes: Int
) {
    val overheadBytes: Int
        get() = if (lastPacketSizeBytes > lastPlaintextBytes) lastPacketSizeBytes - lastPlaintextBytes else 0

    val overheadRatio: Double
        get() = if (lastPlaintextBytes > 0) (lastPacketSizeBytes.toDouble() / lastPlaintextBytes) else 1.0
}
