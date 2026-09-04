package com.talkmitra.offlinevoice.translation

/**
 * Tracks the execution path taken to perform a translation.
 */
enum class TranslationPath(val displayName: String) {
    /** Direct neural / phrase translation between source and target */
    DIRECT("Direct"),

    /** 2-Hop Pivot translation through English (e.g. Tamil → English → Hindi) */
    PIVOT_ENGLISH("Pivot via English"),

    /** Source and target languages are identical, no translation performed */
    SAME_LANGUAGE_PASSTHROUGH("Same Language Passthrough"),

    /** Fallback to original text upon error or missing model */
    FALLBACK_ORIGINAL("Fallback Original");

    val isPivot: Boolean get() = this == PIVOT_ENGLISH
    val isDirect: Boolean get() = this == DIRECT
}
