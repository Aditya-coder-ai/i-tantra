package com.itantra.offlinevoice.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagePairTest {

    @Test
    fun testLanguagePairProperties() {
        val pair = LanguagePair(SupportedLanguage.ENGLISH, SupportedLanguage.HINDI)
        assertEquals("en-hi", pair.key)
        assertEquals("English → Hindi", pair.displayName)
        assertFalse(pair.isSameLanguage)

        val same = LanguagePair(SupportedLanguage.HINDI, SupportedLanguage.HINDI)
        assertTrue(same.isSameLanguage)
    }

    @Test
    fun testPairReverse() {
        val pair = LanguagePair(SupportedLanguage.ENGLISH, SupportedLanguage.TAMIL)
        val reversed = pair.reverse
        assertEquals(SupportedLanguage.TAMIL, reversed.source)
        assertEquals(SupportedLanguage.ENGLISH, reversed.target)
        assertEquals("ta-en", reversed.key)
    }

    @Test
    fun testFromKeyFactory() {
        val pair = LanguagePair.fromKey("en-hi")
        assertEquals(SupportedLanguage.ENGLISH, pair.source)
        assertEquals(SupportedLanguage.HINDI, pair.target)

        val pair2 = LanguagePair.from("ta", "bn")
        assertEquals(SupportedLanguage.TAMIL, pair2.source)
        assertEquals(SupportedLanguage.BENGALI, pair2.target)
    }
}
