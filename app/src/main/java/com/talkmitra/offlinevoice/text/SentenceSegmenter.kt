package com.talkmitra.offlinevoice.text

/**
 * Segments text into sentences using VAD segment boundaries as the primary signal,
 * falling back to punctuation-based splitting when VAD info is unavailable.
 *
 * Design principles:
 * - VAD pauses are the authoritative sentence boundary signal (each VAD chunk = one sentence)
 * - Punctuation-based fallback does NOT artificially split by word count
 * - Each emitted sentence is cleaned (trimmed) and terminated with appropriate punctuation
 */
class SentenceSegmenter {

    private val cleaner = TextCleaner()

    /**
     * Primary path: VAD-driven segmentation.
     *
     * Each VAD chunk represents a pause-delimited speech segment and maps to exactly
     * one sentence. Each chunk is individually cleaned and punctuated.
     *
     * @param vadChunks List of raw text strings, one per VAD speech segment.
     * @return List of cleaned, punctuated sentences.
     */
    fun segmentFromVadChunks(vadChunks: List<String>): List<String> {
        return vadChunks
            .map { cleaner.cleanText(it) }
            .filter { it.isNotEmpty() }
    }

    /**
     * Fallback path: punctuation-based segmentation for a single continuous string
     * when VAD boundary info is not available.
     *
     * Splits only on existing sentence-ending punctuation (`.`, `!`, `?`, `।`, `॥`).
     * Does NOT split on commas, word count, or arbitrary positions.
     *
     * @param text A single continuous text string.
     * @return List of sentences. If no punctuation boundaries exist, returns the
     *         entire input as a single sentence.
     */
    fun segmentSentence(text: String): List<String> {
        val cleaned = cleaner.cleanText(text)
        if (cleaned.isEmpty()) return emptyList()

        // Split on sentence-ending punctuation while keeping the delimiter attached
        val segments = SENTENCE_BOUNDARY.split(cleaned)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (segments.isEmpty()) return listOf(cleaned)

        // Re-clean each segment to ensure proper termination
        return segments.map { cleaner.cleanText(it) }.filter { it.isNotEmpty() }
    }

    private companion object {
        // Split after sentence-ending punctuation followed by whitespace.
        // Uses lookbehind to keep the punctuation attached to the preceding sentence.
        val SENTENCE_BOUNDARY = Regex("(?<=[.!?।॥])(\\s+)")
    }
}
