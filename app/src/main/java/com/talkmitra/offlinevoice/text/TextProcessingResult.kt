package com.talkmitra.offlinevoice.text

/** Outcome status of a [TextProcessor.process] call. */
enum class TextProcessingStatus {
    /** Successfully produced a [ProcessedMessage]. */
    SUCCESS,
    /** Input was empty or whitespace-only after cleaning. */
    EMPTY_MESSAGE,
    /** STT confidence is below threshold — message is produced but flagged for optional user review. */
    LOW_CONFIDENCE_PENDING_REVIEW,
    /** Input was a duplicate of an already-processed final utterance. */
    DUPLICATE,
    /** STT result was partial (non-final) — no message created. */
    PARTIAL_IN_PROGRESS,
    /** Language is not in the supported set — message produced with fallback language tag. */
    UNSUPPORTED_LANGUAGE
}

/**
 * Wrapper returned by [TextProcessor.process], pairing a nullable [ProcessedMessage] with a
 * [TextProcessingStatus] that explains the outcome.
 */
data class TextProcessingResult(
    val status: TextProcessingStatus,
    val message: ProcessedMessage?,
    val partialPreview: String? = null
)
