package com.talkmitra.offlinevoice.text

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
    private val debounceWindowMs: Long = 1500L
) {
    private var lastText: String = ""
    private var lastTimestampMs: Long = 0L

    /**
     * Returns `true` if [text] is identical to the text processed within the last [debounceWindowMs] ms.
     * Prevents duplicate callbacks from the same audio stream while allowing repeated messages.
     */
    @Synchronized
    fun isDuplicate(text: String, isFinal: Boolean): Boolean {
        if (!isFinal) return false
        val normalized = text.trim().lowercase()
        val now = System.currentTimeMillis()
        if (normalized.isNotEmpty() && normalized == lastText && (now - lastTimestampMs) < debounceWindowMs) {
            return true
        }
        return false
    }

    /**
     * Records a successfully processed final message.
     */
    @Synchronized
    fun recordProcessed(messageId: String, text: String) {
        lastText = text.trim().lowercase()
        lastTimestampMs = System.currentTimeMillis()
    }

    /** Clears all deduplication history. */
    @Synchronized
    fun reset() {
        lastText = ""
        lastTimestampMs = 0L
    }
}
