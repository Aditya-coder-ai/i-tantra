package com.itantra.offlinevoice.audio

import kotlin.math.sqrt

/** Lightweight integer PCM level meter; it performs no spectral or speech processing. */
class AudioLevelAnalyzer {
    /** Writes into [output], allowing the recording loop to reuse one level object. */
    fun analyze(samples: ShortArray, count: Int, output: AudioLevel) {
        if (count <= 0) {
            output.rms = 0f
            output.peak = 0f
            return
        }

        var sumSquares = 0.0
        var peak = 0
        for (index in 0 until count) {
            val value = samples[index].toInt()
            val magnitude = if (value == Short.MIN_VALUE.toInt()) 32_768 else kotlin.math.abs(value)
            if (magnitude > peak) peak = magnitude
            sumSquares += value.toDouble() * value
        }
        output.rms = (sqrt(sumSquares / count) / 32_768.0).toFloat()
        output.peak = peak / 32_768f
    }
}

class AudioLevel(var rms: Float = 0f, var peak: Float = 0f)
