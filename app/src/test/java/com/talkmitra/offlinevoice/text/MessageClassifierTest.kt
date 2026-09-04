package com.talkmitra.offlinevoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageClassifierTest {
    private val classifier = MessageClassifier()

    @Test
    fun testNormalMessageClassifiedAsNormal() {
        val result = classifier.classifyMessage("I'll reach there in ten minutes.")
        assertEquals(MessageType.NORMAL, result.type)
        assertEquals(MessagePriority.NORMAL, result.priority)
        assertFalse("No emergency keywords", result.emergencySuggested)
    }

    @Test
    fun testEmergencyKeywordsWithUserConfirmation() {
        val result = classifier.classifyMessage("Help! There is a fire.", userConfirmedEmergency = true)
        assertEquals(MessageType.EMERGENCY, result.type)
        assertEquals(MessagePriority.CRITICAL, result.priority)
    }

    @Test
    fun testEmergencyKeywordsWithoutUserConfirmation_DoesNotAutoClassify() {
        val result = classifier.classifyMessage("Help! There is a fire.", userConfirmedEmergency = false)
        // MUST remain NORMAL — only suggestion is set
        assertEquals("Must NOT auto-classify as EMERGENCY", MessageType.NORMAL, result.type)
        assertEquals(MessagePriority.NORMAL, result.priority)
        assertTrue("Should suggest emergency", result.emergencySuggested)
    }

    @Test
    fun testUserConfirmedEmergencyWithoutKeywords() {
        val result = classifier.classifyMessage("Come here now", userConfirmedEmergency = true)
        assertEquals(MessageType.EMERGENCY, result.type)
        assertEquals(MessagePriority.CRITICAL, result.priority)
    }

    @Test
    fun testHindiEmergencyKeywordSuggestion() {
        val result = classifier.classifyMessage("मुझे मदद चाहिए")
        assertEquals(MessageType.NORMAL, result.type)
        assertTrue("Hindi emergency keyword should trigger suggestion", result.emergencySuggested)
    }

    @Test
    fun testBengaliEmergencyKeywordSuggestion() {
        val result = classifier.classifyMessage("সাহায্য করুন")
        assertTrue("Bengali emergency keyword should trigger suggestion", result.emergencySuggested)
        assertEquals("Must stay NORMAL without user confirmation", MessageType.NORMAL, result.type)
    }

    @Test
    fun testNoEmergencyKeywordsInBenignText() {
        val result = classifier.classifyMessage("The weather is nice today")
        assertFalse(result.emergencySuggested)
        assertEquals(MessageType.NORMAL, result.type)
    }
}
