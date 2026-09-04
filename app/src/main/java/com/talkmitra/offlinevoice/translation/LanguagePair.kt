package com.talkmitra.offlinevoice.translation

/**
 * Represents a directed translation language pair from [source] to [target].
 */
data class LanguagePair(
    val source: SupportedLanguage,
    val target: SupportedLanguage
) {
    val key: String get() = "${source.code}-${target.code}"
    val isSameLanguage: Boolean get() = source == target
    val displayName: String get() = "${source.displayName} → ${target.displayName}"

    val reverse: LanguagePair get() = LanguagePair(target, source)

    override fun toString(): String = key

    companion object {
        fun from(sourceCode: String, targetCode: String): LanguagePair {
            return LanguagePair(
                source = SupportedLanguage.fromCode(sourceCode),
                target = SupportedLanguage.fromCode(targetCode)
            )
        }

        fun fromKey(key: String): LanguagePair {
            val parts = key.split('-', '_', '→', '>').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                return from(parts[0], parts[1])
            }
            return LanguagePair(SupportedLanguage.ENGLISH, SupportedLanguage.HINDI)
        }
    }
}
