package com.itantra.offlinevoice.translation

/**
 * Configuration options for the offline translation subsystem.
 */
data class TranslationConfig(
    val isAutoTranslateEnabled: Boolean = true,
    val preferredListeningLanguage: SupportedLanguage = SupportedLanguage.HINDI,
    val speakingLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
    val enablePivotRouting: Boolean = true,
    val maxCachedModels: Int = 4,
    val translationTimeoutMs: Long = 5000L,
    val maxQueueCapacity: Int = 100,
    val emergencyPriorityBoost: Boolean = true
)
