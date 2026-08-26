package com.itantra.offlinevoice.tts.engine

import android.os.SystemClock
import android.util.Log
import com.itantra.offlinevoice.tts.TTSConfig
import com.itantra.offlinevoice.tts.TTSException
import com.itantra.offlinevoice.tts.TTSLanguage
import com.itantra.offlinevoice.tts.TTSResult

/**
 * Low-level wrapper around the Sherpa-ONNX `OfflineTts` native API.
 *
 * Each instance holds a single loaded model for one language.
 * This class is **not** thread-safe — callers must synchronise externally
 * (the [OfflineTTSEngine] does this).
 *
 * ---
 * **Implementation note for the build:**
 *
 * Sherpa-ONNX is integrated via its Android AAR which exposes JNI
 * bindings. If the AAR is not yet available on Maven Central, the
 * project can include a local AAR in `app/libs/`.
 *
 * For development/compilation before the AAR is integrated, this class
 * uses a **thin abstraction layer** that isolates every native call
 * behind clearly-marked stubs. The stubs produce silent audio so the
 * rest of the pipeline (queue, player, UI) can be developed and tested
 * independently of the native library.
 */
class TTSInference(
    val modelPath: String,
    val tokensPath: String,
    val espeakDataPath: String,
    val language: TTSLanguage,
    private val config: TTSConfig
) {
    companion object {
        private const val TAG = "TTSInference"

        /**
         * Set to `true` once the real Sherpa-ONNX native library is linked.
         * Until then, inference falls back to stub (silent) audio.
         */
        private var nativeAvailable: Boolean = false

        init {
            try {
                System.loadLibrary("sherpa-onnx-jni")
                nativeAvailable = true
                Log.i(TAG, "Sherpa-ONNX native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Sherpa-ONNX native library not found — using stub inference")
            }
        }
    }

    /** Sample rate emitted by this model. */
    val sampleRate: Int get() = language.sampleRate

    /** Opaque handle to the native OfflineTts object, or null for stub mode. */
    private var nativeHandle: Long = 0L
    private var released = false

    init {
        if (nativeAvailable) {
            nativeHandle = nativeCreate(
                modelPath,
                tokensPath,
                espeakDataPath,
                config.numThreads
            )
            if (nativeHandle == 0L) {
                throw TTSException.InitializationException(
                    "Native OfflineTts creation returned null handle for ${language.code}"
                )
            }
        }
        Log.i(TAG, "TTSInference created for ${language.displayName} (native=$nativeAvailable)")
    }

    /**
     * Generates PCM audio for the given [text].
     *
     * @param speechRate Speed multiplier (1.0 = normal).
     * @return [TTSResult] with raw PCM 16-bit mono audio.
     * @throws TTSException.InferenceException on failure.
     */
    fun generate(text: String, speechRate: Float = config.speechRate): TTSResult {
        check(!released) { "TTSInference already released" }

        val startMs = SystemClock.elapsedRealtime()

        val audioSamples: ShortArray = if (nativeAvailable && nativeHandle != 0L) {
            generateNative(text, speechRate)
        } else {
            generateStub(text)
        }

        val elapsedMs = SystemClock.elapsedRealtime() - startMs
        val audioDurationMs = (audioSamples.size.toLong() * 1000L) / sampleRate

        return TTSResult(
            audioData = audioSamples,
            sampleRate = sampleRate,
            audioDurationMs = audioDurationMs,
            processingTimeMs = elapsedMs,
            language = language,
            textLength = text.length
        )
    }

    /** Releases native resources. */
    fun release() {
        if (released) return
        released = true
        if (nativeAvailable && nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
        Log.i(TAG, "TTSInference released for ${language.displayName}")
    }

    // ── Native bridge ────────────────────────────────────────────────

    /**
     * Calls the real Sherpa-ONNX OfflineTts.generate() via JNI.
     * The native method returns a float array of PCM samples which we
     * convert to ShortArray for AudioTrack consumption.
     */
    private fun generateNative(text: String, speechRate: Float): ShortArray {
        return try {
            val floatSamples = nativeGenerate(nativeHandle, text, speechRate)
            floatToShort(floatSamples)
        } catch (e: Exception) {
            throw TTSException.InferenceException(
                "Native inference failed for ${language.code}: ${e.message}", e
            )
        }
    }

    // ── Stub inference (development fallback) ────────────────────────

    /**
     * Generates silence of an appropriate duration.
     *
     * Rule of thumb: ~120 ms per character is a rough approximation
     * of natural speech pace.  The stub lets the full pipeline
     * (queue → player → UI) be developed without the native library.
     */
    private fun generateStub(text: String): ShortArray {
        val charsPerSecond = 8.0 // very rough estimate
        val durationSec = (text.length / charsPerSecond).coerceIn(0.5, 30.0)
        val numSamples = (durationSec * sampleRate).toInt()

        // Generate a faint 440 Hz sine wave so we can confirm audio path works
        val samples = ShortArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            (Short.MAX_VALUE * 0.05 * kotlin.math.sin(2 * Math.PI * 440 * t)).toInt().toShort()
        }
        Log.d(TAG, "Stub inference: ${text.length} chars → ${numSamples} samples (${durationSec}s)")
        return samples
    }

    // ── Utility ──────────────────────────────────────────────────────

    /** Converts normalised float PCM [-1, 1] to 16-bit short PCM. */
    private fun floatToShort(floats: FloatArray): ShortArray {
        val shorts = ShortArray(floats.size)
        for (i in floats.indices) {
            val clamped = floats[i].coerceIn(-1.0f, 1.0f)
            shorts[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
        return shorts
    }

    // ── JNI declarations (resolved when native lib is present) ──────

    private external fun nativeCreate(
        modelPath: String,
        tokensPath: String,
        espeakDataPath: String,
        numThreads: Int
    ): Long

    private external fun nativeGenerate(
        handle: Long,
        text: String,
        speechRate: Float
    ): FloatArray

    private external fun nativeDestroy(handle: Long)
}
