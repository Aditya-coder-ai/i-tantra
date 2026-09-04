package com.talkmitra.offlinevoice.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLanguageTest {

    @Test
    fun testAllTenLanguagesPresent() {
        val codes = SupportedLanguage.getAllCodes()
        assertEquals(10, codes.size)
        assertTrue(codes.containsAll(listOf("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")))
    }

    @Test
    fun testLanguageLookupFromCodes() {
        assertEquals(SupportedLanguage.HINDI, SupportedLanguage.fromCode("hi"))
        assertEquals(SupportedLanguage.HINDI, SupportedLanguage.fromCode("hi-IN"))
        assertEquals(SupportedLanguage.HINDI, SupportedLanguage.fromCode("hin"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("en"))
        assertEquals(SupportedLanguage.ENGLISH, SupportedLanguage.fromCode("en-US"))
        assertEquals(SupportedLanguage.TAMIL, SupportedLanguage.fromCode("ta"))
        assertEquals(SupportedLanguage.GUJARATI, SupportedLanguage.fromCode("gu"))
        assertEquals(SupportedLanguage.MARATHI, SupportedLanguage.fromCode("mr"))
        assertEquals(SupportedLanguage.BENGALI, SupportedLanguage.fromCode("bn"))
        assertEquals(SupportedLanguage.ODIA, SupportedLanguage.fromCode("or"))
        assertEquals(SupportedLanguage.TELUGU, SupportedLanguage.fromCode("te"))
        assertEquals(SupportedLanguage.KANNADA, SupportedLanguage.fromCode("kn"))
        assertEquals(SupportedLanguage.MALAYALAM, SupportedLanguage.fromCode("ml"))
    }

    @Test
    fun testIndicProperty() {
        assertFalse(SupportedLanguage.ENGLISH.isIndic)
        assertTrue(SupportedLanguage.HINDI.isIndic)
        assertTrue(SupportedLanguage.TAMIL.isIndic)
        assertTrue(SupportedLanguage.GUJARATI.isIndic)
        assertTrue(SupportedLanguage.BENGALI.isIndic)
    }

    @Test
    fun testLocaleMapping() {
        assertEquals("hi", SupportedLanguage.HINDI.toLocale().language)
        assertEquals("en", SupportedLanguage.ENGLISH.toLocale().language)
        assertEquals("ta", SupportedLanguage.TAMIL.toLocale().language)
    }
}
