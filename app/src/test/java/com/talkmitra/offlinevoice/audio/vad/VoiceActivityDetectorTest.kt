package com.talkmitra.offlinevoice.audio.vad

import com.talkmitra.offlinevoice.audio.AudioChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class VoiceActivityDetectorTest {

    @Test
    fun testSilenceDoesNotTriggerSpeech() {
        var speechStarted = false
        var speechEnded = false
        val detector = VoiceActivityDetector(
            config = VadConfig(speechOnsetFrames = 3, speechHangoverFrames = 5),
            listener = object : VoiceActivityDetector.Listener {
                override fun onSpeechStart(timestampNanos: Long) { speechStarted = true }
                override fun onSpeechEnd(segment: SpeechSegment) { speechEnded = true }
                override fun onVadStateChanged(isSpeaking: Boolean, confidence: Float) {}
            }
        )

        val silenceChunk = AudioChunk(ShortArray(320) { 0 }).apply { sampleCount = 320 }
        repeat(20) {
            detector.processChunk(silenceChunk)
        }

        assertFalse("Silence should not trigger speech start", speechStarted)
        assertFalse("Silence should not trigger speech end", speechEnded)
    }

    @Test
    fun testVoicedFramesTriggerSpeechAndEndWithSegment() {
        var speechStarted = false
        var capturedSegment: SpeechSegment? = null

        val detector = VoiceActivityDetector(
            config = VadConfig(
                speechOnsetFrames = 3,
                speechHangoverFrames = 5,
                preRollFrames = 4,
                energyThresholdDb = 6.0f,
                minSpeechRms = 0.005f
            ),
            listener = object : VoiceActivityDetector.Listener {
                override fun onSpeechStart(timestampNanos: Long) {
                    speechStarted = true
                }

                override fun onSpeechEnd(segment: SpeechSegment) {
                    capturedSegment = segment
                }

                override fun onVadStateChanged(isSpeaking: Boolean, confidence: Float) {}
            }
        )

        // 1. Send 10 silence frames to establish baseline noise floor
        val silenceChunk = AudioChunk(ShortArray(320) { 0 }).apply { sampleCount = 320 }
        repeat(10) { detector.processChunk(silenceChunk) }

        // 2. Generate a 400 Hz sine wave tone representing speech (~30% amplitude)
        val voiceChunk = AudioChunk(ShortArray(320) { i ->
            (sin(2.0 * Math.PI * 400.0 * i / 16000.0) * 10000).toInt().toShort()
        }).apply { sampleCount = 320 }

        // Send 6 voice frames
        repeat(6) { detector.processChunk(voiceChunk) }
        assertTrue("Voiced frames should trigger speech start", speechStarted)

        // 3. Send 10 silence frames to complete hangover and finish utterance
        repeat(10) { detector.processChunk(silenceChunk) }

        val segment = capturedSegment
        assertTrue("Speech end should emit a SpeechSegment", segment != null)
        if (segment != null) {
            assertTrue("Segment should have duration > 0", segment.durationMs > 0)
            assertTrue("Segment should have samples", segment.samples.isNotEmpty())
            val byteArray = segment.toByteArray()
            assertEquals(segment.samples.size * 2, byteArray.size)
        }
    }

    @Test
    fun testFlushForcesUtteranceEmission() {
        var capturedSegment: SpeechSegment? = null
        val detector = VoiceActivityDetector(
            config = VadConfig(speechOnsetFrames = 2, minSpeechRms = 0.005f),
            listener = object : VoiceActivityDetector.Listener {
                override fun onSpeechStart(timestampNanos: Long) {}
                override fun onSpeechEnd(segment: SpeechSegment) {
                    capturedSegment = segment
                }
                override fun onVadStateChanged(isSpeaking: Boolean, confidence: Float) {}
            }
        )

        val voiceChunk = AudioChunk(ShortArray(320) { i ->
            (sin(2.0 * Math.PI * 300.0 * i / 16000.0) * 8000).toInt().toShort()
        }).apply { sampleCount = 320 }

        repeat(5) { detector.processChunk(voiceChunk) }
        detector.flush()

        assertTrue("Flush should immediately finalize ongoing speech segment", capturedSegment != null)
    }
}
