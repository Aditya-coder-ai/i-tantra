package com.talkmitra.offlinevoice.tts

/**
 * Runtime configuration for the TTS engine.
 *
 * All fields have sensible defaults; callers may copy-and-tweak as needed.
 */
data class TTSConfig(
    /** Speech rate multiplier (0.5 = half speed, 2.0 = double speed). */
    val speechRate: Float = 1.0f,

    /** Pitch multiplier (0.5 = lower, 2.0 = higher). */
    val pitch: Float = 1.0f,

    /** Playback volume (0.0 = silent, 1.0 = full). */
    val volume: Float = 1.0f,

    /**
     * When true the engine attempts sentence-level streaming:
     * each sentence is synthesised and played independently so
     * the user hears audio before the full text is processed.
     */
    val enableStreaming: Boolean = true,

    /**
     * Maximum number of language models held in RAM simultaneously.
     * The least-recently-used model is evicted when the limit is exceeded.
     */
    val maxCachedModels: Int = 2,

    /** How many times an emergency message is repeated. */
    val emergencyRepeatCount: Int = 2,

    /** Silence gap between consecutive sentences (ms). */
    val sentencePauseMs: Long = 300L,

    /** Maximum characters per TTS request. Longer texts are chunked. */
    val maxTextLength: Int = 5000,

    /**
     * Number of threads used by the ONNX Runtime for inference.
     * 0 = let the runtime decide (usually = available cores).
     */
    val numThreads: Int = 2
)
