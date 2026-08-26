package com.itantra.offlinevoice.text

/**
 * Maps language identifiers to ISO 639-1 codes for the 10 supported VoiceLink languages.
 * Unsupported language inputs return an explicit fallback status, never a silent guess.
 *
 * No translation is performed anywhere in this class.
 */
object LanguageNormalizer {

    /** The 10 supported ISO 639-1 codes. */
    val SUPPORTED_CODES: Set<String> = setOf("hi", "gu", "mr", "kn", "ml", "ta", "te", "or", "bn", "en")

    private val DISPLAY_TO_CODE = mapOf(
        // English display names (case-insensitive via lookup)
        "hindi" to "hi",
        "gujarati" to "gu",
        "marathi" to "mr",
        "kannada" to "kn",
        "malayalam" to "ml",
        "tamil" to "ta",
        "telugu" to "te",
        "odia" to "or",
        "oriya" to "or",
        "bengali" to "bn",
        "bangla" to "bn",
        "english" to "en",

        // Native names
        "हिन्दी" to "hi",
        "हिंदी" to "hi",
        "ગુજરાતી" to "gu",
        "मराठी" to "mr",
        "ಕನ್ನಡ" to "kn",
        "മലയാളം" to "ml",
        "தமிழ்" to "ta",
        "తెలుగు" to "te",
        "ଓଡ଼ିଆ" to "or",
        "বাংলা" to "bn",
    )

    data class NormalizationResult(
        val code: String,
        val isSupported: Boolean
    )

    /**
     * Normalizes a language identifier to its ISO 639-1 code.
     *
     * Accepts:
     * - ISO codes directly (e.g. "hi", "en")
     * - Display names (e.g. "Hindi", "TAMIL")
     * - Native names (e.g. "हिन्दी", "বাংলা")
     *
     * @return [NormalizationResult] with `isSupported = false` if the language is unknown.
     *         The `code` will be the original input trimmed/lowercased — not a guess.
     */
    fun normalizeLanguage(input: String): NormalizationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return NormalizationResult("unknown", isSupported = false)

        val lower = trimmed.lowercase()

        // Direct ISO code match
        if (lower in SUPPORTED_CODES) return NormalizationResult(lower, isSupported = true)

        // Display name or native name match
        DISPLAY_TO_CODE[lower]?.let { return NormalizationResult(it, isSupported = true) }

        // Unsupported language — explicit fallback, not a silent guess
        return NormalizationResult(lower, isSupported = false)
    }
}
