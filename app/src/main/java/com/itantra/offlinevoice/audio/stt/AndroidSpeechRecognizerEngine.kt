package com.itantra.offlinevoice.audio.stt

import android.content.Context
import android.content.Intent
import android.os.Build
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
 */
class AndroidSpeechRecognizerEngine(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
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
            destroy()

            onResultCallback = onResult
            onErrorCallback = onError

            try {
                val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                var latestPartialText = ""

                rec.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech in language: ${language.localeTag}")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech started")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech ended")
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        Log.w(TAG, "SpeechRecognizer onError: code $error, latestPartialText: '$latestPartialText'")
                        if (latestPartialText.isNotBlank()) {
                            onResultCallback?.invoke(latestPartialText)
                            return
                        }
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Speech service error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network required for language: ${language.displayName}"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Hold button and speak clearly."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
                            else -> "Speech recognition error ($error)"
                        }
                        onErrorCallback?.invoke(message)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull { it.isNotBlank() } ?: latestPartialText
                        Log.i(TAG, "Recognized text: $text")
                        if (text.isNotBlank()) {
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
                            Log.d(TAG, "Partial speech: $partialText")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.localeTag)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                }

                rec.startListening(intent)
                recognizer = rec
                isListening = true
                Log.i(TAG, "SpeechRecognizer started for language: ${language.localeTag}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech recognizer: ${e.message}", e)
                isListening = false
                onErrorCallback?.invoke("Failed to start speech recognition: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                if (isListening) {
                    recognizer?.stopListening()
                    isListening = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer: ${e.message}")
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                recognizer?.destroy()
                recognizer = null
                isListening = false
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying recognizer: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "AndroidSpeechRecognizer"
    }
}
