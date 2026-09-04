package com.talkmitra.offlinevoice.text

import com.talkmitra.offlinevoice.audio.stt.STTLanguage
import com.talkmitra.offlinevoice.audio.stt.STTResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TextProcessorTest {
    private lateinit var processor: TextProcessor

    @Before
    fun setUp() {
        processor = TextProcessor()
        processor.reset()
    }

    // --- Loop 7: Full pipeline end-to-end ---

    @Test
    fun testFullPipelineEndToEnd() {
        val sttResult = STTResult(
            text = "  I need help there is a fire  ",
            language = STTLanguage.ENGLISH,
            confidence = 0.92f,
            processingTimeMs = 150,
            audioDurationMs = 3000,
            isFinal = true
        )
        val result = processor.process(sttResult, "conv-001", "user-001")

        assertEquals(TextProcessingStatus.SUCCESS, result.status)
        val msg = result.message!!
        assertEquals("I need help there is a fire.", msg.text)
        assertEquals("en", msg.language)
        assertEquals(MessageType.NORMAL, msg.messageType) // not auto-emergency
        assertEquals(MessagePriority.NORMAL, msg.priority)
        assertTrue(msg.messageId.startsWith("VL-"))
        assertTrue(msg.timestamp.endsWith("Z"))
        assertTrue(msg.sequenceNumber > 0)
        assertTrue(msg.utf8ByteSize > 0)
        assertTrue(msg.isFinal)
        assertEquals(ConfidenceStatus.HIGH, msg.confidenceStatus)
        assertTrue(msg.processingTimeMs >= 0)
    }

    @Test
    fun testFullPipelineHindiEndToEnd() {
        val sttResult = STTResult(
            text = "  मुझे   मदद   चाहिए  ",
            language = STTLanguage.HINDI,
            confidence = 0.85f,
            processingTimeMs = 200,
            audioDurationMs = 2000,
            isFinal = true
        )
        val result = processor.process(sttResult, "conv-002", "user-001")
        assertEquals(TextProcessingStatus.SUCCESS, result.status)
        val msg = result.message!!
        assertEquals("मुझे मदद चाहिए।", msg.text)
        assertEquals("hi", msg.language)
        assertTrue("Hindi text should have larger byte size than char count", msg.utf8ByteSize > msg.text.length)
    }

    @Test
    fun testEmergencyWithUserConfirmation() {
        val sttResult = STTResult(
            text = "Help! There is a fire.",
            language = STTLanguage.ENGLISH,
            confidence = 0.95f,
            processingTimeMs = 100,
            audioDurationMs = 2000,
            isFinal = true
        )
        val result = processor.process(sttResult, "conv-001", "user-001", userConfirmedEmergency = true)
        assertEquals(TextProcessingStatus.SUCCESS, result.status)
        assertEquals(MessageType.EMERGENCY, result.message!!.messageType)
        assertEquals(MessagePriority.CRITICAL, result.message!!.priority)
    }

    // --- Loop 8: Edge cases & confidence handling ---

    @Test
    fun testEmptyStringReturnsEmptyMessage() {
        val sttResult = STTResult(
            text = "",
            language = STTLanguage.ENGLISH,
            confidence = 0.90f,
            processingTimeMs = 50,
            audioDurationMs = 500,
            isFinal = true
        )
        val result = processor.process(sttResult, "conv-001", "user-001")
        assertEquals(TextProcessingStatus.EMPTY_MESSAGE, result.status)
        assertNull(result.message)
    }

    @Test
    fun testWhitespaceOnlyReturnsEmptyMessage() {
        val sttResult = STTResult(
            text = "     ",
            language = STTLanguage.ENGLISH,
            confidence = 0.90f,
            processingTimeMs = 50,
            audioDurationMs = 500,
            isFinal = true
        )
        val result = processor.process(sttResult, "conv-001", "user-001")
        assertEquals(TextProcessingStatus.EMPTY_MESSAGE, result.status)
        assertNull(result.message)
    }

    @Test
    fun testPartialResultNeverCreatesMessage() {
        val sttResult = STTResult(
            text = "I need he",
            language = STTLanguage.ENGLISH,
            confidence = 0.60f,
            processingTimeMs = 80,
            audioDurationMs = 1000,
            isFinal = false
        )
        val result = processor.process(sttResult, "conv-001", "user-001")
        assertEquals(TextProcessingStatus.PARTIAL_IN_PROGRESS, result.status)
        assertNull("Partial must not produce a message", result.message)
        assertNotNull("Partial should have a preview", result.partialPreview)
    }

    @Test
    fun testDuplicateFinalProducesOnlyOneMessage() {
        val sttResult = STTResult(
            text = "I need help.",
            language = STTLanguage.ENGLISH,
            confidence = 0.90f,
            processingTimeMs = 100,
            audioDurationMs = 2000,
            isFinal = true
        )

        val first = processor.process(sttResult, "conv-001", "user-001")
        assertEquals(TextProcessingStatus.SUCCESS, first.status)
        assertNotNull(first.message)

        val second = processor.process(sttResult, "conv-001", "user-001")
        assertEquals(TextProcessingStatus.DUPLICATE, second.status)
        assertNull("Duplicate must not produce a message", second.message)
    }

    @Test
    fun testLowConfidencePreservesOriginalTextUnchanged() {
        val sttResult = STTResult(
            text = "mumbly unclear words",
            language = STTLanguage.ENGLISH,
            confidence = 0.35f,
            processingTimeMs = 200,
            audioDurationMs = 3000,
            isFinal = true
        )
        val result = processor.process(sttResult, "conv-001", "user-001")
        assertEquals(TextProcessingStatus.LOW_CONFIDENCE_PENDING_REVIEW, result.status)
        assertNotNull("Low confidence must still produce a message", result.message)
        assertEquals("mumbly unclear words.", result.message!!.text) // cleaned but NOT rewritten
        assertEquals(ConfidenceStatus.LOW, result.message!!.confidenceStatus)
    }

    @Test
    fun testMediumConfidence() {
        val sttResult = STTResult(
            text = "some words",
            language = STTLanguage.ENGLISH,
            confidence = 0.65f,
            processingTimeMs = 100,
            audioDurationMs = 1500,
            isFinal = true
        )
        val result = processor.process(sttResult, "conv-001", "user-001")
        assertEquals(TextProcessingStatus.SUCCESS, result.status)
        assertEquals(ConfidenceStatus.MEDIUM, result.message!!.confidenceStatus)
    }

    @Test
    fun testNoCrashOnAnyEdgeCase() {
        // Ensure no exceptions for unusual inputs
        val cases = listOf("", " ", "\t\n", ".", "!!!", "  .  ")
        for (text in cases) {
            val sttResult = STTResult(
                text = text,
                language = STTLanguage.ENGLISH,
                confidence = 0.90f,
                processingTimeMs = 10,
                audioDurationMs = 100,
                isFinal = true
            )
            // Should not throw
            processor.process(sttResult, "conv-001", "user-001")
        }
    }

    @Test
    fun testProcessingTimeIsMeasured() {
        val sttResult = STTResult(
            text = "Measuring time",
            language = STTLanguage.ENGLISH,
            confidence = 0.90f,
            processingTimeMs = 100,
            audioDurationMs = 2000,
            isFinal = true
        )
        val result = processor.process(sttResult, "conv-001", "user-001")
        assertNotNull(result.message)
        assertTrue("processingTimeMs should be >= 0", result.message!!.processingTimeMs >= 0)
    }
}
