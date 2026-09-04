package com.talkmitra.offlinevoice.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextLanguageDetectorTest {

    @Test
    fun detectsEnglishTypedText() {
        assertEquals(
            SupportedLanguage.ENGLISH,
            TextLanguageDetector.detectUnambiguousLanguage("I need help. There is a fire.")
        )
    }

    @Test
    fun keepsIndicTextOnTheConfiguredLanguagePath() {
        assertNull(TextLanguageDetector.detectUnambiguousLanguage("मुझे मदद चाहिए"))
    }

    @Test
    fun detectsGujaratiTypedText() {
        assertEquals(
            SupportedLanguage.GUJARATI,
            TextLanguageDetector.detectUnambiguousLanguage("મને મદદ જોઈએ")
        )
    }

    @Test
    fun ignoresMixedScriptText() {
        assertNull(TextLanguageDetector.detectUnambiguousLanguage("Help चाहिए"))
    }
}
