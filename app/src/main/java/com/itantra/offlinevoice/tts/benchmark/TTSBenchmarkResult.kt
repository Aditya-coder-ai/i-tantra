package com.itantra.offlinevoice.tts.benchmark

import com.itantra.offlinevoice.tts.TTSLanguage

/**
 * Structured result of a single TTS benchmark run.
 *
 * All fields are real measurements — nothing is fabricated.
 * Values of -1 indicate "not measured" or "not applicable".
 */
data class TTSBenchmarkResult(
    /** Language tested. */
    val language: TTSLanguage,

    /** Model name / voice identifier. */
    val modelName: String,

    /** Size of the model directory on disk (bytes). */
    val modelSizeBytes: Long = -1L,

    /** Input text used for the benchmark. */
    val inputText: String,

    /** Number of characters in the input text. */
    val textLength: Int,

    /** Number of sentences the text was split into. */
    val sentenceCount: Int = 1,

    /** Total wall-clock time for synthesis (ms). */
    val processingTimeMs: Long,

    /** Duration of the generated audio (ms). */
    val audioDurationMs: Long,

    /** Real-Time Factor: processingTime / audioDuration. */
    val realTimeFactor: Float,

    /** RAM usage before model loading (bytes). */
    val ramBeforeBytes: Long = -1L,

    /** RAM usage after model loading + inference (bytes). */
    val ramAfterBytes: Long = -1L,

    /** Peak CPU usage during inference (%). */
    val cpuUsagePercent: Float = -1f,

    /** Sample rate of generated audio (Hz). */
    val sampleRate: Int = 22050,

    /** Number of PCM samples generated. */
    val totalSamples: Int = 0,

    /** Quantization level of the model used. */
    val quantization: String = "FP32",

    /** Timestamp of the benchmark run. */
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Pretty-printed model size. */
    val modelSizeDisplay: String
        get() = when {
            modelSizeBytes < 0 -> "N/A"
            modelSizeBytes < 1024 -> "$modelSizeBytes B"
            modelSizeBytes < 1024 * 1024 -> "%.1f KB".format(modelSizeBytes / 1024.0)
            else -> "%.1f MB".format(modelSizeBytes / (1024.0 * 1024.0))
        }

    /** RAM delta in MB (negative means measurement unavailable). */
    val ramDeltaMB: Float
        get() = if (ramBeforeBytes >= 0 && ramAfterBytes >= 0) {
            (ramAfterBytes - ramBeforeBytes) / (1024f * 1024f)
        } else -1f

    /** Human-readable summary string. */
    fun summary(): String = buildString {
        appendLine("Language: ${language.displayName} (${language.code})")
        appendLine("Model: $modelName")
        appendLine("Model Size: $modelSizeDisplay")
        appendLine("Text: \"${inputText.take(60)}${if (inputText.length > 60) "…" else ""}\"")
        appendLine("Text Length: $textLength chars, $sentenceCount sentence(s)")
        appendLine("Processing: ${processingTimeMs} ms")
        appendLine("Audio: ${audioDurationMs} ms (${totalSamples} samples @ ${sampleRate}Hz)")
        appendLine("RTF: %.3f".format(realTimeFactor))
        if (ramDeltaMB >= 0) appendLine("RAM Δ: %.1f MB".format(ramDeltaMB))
        if (cpuUsagePercent >= 0) appendLine("CPU: %.1f%%".format(cpuUsagePercent))
        appendLine("Quantization: $quantization")
    }
}
