package com.itantra.offlinevoice.tts.engine

import com.itantra.offlinevoice.tts.TTSLanguage

/**
 * Metadata about a TTS model for a specific language.
 *
 * This is a read-only snapshot; it does not hold native handles.
 */
data class TTSModelInfo(
    /** Language this model serves. */
    val language: TTSLanguage,

    /** Absolute path to the model directory on disk. */
    val modelPath: String,

    /** Total size of all model files in bytes, or -1 if not yet measured. */
    val modelSizeBytes: Long = -1L,

    /** `true` when the model is currently loaded in RAM and ready for inference. */
    val isLoaded: Boolean = false,

    /** `true` when the model files exist on disk (even if not yet loaded). */
    val isAvailable: Boolean = false,

    /** Human-readable voice name (e.g. "hi_IN-rohan-medium"). */
    val voiceName: String = "",

    /** Output sample rate in Hz. */
    val sampleRate: Int = 22050,

    /** Quantization level of the model on disk. */
    val quantization: Quantization = Quantization.FP32
) {
    /** Pretty-printed model size. */
    val modelSizeDisplay: String
        get() = when {
            modelSizeBytes < 0 -> "unknown"
            modelSizeBytes < 1024 -> "$modelSizeBytes B"
            modelSizeBytes < 1024 * 1024 -> "%.1f KB".format(modelSizeBytes / 1024.0)
            else -> "%.1f MB".format(modelSizeBytes / (1024.0 * 1024.0))
        }
}

/** Quantization options for TTS models. */
enum class Quantization(val label: String) {
    FP32("FP32 (full precision)"),
    FP16("FP16 (half precision)"),
    INT8("INT8 (quantised)")
}
