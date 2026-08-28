package com.itantra.offlinevoice.translation

/**
 * Detects text that is unambiguously written in English.
 *
 * The voice-language setting remains the source of truth for all Indic scripts,
 * but typed text and speech-recognition results can occasionally be English even
 * when that setting has not yet been changed.  In that case we must not label the
 * message as Hindi and skip the English-to-Hindi translation route.
 */
object TextLanguageDetector {

    /**
     * Returns English only when Latin letters make up the whole meaningful input.
     * Returns null for numbers, punctuation, mixed-script content, and Indic text
     * so that the caller keeps the language selected by the user.
     */
    fun detectUnambiguousLanguage(text: String): SupportedLanguage? {
        // Every currently supported script is in the BMP, so iterating chars keeps
        // this safe on Android API 23 without relying on String.codePoints().
        val letters = text.filter { Character.isLetter(it) }

        if (letters.length < MIN_LETTERS_FOR_DETECTION) return null

        return if (letters.all { it.isEnglishAsciiLetter() }) {
            SupportedLanguage.ENGLISH
        } else {
            null
        }
    }

    private fun Char.isEnglishAsciiLetter(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z'

    private const val MIN_LETTERS_FOR_DETECTION = 2
}
