package com.talkmitra.offlinevoice.audio.debug

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Diagnostic metrics calculated from a raw PCM audio buffer.
 */
data class AudioDiagnostics(
    val sampleRateHz: Int = 16_000,
    val channelCount: Int = 1,
    val bitsPerSample: Int = 16,
    val byteOrder: String = "LITTLE_ENDIAN",
    val sampleCount: Int = 0,
    val byteCount: Int = 0,
    val durationMs: Long = 0L,
    val rmsLinear: Float = 0f,
    val rmsDbfs: Float = -90f,
    val peakAmplitude: Int = 0,
    val peakDbfs: Float = -90f,
    val clippingSampleCount: Int = 0,
    val isSilent: Boolean = true,
    val isClipping: Boolean = false,
    val speechQualityAssessment: String = "No audio captured"
)

/**
 * Comprehensive developer audio debugging helper:
 * - Calculates rigorous acoustic metrics (RMS, peak, clipping, duration)
 * - Writes compliant 44-byte RIFF/WAV files for developer inspection
 * - Plays back raw PCM audio directly through the phone's audio hardware
 * - Synthesizes clean reference speech tones for isolated STT engine testing
 */
class AudioDebugHelper(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeAudioTrack: AudioTrack? = null
    private var isPlaying = false

    /**
     * Analyzes a 16-bit PCM ShortArray buffer and computes comprehensive diagnostic metrics.
     */
    fun analyzePcmBuffer(samples: ShortArray, sampleRateHz: Int = 16_000): AudioDiagnostics {
        if (samples.isEmpty()) {
            return AudioDiagnostics(sampleRateHz = sampleRateHz)
        }

        val sampleCount = samples.size
        val byteCount = sampleCount * 2
        val durationMs = (sampleCount * 1_000L) / sampleRateHz

        var sumSquares = 0.0
        var maxPeak = 0
        var clippingCount = 0

        for (s in samples) {
            val sampleVal = s.toInt()
            val absVal = abs(sampleVal)
            if (absVal > maxPeak) {
                maxPeak = absVal
            }
            if (absVal >= 32760) {
                clippingCount++
            }
            sumSquares += sampleVal.toDouble() * sampleVal.toDouble()
        }

        val rmsRaw = sqrt(sumSquares / sampleCount)
        val rmsNormalized = (rmsRaw / 32768.0).toFloat().coerceIn(0f, 1f)
        val rmsDbfs = if (rmsRaw > 0) (20.0 * log10(max(rmsRaw / 32768.0, 1e-5))).toFloat() else -90f
        val peakDbfs = if (maxPeak > 0) (20.0 * log10(max(maxPeak / 32768.0, 1e-5))).toFloat() else -90f

        val isSilent = rmsDbfs < -50.0f
        val isClipping = clippingCount > 10

        val assessment = when {
            isSilent -> "Audio is nearly silent (RMS < -50 dBFS). Microphone gain may be too low or muted."
            isClipping -> "Audio is severely CLIPPING ($clippingCount samples saturated). Volume is too loud."
            rmsDbfs in -35.0f..-12.0f -> "Optimal speech level (RMS: ${"%.1f".format(rmsDbfs)} dBFS, Peak: ${"%.1f".format(peakDbfs)} dBFS)."
            rmsDbfs < -35.0f -> "Low volume speech (RMS: ${"%.1f".format(rmsDbfs)} dBFS). Hold closer to microphone."
            else -> "High volume speech (RMS: ${"%.1f".format(rmsDbfs)} dBFS)."
        }

        return AudioDiagnostics(
            sampleRateHz = sampleRateHz,
            channelCount = 1,
            bitsPerSample = 16,
            byteOrder = "LITTLE_ENDIAN",
            sampleCount = sampleCount,
            byteCount = byteCount,
            durationMs = durationMs,
            rmsLinear = rmsNormalized,
            rmsDbfs = rmsDbfs,
            peakAmplitude = maxPeak,
            peakDbfs = peakDbfs,
            clippingSampleCount = clippingCount,
            isSilent = isSilent,
            isClipping = isClipping,
            speechQualityAssessment = assessment
        )
    }

    /**
     * Converts a 16-bit PCM ShortArray into a standard 44-byte RIFF/WAVE file on disk.
     */
    fun saveToWavFile(
        file: File,
        samples: ShortArray,
        sampleRateHz: Int = 16_000,
        channels: Int = 1
    ): Boolean {
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                val byteCount = samples.size * 2
                val totalDataLen = byteCount + 36
                val byteRate = sampleRateHz * channels * 2

                val header = ByteArray(44)
                ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put('R'.code.toByte()); put('I'.code.toByte()); put('F'.code.toByte()); put('F'.code.toByte())
                    putInt(totalDataLen)
                    put('W'.code.toByte()); put('A'.code.toByte()); put('V'.code.toByte()); put('E'.code.toByte())
                    put('f'.code.toByte()); put('m'.code.toByte()); put('t'.code.toByte()); put(' '.code.toByte())
                    putInt(16) // Subchunk1Size (16 for PCM)
                    putShort(1.toShort()) // AudioFormat (1 for PCM)
                    putShort(channels.toShort())
                    putInt(sampleRateHz)
                    putInt(byteRate)
                    putShort((channels * 2).toShort()) // BlockAlign
                    putShort(16.toShort()) // BitsPerSample
                    put('d'.code.toByte()); put('a'.code.toByte()); put('t'.code.toByte()); put('a'.code.toByte())
                    putInt(byteCount)
                }

                out.write(header)

                // Write Little-Endian PCM16 samples
                val pcmBytes = ByteArray(byteCount)
                ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
                out.write(pcmBytes)
            }
            Log.i(TAG, "Successfully saved WAV file to: ${file.absolutePath} (${samples.size} samples, ${sampleRateHz}Hz)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write WAV file: ${e.message}", e)
            false
        }
    }

    /**
     * Plays raw PCM audio directly through the phone's speaker via AudioTrack.
     */
    fun playRawPcmAudio(
        samples: ShortArray,
        sampleRateHz: Int = 16_000,
        onCompletion: (() -> Unit)? = null
    ) {
        stopAudioPlayback()

        if (samples.isEmpty()) {
            onCompletion?.invoke()
            return
        }

        Thread({
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRateHz,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufferSize, samples.size * 2)

                val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRateHz)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRateHz,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                activeAudioTrack = audioTrack
                isPlaying = true

                audioTrack.play()
                audioTrack.write(samples, 0, samples.size)

                // Wait for playback to finish
                val durationMs = (samples.size * 1000L) / sampleRateHz
                Thread.sleep(durationMs + 100)

                audioTrack.stop()
                audioTrack.release()
                activeAudioTrack = null
                isPlaying = false

                mainHandler.post { onCompletion?.invoke() }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing raw PCM audio: ${e.message}", e)
                isPlaying = false
                mainHandler.post { onCompletion?.invoke() }
            }
        }, "RawPcmPlaybackThread").start()
    }

    /**
     * Stops any actively playing audio track.
     */
    fun stopAudioPlayback() {
        try {
            activeAudioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
            activeAudioTrack = null
            isPlaying = false
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio playback: ${e.message}")
        }
    }

    /**
     * Synthesizes a calibrated reference audio waveform containing harmonic vocal formants
     * to test the STT pipeline with 100% known, noiseless reference speech frequencies.
     */
    fun synthesizeReferenceSpeechAudio(phrase: String, sampleRateHz: Int = 16_000): ShortArray {
        val durationSeconds = when {
            phrase.contains("fire", ignoreCase = true) -> 1.8f
            phrase.contains("help", ignoreCase = true) -> 1.5f
            else -> 2.0f
        }
        val totalSamples = (sampleRateHz * durationSeconds).toInt()
        val samples = ShortArray(totalSamples)

        // Fundamental frequencies and vowel formants (F0=130Hz, F1=730Hz, F2=1090Hz for vocal timbre)
        val f0 = 130.0
        val f1 = 730.0
        val f2 = 1090.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRateHz
            // Envelope with natural rise and fall
            val envelope = sin(Math.PI * (i.toDouble() / totalSamples))
            val signal = (0.5 * sin(2.0 * Math.PI * f0 * t) +
                    0.3 * sin(2.0 * Math.PI * f1 * t) +
                    0.2 * sin(2.0 * Math.PI * f2 * t)) * envelope

            samples[i] = (signal * 22000).toInt().coerceIn(-32767, 32767).toShort()
        }
        return samples
    }

    companion object {
        private const val TAG = "AudioDebugHelper"
    }
}
