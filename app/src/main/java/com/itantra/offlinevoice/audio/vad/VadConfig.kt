package com.itantra.offlinevoice.audio.vad

/**
 * Configuration parameters for the Voice Activity Detector (VAD).
 *
 * @property sampleRateHz Sampling rate in Hz (expected 16,000 Hz).
 * @property chunkDurationMs Duration of each incoming PCM chunk (expected 20 ms).
 * @property energyThresholdDb Energy delta in dB required above dynamic noise floor to consider a frame voiced.
 * @property minSpeechRms Minimum absolute RMS amplitude threshold to guard against ambient floor shifts in quiet rooms.
 * @property minZcr Minimum zero-crossing rate to reject extreme low-frequency rumble.
 * @property maxZcr Maximum zero-crossing rate to reject pure high-frequency hiss.
 * @property speechOnsetFrames Consecutive voiced frames required to transition from SILENCE to SPEECH (e.g. 3 frames = 60 ms).
 * @property speechHangoverFrames Consecutive silent frames required to transition from SPEECH to SILENCE (e.g. 20 frames = 400 ms).
 * @property preRollFrames Number of preceding 20 ms frames preserved in ring buffer to prevent clipping leading phonemes.
 * @property maxUtteranceMs Maximum permitted duration for a single speech segment before forced split (memory safety).
 */
data class VadConfig(
    val sampleRateHz: Int = 16_000,
    val chunkDurationMs: Int = 20,
    val energyThresholdDb: Float = 12.0f,
    val minSpeechRms: Float = 0.012f,
    val minZcr: Float = 0.01f,
    val maxZcr: Float = 0.65f,
    val speechOnsetFrames: Int = 3,
    val speechHangoverFrames: Int = 22,
    val preRollFrames: Int = 15,
    val maxUtteranceMs: Long = 30_000L
) {
    val chunkSamples: Int get() = sampleRateHz * chunkDurationMs / 1_000
    val maxUtteranceSamples: Int get() = (sampleRateHz * (maxUtteranceMs / 1_000.0)).toInt()
}
