package com.talkmitra.offlinevoice.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ReplayAndDeduplicationTest {

    private lateinit var replayProtection: ReplayProtection
    private lateinit var deduplicator: SecureMessageDeduplicator

    @Before
    fun setUp() {
        replayProtection = ReplayProtection(windowSize = 128, maxTimestampSkewMs = 300_000L)
        deduplicator = SecureMessageDeduplicator(maxCapacity = 512, entryTtlMs = 600_000L)
    }

    @Test
    fun testSequentialPacketsAccepted() {
        val sessionId = "SES-TEST-01"
        val now = System.currentTimeMillis().toString()

        for (seq in 1L..10L) {
            replayProtection.validateAndRecord(sessionId, seq, now)
        }
    }

    @Test
    fun testReplayedSequenceNumberRejected() {
        val sessionId = "SES-TEST-02"
        val now = System.currentTimeMillis().toString()

        replayProtection.validateAndRecord(sessionId, 1L, now)
        replayProtection.validateAndRecord(sessionId, 2L, now)

        try {
            // Replay packet sequence 1
            replayProtection.validateAndRecord(sessionId, 1L, now)
            fail("Expected ReplayAttackException on replayed sequence number")
        } catch (e: ReplayAttackException) {
            assertTrue(e.message!!.contains("Duplicate") || e.message!!.contains("Replay"))
        }
    }

    @Test
    fun testOutOfOrderWithinWindowAccepted() {
        val sessionId = "SES-TEST-03"
        val now = System.currentTimeMillis().toString()

        replayProtection.validateAndRecord(sessionId, 10L, now)
        replayProtection.validateAndRecord(sessionId, 5L, now)
        replayProtection.validateAndRecord(sessionId, 8L, now)
        replayProtection.validateAndRecord(sessionId, 1L, now)

        // But sending seq 5 again must be rejected
        try {
            replayProtection.validateAndRecord(sessionId, 5L, now)
            fail("Expected rejection on duplicate sequence 5")
        } catch (e: ReplayAttackException) {
            assertTrue(e.message!!.contains("Duplicate"))
        }
    }

    @Test
    fun testOutOfWindowSequenceRejected() {
        val sessionId = "SES-TEST-04"
        val now = System.currentTimeMillis().toString()

        // Highest sequence jumps to 200
        replayProtection.validateAndRecord(sessionId, 200L, now)

        // Sequence 50 is beyond the 128 window (200 - 50 = 150 >= 128)
        try {
            replayProtection.validateAndRecord(sessionId, 50L, now)
            fail("Expected ReplayAttackException for sequence outside sliding window")
        } catch (e: ReplayAttackException) {
            assertTrue(e.message!!.contains("window"))
        }
    }

    @Test
    fun testExpiredTimestampRejected() {
        val sessionId = "SES-TEST-05"
        val expiredTimestamp = (System.currentTimeMillis() - 400_000L).toString() // 6.6 minutes old

        try {
            replayProtection.validateAndRecord(sessionId, 1L, expiredTimestamp)
            fail("Expected ReplayAttackException on expired timestamp")
        } catch (e: ReplayAttackException) {
            assertTrue(e.message!!.contains("expired"))
        }
    }

    @Test
    fun testMeshMessageDeduplication() {
        val messageId1 = "VL-MSG-1001"
        val messageId2 = "VL-MSG-1002"

        // First time seen -> fresh
        assertTrue(deduplicator.checkAndRecord(messageId1))
        assertTrue(deduplicator.isDuplicate(messageId1))

        // Multi-path relay of messageId1 arrives again -> duplicate
        assertFalse(deduplicator.checkAndRecord(messageId1))

        // New messageId2 -> fresh
        assertTrue(deduplicator.checkAndRecord(messageId2))

        // Clear resets deduplication cache
        deduplicator.clear()
        assertFalse(deduplicator.isDuplicate(messageId1))
    }
}
