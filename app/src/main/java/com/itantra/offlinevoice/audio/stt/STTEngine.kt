package com.itantra.offlinevoice.audio.stt

/**
 * Interface for an offline Speech-to-Text engine.
 * Allows switching underlying implementations (Sherpa, Vosk, TFLite) without logic changes.
 */
interface STTEngine {
    /**
     * Initializes the engine for a specific language.
     * Should be called on a background thread.
     */
    fun initialize(language: STTLanguage): Boolean

    /**
     * Transcribes a complete segment of 16kHz PCM audio.
     */
    fun transcribe(samples: ShortArray): STTResult

    /**
     * Releases model resources from RAM.
     */
    fun release()

    /**
     * Returns true if the model for the given language is downloaded and ready.
     */
    fun isModelAvailable(language: STTLanguage): Boolean
}
