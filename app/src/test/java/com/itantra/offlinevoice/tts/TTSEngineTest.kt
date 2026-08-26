package com.itantra.offlinevoice.tts

import com.itantra.offlinevoice.text.ConfidenceStatus
import com.itantra.offlinevoice.text.MessagePriority
import com.itantra.offlinevoice.text.MessageType
import com.itantra.offlinevoice.text.ProcessedMessage
import com.itantra.offlinevoice.tts.benchmark.TTSBenchmark
import com.itantra.offlinevoice.tts.preprocessing.TTSTextPreprocessor
import com.itantra.offlinevoice.tts.queue.TTSQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the TTS subsystem.
 *
 * These tests cover:
 * - Text preprocessing for all 10 languages
 * - Unicode handling
 * - Queue priority ordering
 * - Language resolution
 * - TTSResult RTF calculation
 * - Exception hierarchy
 * - Edge cases (empty text, unsupported language, etc.)
 *
 * NOTE: Tests that require the Android runtime or native Sherpa-ONNX
 * library are marked with comments and should be run as instrumentation
 * tests on a device.
 */
class TTSEngineTest {

    private lateinit var preprocessor: TTSTextPreprocessor
    private lateinit var queue: TTSQueue

    @Before
    fun setUp() {
        preprocessor = TTSTextPreprocessor()
        queue = TTSQueue(maxSize = 10)
    }

    // ══════════════════════════════════════════════════════════════════
    // Language Resolution
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `fromCode resolves all 10 supported language codes`() {
        val codes = listOf("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")
        for (code in codes) {
            val lang = TTSLanguage.fromCode(code)
            assertNotNull("Language for code '$code' should not be null", lang)
            assertEquals(code, lang!!.code)
        }
    }

    @Test
    fun `fromCode returns null for unknown language`() {
        assertNull(TTSLanguage.fromCode("xx"))
        assertNull(TTSLanguage.fromCode("fr"))
        assertNull(TTSLanguage.fromCode(""))
    }

    @Test
    fun `fromCode is case-insensitive`() {
        assertEquals(TTSLanguage.HINDI, TTSLanguage.fromCode("HI"))
        assertEquals(TTSLanguage.ENGLISH, TTSLanguage.fromCode("En"))
        assertEquals(TTSLanguage.TAMIL, TTSLanguage.fromCode("TA"))
    }

    @Test
    fun `fromCode trims whitespace`() {
        assertEquals(TTSLanguage.HINDI, TTSLanguage.fromCode("  hi  "))
        assertEquals(TTSLanguage.ENGLISH, TTSLanguage.fromCode(" en "))
    }

    // ══════════════════════════════════════════════════════════════════
    // Text Preprocessing — Unicode Preservation
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `preprocessor preserves Hindi script`() {
        val input = "मुझे मदद चाहिए।"
        val result = preprocessor.preprocess(input, TTSLanguage.HINDI)
        assertTrue("Hindi script should be preserved", result.contains("मुझे"))
        assertTrue("Hindi script should be preserved", result.contains("मदद"))
        assertTrue("Hindi script should be preserved", result.contains("चाहिए"))
    }

    @Test
    fun `preprocessor preserves Gujarati script`() {
        val input = "મને મદદ જોઈએ."
        val result = preprocessor.preprocess(input, TTSLanguage.GUJARATI)
        assertTrue("Gujarati script should be preserved", result.contains("મને"))
    }

    @Test
    fun `preprocessor preserves Marathi script`() {
        val input = "मला मदत हवी आहे."
        val result = preprocessor.preprocess(input, TTSLanguage.MARATHI)
        assertTrue("Marathi script should be preserved", result.contains("मला"))
    }

    @Test
    fun `preprocessor preserves Kannada script`() {
        val input = "ನನಗೆ ಸಹಾಯ ಬೇಕು."
        val result = preprocessor.preprocess(input, TTSLanguage.KANNADA)
        assertTrue("Kannada script should be preserved", result.contains("ನನಗೆ"))
    }

    @Test
    fun `preprocessor preserves Malayalam script`() {
        val input = "എനിക്ക് സഹായം വേണം."
        val result = preprocessor.preprocess(input, TTSLanguage.MALAYALAM)
        assertTrue("Malayalam script should be preserved", result.contains("എനിക്ക്"))
    }

    @Test
    fun `preprocessor preserves Tamil script`() {
        val input = "எனக்கு உதவி வேண்டும்."
        val result = preprocessor.preprocess(input, TTSLanguage.TAMIL)
        assertTrue("Tamil script should be preserved", result.contains("எனக்கு"))
    }

