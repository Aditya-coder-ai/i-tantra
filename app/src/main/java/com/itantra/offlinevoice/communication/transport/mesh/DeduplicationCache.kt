package com.itantra.offlinevoice.communication.transport.mesh

import java.nio.ByteBuffer
import java.util.LinkedHashMap
import java.util.UUID

/**
 * LRU In-Memory deduplication cache with sliding expiration window.
 * Bounds RAM usage to ~32 KB while preventing broadcast storms and routing loops.
 */
class DeduplicationCache(
    private val maxCapacity: Int = 2048,
    private val expirationWindowMs: Long = 10 * 60 * 1000L // 10 minutes
) {
    private val cache = object : LinkedHashMap<UUID, Long>(maxCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Long>?): Boolean {
            return size > maxCapacity
        }
    }

    @Synchronized
    fun isDuplicateOrSeen(messageId: ByteArray, nowMs: Long = System.currentTimeMillis()): Boolean {
        val uuid = bytesToUuid(messageId)
        val lastSeen = cache[uuid]
        if (lastSeen != null && (nowMs - lastSeen) < expirationWindowMs) {
            return true
        }
        cache[uuid] = nowMs
        return false
    }

    @Synchronized
    fun markSeen(messageId: ByteArray, nowMs: Long = System.currentTimeMillis()) {
        val uuid = bytesToUuid(messageId)
        cache[uuid] = nowMs
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    private fun bytesToUuid(bytes: ByteArray): UUID {
        require(bytes.size == 16) { "UUID bytes must be 16 bytes" }
        val bb = ByteBuffer.wrap(bytes)
        return UUID(bb.long, bb.long)
    }
}
