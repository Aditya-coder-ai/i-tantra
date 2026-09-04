package com.talkmitra.offlinevoice.tts.engine

import android.content.Context
import android.util.Log
import com.talkmitra.offlinevoice.tts.TTSConfig
import com.talkmitra.offlinevoice.tts.TTSException
import com.talkmitra.offlinevoice.tts.TTSLanguage
import java.io.File
import java.util.LinkedHashMap

/**
 * Manages TTS model files on disk and their in-memory lifecycle.
 *
 * Models are stored under:
 * ```
 * context.filesDir/models/tts/{language_code}/
 *   ├── model.onnx          (VITS/Piper ONNX model)
 *   ├── tokens.txt           (token vocabulary)
 *   └── espeak-ng-data/      (phonemiser data, shared or per-language)
 * ```
 *
 * Loaded [TTSInference] instances are kept in an LRU cache limited by
 * [TTSConfig.maxCachedModels]. The least-recently-used model is evicted
 * when the limit is exceeded.
 */
class TTSModelManager(
    private val context: Context,
    private val config: TTSConfig = TTSConfig()
) {
    companion object {
        private const val TAG = "TTSModelMgr"
        private const val MODELS_ROOT = "models/tts"
    }

    /**
     * LRU cache of loaded inference handles.
     * Access order = `true` so that the *least* recently accessed entry
     * is always at the head of the iteration order (first to be evicted).
     */
    private val loadedModels: LinkedHashMap<TTSLanguage, TTSInference> =
        object : LinkedHashMap<TTSLanguage, TTSInference>(
            config.maxCachedModels + 1, 0.75f, /* accessOrder = */ true
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TTSLanguage, TTSInference>?): Boolean {
                if (size > config.maxCachedModels) {
                    eldest?.let {
                        Log.i(TAG, "LRU evicting model for ${it.key.displayName}")
                        it.value.release()
                    }
                    return true
                }
                return false
            }
        }

    // ── Path helpers ─────────────────────────────────────────────────

    /** Root directory for all TTS models. */
    fun getModelsRoot(): File = File(context.filesDir, MODELS_ROOT)

    /** Directory for a specific language's model files. */
    fun getModelDir(language: TTSLanguage): File =
        File(getModelsRoot(), language.code)

    /** Path to the ONNX model file. */
    fun getModelPath(language: TTSLanguage): String =
        File(getModelDir(language), "model.onnx").absolutePath

    /** Path to the tokens file. */
    fun getTokensPath(language: TTSLanguage): String =
        File(getModelDir(language), "tokens.txt").absolutePath

    /**
     * Path to `espeak-ng-data`.
     * Piper models use eSpeak-NG for grapheme-to-phoneme conversion.
     * The data directory can be shared across languages.
     */
    fun getEspeakDataPath(language: TTSLanguage): String {
        // First check for a language-specific copy
        val langSpecific = File(getModelDir(language), "espeak-ng-data")
        if (langSpecific.exists()) return langSpecific.absolutePath

        // Fall back to a shared directory at the models root
        val shared = File(getModelsRoot(), "espeak-ng-data")
        return shared.absolutePath
    }

    // ── Availability ─────────────────────────────────────────────────

    /** Returns `true` if the required model files exist on disk. */
    fun isModelAvailable(language: TTSLanguage): Boolean {
        val dir = getModelDir(language)
        if (!dir.exists() || !dir.isDirectory) return false

        val hasModel = File(dir, "model.onnx").exists()
        val hasTokens = File(dir, "tokens.txt").exists()
        val hasEspeak = File(getEspeakDataPath(language)).exists()

        return hasModel && hasTokens && hasEspeak
    }

    /** Returns `true` if the model for [language] is currently loaded in RAM. */
    fun isModelLoaded(language: TTSLanguage): Boolean =
        loadedModels.containsKey(language)

    // ── Load / Unload ────────────────────────────────────────────────

    /**
     * Loads the model for [language] into RAM.
     *
     * If the model is already loaded, the cached instance is returned.
     * If the LRU limit is exceeded, the oldest model is automatically evicted.
     *
     * @throws TTSException.ModelNotFoundException if files are missing.
     * @throws TTSException.InitializationException if native init fails.
     */
    @Synchronized
    fun loadModel(language: TTSLanguage): TTSInference {
        // Return cached instance if available (also bumps LRU order)
        loadedModels[language]?.let { return it }

        if (!isModelAvailable(language)) {
            throw TTSException.ModelNotFoundException(language)
        }

        Log.i(TAG, "Loading TTS model for ${language.displayName}…")

        val inference = try {
            TTSInference(
                modelPath = getModelPath(language),
                tokensPath = getTokensPath(language),
                espeakDataPath = getEspeakDataPath(language),
                language = language,
                config = config
            )
        } catch (e: OutOfMemoryError) {
            throw TTSException.OutOfMemoryException(
                "Out of memory loading model for ${language.displayName}", e
            )
        } catch (e: Exception) {
            throw TTSException.InitializationException(
                "Failed to load model for ${language.displayName}: ${e.message}", e
            )
        }

        // Put into cache (may trigger LRU eviction)
        loadedModels[language] = inference
        Log.i(TAG, "Model for ${language.displayName} loaded (cache size: ${loadedModels.size})")
        return inference
    }

    /** Explicitly unloads a single language model. */
    @Synchronized
    fun unloadModel(language: TTSLanguage) {
        loadedModels.remove(language)?.let {
            it.release()
            Log.i(TAG, "Unloaded model for ${language.displayName}")
        }
    }

    /** Unloads all models and clears the cache. */
    @Synchronized
    fun releaseAll() {
        loadedModels.values.forEach { it.release() }
        loadedModels.clear()
        Log.i(TAG, "All TTS models released")
    }

    // ── Info ──────────────────────────────────────────────────────────

    /** Returns metadata about the model for [language]. */
    fun getModelInfo(language: TTSLanguage): TTSModelInfo {
        val dir = getModelDir(language)
        val modelFile = File(dir, "model.onnx")
        val available = isModelAvailable(language)
        val sizeBytes = if (modelFile.exists()) {
            calculateDirSize(dir)
        } else {
            -1L
        }

        return TTSModelInfo(
            language = language,
            modelPath = dir.absolutePath,
            modelSizeBytes = sizeBytes,
            isLoaded = isModelLoaded(language),
            isAvailable = available,
            voiceName = detectVoiceName(dir),
            sampleRate = language.sampleRate
        )
    }

    /** Returns metadata for all 10 languages. */
    fun getAllModelInfo(): List<TTSModelInfo> =
        TTSLanguage.values().map { getModelInfo(it) }

    // ── Internals ────────────────────────────────────────────────────

    /** Recursively computes total byte size of a directory. */
    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Tries to read a voice name from the model directory. */
    private fun detectVoiceName(dir: File): String {
        // Look for a MODEL_CARD or config.json with voice metadata
        val configFile = File(dir, "config.json")
        if (configFile.exists()) {
            try {
                val text = configFile.readText()
                // Simple extraction — not a full JSON parser
                val nameMatch = Regex("\"voice\"\\s*:\\s*\"([^\"]+)\"").find(text)
                if (nameMatch != null) return nameMatch.groupValues[1]
            } catch (_: Exception) { /* ignore */ }
        }
        // Fallback: use the directory name
        return dir.name
    }
}
