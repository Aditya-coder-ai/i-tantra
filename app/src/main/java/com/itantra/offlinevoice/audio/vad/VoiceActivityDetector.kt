package com.itantra.offlinevoice.audio.vad

import com.itantra.offlinevoice.audio.AudioChunk
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Offline Voice Activity Detector (VAD) with adaptive noise-floor estimation,
 * circular pre-roll buffer, and hangover smoothing.
 *
 * Incoming [AudioChunk] buffers are consumed and copied safely to avoid retaining
 * the recorder's reusable buffer.
 */
class VoiceActivityDetector(
    private val config: VadConfig = VadConfig(),
    private val listener: Listener
) {
    interface Listener {
        fun onSpeechStart(timestampNanos: Long)
        fun onSpeechEnd(segment: SpeechSegment)
        fun onVadStateChanged(isSpeaking: Boolean, confidence: Float)
    }

    enum class State {
        SILENCE,
        POSSIBLE_SPEECH,
        SPEECH,
        HANGOVER
    }

    private val lock = Any()

    @Volatile
    var state: State = State.SILENCE
        private set

    // Adaptive noise floor in dBFS (initialized to realistic ambient baseline)
    private var noiseFloorDb = -55.0f

    // Consecutive frame counters
    private var voiceFrameCount = 0
    private var silenceFrameCount = 0

    // Circular pre-roll buffer to preserve leading phonemes
    private val preRollCapacity = config.preRollFrames
    private val preRollBuffer = Array(preRollCapacity) { ShortArray(config.chunkSamples) }
    private var preRollHead = 0
    private var preRollSize = 0

    // Active speech accumulator
    private val speechChunks = ArrayList<ShortArray>()
    private var speechStartTimestampNanos = 0L
    private var totalAccumulatedSamples = 0

    /**
     * Process one audio chunk. Safe to call from the audio recording thread.
     * Deep-copies the PCM samples when necessary.
     */
    fun processChunk(chunk: AudioChunk) {
        val sampleCount = chunk.sampleCount
        if (sampleCount <= 0) return

        val samples = chunk.samples
        val rms = computeRms(samples, sampleCount)
        val db = if (rms > 0f) (20.0 * log10(max(rms.toDouble(), 1e-5))).toFloat() else -90f
        val zcr = computeZcr(samples, sampleCount)

        synchronized(lock) {
            val isVoiced = evaluateVoicedFrame(rms, db, zcr)
            val confidence = computeConfidence(rms, db, zcr, isVoiced)

            when (state) {
                State.SILENCE -> {
                    updateNoiseFloor(db)
                    if (isVoiced) {
                        voiceFrameCount = 1
                        silenceFrameCount = 0
                        state = if (voiceFrameCount >= config.speechOnsetFrames) {
                            beginSpeechLocked(chunk.timestampNanos)
                        } else {
                            State.POSSIBLE_SPEECH
                        }
                    }
                    storePreRoll(samples, sampleCount)
                }

                State.POSSIBLE_SPEECH -> {
                    storePreRoll(samples, sampleCount)
                    if (isVoiced) {
                        voiceFrameCount++
                        if (voiceFrameCount >= config.speechOnsetFrames) {
                            state = beginSpeechLocked(chunk.timestampNanos)
                        }
                    } else {
                        // False alarm; revert to silence
                        voiceFrameCount = 0
                        state = State.SILENCE
                        listener.onVadStateChanged(isSpeaking = false, confidence = 0f)
                    }
                }

                State.SPEECH -> {
                    appendSpeechChunkLocked(samples, sampleCount)
                    if (isVoiced) {
                        silenceFrameCount = 0
                    } else {
                        silenceFrameCount++
                        if (silenceFrameCount >= 1) {
                            state = State.HANGOVER
                        }
                    }
                    checkMaxUtteranceLocked()
                }

                State.HANGOVER -> {
                    appendSpeechChunkLocked(samples, sampleCount)
                    if (isVoiced) {
                        silenceFrameCount = 0
                        state = State.SPEECH
                    } else {
                        silenceFrameCount++
                        if (silenceFrameCount >= config.speechHangoverFrames) {
                            finishSpeechLocked()
                        }
                    }
                    checkMaxUtteranceLocked()
                }
            }

            val isCurrentlySpeaking = (state == State.SPEECH || state == State.HANGOVER)
            listener.onVadStateChanged(isCurrentlySpeaking, confidence)
        }
    }

    private fun evaluateVoicedFrame(rms: Float, db: Float, zcr: Float): Boolean {
        val deltaDb = db - noiseFloorDb
        val energyPass = (deltaDb >= config.energyThresholdDb) || (rms >= config.minSpeechRms * 1.8f)
        val minAmplitudePass = rms >= config.minSpeechRms
        val zcrPass = zcr in config.minZcr..config.maxZcr
        return energyPass && minAmplitudePass && zcrPass
    }

    private fun computeConfidence(rms: Float, db: Float, zcr: Float, isVoiced: Boolean): Float {
        if (!isVoiced) return 0f
        val deltaDb = max(0f, db - noiseFloorDb)
        val energyScore = (deltaDb / 25f).coerceIn(0f, 1f)
        val rmsScore = (rms / 0.15f).coerceIn(0f, 1f)
        return (0.6f * energyScore + 0.4f * rmsScore).coerceIn(0f, 1f)
    }

    private fun updateNoiseFloor(currentDb: Float) {
        if (currentDb < noiseFloorDb) {
            // Rapid downward tracking when environment gets quieter
            noiseFloorDb = noiseFloorDb * 0.85f + currentDb * 0.15f
        } else if (currentDb < noiseFloorDb + 8.0f) {
            // Slow upward drift tracking ambient noise rise without chasing speech
            noiseFloorDb = noiseFloorDb * 0.98f + currentDb * 0.02f
        }
        noiseFloorDb = noiseFloorDb.coerceIn(-85f, -20f)
    }

    private fun storePreRoll(samples: ShortArray, count: Int) {
        val target = preRollBuffer[preRollHead]
        System.arraycopy(samples, 0, target, 0, count)
        preRollHead = (preRollHead + 1) % preRollCapacity
        if (preRollSize < preRollCapacity) preRollSize++
    }

    private fun beginSpeechLocked(timestampNanos: Long): State {
        speechStartTimestampNanos = timestampNanos
        speechChunks.clear()
        totalAccumulatedSamples = 0

        // Extract pre-roll in chronological order
        val startIndex = if (preRollSize < preRollCapacity) 0 else preRollHead
        for (i in 0 until preRollSize) {
            val index = (startIndex + i) % preRollCapacity
            val source = preRollBuffer[index]
            val chunkCopy = source.copyOf()
            speechChunks.add(chunkCopy)
            totalAccumulatedSamples += chunkCopy.size
        }

        // Reset pre-roll
        preRollSize = 0
        preRollHead = 0

        listener.onSpeechStart(timestampNanos)
        return State.SPEECH
    }

    private fun appendSpeechChunkLocked(samples: ShortArray, count: Int) {
        val chunkCopy = ShortArray(count)
        System.arraycopy(samples, 0, chunkCopy, 0, count)
        speechChunks.add(chunkCopy)
        totalAccumulatedSamples += count
    }

    private fun checkMaxUtteranceLocked() {
        if (totalAccumulatedSamples >= config.maxUtteranceSamples) {
            finishSpeechLocked()
        }
    }

    private fun finishSpeechLocked() {
        if (speechChunks.isNotEmpty()) {
            val flattened = ShortArray(totalAccumulatedSamples)
            var offset = 0
            for (chunk in speechChunks) {
                System.arraycopy(chunk, 0, flattened, offset, chunk.size)
                offset += chunk.size
            }
            val segment = SpeechSegment(
                samples = flattened,
                sampleRateHz = config.sampleRateHz,
                startTimestampNanos = speechStartTimestampNanos
            )
            listener.onSpeechEnd(segment)
        }

        speechChunks.clear()
        totalAccumulatedSamples = 0
        voiceFrameCount = 0
        silenceFrameCount = 0
        state = State.SILENCE
    }

    /** Flushes any ongoing speech utterance and resets VAD state. */
    fun flush() = synchronized(lock) {
        if (state == State.SPEECH || state == State.HANGOVER || speechChunks.isNotEmpty()) {
            finishSpeechLocked()
        } else if (preRollSize > 0) {
            // Manual push-to-talk release: extract preserved pre-roll frames so short speech isn't lost
            beginSpeechLocked(System.nanoTime())
            finishSpeechLocked()
        } else {
            reset()
        }
    }

    /** Fully resets VAD detector buffers and baseline. */
    fun reset() = synchronized(lock) {
        speechChunks.clear()
        totalAccumulatedSamples = 0
        voiceFrameCount = 0
        silenceFrameCount = 0
        preRollSize = 0
        preRollHead = 0
        noiseFloorDb = -55.0f
        state = State.SILENCE
    }

    private fun computeRms(samples: ShortArray, count: Int): Float {
        var sumSquares = 0.0
        for (i in 0 until count) {
            val v = samples[i].toDouble()
            sumSquares += v * v
        }
        return (sqrt(sumSquares / count) / 32768.0).toFloat()
    }

    private fun computeZcr(samples: ShortArray, count: Int): Float {
        if (count <= 1) return 0f
        var zeroCrossings = 0
        for (i in 1 until count) {
            val prev = samples[i - 1]
            val curr = samples[i]
            if ((prev >= 0 && curr < 0) || (prev < 0 && curr >= 0)) {
                zeroCrossings++
            }
        }
        return zeroCrossings.toFloat() / count
    }
}