    @Test
    fun `preprocessor preserves Telugu script`() {
        val input = "నాకు సహాయం కావాలి."
        val result = preprocessor.preprocess(input, TTSLanguage.TELUGU)
        assertTrue("Telugu script should be preserved", result.contains("నాకు"))
    }

    @Test
    fun `preprocessor preserves Odia script`() {
        val input = "ମୋତେ ସାହାଯ୍ୟ ଦରକାର।"
        val result = preprocessor.preprocess(input, TTSLanguage.ODIA)
        assertTrue("Odia script should be preserved", result.contains("ମୋତେ"))
    }

    @Test
    fun `preprocessor preserves Bengali script`() {
        val input = "আমার সাহায্য দরকার।"
        val result = preprocessor.preprocess(input, TTSLanguage.BENGALI)
        assertTrue("Bengali script should be preserved", result.contains("আমার"))
    }

    @Test
    fun `preprocessor preserves English text`() {
        val input = "I need help."
        val result = preprocessor.preprocess(input, TTSLanguage.ENGLISH)
        assertTrue("English text should be preserved", result.contains("need help"))
    }

    // ══════════════════════════════════════════════════════════════════
    // Text Preprocessing — Normalisation
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `preprocessor collapses multiple spaces`() {
        val result = preprocessor.preprocess("I   need    help.", TTSLanguage.ENGLISH)
        assertFalse("Multiple spaces should be collapsed", result.contains("  "))
    }

    @Test
    fun `preprocessor trims whitespace`() {
        val result = preprocessor.preprocess("  hello world  ", TTSLanguage.ENGLISH)
        assertFalse("Leading whitespace should be trimmed", result.startsWith(" "))
        assertFalse("Trailing whitespace should be trimmed", result.endsWith(" "))
    }

    @Test
    fun `preprocessor strips control characters`() {
        val input = "Hello\u0000world\u0001test\u007F"
        val result = preprocessor.preprocess(input, TTSLanguage.ENGLISH)
        assertFalse("Control chars should be removed", result.contains("\u0000"))
        assertFalse("Control chars should be removed", result.contains("\u0001"))
        assertTrue("Regular text should remain", result.contains("Hello"))
        assertTrue("Regular text should remain", result.contains("world"))
    }

    @Test
    fun `preprocessor expands English abbreviations`() {
        val result = preprocessor.preprocess("Dr. Smith is here.", TTSLanguage.ENGLISH)
        assertTrue("Dr. should expand to Doctor", result.contains("Doctor"))
    }

    @Test
    fun `preprocessor ensures trailing punctuation for Hindi`() {
        val result = preprocessor.preprocess("मुझे मदद चाहिए", TTSLanguage.HINDI)
        val lastChar = result.last()
        assertTrue(
            "Hindi text should end with danda or period",
            lastChar == '।' || lastChar == '.'
        )
    }

    @Test
    fun `preprocessor ensures trailing punctuation for English`() {
        val result = preprocessor.preprocess("I need help", TTSLanguage.ENGLISH)
        assertTrue("English text should end with period", result.endsWith("."))
    }

    @Test
    fun `preprocessor does not double-add punctuation`() {
        val result = preprocessor.preprocess("I need help.", TTSLanguage.ENGLISH)
        assertFalse("Should not add double period", result.endsWith(".."))
    }

    @Test
    fun `preprocessor does not translate text`() {
        // Hindi text must remain Hindi, not be translated to English
        val input = "मुझे मदद चाहिए।"
        val result = preprocessor.preprocess(input, TTSLanguage.HINDI)
        assertFalse("Should not translate to English", result.contains("help"))
        assertTrue("Hindi text must remain", result.contains("मदद"))
    }

    // ══════════════════════════════════════════════════════════════════
    // Queue Priority Ordering
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `queue dequeues CRITICAL before NORMAL`() {
        val normal = createMessage(id = "msg-1", priority = MessagePriority.NORMAL)
        val critical = createMessage(id = "msg-2", priority = MessagePriority.CRITICAL)

        queue.enqueue(normal)
        queue.enqueue(critical)

        val first = queue.dequeue()
        assertEquals("CRITICAL should dequeue first", "msg-2", first?.messageId)

        val second = queue.dequeue()
        assertEquals("NORMAL should dequeue second", "msg-1", second?.messageId)
    }

    @Test
    fun `queue dequeues HIGH before NORMAL`() {
        val normal = createMessage(id = "msg-1", priority = MessagePriority.NORMAL)
        val high = createMessage(id = "msg-2", priority = MessagePriority.HIGH)

        queue.enqueue(normal)
        queue.enqueue(high)

        val first = queue.dequeue()
        assertEquals("HIGH should dequeue first", "msg-2", first?.messageId)
    }

