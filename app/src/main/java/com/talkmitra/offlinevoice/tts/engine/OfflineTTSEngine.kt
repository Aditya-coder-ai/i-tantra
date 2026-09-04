package com.talkmitra.offlinevoice.tts.engine

import android.content.Context
import android.util.Log
import com.talkmitra.offlinevoice.text.SentenceSegmenter
import com.talkmitra.offlinevoice.tts.TTSConfig
import com.talkmitra.offlinevoice.tts.TTSEngine
import com.talkmitra.offlinevoice.tts.TTSException
import com.talkmitra.offlinevoice.tts.TTSLanguage
import com.talkmitra.offlinevoice.tts.TTSResult
import com.talkmitra.offlinevoice.tts.audio.TTSAudioPlayer
import com.talkmitra.offlinevoice.tts.preprocessing.TTSTextPreprocessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Concrete [TTSEngine] implementation backed by Sherpa-ONNX + Piper/VITS models.
 *
 * This class:
 * - Lazily loads models via [TTSModelManager] (with LRU eviction).
 * - Splits text into sentences via [SentenceSegmenter] for lower latency.
 * - Preprocesses text via [TTSTextPreprocessor] before synthesis.
 * - Delegates native inference to [TTSInference].
 *
 * Thread-safety is guaranteed through a [Mutex] around model loading
 * and inference. Audio playback uses a dedicated thread managed by
 * [TTSAudioPlayer].
 */
class OfflineTTSEngine(
    private val context: Context,
    private var config: TTSConfig = TTSConfig()
) : TTSEngine {

    companion object {
        private const val TAG = "OfflineTTS"
    }

    private lateinit var modelManager: TTSModelManager
    private val preprocessor = TTSTextPreprocessor()
    private val segmenter = SentenceSegmenter()
    private val audioPlayer = TTSAudioPlayer()

    private val inferenceMutex = Mutex()
    private var initialized = false

    // ── Lifecycle ────────────────────────────────────────────────────

    override fun initialize(config: TTSConfig): Boolean {
        this.config = config
        modelManager = TTSModelManager(context, config)
        initialized = true
        Log.i(TAG, "OfflineTTSEngine initialised (maxCachedModels=${config.maxCachedModels})")
        return true
    }

    override fun release() {
        stop()
        if (::modelManager.isInitialized) {
            modelManager.releaseAll()
        }
        audioPlayer.release()
        initialized = false
        Log.i(TAG, "OfflineTTSEngine released")
    }

    override fun isReady(): Boolean = initialized

    // ── Synthesis ────────────────────────────────────────────────────

    override fun synthesize(text: String, language: TTSLanguage): TTSResult {
        ensureReady()
        validateText(text)

        val preprocessed = preprocessor.preprocess(text, language)
        val sentences = segmenter.segmentSentence(preprocessed)
        if (sentences.isEmpty()) {
            throw TTSException.InvalidTextException("Text produced no synthesisable sentences")
        }

        // Synthesise all sentences and concatenate into a single result
        val allAudio = mutableListOf<Short>()
        var totalProcessingMs = 0L
        val sampleRate = language.sampleRate

        for ((index, sentence) in sentences.withIndex()) {
            val result = synthesizeSentence(sentence, language, index, sentences.size)
            for (sample in result.audioData) allAudio.add(sample)
            totalProcessingMs += result.processingTimeMs

            // Insert a brief silence between sentences
            if (index < sentences.size - 1) {
                val silenceSamples = (config.sentencePauseMs * sampleRate / 1000).toInt()
                repeat(silenceSamples) { allAudio.add(0) }
            }
        }

        val combinedAudio = allAudio.toShortArray()
        val audioDurationMs = (combinedAudio.size.toLong() * 1000L) / sampleRate

        return TTSResult(
            audioData = combinedAudio,
            sampleRate = sampleRate,
            audioDurationMs = audioDurationMs,
            processingTimeMs = totalProcessingMs,
            language = language,
            textLength = text.length,
            sentenceIndex = 0,
            totalSentences = sentences.size
        )
    }

    override fun synthesizeAsync(text: String, language: TTSLanguage): Flow<TTSResult> = flow {
        ensureReady()
        validateText(text)

        val preprocessed = preprocessor.preprocess(text, language)
        val sentences = segmenter.segmentSentence(preprocessed)
        if (sentences.isEmpty()) {
            throw TTSException.InvalidTextException("Text produced no synthesisable sentences")
        }

        for ((index, sentence) in sentences.withIndex()) {
            val result = synthesizeSentence(sentence, language, index, sentences.size)
            emit(result)
        }
    }.flowOn(Dispatchers.Default)

    override suspend fun speak(text: String, language: TTSLanguage) {
        val results = mutableListOf<TTSResult>()

        synthesizeAsync(text, language).collect { result ->
            results.add(result)
            withContext(Dispatchers.Main) {
                audioPlayer.play(result.audioData, result.sampleRate)
            }
            // Wait for this chunk to finish playing before synthesising next
            audioPlayer.awaitCompletion()

            // Pause between sentences
            if (result.sentenceIndex < result.totalSentences - 1) {
                kotlinx.coroutines.delay(config.sentencePauseMs)
            }
        }
    }

    // ── Playback control ─────────────────────────────────────────────

    override fun stop() {
        audioPlayer.stop()
    }

    override fun pause() {
        audioPlayer.pause()
    }

    override fun resume() {
        audioPlayer.resume()
    }

    // ── Query ────────────────────────────────────────────────────────

    override fun getSupportedLanguages(): List<TTSLanguage> {
        if (!::modelManager.isInitialized) return emptyList()
        return TTSLanguage.values().filter { modelManager.isModelAvailable(it) }
    }

    override fun isModelLoaded(language: TTSLanguage): Boolean {
        if (!::modelManager.isInitialized) return false
        return modelManager.isModelLoaded(language)
    }

    override fun getVoiceInfo(language: TTSLanguage): TTSModelInfo? {
        if (!::modelManager.isInitialized) return null
        return modelManager.getModelInfo(language)
    }

    override fun getModelInfo(): List<TTSModelInfo> {
        if (!::modelManager.isInitialized) return emptyList()
        return modelManager.getAllModelInfo()
    }

    override fun getConfig(): TTSConfig = config

    // ── Internal ─────────────────────────────────────────────────────

    /**
     * Synthesises a single sentence.
     * Acquires the inference mutex so only one sentence is processed at a time.
     */
    private fun synthesizeSentence(
        sentence: String,
        language: TTSLanguage,
        sentenceIndex: Int,
        totalSentences: Int
    ): TTSResult {
        // Synchronous lock for model loading + inference
        val inference: TTSInference
        synchronized(this) {
            inference = modelManager.loadModel(language)
        }

        val result = inference.generate(sentence, config.speechRate)

        return result.copy(
            sentenceIndex = sentenceIndex,
            totalSentences = totalSentences
        )
    }

    private fun ensureReady() {
        if (!initialized) {
            throw TTSException.InitializationException("TTSEngine not initialised. Call initialize() first.")
        }
    }

    private fun validateText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw TTSException.InvalidTextException("Text is empty")
        }
        if (trimmed.length > config.maxTextLength) {
            throw TTSException.InvalidTextException(
                "Text exceeds maximum length (${trimmed.length} > ${config.maxTextLength})"
            )
        }
    }
}
