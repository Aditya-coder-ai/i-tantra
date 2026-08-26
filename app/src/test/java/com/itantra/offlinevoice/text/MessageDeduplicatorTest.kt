package com.itantra.offlinevoice.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageDeduplicatorTest {
    private lateinit var dedup: MessageDeduplicator

    @Before
    fun setUp() {
        dedup = MessageDeduplicator()
    }

    @Test
    fun testPartialResultNeverCreatesMessage() {
        // Partial results (isFinal=false) should never be flagged as duplicates
        // because they should never produce messages at all — gated elsewhere
        assertFalse("Partial must not be considered duplicate", dedup.isDuplicate("I need he...", isFinal = false))
    }

    @Test
    fun testFirstFinalIsNotDuplicate() {
        assertFalse("First occurrence of a final must not be duplicate", dedup.isDuplicate("I need help.", isFinal = true))
    }

    @Test
    fun testSecondIdenticalFinalIsDuplicate() {
        val text = "I need help."
        // First time: not a duplicate, but we must record it
        assertFalse(dedup.isDuplicate(text, isFinal = true))
        dedup.recordProcessed("msg-001", text)

        // Second time: same text, isFinal=true → duplicate
        assertTrue("Same final text delivered twice must be a duplicate", dedup.isDuplicate(text, isFinal = true))
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

    @Test
    fun testEvictsOldestWhenCapacityExceeded() {
        val smallDedup = MessageDeduplicator(maxHistory = 3)
        smallDedup.recordProcessed("msg-1", "first")
        smallDedup.recordProcessed("msg-2", "second")
        smallDedup.recordProcessed("msg-3", "third")

        // All three should be detected as duplicates
        assertTrue(smallDedup.isDuplicate("first", isFinal = true))

        // Adding a 4th should evict "first"
        smallDedup.recordProcessed("msg-4", "fourth")
        assertFalse("Evicted text should no longer be duplicate", smallDedup.isDuplicate("first", isFinal = true))
    }
}
