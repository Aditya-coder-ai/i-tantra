package com.talkmitra.offlinevoice.tts.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/**
 * Plays raw PCM 16-bit mono audio through Android's [AudioTrack] API.
 *
 * Supports:
 * - Immediate playback of a complete buffer.
 * - Pause / resume / stop.
 * - Awaiting completion of the current chunk.
 *
 * All heavy work (writing to AudioTrack) runs on a dedicated background
 * thread so the caller is never blocked on audio I/O.
 */
class TTSAudioPlayer {

    companion object {
        private const val TAG = "TTSAudioPlayer"
    }

    /** Observable playback state. */
    enum class PlaybackState { IDLE, PLAYING, PAUSED, STOPPED, ERROR }

    private val state = AtomicReference(PlaybackState.IDLE)
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    /** Callback invoked when playback state changes (called from the playback thread). */
    var onStateChange: ((PlaybackState) -> Unit)? = null

    /** Currently active sample rate. */
    var currentSampleRate: Int = 22050
        private set

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Plays raw PCM 16-bit mono audio.
     *
     * If audio is already playing it is stopped first.
     *
     * @param audioData PCM samples (16-bit signed, mono).
     * @param sampleRate Sample rate in Hz (e.g. 22050).
     * @param isEmergency If true, uses USAGE_ALARM attributes.
     */
    fun play(
        audioData: ShortArray,
        sampleRate: Int = 22050,
        isEmergency: Boolean = false
    ) {
        stop() // ensure clean state

        currentSampleRate = sampleRate

        val usage = if (isEmergency) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val contentType = if (isEmergency) {
            AudioAttributes.CONTENT_TYPE_SONIFICATION
        } else {
            AudioAttributes.CONTENT_TYPE_SPEECH
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(contentType)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(audioData.size * 2)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioTrack: ${e.message}")
            setState(PlaybackState.ERROR)
            return
        }

        val track = audioTrack ?: return

        // Write data and start playback
        track.write(audioData, 0, audioData.size)
        track.setNotificationMarkerPosition(audioData.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                setState(PlaybackState.IDLE)
            }
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })

        setState(PlaybackState.PLAYING)
        track.play()

        Log.d(TAG, "Playing ${audioData.size} samples at ${sampleRate}Hz")
    }

    /**
     * Plays audio on a background thread using STREAM mode.
     * Better for large audio buffers — writes in chunks.
     */
    fun playStreaming(audioData: ShortArray, sampleRate: Int = 22050, isEmergency: Boolean = false) {
        stop()

        currentSampleRate = sampleRate

        val usage = if (isEmergency) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create streaming AudioTrack: ${e.message}")
            setState(PlaybackState.ERROR)
            return
        }

        val track = audioTrack ?: return

        setState(PlaybackState.PLAYING)
        track.play()

        playbackThread = thread(name = "tts-audio-playback") {
            try {
                val chunkSize = minBuf / 2 // in shorts
                var offset = 0
                while (offset < audioData.size && state.get() == PlaybackState.PLAYING) {
                    val remaining = audioData.size - offset
                    val toWrite = minOf(chunkSize, remaining)
                    val written = track.write(audioData, offset, toWrite)
                    if (written < 0) {
                        Log.e(TAG, "AudioTrack.write() returned $written")
                        break
                    }
                    offset += written
                }
                // Wait for playback to drain
                if (state.get() == PlaybackState.PLAYING) {
                    track.stop()
                    setState(PlaybackState.IDLE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback thread error: ${e.message}")
                setState(PlaybackState.ERROR)
            }
        }

        Log.d(TAG, "Streaming ${audioData.size} samples at ${sampleRate}Hz")
    }

    /** Stops playback immediately. */
    fun stop() {
        setState(PlaybackState.STOPPED)
        try {
            audioTrack?.stop()
        } catch (_: IllegalStateException) { /* already stopped */ }
        audioTrack?.release()
        audioTrack = null
        playbackThread?.interrupt()
        playbackThread = null
        setState(PlaybackState.IDLE)
    }

    /** Pauses playback. Can be resumed with [resume]. */
    fun pause() {
        if (state.get() != PlaybackState.PLAYING) return
        try {
            audioTrack?.pause()
            setState(PlaybackState.PAUSED)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Pause failed: ${e.message}")
        }
    }

    /** Resumes previously paused playback. */
    fun resume() {
        if (state.get() != PlaybackState.PAUSED) return
        try {
            audioTrack?.play()
            setState(PlaybackState.PLAYING)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Resume failed: ${e.message}")
        }
    }

    /** Current playback state. */
    fun getState(): PlaybackState = state.get()

    /** Returns `true` if audio is currently playing. */
    fun isPlaying(): Boolean = state.get() == PlaybackState.PLAYING

    /** Releases all resources. Call when the TTS engine is destroyed. */
    fun release() {
        stop()
    }

    /**
     * Suspends until the current audio chunk finishes playing.
     * Returns immediately if nothing is playing.
     */
    suspend fun awaitCompletion() {
        if (state.get() != PlaybackState.PLAYING) return

        suspendCancellableCoroutine { continuation ->
            val previousListener = onStateChange
            onStateChange = { newState ->
                previousListener?.invoke(newState)
                if (newState != PlaybackState.PLAYING && newState != PlaybackState.PAUSED) {
                    onStateChange = previousListener
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            // Check again — playback might have finished between the check and setting the listener
            if (state.get() != PlaybackState.PLAYING) {
                onStateChange = previousListener
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    // ── Internal ─────────────────────────────────────────────────────

    private fun setState(newState: PlaybackState) {
        val old = state.getAndSet(newState)
        if (old != newState) {
            onStateChange?.invoke(newState)
        }
    }
}
