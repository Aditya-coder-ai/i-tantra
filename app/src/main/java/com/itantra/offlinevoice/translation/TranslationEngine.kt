package com.itantra.offlinevoice.translation

import kotlinx.coroutines.flow.StateFlow

/**
 * Core interface for the offline machine translation engine.
 * Keeps the rest of the application decoupled from the underlying translation runtime or models.
 */
interface TranslationEngine {

    /**
     * Whether the translation engine is initialized and ready for inference.
     */
    val isReady: StateFlow<Boolean>

    /**
     * Telemetry metrics for translation requests.
     */
    val metrics: TranslationMetrics

    /**
     * Initializes runtime resources and verifies local model files.
     */
    suspend fun initialize(): Boolean

    /**
     * Synchronously/suspendingly translates [text] from [sourceLanguage] to [targetLanguage].
     *
     * @param text The input text to translate.
     * @param sourceLanguage The source language.
     * @param targetLanguage The desired target language.
     * @param isEmergency Whether this translation has emergency priority.
     * @return Fully populated [TranslationResult].
     */
    suspend fun translate(
        text: String,
        sourceLanguage: SupportedLanguage,
        targetLanguage: SupportedLanguage,
        isEmergency: Boolean = false
    ): TranslationResult

    /**
     * Translates a raw language code pair.
     */
    suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        isEmergency: Boolean = false
    ): TranslationResult = translate(
        text = text,
        sourceLanguage = SupportedLanguage.fromCode(sourceCode),
        targetLanguage = SupportedLanguage.fromCode(targetCode),
        isEmergency = isEmergency
    )

    /**
     * Checks if a given language pair is supported by direct model or pivot routing.
     */
    fun isLanguagePairSupported(source: SupportedLanguage, target: SupportedLanguage): Boolean

    /**
     * Returns all supported target languages from a given source language.
     */
    fun getSupportedTargetLanguages(source: SupportedLanguage): List<SupportedLanguage>

    /**
     * Returns all available language pairs.
     */
    fun getSupportedLanguagePairs(): List<LanguagePair>

    /**
     * Returns human-readable model info (name, version, memory footprint).
     */
    fun getModelInfo(): Map<String, String>

    /**
     * Releases cached models and runtime resources.
     */
    fun release()
}
