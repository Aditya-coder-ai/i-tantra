package com.itantra.offlinevoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageNormalizerTest {

    @Test fun testHindi()     { assertSupported("hi", LanguageNormalizer.normalizeLanguage("Hindi")) }
    @Test fun testGujarati()  { assertSupported("gu", LanguageNormalizer.normalizeLanguage("Gujarati")) }
    @Test fun testMarathi()   { assertSupported("mr", LanguageNormalizer.normalizeLanguage("Marathi")) }
    @Test fun testKannada()   { assertSupported("kn", LanguageNormalizer.normalizeLanguage("Kannada")) }
    @Test fun testMalayalam() { assertSupported("ml", LanguageNormalizer.normalizeLanguage("Malayalam")) }
    @Test fun testTamil()     { assertSupported("ta", LanguageNormalizer.normalizeLanguage("Tamil")) }
    @Test fun testTelugu()    { assertSupported("te", LanguageNormalizer.normalizeLanguage("Telugu")) }
    @Test fun testOdia()      { assertSupported("or", LanguageNormalizer.normalizeLanguage("Odia")) }
    @Test fun testBengali()   { assertSupported("bn", LanguageNormalizer.normalizeLanguage("Bengali")) }
    @Test fun testEnglish()   { assertSupported("en", LanguageNormalizer.normalizeLanguage("English")) }

    @Test
    fun testDirectIsoCode() {
        assertSupported("hi", LanguageNormalizer.normalizeLanguage("hi"))
        assertSupported("ta", LanguageNormalizer.normalizeLanguage("ta"))
    }

    @Test
    fun testNativeNames() {
        assertSupported("hi", LanguageNormalizer.normalizeLanguage("हिन्दी"))
        assertSupported("bn", LanguageNormalizer.normalizeLanguage("বাংলা"))
        assertSupported("ta", LanguageNormalizer.normalizeLanguage("தமிழ்"))
    }

    @Test
    fun testCaseInsensitive() {
        assertSupported("hi", LanguageNormalizer.normalizeLanguage("HINDI"))
        assertSupported("en", LanguageNormalizer.normalizeLanguage("english"))
    }

    @Test
    fun testUnsupportedLanguageReturnsExplicitFallback() {
        val result = LanguageNormalizer.normalizeLanguage("French")
        assertFalse("Unsupported language must return isSupported=false", result.isSupported)
        assertEquals("french", result.code)
    }

    @Test
    fun testEmptyInput() {
        val result = LanguageNormalizer.normalizeLanguage("")
        assertFalse(result.isSupported)
        assertEquals("unknown", result.code)
    }

    @Test
    fun testOriyaAlias() {
        assertSupported("or", LanguageNormalizer.normalizeLanguage("Oriya"))
    }

    @Test
    fun testBanglaAlias() {
        assertSupported("bn", LanguageNormalizer.normalizeLanguage("Bangla"))
    }

    private fun assertSupported(expectedCode: String, result: LanguageNormalizer.NormalizationResult) {
        assertTrue("Expected supported language", result.isSupported)
        assertEquals(expectedCode, result.code)
    }
}
