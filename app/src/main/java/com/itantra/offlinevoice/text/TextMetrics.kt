package com.itantra.offlinevoice.text

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Generates collision-resistant message IDs, UTC timestamps, and sequence numbers.
 */
object MessageIdentity {

    private val sequenceCounter = AtomicLong(0)

    /**
     * Generates a collision-resistant message ID using UUID v4.
     *
     * UUID v4 is chosen over sequential IDs because:
     * - 122 random bits make collisions negligible even across distributed devices
     * - No global state or coordination required
     * - Deterministic length (36 chars) simplifies downstream parsing
     */
    fun generateMessageId(): String = "VL-${UUID.randomUUID()}"

    /**
     * Returns the current UTC timestamp in ISO-8601 format.
     * Stored internally in UTC to avoid timezone ambiguity.
     */
    fun utcTimestamp(): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    /**
     * Returns the next monotonically increasing sequence number.
     * Independent of timestamp ordering — provides a total order within a session.
     */
    fun nextSequenceNumber(): Long = sequenceCounter.incrementAndGet()

    /** Resets the sequence counter (e.g. on new conversation). */
    fun resetSequence() { sequenceCounter.set(0) }
}

/**
 * Utility for computing text metrics, primarily UTF-8 byte size for
 * transmission budget calculations across multi-byte Indic scripts.
 */
class TextMetrics {
    /**
     * Computes the UTF-8 byte size of a string.
     * Critical for Indic scripts where a single grapheme can be 3-9 bytes.
     */
    fun utf8ByteSize(text: String): Int =
        text.toByteArray(StandardCharsets.UTF_8).size
}
