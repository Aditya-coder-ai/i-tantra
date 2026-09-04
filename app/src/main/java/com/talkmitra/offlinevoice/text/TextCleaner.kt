package com.talkmitra.offlinevoice.text

import java.text.Normalizer

/**
 * Normalizes raw STT text: trims, collapses whitespace, normalizes punctuation and Unicode.
 * Never rewrites meaning, translates, or performs grammar correction.
 *
 * All operations are purely offline string manipulation — no cloud APIs, no LLMs.
 */
class TextCleaner {

    /**
     * Cleans raw STT text:
     * 1. Unicode NFC normalization
     * 2. Trim leading/trailing whitespace
     * 3. Collapse repeated whitespace to a single space
     * 4. Collapse duplicate punctuation (e.g. "!!!" → "!", "..." preserved as ellipsis "…")
     * 5. Ensure the text ends with an appropriate sentence-ending punctuation for the detected script
     *
     * Script and numbers are always preserved. No meaning-changing edits.
     */
    fun cleanText(raw: String): String {
        if (raw.isBlank()) return ""

        var text = Normalizer.normalize(raw, Normalizer.Form.NFC)

        // Trim leading and trailing whitespace
        text = text.trim()

        // Collapse internal runs of whitespace (spaces, tabs, etc.) to a single space
        text = text.replace(MULTIPLE_WHITESPACE, " ")

        // Normalize three-dot sequences into proper ellipsis character
        text = text.replace("...", "\u2026")

        // Collapse repeated punctuation marks (same character repeated 2+)
        text = text.replace(DUPLICATE_PUNCTUATION) { match ->
            match.value.substring(0, 1)
        }

        // Ensure the text ends with sentence-ending punctuation
        text = ensureTrailingPunctuation(text)

        return text
    }

    /**
     * Appends a script-appropriate sentence terminator if the string does not already end
     * with one. Uses Devanagari purna viram (।) for Indic scripts, full stop (.) for Latin/other.
     */
    private fun ensureTrailingPunctuation(text: String): String {
        if (text.isEmpty()) return text

        val lastChar = text.last()
        if (SENTENCE_TERMINATORS.contains(lastChar)) return text

        val script = detectPredominantScript(text)
        return if ((script == Script.DEVANAGARI) || (script == Script.BENGALI) || (script == Script.GUJARATI) || (script == Script.ODIA)) {
            "$text।"
        } else {
            "$text."
        }
    }

    internal enum class Script {
        LATIN, DEVANAGARI, GUJARATI, BENGALI, ODIA,
        KANNADA, TELUGU, TAMIL, MALAYALAM, OTHER
    }

    internal fun detectPredominantScript(text: String): Script {
        val counts = mutableMapOf<Script, Int>()
        for (char in text) {
            val script = classifyChar(char)
            if (script != Script.OTHER && script != Script.LATIN) {
                counts[script] = (counts[script] ?: 0) + 1
            }
        }
        if (counts.isEmpty()) return Script.LATIN
        return counts.maxByOrNull { it.value }?.key ?: Script.LATIN
    }

    private fun classifyChar(c: Char): Script {
        return when (c.code) {
            in 0x0900..0x097F -> Script.DEVANAGARI  // Hindi/Marathi share Devanagari
            in 0x0A80..0x0AFF -> Script.GUJARATI
            in 0x0980..0x09FF -> Script.BENGALI
            in 0x0B00..0x0B7F -> Script.ODIA
            in 0x0C00..0x0C7F -> Script.TELUGU
            in 0x0C80..0x0CFF -> Script.KANNADA
            in 0x0D00..0x0D7F -> Script.MALAYALAM
            in 0x0B80..0x0BFF -> Script.TAMIL
            in 0x0041..0x007A -> Script.LATIN
            in 0x00C0..0x024F -> Script.LATIN   // Latin Extended
            else -> Script.OTHER
        }
    }

    companion object {
        private val MULTIPLE_WHITESPACE = Regex("\\s{2,}")
        private val DUPLICATE_PUNCTUATION = Regex("([!?;:,।]){2,}")
        private val SENTENCE_TERMINATORS = setOf('.', '!', '?', '।', '\u2026')
    }
}
