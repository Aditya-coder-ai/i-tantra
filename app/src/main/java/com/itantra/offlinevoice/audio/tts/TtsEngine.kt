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
        if (!_isReady.value || text.isBlank()) return false

        val locale = getLocaleForCode(languageCode)
        val langResult = tts.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault())
        }
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "iTantra_TTS_${System.currentTimeMillis()}")
        return result == TextToSpeech.SUCCESS
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
        val clean = code.trim().lowercase()
        return when {
            clean.startsWith("hi") -> Locale("hi", "IN")
            clean.startsWith("gu") -> Locale("gu", "IN")
            clean.startsWith("mr") -> Locale("mr", "IN")
            clean.startsWith("kn") -> Locale("kn", "IN")
            clean.startsWith("ml") -> Locale("ml", "IN")
            clean.startsWith("ta") -> Locale("ta", "IN")
            clean.startsWith("te") -> Locale("te", "IN")
            clean.startsWith("bn") -> Locale("bn", "IN")
            clean.startsWith("or") || clean.startsWith("od") -> Locale("or", "IN")
            clean.startsWith("en") -> Locale.ENGLISH
            else -> Locale("hi", "IN")
        }
    }
}
