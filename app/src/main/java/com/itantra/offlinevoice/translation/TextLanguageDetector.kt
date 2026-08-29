package com.itantra.offlinevoice.translation

/**
 * Detects text written in a supported, unambiguous script.
 *
 * The voice-language setting remains the source of truth for all Indic scripts,
 * but typed text and speech-recognition results can occasionally use a different
 * script. In that case we must not label the message with a stale setting and
 * skip the translation route.
 */
object TextLanguageDetector {

    /**
     * Returns a language only when every meaningful letter belongs to the same,
     * uniquely identifiable script. Devanagari deliberately returns null because
     * it is shared by Hindi and Marathi, so the user's selected source language
     * remains the source of truth for that script.
     */
    fun detectUnambiguousLanguage(text: String): SupportedLanguage? {
        // Every currently supported script is in the BMP, so iterating chars keeps
        // this safe on Android API 23 without relying on String.codePoints().
        val letters = text.filter { Character.isLetter(it) }

        if (letters.length < MIN_LETTERS_FOR_DETECTION) return null

        val detectedLanguages = letters.map(::languageForChar).toSet()
        return detectedLanguages.singleOrNull()
    }

    private fun languageForChar(char: Char): SupportedLanguage? = when (char) {
        in 'A'..'Z', in 'a'..'z' -> SupportedLanguage.ENGLISH
        in '\u0980'..'\u09FF' -> SupportedLanguage.BENGALI
        in '\u0A80'..'\u0AFF' -> SupportedLanguage.GUJARATI
        in '\u0B00'..'\u0B7F' -> SupportedLanguage.ODIA
        in '\u0B80'..'\u0BFF' -> SupportedLanguage.TAMIL
        in '\u0C00'..'\u0C7F' -> SupportedLanguage.TELUGU
        in '\u0C80'..'\u0CFF' -> SupportedLanguage.KANNADA
        in '\u0D00'..'\u0D7F' -> SupportedLanguage.MALAYALAM
        else -> null
    }

    private const val MIN_LETTERS_FOR_DETECTION = 2
}
