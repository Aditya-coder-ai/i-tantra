package com.itantra.offlinevoice.audio.stt

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Implementation of [STTEngine] using the Vosk Android SDK.
 */
class VoskSttEngine(private val context: Context) : STTEngine {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var currentLanguage: STTLanguage? = null

    override fun initialize(language: STTLanguage): Boolean {
        if (currentLanguage == language && model != null) return true

        release()

        val modelPath = getModelPath(language)
        if (!isModelAvailable(language)) {
            Log.e("VoskStt", "Model for ${language.code} not found at $modelPath")
            return false
        }

        return try {
            model = Model(modelPath)
            recognizer = Recognizer(model, 16000.0f)
            currentLanguage = language
            Log.i("VoskStt", "Model for ${language.code} initialized successfully")
            true
        } catch (e: Exception) {
            Log.e("VoskStt", "Failed to init Vosk model: ${e.message}")
            false
        }
    }

    override fun transcribe(samples: ShortArray): STTResult {
        val rec = recognizer ?: return STTResult("", currentLanguage ?: STTLanguage.ENGLISH, 0f, 0, 0)

        val startTime = SystemClock.elapsedRealtime()
        
        // Use reflection to call acceptWaveform if the compiler fails to resolve the overloaded method
        try {
            val method = rec.javaClass.getMethod("acceptWaveform", ShortArray::class.java, Int::class.java)
            method.invoke(rec, samples, samples.size)
        } catch (e: Exception) {
            try {
                val bytes = ShortArrayToByteArray(samples)
                val method = rec.javaClass.getMethod("acceptWaveform", ByteArray::class.java, Int::class.java)
                method.invoke(rec, bytes, bytes.size)
            } catch (e2: Exception) {
                Log.e("VoskStt", "Failed to invoke acceptWaveform: ${e2.message}")
            }
        }
        
        val jsonResult = rec.getResult()
        val endTime = SystemClock.elapsedRealtime()

        val text = try {
            JSONObject(jsonResult).optString("text", "")
        } catch (e: Exception) {
            ""
        }

        val audioDurationMs = (samples.size.toFloat() / 16000 * 1000).toLong()

        return STTResult(
            text = text,
            language = currentLanguage ?: STTLanguage.ENGLISH,
            confidence = 1.0f,
            processingTimeMs = endTime - startTime,
            audioDurationMs = audioDurationMs
        )
    }

    override fun release() {
        recognizer?.close()
        recognizer = null
        model = null
        currentLanguage = null
    }

    override fun isModelAvailable(language: STTLanguage): Boolean {
        val path = getModelPath(language)
        val dir = File(path)
        return dir.exists() && dir.isDirectory && (dir.list()?.isNotEmpty() ?: false)
    }

    private fun getModelPath(language: STTLanguage): String {
        return File(context.filesDir, "models/stt/${language.code}").absolutePath
    }

    private fun ShortArrayToByteArray(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            bytes[i * 2] = (s and 0xff).toByte()
            bytes[i * 2 + 1] = (s shr 8 and 0xff).toByte()
        }
        return bytes
    }
}
