package com.itantra.offlinevoice.translation

import com.itantra.offlinevoice.text.ConfidenceStatus
import com.itantra.offlinevoice.text.MessagePriority
import com.itantra.offlinevoice.text.MessageType
import com.itantra.offlinevoice.text.ProcessedMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationQueueTest {

    private fun createTestMessage(
        id: String,
        text: String,
        language: String,
        priority: MessagePriority = MessagePriority.NORMAL,
        type: MessageType = MessageType.NORMAL
    ): ProcessedMessage {
        return ProcessedMessage(
            messageId = id,
            conversationId = "CONV-1",
            senderId = "VL-DEV1",
            text = text,
            language = language,
            messageType = type,
            priority = priority,
            timestamp = "2026-08-28T10:00:00Z",
            sequenceNumber = 1L,
            confidence = 0.95f,
            confidenceStatus = ConfidenceStatus.HIGH,
            isFinal = true,
            utf8ByteSize = text.toByteArray().size,
            processingTimeMs = 12L
        )
    }

    @Test
    fun testPriorityComparator() {
        val normalMsg = createTestMessage("VL-1", "Hello", "en", MessagePriority.NORMAL, MessageType.NORMAL)
        val emergencyMsg = createTestMessage("VL-2", "Fire! I need help!", "en", MessagePriority.CRITICAL, MessageType.EMERGENCY)

        val normalItem = TranslationWorkItem(normalMsg, SupportedLanguage.HINDI)
        val emergencyItem = TranslationWorkItem(emergencyMsg, SupportedLanguage.HINDI)

        // Emergency items have higher precedence (compareTo < 0)
        assertTrue(emergencyItem.compareTo(normalItem) < 0)
        assertTrue(normalItem.compareTo(emergencyItem) > 0)
    }

    @Test
    fun testQueueExecution() = runBlocking {
        val engine = OfflineTranslationEngine()
        val queue = TranslationQueue(engine)

        val message = createTestMessage("VL-100", "I am safe.", "en")

        val result = queue.enqueueAndWait(message, SupportedLanguage.HINDI)
        assertEquals("मैं सुरक्षित हूँ।", result.translatedText)
        assertEquals(SupportedLanguage.HINDI, result.targetLanguage)

        queue.shutdown()
    }
}
