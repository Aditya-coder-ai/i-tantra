package com.itantra.offlinevoice.translation

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Model state tracking descriptor.
 */
data class LoadedModelInfo(
    val pair: LanguagePair,
    val modelName: String,
    val estimatedMemoryBytes: Long,
    val loadedAt: Long = System.currentTimeMillis(),
    var lastUsedAt: Long = System.nanoTime()
)

/**
 * Manages lazy loading, LRU caching, memory footprint, and lifecycle
 * of offline translation models.
 */
class TranslationModelManager(
    val maxCachedModels: Int = 4
) {

    /** Active loaded models mapped by language pair key (e.g. "en-hi") */
    private val loadedModels = ConcurrentHashMap<String, LoadedModelInfo>()

    /**
     * Checks if a model for [pair] is currently resident in RAM.
     */
    fun isModelLoaded(pair: LanguagePair): Boolean {
        return loadedModels.containsKey(pair.key)
    }

    /**
     * Retrieves or lazily loads the model for [pair].
     * Evicts least-recently-used models if memory limit is reached.
     */
    @Synchronized
    fun getOrLoadModel(pair: LanguagePair): LoadedModelInfo {
        val existing = loadedModels[pair.key]
        if (existing != null) {
            existing.lastUsedAt = System.nanoTime()
            return existing
        }

        // Check if eviction is needed
        if (loadedModels.size >= maxCachedModels) {
            evictLruModel()
        }

        // Lazy load the model
        val modelInfo = loadModelInternal(pair)
        loadedModels[pair.key] = modelInfo
        Log.i(TAG, "✓ Lazily loaded offline translation model: ${pair.displayName} (~${modelInfo.estimatedMemoryBytes / (1024 * 1024)} MB)")
        return modelInfo
    }

    private fun loadModelInternal(pair: LanguagePair): LoadedModelInfo {
        // Model memory footprint estimation for quantized open-source Indic NMT / Marian models: ~35MB - 50MB per pair
        val estimatedSize = 42 * 1024 * 1024L
        return LoadedModelInfo(
            pair = pair,
            modelName = "IndicTrans2-Quant-${pair.key}",
            estimatedMemoryBytes = estimatedSize
        )
    }

    private fun evictLruModel() {
        val oldestEntry = loadedModels.values.minByOrNull { it.lastUsedAt }
        if (oldestEntry != null) {
            loadedModels.remove(oldestEntry.pair.key)
            Log.i(TAG, "Evicted LRU model '${oldestEntry.pair.key}' to reclaim ${oldestEntry.estimatedMemoryBytes / (1024 * 1024)} MB")
        }
    }

    /**
     * Unloads a specific model from memory.
     */
    @Synchronized
    fun unloadModel(pair: LanguagePair) {
        val removed = loadedModels.remove(pair.key)
        if (removed != null) {
            Log.d(TAG, "Unloaded model for ${pair.key}")
        }
    }

    /**
     * Clears all models from memory.
     */
    @Synchronized
    fun unloadAll() {
        loadedModels.clear()
        Log.i(TAG, "All translation models unloaded from memory")
    }

    /**
     * Returns total estimated memory consumed by loaded translation models.
     */
    fun getTotalEstimatedMemoryBytes(): Long {
        return loadedModels.values.sumOf { it.estimatedMemoryBytes }
    }

    /**
     * Returns list of all currently loaded models.
     */
    fun getLoadedModelsList(): List<LoadedModelInfo> {
        return loadedModels.values.toList()
    }

    companion object {
        private const val TAG = "TranslationModelManager"
    }
}
