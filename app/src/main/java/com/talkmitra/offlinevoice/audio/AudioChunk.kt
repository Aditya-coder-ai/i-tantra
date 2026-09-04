package com.talkmitra.offlinevoice.audio

/**
 * A view of the recorder's reusable PCM buffer.
 *
 * Samples are signed 16-bit, mono PCM at [AudioConfig.sampleRateHz]. Consumers must finish
 * reading this chunk before their callback returns and must not retain it: its backing array is
 * reused by the next microphone read to avoid per-frame allocations.
 */
class AudioChunk internal constructor(
    val samples: ShortArray
) {
    var sampleCount: Int = 0
        internal set
    var timestampNanos: Long = 0L
        internal set
}