    @Test
    fun `queue maintains FIFO within same priority`() {
        val msg1 = createMessage(id = "msg-1", priority = MessagePriority.NORMAL)
        val msg2 = createMessage(id = "msg-2", priority = MessagePriority.NORMAL)
        val msg3 = createMessage(id = "msg-3", priority = MessagePriority.NORMAL)

        queue.enqueue(msg1)
        queue.enqueue(msg2)
        queue.enqueue(msg3)

        assertEquals("msg-1", queue.dequeue()?.messageId)
        assertEquals("msg-2", queue.dequeue()?.messageId)
        assertEquals("msg-3", queue.dequeue()?.messageId)
    }

    @Test
    fun `queue returns null when empty`() {
        assertNull(queue.dequeue())
    }

    @Test
    fun `queue evicts oldest NORMAL when full`() {
        // Fill queue with NORMAL messages
        for (i in 1..10) {
            queue.enqueue(createMessage(id = "msg-$i", priority = MessagePriority.NORMAL))
        }
        assertEquals(10, queue.size())

        // Add one more — should evict the oldest (msg-1)
        queue.enqueue(createMessage(id = "msg-11", priority = MessagePriority.NORMAL))
        assertEquals(10, queue.size())

        val first = queue.dequeue()
        assertEquals("Oldest should have been evicted", "msg-2", first?.messageId)
    }

    @Test
    fun `queue reports hasEmergencyMessages correctly`() {
        assertFalse(queue.hasEmergencyMessages())

        queue.enqueue(createMessage(id = "msg-1", priority = MessagePriority.NORMAL))
        assertFalse(queue.hasEmergencyMessages())

        queue.enqueue(createMessage(id = "msg-2", priority = MessagePriority.CRITICAL))
        assertTrue(queue.hasEmergencyMessages())
    }

    @Test
    fun `queue notifies on emergency enqueue`() {
        var notified = false
        queue.onEmergencyEnqueued = { notified = true }

        queue.enqueue(createMessage(priority = MessagePriority.NORMAL))
        assertFalse("Should not notify for NORMAL", notified)

        queue.enqueue(createMessage(
            priority = MessagePriority.CRITICAL,
            type = MessageType.EMERGENCY
        ))
        assertTrue("Should notify for EMERGENCY/CRITICAL", notified)
    }

