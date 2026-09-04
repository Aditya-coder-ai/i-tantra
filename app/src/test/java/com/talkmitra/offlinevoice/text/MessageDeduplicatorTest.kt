package com.talkmitra.offlinevoice.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageDeduplicatorTest {
    private lateinit var dedup: MessageDeduplicator

    @Before
    fun setUp() {
        dedup = MessageDeduplicator(debounceWindowMs = 5000L)
    }

    @Test
    fun testPartialResultNeverCreatesMessage() {
        assertFalse("Partial must not be considered duplicate", dedup.isDuplicate("I need he...", isFinal = false))
    }

    @Test
    fun testFirstFinalIsNotDuplicate() {
        assertFalse("First occurrence of a final must not be duplicate", dedup.isDuplicate("I need help.", isFinal = true))
    }

    @Test
    fun testSecondIdenticalFinalIsDuplicate() {
        val text = "I need help."
        assertFalse(dedup.isDuplicate(text, isFinal = true))
        dedup.recordProcessed("msg-001", text)

        assertTrue("Same final text delivered twice within debounce window must be a duplicate", dedup.isDuplicate(text, isFinal = true))
    }

    @Test
    fun testDifferentTextsAreNotDuplicates() {
        dedup.recordProcessed("msg-001", "I need help.")
        assertFalse(dedup.isDuplicate("There is a fire.", isFinal = true))
    }

    @Test
    fun testResetClearsDuplicateHistory() {
        dedup.recordProcessed("msg-001", "I need help.")
        assertTrue(dedup.isDuplicate("I need help.", isFinal = true))

        dedup.reset()
        assertFalse("After reset, the same text should no longer be duplicate", dedup.isDuplicate("I need help.", isFinal = true))
    }

    @Test
    fun testCaseInsensitiveDedup() {
        dedup.recordProcessed("msg-001", "I Need Help.")
        assertTrue("Dedup should be case-insensitive", dedup.isDuplicate("i need help.", isFinal = true))
    }
}
