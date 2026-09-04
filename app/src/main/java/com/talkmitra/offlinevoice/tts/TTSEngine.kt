package com.talkmitra.offlinevoice.tts

import com.talkmitra.offlinevoice.tts.engine.TTSModelInfo
import kotlinx.coroutines.flow.Flow

/**
 * Generic interface for an offline Text-to-Speech engine.
 *
 * The rest of the application must depend **only** on this interface,
 * never on the concrete Sherpa-ONNX/Piper implementation directly.
 * This allows the underlying TTS model/runtime to be swapped without
 * touching any other module.
 *
 * Thread-safety: implementations must be safe for concurrent calls.
 */
interface TTSEngine {

    // ── Lifecycle ────────────────────────────────────────────────────

    /**
     * Initialises the engine with the given [config].
     * Must be called before any synthesis.
     * @return `true` if the engine is ready.
     */
    fun initialize(config: TTSConfig = TTSConfig()): Boolean

    /**
     * Releases **all** loaded models and native resources.
     * The engine may be re-initialised after this call.
     */
    fun release()

    /** Returns `true` when [initialize] has completed successfully. */
    fun isReady(): Boolean

    // ── Synthesis ────────────────────────────────────────────────────

    /**
     * Synchronously synthesises [text] in the given [language].
     *
     * For multi-sentence text, the engine may internally split into
     * sentences but returns a single concatenated [TTSResult].
     *
     * @throws TTSException on failure.
     */
    fun synthesize(text: String, language: TTSLanguage): TTSResult

    /**
     * Asynchronously synthesises [text] sentence-by-sentence.
     *
     * Each emitted [TTSResult] represents one sentence's audio,
     * enabling progressive playback while later sentences are still
     * being processed.
     */
    fun synthesizeAsync(text: String, language: TTSLanguage): Flow<TTSResult>

    /**
     * Convenience: synthesise **and** play through the built-in audio player.
     * Blocks until playback completes or is interrupted.
     */
    suspend fun speak(text: String, language: TTSLanguage)

    // ── Playback control ─────────────────────────────────────────────

    /** Stops synthesis and playback immediately. */
    fun stop()

    /** Pauses ongoing playback (resumes from the same position). */
    fun pause()

    /** Resumes previously paused playback. */
    fun resume()

    // ── Query ────────────────────────────────────────────────────────

    /** Returns the list of languages for which a model is available on disk. */
    fun getSupportedLanguages(): List<TTSLanguage>

    /** Returns `true` if the model for [language] is loaded in RAM. */
    fun isModelLoaded(language: TTSLanguage): Boolean

    /** Returns metadata about the voice used for [language]. */
    fun getVoiceInfo(language: TTSLanguage): TTSModelInfo?

    /** Returns metadata about every known model (loaded or on-disk). */
    fun getModelInfo(): List<TTSModelInfo>

    /** Returns the current engine configuration. */
    fun getConfig(): TTSConfig
}
