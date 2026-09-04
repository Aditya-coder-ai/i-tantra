package com.talkmitra.offlinevoice.audio.stt

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

        unpackFromAssetsIfAvailable(language)

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

    private fun unpackFromAssetsIfAvailable(language: STTLanguage) {
        val targetDir = File(getModelPath(language))
        if (targetDir.exists() && (targetDir.list()?.isNotEmpty() == true)) {
            return
        }
        val assetPaths = listOf("models/stt/${language.code}", "model-${language.code}")
        for (assetPath in assetPaths) {
            try {
                val files = context.assets.list(assetPath) ?: continue
                if (files.isNotEmpty()) {
                    targetDir.mkdirs()
                    for (file in files) {
                        context.assets.open("$assetPath/$file").use { input ->
                            File(targetDir, file).outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    Log.i("VoskStt", "Unpacked model for ${language.code} from assets: $assetPath")
                    break
                }
            } catch (_: Exception) {
                // Continue to next asset path
            }
        }
    }

    override fun transcribe(samples: ShortArray): STTResult {
        val rec = recognizer ?: return STTResult("", currentLanguage ?: STTLanguage.ENGLISH, 0f, 0, 0)

        val startTime = SystemClock.elapsedRealtime()

        try {
            rec.reset()
        } catch (_: Exception) {
            // Reset if supported by recognizer version
        }

        // Feed audio in slices (e.g. 3200 samples = 200ms) for reliable Kaldi lattice decoding
        val chunkSize = 3200
        var offset = 0
        while (offset < samples.size) {
            val count = minOf(chunkSize, samples.size - offset)
            val chunk = if (count == samples.size) samples else samples.copyOfRange(offset, offset + count)
            try {
                val method = rec.javaClass.getMethod("acceptWaveform", ShortArray::class.java, Int::class.java)
                method.invoke(rec, chunk, count)
            } catch (e: Exception) {
                try {
                    val bytes = ShortArrayToByteArray(chunk)
                    val method = rec.javaClass.getMethod("acceptWaveform", ByteArray::class.java, Int::class.java)
                    method.invoke(rec, bytes, bytes.size)
                } catch (e2: Exception) {
                    Log.e("VoskStt", "Failed to invoke acceptWaveform: ${e2.message}")
                }
            }
            offset += count
        }

        // Retrieve final complete decoded sentence
        val jsonResult = try {
            val finalMethod = rec.javaClass.getMethod("getFinalResult")
            finalMethod.invoke(rec) as String
        } catch (e: Exception) {
            try {
                rec.finalResult
            } catch (e2: Exception) {
                rec.result
            }
        }

        val endTime = SystemClock.elapsedRealtime()

        val text = try {
            val json = JSONObject(jsonResult)
            var extracted = json.optString("text", "")
            if (extracted.isBlank()) {
                val alts = json.optJSONArray("alternatives")
                if (alts != null && alts.length() > 0) {
                    extracted = alts.getJSONObject(0).optString("text", "")
                }
            }
            extracted
        } catch (e: Exception) {
            ""
        }

        val audioDurationMs = (samples.size.toFloat() / 16000 * 1000).toLong()
        val processingTimeMs = endTime - startTime
        val rtf = if (audioDurationMs > 0) processingTimeMs.toFloat() / audioDurationMs else 0f

        Log.i("VoskStt", "STT Result: samples=${samples.size}, audioDuration=${audioDurationMs}ms, procTime=${processingTimeMs}ms, RTF=${"%.2f".format(rtf)}, lang=${currentLanguage?.code}, text='$text'")

        return STTResult(
            text = text,
            language = currentLanguage ?: STTLanguage.ENGLISH,
            confidence = if (text.isNotBlank()) 0.95f else 0.0f,
            processingTimeMs = processingTimeMs,
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
