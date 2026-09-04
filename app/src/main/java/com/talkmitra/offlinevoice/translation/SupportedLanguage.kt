package com.talkmitra.offlinevoice.translation

import java.util.Locale

/**
 * Supported offline languages in VoiceLink.
 * Standardizes ISO language codes, English names, native script names, and script families.
 */
enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val scriptFamily: String
) {
    HINDI("hi", "Hindi", "हिन्दी", "Devanagari"),
    GUJARATI("gu", "Gujarati", "ગુજરાતી", "Gujarati"),
    MARATHI("mr", "Marathi", "मराठी", "Devanagari"),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ", "Kannada"),
    MALAYALAM("ml", "Malayalam", "മലയാളം", "Malayalam"),
    TAMIL("ta", "Tamil", "தமிழ்", "Tamil"),
    TELUGU("te", "Telugu", "తెలుగు", "Telugu"),
    ODIA("or", "Odia", "ଓଡ଼ିଆ", "Odia"),
    BENGALI("bn", "Bengali", "বাংলা", "Bengali"),
    ENGLISH("en", "English", "English", "Latin");

    val isIndic: Boolean get() = this != ENGLISH

    fun toLocale(): Locale = when (this) {
        ENGLISH -> Locale.ENGLISH
        HINDI -> Locale("hi", "IN")
        GUJARATI -> Locale("gu", "IN")
        MARATHI -> Locale("mr", "IN")
        KANNADA -> Locale("kn", "IN")
        MALAYALAM -> Locale("ml", "IN")
        TAMIL -> Locale("ta", "IN")
        TELUGU -> Locale("te", "IN")
        ODIA -> Locale("or", "IN")
        BENGALI -> Locale("bn", "IN")
    }

    companion object {
        /**
         * Resolves a [SupportedLanguage] from a raw code or tag (e.g. "hi", "hi-IN", "hin", "hindi", "हिन्दी").
         */
        fun fromCode(code: String): SupportedLanguage {
            val raw = code.trim().substringBefore('·').trim().substringBefore('-').trim()
            val normalized = raw.lowercase()
            val baseCode = normalized

            return entries.firstOrNull { it.code == baseCode }
                ?: entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
                ?: entries.firstOrNull { it.displayName.equals(normalized, ignoreCase = true) }
                ?: entries.firstOrNull { it.nativeName.equals(raw, ignoreCase = true) }
                ?: when (baseCode) {
                    "hin", "hindi", "हिन्दी", "हिंदी" -> HINDI
                    "guj", "gujarati", "ગુજરાતી" -> GUJARATI
                    "mar", "marathi", "मराठी" -> MARATHI
                    "kan", "kannada", "ಕನ್ನಡ" -> KANNADA
                    "mal", "malayalam", "മലയാളം" -> MALAYALAM
                    "tam", "tamil", "தமிழ்" -> TAMIL
                    "tel", "telugu", "తెలుగు" -> TELUGU
                    "ori", "od", "oriya", "odia", "ଓଡ଼ିଆ" -> ODIA
                    "ben", "bangla", "bengali", "বাংলা" -> BENGALI
                    "eng", "english" -> ENGLISH
                    else -> when (raw) {
                        "हिन्दी", "हिंदी" -> HINDI
                        "ગુજરાતી" -> GUJARATI
                        "मराठी" -> MARATHI
                        "ಕನ್ನಡ" -> KANNADA
                        "മലയാളം" -> MALAYALAM
                        "தமிழ்" -> TAMIL
                        "తెలుగు" -> TELUGU
                        "ଓଡ଼ିଆ" -> ODIA
                        "বাংলা" -> BENGALI
                        else -> ENGLISH
                    }
                }
        }

        fun getAllCodes(): List<String> = entries.map { it.code }
    }
}
