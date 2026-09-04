package com.talkmitra.offlinevoice.text

import com.talkmitra.offlinevoice.audio.stt.STTResult

/**
 * Top-level pipeline orchestrator. Accepts an [STTResult] and produces a
 * [TextProcessingResult] by wiring together cleaning, segmentation, deduplication,
 * classification, identity assignment, and metrics computation.
 *
 * Pipeline: clean → dedup/final-check → classify → build message → attach metrics
 *
 * All operations are fully offline — no cloud APIs, no LLMs.
 */
class TextProcessor(
    private val cleaner: TextCleaner = TextCleaner(),
    private val classifier: MessageClassifier = MessageClassifier(),
    private val deduplicator: MessageDeduplicator = MessageDeduplicator(),
    private val builder: MessageBuilder = MessageBuilder(),
) {

    /**
     * Processes an STT result through the full text pipeline.
     *
     * @param sttResult The raw STT output.
     * @param conversationId ID of the current conversation.
     * @param senderId ID of the sending user/device.
     * @param userConfirmedEmergency True if the user has explicitly confirmed emergency status.
     * @return [TextProcessingResult] wrapping either a [ProcessedMessage] or a status explaining why none was created.
     */
    fun process(
        sttResult: STTResult,
        conversationId: String,
        senderId: String,
        userConfirmedEmergency: Boolean = false,
    ): TextProcessingResult {
        val startNanos = System.nanoTime()

        // 1. Partial result gating — never produce a message, only a preview
        if (!sttResult.isFinal) {
            val preview = cleaner.cleanText(sttResult.text)
            return TextProcessingResult(
                status = TextProcessingStatus.PARTIAL_IN_PROGRESS,
                message = null,
                partialPreview = preview.ifEmpty { null }
            )
        }

        // 2. Clean the text
        val cleaned = cleaner.cleanText(sttResult.text)

        // 3. Empty/whitespace check
        if (cleaned.isEmpty()) {
            return TextProcessingResult(
                status = TextProcessingStatus.EMPTY_MESSAGE,
                message = null
            )
        }

        // 4. Deduplication check
        if (deduplicator.isDuplicate(cleaned, isFinal = true)) {
            return TextProcessingResult(
                status = TextProcessingStatus.DUPLICATE,
                message = null
            )
        }

        // 5. Language normalization
        val langResult = LanguageNormalizer.normalizeLanguage(sttResult.language.code)
        val statusOverride = if (!langResult.isSupported) TextProcessingStatus.UNSUPPORTED_LANGUAGE else null

        // 6. Classification
        val classification = classifier.classifyMessage(cleaned, userConfirmedEmergency)

        // 7. Compute processing time
        val processingTimeMs = (System.nanoTime() - startNanos) / 1_000_000

        // 8. Build the message
        val message = builder.build(
            text = cleaned,
            language = langResult.code,
            conversationId = conversationId,
            senderId = senderId,
            classification = classification,
            confidence = sttResult.confidence,
            processingTimeMs = processingTimeMs
        )

        // 9. Record for deduplication
        deduplicator.recordProcessed(message.messageId, cleaned)

        // 10. Determine final status
        val status = statusOverride
            ?: if (sttResult.confidence < LOW_CONFIDENCE_THRESHOLD) TextProcessingStatus.LOW_CONFIDENCE_PENDING_REVIEW
            else TextProcessingStatus.SUCCESS

        return TextProcessingResult(
            status = status,
            message = message
        )
    }

    /** Resets pipeline state (deduplication history, sequence counters). */
    fun reset() {
        deduplicator.reset()
        MessageIdentity.resetSequence()
    }

    private companion object {
        const val LOW_CONFIDENCE_THRESHOLD = 0.50f
    }
}
