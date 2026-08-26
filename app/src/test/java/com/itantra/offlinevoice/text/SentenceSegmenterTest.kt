package com.itantra.offlinevoice.text

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSegmenterTest {
    private val segmenter = SentenceSegmenter()

    // --- VAD-driven path ---

    @Test
    fun testThreeVadChunksProduceThreeSentences() {
        val vadChunks = listOf("I need help", "There is a fire", "Please send someone")
        val result = segmenter.segmentFromVadChunks(vadChunks)
        assertEquals(3, result.size)
        assertEquals("I need help.", result[0])
        assertEquals("There is a fire.", result[1])
        assertEquals("Please send someone.", result[2])
    }

    @Test
    fun testVadChunkWithExistingPunctuation() {
        val vadChunks = listOf("Help!", "Are you okay?")
        val result = segmenter.segmentFromVadChunks(vadChunks)
        assertEquals(2, result.size)
        assertEquals("Help!", result[0])
        assertEquals("Are you okay?", result[1])
    }

    @Test
    fun testVadChunksWithBlankSegmentsFiltered() {
        val vadChunks = listOf("Hello", "   ", "", "World")
        val result = segmenter.segmentFromVadChunks(vadChunks)
        assertEquals(2, result.size)
        assertEquals("Hello.", result[0])
        assertEquals("World.", result[1])
    }

    @Test
    fun testVadChunksHindiSegments() {
        val vadChunks = listOf("मुझे मदद चाहिए", "आग लगी है")
        val result = segmenter.segmentFromVadChunks(vadChunks)
        assertEquals(2, result.size)
        assertEquals("मुझे मदद चाहिए।", result[0])
        assertEquals("आग लगी है।", result[1])
    }

    // --- Punctuation-fallback path ---

    @Test
    fun testContinuousStringNoPunctuationReturnsSingleSentence() {
        val result = segmenter.segmentSentence("I need help there is a fire")
        assertEquals(1, result.size)
        assertEquals("I need help there is a fire.", result[0])
    }

    @Test
    fun testContinuousStringWithPunctuationSplitsCorrectly() {
        val result = segmenter.segmentSentence("I need help. There is a fire. Please send someone.")
        assertEquals(3, result.size)
        assertEquals("I need help.", result[0])
        assertEquals("There is a fire.", result[1])
        assertEquals("Please send someone.", result[2])
    }

    @Test
    fun testNoArtificialShortSentenceSplitting() {
        // A long sentence without punctuation should NOT be split
        val longSentence = "The quick brown fox jumps over the lazy dog and runs across the field towards the distant hills where the sunset casts long shadows"
        val result = segmenter.segmentSentence(longSentence)
        assertEquals("Should not over-split", 1, result.size)
    }

    @Test
    fun testEmptyInput() {
        assertEquals(emptyList<String>(), segmenter.segmentSentence(""))
        assertEquals(emptyList<String>(), segmenter.segmentSentence("   "))
    }

    @Test
    fun testHindiPunctuationFallback() {
        val result = segmenter.segmentSentence("मुझे मदद चाहिए। आग लगी है।")
        assertEquals(2, result.size)
    }
}
