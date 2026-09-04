package com.talkmitra.offlinevoice.audio.vad

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Represents a discrete spoken utterance captured by the Voice Activity Detector.
 *
 * @property samples Full 16-bit PCM samples encompassing the pre-roll lead-in, active speech, and hangover tail.
 * @property sampleRateHz Sampling rate of the captured audio in Hz.
 * @property startTimestampNanos Timestamp in nanoseconds when speech onset occurred.
 */
class SpeechSegment(
    val samples: ShortArray,
    val sampleRateHz: Int,
    val startTimestampNanos: Long
) {
    val durationMs: Long
        get() = (samples.size * 1_000L) / sampleRateHz

    val sampleCount: Int
        get() = samples.size

    /** Converts the 16-bit PCM ShortArray to little-endian byte array format for downstream encoders or STT engines. */
    fun toByteArray(): ByteArray {
        val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            byteBuffer.putShort(sample)
        }
        return byteBuffer.array()
    }
}
