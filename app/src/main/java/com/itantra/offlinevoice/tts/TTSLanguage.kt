package com.itantra.offlinevoice.tts

/**
 * Supported languages for VoiceLink TTS.
 *
 * Each entry maps to a Piper/VITS model directory under
 * `context.filesDir/models/tts/{code}/` and carries display metadata
 * plus the native sample rate emitted by that language's model.
 *
 * Mirror of [com.itantra.offlinevoice.audio.stt.STTLanguage] — kept
 * separate so TTS can evolve its model catalogue independently.
 */
enum class TTSLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    /** Default sample rate for this language's Piper model (Hz). */
    val sampleRate: Int = 22050
) {
    HINDI("hi", "Hindi", "हिन्दी"),
    GUJARATI("gu", "Gujarati", "ગુજરાતી"),
    MARATHI("mr", "Marathi", "मराठी"),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ"),
    MALAYALAM("ml", "Malayalam", "മലയാളം"),
    TAMIL("ta", "Tamil", "தமிழ்"),
    TELUGU("te", "Telugu", "తెలుగు"),
    ODIA("or", "Odia", "ଓଡ଼ିଆ"),
    BENGALI("bn", "Bengali", "বাংলা"),
    ENGLISH("en", "English", "English");

    companion object {
        /**
         * Resolves a language code (e.g. "hi", "en") to a [TTSLanguage].
         * Returns `null` for unknown codes — the caller decides the fallback policy.
         */
        fun fromCode(code: String): TTSLanguage? =
            values().find { it.code == code.trim().lowercase() }
    }
}
