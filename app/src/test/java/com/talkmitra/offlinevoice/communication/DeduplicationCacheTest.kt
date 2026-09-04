package com.talkmitra.offlinevoice.communication

import com.talkmitra.offlinevoice.communication.transport.mesh.DeduplicationCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeduplicationCacheTest {

    @Test
    fun testDuplicateDetection() {
        val cache = DeduplicationCache(maxCapacity = 100, expirationWindowMs = 5000L)
        val msgId1 = ByteArray(16) { 1 }
        val msgId2 = ByteArray(16) { 2 }

        assertFalse(cache.isDuplicateOrSeen(msgId1))
        assertTrue(cache.isDuplicateOrSeen(msgId1)) // second time is duplicate

        assertFalse(cache.isDuplicateOrSeen(msgId2))
        assertTrue(cache.isDuplicateOrSeen(msgId2))
    }

    @Test
    fun testExpiration() {
        val cache = DeduplicationCache(maxCapacity = 100, expirationWindowMs = 1000L)
        val msgId = ByteArray(16) { 5 }

        val t0 = 100_000L
        assertFalse(cache.isDuplicateOrSeen(msgId, nowMs = t0))
        assertTrue(cache.isDuplicateOrSeen(msgId, nowMs = t0 + 500L))

        // After expiration window
        assertFalse(cache.isDuplicateOrSeen(msgId, nowMs = t0 + 1500L))
    }
}
