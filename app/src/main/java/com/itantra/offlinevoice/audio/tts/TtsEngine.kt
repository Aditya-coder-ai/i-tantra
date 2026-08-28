package com.itantra.offlinevoice.audio.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * On-device offline Text-To-Speech engine using Android's native TextToSpeech engine.
 */
class TtsEngine(context: Context) : TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var speechRate: Float = 1.0f
    private var speechPitch: Float = 1.0f
    var lastError: String? = null
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            _isReady.value = true
            tts.setSpeechRate(speechRate)
            tts.setPitch(speechPitch)
            Log.d("TtsEngine", "TTS initialized successfully")
        } else {
            Log.e("TtsEngine", "TTS initialization failed with status $status")
        }
    }

    fun speak(text: String, languageCode: String = "hi"): Boolean {
        if (!_isReady.value || text.isBlank()) {
            lastError = "Text-to-speech is not ready"
            return false
        }

        val locale = getLocaleForCode(languageCode)
        val langResult = tts.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            // A locale-specific Hindi voice is not guaranteed, while a generic Hindi
            // voice may still be installed. Try it before reporting an unavailable
            // language. Never fall back to the phone's default (often English), as
            // that makes a Hindi translation play with an English voice.
            val genericLocale = Locale(locale.language)
            val genericResult = if (genericLocale != locale) tts.setLanguage(genericLocale) else langResult
            if (genericResult == TextToSpeech.LANG_MISSING_DATA || genericResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                lastError = "${locale.displayLanguage} voice is not installed on this device"
                Log.e(TAG, "$lastError; refusing to fall back to the default voice")
                return false
            }
        }

        lastError = null
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "iTantra_TTS_${System.currentTimeMillis()}")
        if (result != TextToSpeech.SUCCESS) {
            lastError = "Unable to start ${locale.displayLanguage} speech"
            Log.e(TAG, lastError ?: "TTS speak failed")
            return false
        }
        return true
    }

    fun stop() {
        if (_isReady.value) {
            tts.stop()
        }
    }

    fun setSpeed(rate: Float) {
        speechRate = rate
        if (_isReady.value) tts.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        speechPitch = pitch
        if (_isReady.value) tts.setPitch(pitch)
    }

    fun shutdown() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (e: Exception) {
            // Ignored on shutdown
        }
    }

    private fun getLocaleForCode(code: String): Locale {
        return com.itantra.offlinevoice.translation.SupportedLanguage.fromCode(code).toLocale()
    }

    companion object {
        private const val TAG = "TtsEngine"
    }
}
