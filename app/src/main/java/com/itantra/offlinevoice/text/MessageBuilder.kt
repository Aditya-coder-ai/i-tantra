package com.itantra.offlinevoice.text

/**
 * Assembles a [ProcessedMessage] from cleaned text, classification, identity,
 * and metadata components produced by the pipeline stages.
 */
class MessageBuilder {
    private val metrics = TextMetrics()

    fun build(
        text: String,
        language: String,
        conversationId: String,
        senderId: String,
        classification: MessageClassifier.ClassificationResult,
        confidence: Float,
        processingTimeMs: Long,
    ): ProcessedMessage {
        val messageId = MessageIdentity.generateMessageId()
        val timestamp = MessageIdentity.utcTimestamp()
        val sequenceNumber = MessageIdentity.nextSequenceNumber()
        val confidenceStatus = toConfidenceStatus(confidence)

        return ProcessedMessage(
            messageId = messageId,
            conversationId = conversationId,
            senderId = senderId,
            text = text,
            language = language,
            messageType = classification.type,
            priority = classification.priority,
            timestamp = timestamp,
            sequenceNumber = sequenceNumber,
            confidence = confidence,
            confidenceStatus = confidenceStatus,
            isFinal = true,
            utf8ByteSize = metrics.utf8ByteSize(text),
            processingTimeMs = processingTimeMs
        )
    }

    private fun toConfidenceStatus(confidence: Float): ConfidenceStatus = when {
        confidence >= 0.80f -> ConfidenceStatus.HIGH
        confidence >= 0.50f -> ConfidenceStatus.MEDIUM
        else -> ConfidenceStatus.LOW
    }
}
