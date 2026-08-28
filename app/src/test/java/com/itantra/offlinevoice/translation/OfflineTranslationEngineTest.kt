package com.itantra.offlinevoice.translation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineTranslationEngineTest {

    private lateinit var engine: OfflineTranslationEngine

    @Before
    fun setUp() {
        engine = OfflineTranslationEngine()
    }

    @Test
    fun testSameLanguagePassthrough() = runBlocking {
        val result = engine.translate(
            text = "मुझे मदद चाहिए",
            sourceLanguage = SupportedLanguage.HINDI,
            targetLanguage = SupportedLanguage.HINDI
        )

        assertFalse(result.isTranslationRequired)
        assertEquals("मुझे मदद चाहिए", result.translatedText)
        assertEquals("मुझे मदद चाहिए", result.originalText)
        assertEquals(SupportedLanguage.HINDI, result.originalLanguage)
        assertEquals(SupportedLanguage.HINDI, result.targetLanguage)
        assertEquals(TranslationPath.SAME_LANGUAGE_PASSTHROUGH, result.translationPath)
    }

    @Test
    fun testEnglishToHindiEmergencyTranslation() = runBlocking {
        val result = engine.translate(
            text = "I need help. There is a fire.",
            sourceLanguage = SupportedLanguage.ENGLISH,
            targetLanguage = SupportedLanguage.HINDI,
            isEmergency = true
        )

        assertTrue(result.isTranslationRequired)
        assertEquals("I need help. There is a fire.", result.originalText)
        assertEquals(SupportedLanguage.ENGLISH, result.originalLanguage)
        assertEquals("मुझे मदद चाहिए। वहाँ आग लगी है।", result.translatedText)
        assertEquals(SupportedLanguage.HINDI, result.targetLanguage)
        assertEquals(TranslationPath.DIRECT, result.translationPath)
    }

    @Test
    fun testDefaultEmergencyAlertTranslatesFromEnglishToHindi() = runBlocking {
        val result = engine.translate(
            text = "Emergency assistance required. My location should be checked.",
            sourceLanguage = SupportedLanguage.ENGLISH,
            targetLanguage = SupportedLanguage.HINDI,
            isEmergency = true
        )

        assertEquals(
            "आपातकालीन सहायता आवश्यक है। मेरे स्थान की जाँच की जानी चाहिए।",
            result.translatedText
        )
    }

    @Test
    fun testHindiToEnglishTranslation() = runBlocking {
        val result = engine.translate(
            text = "मुझे मदद चाहिए।",
            sourceLanguage = SupportedLanguage.HINDI,
            targetLanguage = SupportedLanguage.ENGLISH
        )

        assertTrue(result.isTranslationRequired)
        assertEquals("मुझे मदद चाहिए।", result.originalText)
        assertEquals("I need help.", result.translatedText)
        assertEquals(SupportedLanguage.ENGLISH, result.targetLanguage)
    }

    @Test
    fun testEnglishToHindiGreetingsAndQuestions() = runBlocking {
        val helloResult = engine.translate(
            text = "Hello",
            sourceLanguage = SupportedLanguage.ENGLISH,
            targetLanguage = SupportedLanguage.HINDI
        )
        assertEquals("नमस्ते", helloResult.translatedText)

        val whereResult = engine.translate(
            text = "Where are you?",
            sourceLanguage = SupportedLanguage.ENGLISH,
            targetLanguage = SupportedLanguage.HINDI
        )
        assertEquals("आप कहाँ हैं?", whereResult.translatedText)

        val howResult = engine.translate(
            text = "How are you?",
            sourceLanguage = SupportedLanguage.ENGLISH,
            targetLanguage = SupportedLanguage.HINDI
        )
        assertEquals("आप कैसे हैं?", howResult.translatedText)
    }

    @Test
    fun testHindiToEnglishGreetingsAndQuestions() = runBlocking {
        val helloResult = engine.translate(
            text = "नमस्ते",
            sourceLanguage = SupportedLanguage.HINDI,
            targetLanguage = SupportedLanguage.ENGLISH
        )
        assertEquals("Hello", helloResult.translatedText)

        val whereResult = engine.translate(
            text = "आप कहाँ हैं?",
            sourceLanguage = SupportedLanguage.HINDI,
            targetLanguage = SupportedLanguage.ENGLISH
        )
        assertEquals("Where are you?", whereResult.translatedText)

        val fineResult = engine.translate(
            text = "मैं ठीक हूँ",
            sourceLanguage = SupportedLanguage.HINDI,
            targetLanguage = SupportedLanguage.ENGLISH
        )
        assertEquals("I am fine", fineResult.translatedText)
    }

    @Test
    fun testEnglishToBengaliTranslation() = runBlocking {
        val result = engine.translate(
            text = "I am safe.",
            sourceLanguage = SupportedLanguage.ENGLISH,
            targetLanguage = SupportedLanguage.BENGALI
        )

        assertTrue(result.isTranslationRequired)
        assertEquals("আমি নিরাপদ.", result.translatedText)
        assertEquals(SupportedLanguage.BENGALI, result.targetLanguage)
    }

    @Test
    fun testTamilToEnglishTranslation() = runBlocking {
        val result = engine.translate(
            text = "எனக்கு உதவி தேவை.",
            sourceLanguage = SupportedLanguage.TAMIL,
            targetLanguage = SupportedLanguage.ENGLISH
        )

        assertTrue(result.isTranslationRequired)
        assertEquals("I need help.", result.translatedText)
        assertEquals(SupportedLanguage.ENGLISH, result.targetLanguage)
    }

    @Test
    fun testTamilToHindiPivotTranslation() = runBlocking {
        val result = engine.translate(
            text = "எனக்கு உதவி தேவை.",
            sourceLanguage = SupportedLanguage.TAMIL,
            targetLanguage = SupportedLanguage.HINDI
        )

        assertTrue(result.isTranslationRequired)
        assertEquals(TranslationPath.PIVOT_ENGLISH, result.translationPath)
        assertEquals("मुझे मदद चाहिए।", result.translatedText)
        assertNotNull(result.intermediateText)
        assertEquals("I need help.", result.intermediateText)
    }

    @Test
    fun testPreserveOriginalDecryptedText() = runBlocking {
        val original = "Send help immediately."
        val result = engine.translate(
            text = original,
            sourceLanguage = SupportedLanguage.ENGLISH,
            targetLanguage = SupportedLanguage.GUJARATI
        )

        // Original text remains pristine and unmutated
        assertEquals(original, result.originalText)
        assertEquals("તરત જ મદદ મોકલો.", result.translatedText)
    }
}
