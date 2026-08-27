package com.itantra.offlinevoice.audio

import android.media.AudioFormat
import android.media.MediaRecorder

/** PCM format consumed by the later VAD and offline STT stages. */
data class AudioConfig(
    val sampleRateHz: Int = 16_000,
    val audioSource: Int = MediaRecorder.AudioSource.MIC,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
    val chunkDurationMs: Int = 20
) {
    val chunkSamples: Int get() = sampleRateHz * chunkDurationMs / 1_000
    val bytesPerSample: Int get() = 2
}