    @Test
    fun `queue clear empties all messages`() {
        queue.enqueue(createMessage(id = "msg-1"))
        queue.enqueue(createMessage(id = "msg-2"))
        queue.clear()
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size())
    }

    // ══════════════════════════════════════════════════════════════════
    // TTSResult — RTF Calculation
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `RTF is calculated correctly`() {
        val result = TTSResult(
            audioData = ShortArray(22050), // 1 second at 22050 Hz
            sampleRate = 22050,
            audioDurationMs = 1000,
            processingTimeMs = 500,
            language = TTSLanguage.ENGLISH,
            textLength = 10
        )
        assertEquals(0.5f, result.realTimeFactor, 0.01f)
    }

    @Test
    fun `RTF is 0 when audio duration is 0`() {
        val result = TTSResult(
            audioData = ShortArray(0),
            sampleRate = 22050,
            audioDurationMs = 0,
            processingTimeMs = 100,
            language = TTSLanguage.ENGLISH,
            textLength = 0
        )
        assertEquals(0f, result.realTimeFactor, 0.001f)
    }

    @Test
    fun `RTF greater than 1 means slower than real-time`() {
        val result = TTSResult(
            audioData = ShortArray(22050),
            sampleRate = 22050,
            audioDurationMs = 1000,
            processingTimeMs = 2000,
            language = TTSLanguage.ENGLISH,
            textLength = 10
        )
        assertTrue("RTF > 1 means slower than real-time", result.realTimeFactor > 1.0f)
    }

    // ══════════════════════════════════════════════════════════════════
    // TTSLanguage Enum
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `all 10 languages are defined`() {
        assertEquals(10, TTSLanguage.values().size)
    }

    @Test
    fun `all language codes are unique`() {
        val codes = TTSLanguage.values().map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `all languages have non-empty display names`() {
        TTSLanguage.values().forEach {
            assertTrue("Display name for ${it.code} should not be empty", it.displayName.isNotEmpty())
            assertTrue("Native name for ${it.code} should not be empty", it.nativeName.isNotEmpty())
        }
    }

    @Test
    fun `default sample rate is 22050`() {
        TTSLanguage.values().forEach {
            assertEquals("Default sample rate should be 22050", 22050, it.sampleRate)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // TTSConfig
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `config has sensible defaults`() {
        val config = TTSConfig()
        assertEquals(1.0f, config.speechRate, 0.01f)
        assertEquals(1.0f, config.pitch, 0.01f)
        assertEquals(1.0f, config.volume, 0.01f)
        assertTrue(config.enableStreaming)
        assertEquals(2, config.maxCachedModels)
        assertEquals(2, config.emergencyRepeatCount)
        assertEquals(300L, config.sentencePauseMs)
        assertEquals(2, config.numThreads)
    }

    // ══════════════════════════════════════════════════════════════════
    // TTSException
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `ModelNotFoundException contains language info`() {
        val ex = TTSException.ModelNotFoundException(TTSLanguage.HINDI)
        assertTrue(ex.message!!.contains("Hindi"))
        assertTrue(ex.message!!.contains("TTS_ERROR_MODEL_NOT_FOUND"))
    }

    @Test
    fun `LanguageUnsupportedException contains language code`() {
        val ex = TTSException.LanguageUnsupportedException("xx")
        assertTrue(ex.message!!.contains("xx"))
        assertTrue(ex.message!!.contains("TTS_ERROR_LANGUAGE_UNSUPPORTED"))
    }

    @Test
    fun `InvalidTextException contains message`() {
        val ex = TTSException.InvalidTextException("Text is empty")
        assertTrue(ex.message!!.contains("TTS_ERROR_INVALID_TEXT"))
    }

    // ══════════════════════════════════════════════════════════════════
    // Edge Cases
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `preprocessor handles empty string`() {
        val result = preprocessor.preprocess("", TTSLanguage.ENGLISH)
        assertTrue("Empty input should produce empty or minimal output", result.length <= 1)
    }

    @Test
    fun `preprocessor handles whitespace-only string`() {
        val result = preprocessor.preprocess("   \t\n  ", TTSLanguage.ENGLISH)
        assertTrue("Whitespace-only input should produce empty or minimal output", result.isEmpty() || result == ".")
    }

    @Test
    fun `preprocessor handles very long text`() {
        val longText = "This is a test. ".repeat(300)
        val result = preprocessor.preprocess(longText, TTSLanguage.ENGLISH)
        assertTrue("Long text should be processed", result.isNotEmpty())
    }

    @Test
    fun `preprocessor handles mixed scripts`() {
        val input = "Hello मुझे help चाहिए."
        val result = preprocessor.preprocess(input, TTSLanguage.HINDI)
        assertTrue("Mixed script text should be preserved", result.contains("Hello"))
        assertTrue("Mixed script text should be preserved", result.contains("मुझे"))
    }

    @Test
    fun `preprocessor handles numbers in text`() {
        val input = "Building 5 is on fire."
        val result = preprocessor.preprocess(input, TTSLanguage.ENGLISH)
        assertTrue("Text with numbers should be processed", result.contains("5") || result.contains("five"))
    }

    @Test
    fun `preprocessor handles special punctuation`() {
        val input = "Help! Is anyone there? I need help."
        val result = preprocessor.preprocess(input, TTSLanguage.ENGLISH)
        assertTrue("Exclamation should be preserved", result.contains("!"))
        assertTrue("Question mark should be preserved", result.contains("?"))
    }

    // ══════════════════════════════════════════════════════════════════
    // Benchmark Test Sentences
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun `benchmark has test sentences for all 10 languages`() {
        TTSLanguage.values().forEach { lang ->
            val sentences = TTSBenchmark.TEST_SENTENCES[lang]
            assertNotNull("Test sentences should exist for ${lang.displayName}", sentences)
            assertTrue(
                "Should have at least 1 test sentence for ${lang.displayName}",
                sentences!!.isNotEmpty()
            )
        }
    }

    @Test
    fun `benchmark test sentences are non-empty`() {
        TTSBenchmark.TEST_SENTENCES.forEach { (lang, sentences) ->
            sentences.forEach { sentence ->
                assertTrue(
                    "Test sentence for ${lang.displayName} should not be blank",
                    sentence.isNotBlank()
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════

    private fun createMessage(
        id: String = "VL-test-${System.nanoTime()}",
        text: String = "Test message",
        language: String = "en",
        priority: MessagePriority = MessagePriority.NORMAL,
        type: MessageType = MessageType.NORMAL
    ): ProcessedMessage = ProcessedMessage(
        messageId = id,
        conversationId = "conv-test",
        senderId = "sender-test",
        text = text,
        language = language,
        messageType = type,
        priority = priority,
        timestamp = System.currentTimeMillis().toString(),
        sequenceNumber = 1L,
        confidence = 0.95f,
        confidenceStatus = ConfidenceStatus.HIGH,
        isFinal = true,
        utf8ByteSize = text.toByteArray(Charsets.UTF_8).size,
        processingTimeMs = 0L
    )
}
