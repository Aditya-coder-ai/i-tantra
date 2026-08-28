package com.itantra.offlinevoice.audio.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Native on-device Speech-to-Text engine utilizing Android OS built-in SpeechRecognizer.
 * Requires zero bundled model downloads and supports offline speech recognition on modern devices.
 *
 * Architecture: a SINGLE persistent SpeechRecognizer instance is created once and reused.
 * Between sessions we call cancel() (not destroy()) so the binder connection stays alive.
 * stopListening() tells the service "I'm done speaking, give me final results now" — it
 * does NOT cancel recognition. The result/error callbacks fire asynchronously afterwards.
 */
class AndroidSpeechRecognizerEngine(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var isListening = false
    @Volatile private var hasDeliveredResult = false
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        language: STTLanguage,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            onResultCallback = onResult
            onErrorCallback = onError
            hasDeliveredResult = false

            try {
                // Reuse existing recognizer — only create once
                var rec = recognizer
                if (rec == null) {
                    Log.i(TAG, "Creating new SpeechRecognizer instance")
                    rec = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                    recognizer = rec
                } else {
                    // Cancel any previous in-flight recognition without destroying the binder
                    try { rec.cancel() } catch (_: Exception) {}
                }

                var latestPartialText = ""

                rec.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "✓ Ready for speech in language: ${language.localeTag}")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "✓ Speech started (user is speaking)")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "✓ End of speech detected — waiting for final results")
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        Log.w(TAG, "✗ SpeechRecognizer onError: code=$error, partialText='$latestPartialText'")

                        // If we collected partial results before the error, use them as the result
                        if (latestPartialText.isNotBlank() && !hasDeliveredResult) {
                            hasDeliveredResult = true
                            Log.i(TAG, "→ Using partial text as result: '$latestPartialText'")
                            onResultCallback?.invoke(latestPartialText)
                            return
                        }

                        if (hasDeliveredResult) return // Already delivered, ignore error

                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Speech service client error — try again"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network required for language: ${language.displayName}"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Hold button and speak clearly."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy — try again"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
                            else -> "Speech recognition error ($error)"
                        }
                        onErrorCallback?.invoke(message)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        if (hasDeliveredResult) return // Already delivered via partials

                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull { it.isNotBlank() } ?: latestPartialText
                        Log.i(TAG, "✓ Final recognized text: '$text' (alternatives: ${matches?.size ?: 0})")

                        if (text.isNotBlank()) {
                            hasDeliveredResult = true
                            onResultCallback?.invoke(text)
                        } else {
                            onErrorCallback?.invoke("No speech detected. Please speak clearly into the microphone.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partialText = partials?.firstOrNull { it.isNotBlank() }
                        if (!partialText.isNullOrBlank()) {
                            latestPartialText = partialText
                            Log.d(TAG, "~ Partial speech: '$partialText'")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.localeTag)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, language.localeTag)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    // Give the recognizer generous time windows
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                }

                rec.startListening(intent)
                isListening = true
                Log.i(TAG, "★ SpeechRecognizer.startListening() for language: ${language.localeTag}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech recognizer: ${e.message}", e)
                isListening = false
                onErrorCallback?.invoke("Failed to start speech recognition: ${e.message}")
            }
        }
    }

    /**
     * Signals the speech recognizer that the user has stopped speaking.
     * This does NOT cancel recognition — it tells the service to finalize results.
     * The onResults/onError callback will fire asynchronously after this.
     */
    fun stopListening() {
        mainHandler.post {
            try {
                if (isListening) {
                    Log.d(TAG, "stopListening() — telling recognizer user stopped speaking")
                    recognizer?.stopListening()
                    // Do NOT set isListening = false here.
                    // Let the onResults/onError callback handle that.
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer: ${e.message}")
            }
        }
    }

    fun destroy() {
        try {
            recognizer?.destroy()
            recognizer = null
            isListening = false
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying recognizer: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AndroidSpeechRecognizer"
    }
}
