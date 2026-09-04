package com.talkmitra.offlinevoice.audio.stt

/** Structured result from the STT engine. */
data class STTResult(
    val text: String,
    val language: STTLanguage,
    val confidence: Float,
    val processingTimeMs: Long,
    val audioDurationMs: Long,
    val isFinal: Boolean = true
) {
    /** Real-Time Factor: processing time / audio duration. Lower is better. */
    val realTimeFactor: Float
        get() = if (audioDurationMs > 0) processingTimeMs.toFloat() / audioDurationMs else 0f
}
