package com.talkmitra.offlinevoice.translation

import android.util.Log

/**
 * Plans and resolves the optimal translation route for any given language pair.
 * Prefers DIRECT models whenever available, and falls back to 2-hop PIVOT_ENGLISH
 * when direct translation between non-English Indian language pairs is requested.
 */
class TranslationRouter(
    private val directPairs: Set<LanguagePair> = DEFAULT_DIRECT_PAIRS
) {

    /**
     * Resolves the route for [source] to [target].
     *
     * @return [TranslationRoute] detailing steps and translation path.
     */
    fun resolveRoute(source: SupportedLanguage, target: SupportedLanguage): TranslationRoute {
        // 1. Same Language: Passthrough
        if (source == target) {
            return TranslationRoute(
                source = source,
                target = target,
                path = TranslationPath.SAME_LANGUAGE_PASSTHROUGH,
                steps = emptyList()
            )
        }

        val pair = LanguagePair(source, target)

        // 2. Direct Model Exists
        if (directPairs.contains(pair)) {
            return TranslationRoute(
                source = source,
                target = target,
                path = TranslationPath.DIRECT,
                steps = listOf(pair)
            )
        }

        // 3. Pivot via English (e.g. ta -> en -> hi)
        if (source != SupportedLanguage.ENGLISH && target != SupportedLanguage.ENGLISH) {
            val hop1 = LanguagePair(source, SupportedLanguage.ENGLISH)
            val hop2 = LanguagePair(SupportedLanguage.ENGLISH, target)

            if (directPairs.contains(hop1) && directPairs.contains(hop2)) {
                Log.d(TAG, "Routing ${pair.displayName} via PIVOT_ENGLISH (${hop1.key} + ${hop2.key})")
                return TranslationRoute(
                    source = source,
                    target = target,
                    path = TranslationPath.PIVOT_ENGLISH,
                    steps = listOf(hop1, hop2)
                )
            }
        }

        // 4. Fallback Original if neither direct nor pivot is available
        Log.w(TAG, "No valid route for ${pair.displayName}")
        return TranslationRoute(
            source = source,
            target = target,
            path = TranslationPath.FALLBACK_ORIGINAL,
            steps = emptyList()
        )
    }

    fun isPairSupported(source: SupportedLanguage, target: SupportedLanguage): Boolean {
        val route = resolveRoute(source, target)
        return route.path != TranslationPath.FALLBACK_ORIGINAL
    }

    companion object {
        private const val TAG = "TranslationRouter"

        /**
         * Default direct translation pairs supported in VoiceLink:
         * English <-> all 9 Indic languages + bidirectional Indic pairs with direct models.
         */
        val DEFAULT_DIRECT_PAIRS: Set<LanguagePair> = buildSet {
            val indicLanguages = SupportedLanguage.entries.filter { it.isIndic }

            // English <-> All Indic languages (Bidirectional)
            for (lang in indicLanguages) {
                add(LanguagePair(SupportedLanguage.ENGLISH, lang))
                add(LanguagePair(lang, SupportedLanguage.ENGLISH))
            }

            // Direct Indic <-> Indic pairs (Hindi <-> Marathi, Hindi <-> Gujarati, Tamil <-> Telugu, etc.)
            add(LanguagePair(SupportedLanguage.HINDI, SupportedLanguage.MARATHI))
            add(LanguagePair(SupportedLanguage.MARATHI, SupportedLanguage.HINDI))
            add(LanguagePair(SupportedLanguage.HINDI, SupportedLanguage.GUJARATI))
            add(LanguagePair(SupportedLanguage.GUJARATI, SupportedLanguage.HINDI))
            add(LanguagePair(SupportedLanguage.HINDI, SupportedLanguage.BENGALI))
            add(LanguagePair(SupportedLanguage.BENGALI, SupportedLanguage.HINDI))
            add(LanguagePair(SupportedLanguage.TAMIL, SupportedLanguage.TELUGU))
            add(LanguagePair(SupportedLanguage.TELUGU, SupportedLanguage.TAMIL))
        }
    }
}

/**
 * Resolved routing plan for a translation request.
 */
data class TranslationRoute(
    val source: SupportedLanguage,
    val target: SupportedLanguage,
    val path: TranslationPath,
    val steps: List<LanguagePair>
) {
    val isPivot: Boolean get() = path == TranslationPath.PIVOT_ENGLISH
    val isDirect: Boolean get() = path == TranslationPath.DIRECT
    val isPassthrough: Boolean get() = path == TranslationPath.SAME_LANGUAGE_PASSTHROUGH
}
