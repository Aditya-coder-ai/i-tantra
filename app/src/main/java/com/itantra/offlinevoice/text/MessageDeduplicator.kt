package com.itantra.offlinevoice.text

/**
 * Prevents duplicate [ProcessedMessage] creation when the STT engine fires
 * identical final callbacks for the same utterance.
 *
 * Uses a bounded LRU window of recently processed final texts and their message IDs,
 * not just naive string comparison.
 *
 * Thread-safe: all state is accessed under the intrinsic lock.
 */
class MessageDeduplicator(
    private val maxHistory: Int = 64
) {
    /** Tracks recent final texts with their assigned message IDs. */
    private val recentFinals = LinkedHashMap<String, String>(maxHistory, 0.75f, true)

    /**
     * Returns `true` if [text] has already been processed as a final result.
     *
     * Partial results (`isFinal == false`) are never considered duplicates — they
     * must be gated out by the caller before reaching message creation.
     */
    @Synchronized
    fun isDuplicate(text: String, isFinal: Boolean): Boolean {
        if (!isFinal) return false
        val normalized = text.trim().lowercase()
        return recentFinals.containsKey(normalized)
    }

    /**
     * Records a successfully processed final message so future duplicates are detected.
     */
    @Synchronized
    fun recordProcessed(messageId: String, text: String) {
        val normalized = text.trim().lowercase()
        recentFinals[normalized] = messageId
        // Evict oldest entries when capacity is exceeded
        while (recentFinals.size > maxHistory) {
            val oldest = recentFinals.keys.iterator().next()
            recentFinals.remove(oldest)
        }
    }

    /** Clears all deduplication history (e.g. on conversation reset). */
    @Synchronized
    fun reset() {
        recentFinals.clear()
    }
}
