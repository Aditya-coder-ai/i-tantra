package com.itantra.offlinevoice.text

/**
 * A fully assembled, transmission-ready message produced by the text-processing pipeline.
 *
 * This data class is the downstream contract: the future encryption/networking module
 * can assume every field is populated and internally consistent when it receives one.
 */
data class ProcessedMessage(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val text: String,
    val language: String,
    val messageType: MessageType,
    val priority: MessagePriority,
    val timestamp: String,
    val sequenceNumber: Long,
    val confidence: Float,
    val confidenceStatus: ConfidenceStatus,
    val isFinal: Boolean,
    val utf8ByteSize: Int,
    val processingTimeMs: Long,
)
