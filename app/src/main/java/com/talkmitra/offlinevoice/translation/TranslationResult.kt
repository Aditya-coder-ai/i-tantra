package com.talkmitra.offlinevoice.translation

import org.json.JSONObject

/**
 * Output data structure for a completed translation operation.
 * Guaranteed to preserve the original decrypted text and source language untouched.
 */
data class TranslationResult(
    val originalText: String,
    val originalLanguage: SupportedLanguage,
    val translatedText: String,
    val targetLanguage: SupportedLanguage,
    val translationTimeMs: Long,
    val isTranslationRequired: Boolean,
    val translationPath: TranslationPath = if (!isTranslationRequired) TranslationPath.SAME_LANGUAGE_PASSTHROUGH else TranslationPath.DIRECT,
    val modelName: String = "VoiceLink-IndicNMT-v1",
    val confidence: Float = 0.95f,
    val errorMessage: String? = null,
    val intermediateText: String? = null // Set when PIVOT_ENGLISH is used
) {
    val isSuccess: Boolean get() = errorMessage == null
    val hasChangedText: Boolean get() = originalText != translatedText

    fun toJson(): String {
        return JSONObject().apply {
            put("originalText", originalText)
            put("originalLanguage", originalLanguage.code)
            put("translatedText", translatedText)
            put("targetLanguage", targetLanguage.code)
            put("translationTimeMs", translationTimeMs)
            put("isTranslationRequired", isTranslationRequired)
            put("translationPath", translationPath.name)
            put("modelName", modelName)
            put("confidence", confidence.toDouble())
            put("errorMessage", errorMessage)
            put("intermediateText", intermediateText)
        }.toString()
    }

    companion object {
        fun sameLanguagePassthrough(
            text: String,
            language: SupportedLanguage
        ): TranslationResult {
            return TranslationResult(
                originalText = text,
                originalLanguage = language,
                translatedText = text,
                targetLanguage = language,
                translationTimeMs = 0L,
                isTranslationRequired = false,
                translationPath = TranslationPath.SAME_LANGUAGE_PASSTHROUGH,
                modelName = "None (Passthrough)",
                confidence = 1.0f
            )
        }

        fun fallback(
            text: String,
            source: SupportedLanguage,
            target: SupportedLanguage,
            reason: String
        ): TranslationResult {
            return TranslationResult(
                originalText = text,
                originalLanguage = source,
                translatedText = text, // Preserves original text on fallback
                targetLanguage = target,
                translationTimeMs = 0L,
                isTranslationRequired = true,
                translationPath = TranslationPath.FALLBACK_ORIGINAL,
                modelName = "None (Fallback)",
                confidence = 0.0f,
                errorMessage = reason
            )
        }
    }
}
