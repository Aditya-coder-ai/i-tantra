package com.itantra.offlinevoice.tts

/**
 * Result of a single TTS synthesis operation.
 *
 * Contains raw PCM audio data and all associated performance metrics.
 * When sentence-level streaming is used, each sentence produces its own [TTSResult].
 */
data class TTSResult(
    /** Raw PCM 16-bit mono audio samples. */
    val audioData: ShortArray,

    /** Sample rate of [audioData] in Hz (typically 22 050 for Piper models). */
    val sampleRate: Int,

    /** Duration of the generated audio in milliseconds. */
    val audioDurationMs: Long,

    /** Wall-clock time spent on synthesis in milliseconds. */
    val processingTimeMs: Long,

    /** Language used for synthesis. */
    val language: TTSLanguage,

    /** Length of the input text (characters). */
    val textLength: Int,

    /** 0-based index of this sentence within a multi-sentence message. */
    val sentenceIndex: Int = 0,

    /** Total number of sentences in the originating message. */
    val totalSentences: Int = 1
) {
    /**
     * Real-Time Factor: `processingTime / audioDuration`.
     * A value < 1.0 means synthesis is faster than real-time.
     */
    val realTimeFactor: Float
        get() = if (audioDurationMs > 0) processingTimeMs.toFloat() / audioDurationMs else 0f

    // ShortArray does not participate in data-class equals/hashCode by default.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TTSResult) return false
        return audioData.contentEquals(other.audioData) &&
                sampleRate == other.sampleRate &&
                audioDurationMs == other.audioDurationMs &&
                processingTimeMs == other.processingTimeMs &&
                language == other.language &&
                textLength == other.textLength &&
                sentenceIndex == other.sentenceIndex &&
                totalSentences == other.totalSentences
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + audioDurationMs.hashCode()
        result = 31 * result + processingTimeMs.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + textLength
        result = 31 * result + sentenceIndex
        result = 31 * result + totalSentences
        return result
    }
}
