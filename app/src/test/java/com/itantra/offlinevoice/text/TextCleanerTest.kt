package com.itantra.offlinevoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCleanerTest {
    private val cleaner = TextCleaner()

    // --- Spec test cases ---
    @Test
    fun testCollapseWhitespaceEnglish() {
        assertEquals("I need help.", cleaner.cleanText("   I   need    help   "))
    }

    @Test
    fun testCollapseWhitespaceHindi() {
        assertEquals("मुझे मदद चाहिए।", cleaner.cleanText("मुझे   मदद चाहिए"))
    }

    // --- 10-language round-trip tests ---
    @Test
    fun testHindiScriptPreserved() {
        val result = cleaner.cleanText("  नमस्ते दुनिया  ")
        assertEquals("नमस्ते दुनिया।", result)
        assertTrue("Hindi script must survive", result.contains("नमस्ते"))
    }

    @Test
    fun testGujaratiScriptPreserved() {
        val result = cleaner.cleanText("  નમસ્તે દુનિયા  ")
        assertEquals("નમસ્તે દુનિયા।", result)
        assertTrue("Gujarati script must survive", result.contains("નમસ્તે"))
    }

    @Test
    fun testMarathiScriptPreserved() {
        val result = cleaner.cleanText("  नमस्कार जग  ")
        assertEquals("नमस्कार जग।", result)
        assertTrue("Marathi (Devanagari) script must survive", result.contains("नमस्कार"))
    }

    @Test
    fun testKannadaScriptPreserved() {
        val result = cleaner.cleanText("  ನಮಸ್ಕಾರ ಜಗತ್ತು  ")
        assertEquals("ನಮಸ್ಕಾರ ಜಗತ್ತು.", result)
        assertTrue("Kannada script must survive", result.contains("ನಮಸ್ಕಾರ"))
    }

    @Test
    fun testMalayalamScriptPreserved() {
        val result = cleaner.cleanText("  നമസ്കാരം ലോകം  ")
        assertEquals("നമസ്കാരം ലോകം.", result)
        assertTrue("Malayalam script must survive", result.contains("നമസ്കാരം"))
    }

    @Test
    fun testTamilScriptPreserved() {
        val result = cleaner.cleanText("  வணக்கம் உலகம்  ")
        assertEquals("வணக்கம் உலகம்.", result)
        assertTrue("Tamil script must survive", result.contains("வணக்கம்"))
    }

    @Test
    fun testTeluguScriptPreserved() {
        val result = cleaner.cleanText("  నమస్కారం ప్రపంచం  ")
        assertEquals("నమస్కారం ప్రపంచం.", result)
        assertTrue("Telugu script must survive", result.contains("నమస్కారం"))
    }

    @Test
    fun testOdiaScriptPreserved() {
        val result = cleaner.cleanText("  ନମସ୍କାର ଜଗତ  ")
        assertEquals("ନମସ୍କାର ଜଗତ।", result)
        assertTrue("Odia script must survive", result.contains("ନମସ୍କାର"))
    }

    @Test
    fun testBengaliScriptPreserved() {
        val result = cleaner.cleanText("  নমস্কার পৃথিবী  ")
        assertEquals("নমস্কার পৃথিবী।", result)
        assertTrue("Bengali script must survive", result.contains("নমস্কার"))
    }

    @Test
    fun testEnglishPreserved() {
        val result = cleaner.cleanText("  Hello world  ")
        assertEquals("Hello world.", result)
    }

    // --- Punctuation & edge cases ---
    @Test
    fun testDuplicatePunctuationCollapsed() {
        assertEquals("Help!", cleaner.cleanText("Help!!!"))
    }

    @Test
    fun testEllipsisPreserved() {
        val result = cleaner.cleanText("Wait...")
        assertTrue("Ellipsis character should be present", result.contains("\u2026"))
    }

    @Test
    fun testExistingTerminatorNotDuplicated() {
        assertEquals("I need help.", cleaner.cleanText("I need help."))
        assertEquals("I need help!", cleaner.cleanText("I need help!"))
        assertEquals("Really?", cleaner.cleanText("Really?"))
    }

    @Test
    fun testBlankInputReturnsEmpty() {
        assertEquals("", cleaner.cleanText(""))
        assertEquals("", cleaner.cleanText("   "))
    }

    @Test
    fun testNumbersPreserved() {
        assertEquals("Call 112 now.", cleaner.cleanText("  Call  112  now  "))
    }
}
