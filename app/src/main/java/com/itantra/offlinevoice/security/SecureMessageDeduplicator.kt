package com.itantra.offlinevoice.security

import java.util.concurrent.ConcurrentHashMap

/**
 * Message deduplicator specifically designed for multi-hop mesh forwarding.
 *
 * In a mesh network, intermediate nodes may forward the same packet across
 * divergent paths. This bounded LRU deduplicator tracks recent message IDs
 * with time-to-live (TTL) to drop duplicate packets immediately without
 * running redundant cryptographic operations or creating duplicate audio/TTS events.
 */
class SecureMessageDeduplicator(
    private val maxCapacity: Int = 512,
    private val entryTtlMs: Long = 10 * 60 * 1000L // 10 minutes TTL
) {

    private data class DeduplicationEntry(
        val messageId: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val seenMessageIds = LinkedHashMap<String, Long>(maxCapacity, 0.75f, true)

    /**
     * Checks whether [messageId] has already been seen within the TTL window.
     */
    @Synchronized
    fun isDuplicate(messageId: String): Boolean {
        cleanExpiredEntries()
        return seenMessageIds.containsKey(messageId)
    }

    /**
     * Records a [messageId] as seen.
     */
    @Synchronized
    fun recordSeen(messageId: String) {
        cleanExpiredEntries()
        seenMessageIds[messageId] = System.currentTimeMillis()

        // Enforce maximum capacity
        while (seenMessageIds.size > maxCapacity) {
            val oldest = seenMessageIds.keys.iterator().next()
            seenMessageIds.remove(oldest)
        }
    }

    /**
     * Atomically checks and records if the message is fresh (returns false if duplicate, true if fresh and recorded).
     */
    @Synchronized
    fun checkAndRecord(messageId: String): Boolean {
        if (isDuplicate(messageId)) {
            return false
        }
        recordSeen(messageId)
        return true
    }

    /**
     * Cleans expired entries based on TTL.
     */
    @Synchronized
    private fun cleanExpiredEntries() {
        val now = System.currentTimeMillis()
        val iterator = seenMessageIds.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > entryTtlMs) {
                iterator.remove()
            }
        }
    }

    /**
     * Clears all deduplication history.
     */
    @Synchronized
    fun clear() {
        seenMessageIds.clear()
    }
}
