package com.itantra.offlinevoice.audio.stt

/** Supported languages for VoiceLink STT. */
enum class STTLanguage(val code: String, val displayName: String, val nativeName: String) {
    HINDI("hi", "Hindi", "हिन्दी"),
    ENGLISH("en", "English", "English"),
    GUJARATI("gu", "Gujarati", "ગુજરાતી"),
    MARATHI("mr", "Marathi", "मराठी"),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ"),
    MALAYALAM("ml", "Malayalam", "മലയാളം"),
    TAMIL("ta", "Tamil", "தமிழ்"),
    TELUGU("te", "Telugu", "తెలుగు"),
    ODIA("or", "Odia", "ଓଡ଼ିଆ"),
    BENGALI("bn", "Bengali", "বাংলা");

    val localeTag: String
        get() = when (code) {
            "hi" -> "hi-IN"
            "gu" -> "gu-IN"
            "mr" -> "mr-IN"
            "kn" -> "kn-IN"
            "ml" -> "ml-IN"
            "ta" -> "ta-IN"
            "te" -> "te-IN"
            "bn" -> "bn-IN"
            "or" -> "or-IN"
            "en" -> "en-IN"
            else -> "en-US"
        }

    companion object {
        fun fromCode(code: String): STTLanguage = values().find { it.code == code } ?: ENGLISH
    }
}
