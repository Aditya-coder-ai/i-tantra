package com.itantra.offlinevoice.tts.preprocessing

import com.itantra.offlinevoice.tts.TTSLanguage

/**
 * Lightweight text preprocessing specifically for TTS synthesis.
 *
 * Rules:
 * - Normalise whitespace (collapse multiple spaces/newlines, trim).
 * - Strip control characters that would confuse the TTS model.
 * - Expand common abbreviations for English.
 * - Preserve Indic scripts exactly (no transliteration!).
 * - Ensure sentence-ending punctuation for better prosody.
 * - Handle digits in simple contexts.
 *
 * This preprocessor intentionally does NOT:
 * - Translate text between languages.
 * - Rewrite the meaning of the message.
 * - Use an LLM or any heavy-weight NLP.
 */
class TTSTextPreprocessor {

    /**
     * Preprocesses [text] for synthesis in [language].
     * Returns TTS-ready text.
     */
    fun preprocess(text: String, language: TTSLanguage): String {
        var result = text

        // Step 1: Strip control characters (keep Unicode letters, digits, punctuation, whitespace)
        result = stripControlChars(result)

        // Step 2: Normalise whitespace
        result = normalizeWhitespace(result)

        // Step 3: Language-specific preprocessing
        result = when (language) {
            TTSLanguage.ENGLISH -> preprocessEnglish(result)
            else -> preprocessIndic(result, language)
        }

        // Step 4: Ensure trailing punctuation for prosody
        result = ensureTrailingPunctuation(result, language)

        return result.trim()
    }

    // ── Whitespace normalisation ─────────────────────────────────────

    private fun normalizeWhitespace(text: String): String {
        return text
            .replace(Regex("\\r\\n|\\r"), "\n")       // Normalise line endings
            .replace(Regex("[ \\t]+"), " ")             // Collapse horizontal whitespace
            .replace(Regex("\\n{3,}"), "\n\n")          // Collapse excessive newlines
            .trim()
    }

    // ── Control character removal ────────────────────────────────────

    private fun stripControlChars(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                // Keep newlines, tabs, and regular whitespace
                ch == '\n' || ch == '\r' || ch == '\t' || ch == ' ' -> sb.append(ch)
                // Remove other control characters (U+0000–U+001F, U+007F–U+009F)
                ch.isISOControl() -> { /* skip */ }
                // Keep everything else (letters, digits, punctuation, Indic scripts)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    // ── English-specific preprocessing ───────────────────────────────

    private fun preprocessEnglish(text: String): String {
        var result = text

        // Expand common abbreviations
        ENGLISH_ABBREVIATIONS.forEach { (abbrev, expansion) ->
            val pattern = if (abbrev.endsWith('.')) {
                "\\b${Regex.escape(abbrev)}(?=\\s|$)"
            } else {
                "\\b${Regex.escape(abbrev)}\\b"
            }
            result = result.replace(
                Regex(pattern, RegexOption.IGNORE_CASE),
                expansion
            )
        }

        return result
    }

    // ── Indic script preprocessing ───────────────────────────────────

    private fun preprocessIndic(text: String, language: TTSLanguage): String {
        var result = text

        // Replace any Latin-script abbreviations that might appear in mixed text
        ENGLISH_ABBREVIATIONS.forEach { (abbrev, expansion) ->
            val pattern = if (abbrev.endsWith('.')) {
                "\\b${Regex.escape(abbrev)}(?=\\s|$)"
            } else {
                "\\b${Regex.escape(abbrev)}\\b"
            }
            result = result.replace(
                Regex(pattern, RegexOption.IGNORE_CASE),
                expansion
            )
        }

        // Normalise Devanagari punctuation (purna viram variants)
        result = result.replace('\u0965', '\u0964') // ॥ → ।

        return result
    }

    // ── Punctuation helpers ──────────────────────────────────────────

    /**
     * Ensures the text ends with appropriate punctuation.
     * TTS models generally produce better prosody when sentences
     * are properly terminated.
     */
    private fun ensureTrailingPunctuation(text: String, language: TTSLanguage): String {
        if (text.isEmpty()) return text

        val lastChar = text.last()

        // Already has sentence-ending punctuation
        if (lastChar in SENTENCE_ENDERS) return text

        // Add language-appropriate full stop
        return when (language) {
            TTSLanguage.HINDI, TTSLanguage.MARATHI -> "$text।"
            TTSLanguage.ODIA -> "$text।"
            TTSLanguage.GUJARATI -> "$text."
            TTSLanguage.BENGALI -> "$text।"
            TTSLanguage.KANNADA, TTSLanguage.TELUGU,
            TTSLanguage.MALAYALAM, TTSLanguage.TAMIL -> "$text."
            TTSLanguage.ENGLISH -> "$text."
        }
    }

    companion object {
        /** Characters that count as sentence-ending punctuation. */
        private val SENTENCE_ENDERS = setOf(
            '.', '!', '?', '।', '॥',
            '\u0964', // Devanagari Danda
            '\u0965'  // Devanagari Double Danda
        )

        /**
         * Common abbreviations expanded for clearer TTS pronunciation.
         * Only applied for English text and Latin-script fragments in Indic text.
         */
        private val ENGLISH_ABBREVIATIONS = mapOf(
            "Mr." to "Mister",
            "Mrs." to "Misses",
            "Ms." to "Miss",
            "Dr." to "Doctor",
            "Prof." to "Professor",
            "Sr." to "Senior",
            "Jr." to "Junior",
            "St." to "Saint",
            "Ave." to "Avenue",
            "Blvd." to "Boulevard",
            "Dept." to "Department",
            "Govt." to "Government",
            "govt." to "government",
            "approx." to "approximately",
            "etc." to "etcetera",
            "vs." to "versus",
            "No." to "Number",
            "no." to "number",
            "km" to "kilometres",
            "kg" to "kilograms"
        )
    }
}
