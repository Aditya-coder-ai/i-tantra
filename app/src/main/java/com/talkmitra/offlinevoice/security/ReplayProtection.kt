package com.talkmitra.offlinevoice.security

import java.text.SimpleDateFormat
import java.util.BitSet
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * Sliding-window replay protection engine.
 *
 * Tracks monotonic sequence numbers per session using a sliding bitmask window
 * and verifies timestamp freshness against a maximum skew tolerance.
 */
class ReplayProtection(
    private val windowSize: Int = 128,
    private val maxTimestampSkewMs: Long = 5 * 60 * 1000L // 5 minutes
) {

    private data class SessionReplayWindow(
        var highestSeq: Long = 0L,
        val bitSet: BitSet = BitSet(128)
    )

    private val sessionWindows = ConcurrentHashMap<String, SessionReplayWindow>()

    /**
     * Validates that an incoming packet's sequence number and timestamp are fresh
     * and non-replayed, and updates the sliding window.
     *
     * @param sessionId The unique ID of the active session.
     * @param sequenceNumber Monotonically increasing sequence number from the sender.
     * @param timestampStr ISO-8601 or epoch timestamp string.
     * @throws ReplayAttackException if packet is replayed, out-of-order beyond window, or timestamp expired.
     */
    @Synchronized
    fun validateAndRecord(sessionId: String, sequenceNumber: Long, timestampStr: String) {
        if (sequenceNumber <= 0L) {
            throw ReplayAttackException("Invalid sequence number: $sequenceNumber (must be > 0)")
        }

        // 1. Timestamp Freshness Check
        val parsedTimestamp = parseTimestamp(timestampStr)
        val now = System.currentTimeMillis()
        val delta = now - parsedTimestamp

        // Reject if older than max allowed skew or more than 1 minute in the future
        if (delta > maxTimestampSkewMs) {
            throw ReplayAttackException("Packet timestamp expired ($delta ms old, max allowed is $maxTimestampSkewMs ms)")
        }
        if (delta < -60_000L) {
            throw ReplayAttackException("Packet timestamp is too far in the future ($delta ms skew)")
        }

        // 2. Sliding Window Sequence Number Check
        val window = sessionWindows.getOrPut(sessionId) { SessionReplayWindow() }

        if (sequenceNumber > window.highestSeq) {
            val diff = sequenceNumber - window.highestSeq
            if (diff < windowSize) {
                // Shift bits left by diff
                for (i in (windowSize - 1) downTo diff.toInt()) {
                    window.bitSet.set(i, window.bitSet.get(i - diff.toInt()))
                }
                for (i in 0 until minOf(diff.toInt(), windowSize)) {
                    window.bitSet.clear(i)
                }
            } else {
                window.bitSet.clear()
            }
            window.highestSeq = sequenceNumber
            window.bitSet.set(0) // Index 0 represents highestSeq
        } else {
            val diff = window.highestSeq - sequenceNumber
            if (diff >= windowSize) {
                throw ReplayAttackException("Sequence number $sequenceNumber fell outside replay window (highest: ${window.highestSeq})")
            }
            if (window.bitSet.get(diff.toInt())) {
                throw ReplayAttackException("Duplicate packet detected! Sequence number $sequenceNumber already received.")
            }
            window.bitSet.set(diff.toInt())
        }
    }

    /**
     * Resets replay protection window for a specific session.
     */
    @Synchronized
    fun resetSession(sessionId: String) {
        sessionWindows.remove(sessionId)
    }

    /**
     * Resets all session replay windows.
     */
    @Synchronized
    fun resetAll() {
        sessionWindows.clear()
    }

    private fun parseTimestamp(tsStr: String): Long {
        // Try parsing epoch millis first
        tsStr.toLongOrNull()?.let { return it }

        // Try parsing ISO-8601
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val cleanStr = tsStr.substringBefore('.').substringBefore('Z')
            sdf.parse(cleanStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
