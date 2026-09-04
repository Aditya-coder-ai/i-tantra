package com.talkmitra.offlinevoice.tts.queue

import android.content.Context
import android.util.Log
import com.talkmitra.offlinevoice.text.MessagePriority
import com.talkmitra.offlinevoice.text.MessageType
import com.talkmitra.offlinevoice.text.ProcessedMessage
import com.talkmitra.offlinevoice.tts.TTSConfig
import com.talkmitra.offlinevoice.tts.TTSEngine
import com.talkmitra.offlinevoice.tts.TTSException
import com.talkmitra.offlinevoice.tts.TTSLanguage
import com.talkmitra.offlinevoice.tts.TTSResult
import com.talkmitra.offlinevoice.tts.audio.AudioFocusManager
import com.talkmitra.offlinevoice.tts.audio.TTSAudioPlayer
import com.talkmitra.offlinevoice.tts.engine.OfflineTTSEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Top-level orchestrator for the TTS playback pipeline.
 *
 * Accepts [ProcessedMessage] objects, queues them by priority,
 * and sequentially synthesises + plays each one through the audio system.
 *
 * Architecture:
 * ```
 * ProcessedMessage
 *        ↓
 *   TTSQueue (priority ordering)
 *        ↓
 *   TTSEngine.synthesize()
 *        ↓
 *   TTSAudioPlayer.play()
 *        ↓
 *   🔊 Speaker
 * ```
 *
 * Emergency messages bypass the normal queue and are handled by
 * [EmergencyTTSManager].
 */
class TTSPlaybackManager(
    private val context: Context,
    private val config: TTSConfig = TTSConfig()
) {
    companion object {
        private const val TAG = "TTSPlayback"
    }

    /** Observable state of the playback pipeline. */
    enum class PlaybackState {
        IDLE,
        PREPROCESSING,
        SYNTHESIZING,
        PLAYING,
        QUEUED,
        PAUSED,
        ERROR
    }

    // ── Components ───────────────────────────────────────────────────

    private val engine: TTSEngine = OfflineTTSEngine(context, config)
    private val queue = TTSQueue()
    private val audioPlayer = TTSAudioPlayer()
    private val audioFocusManager = AudioFocusManager(context)
    private val emergencyManager = EmergencyTTSManager(context, engine, audioFocusManager)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var consumeJob: Job? = null

    // ── Observable state ─────────────────────────────────────────────

    @Volatile
    var playbackState: PlaybackState = PlaybackState.IDLE
        private set

    /** Callback invoked on state changes. */
    var onStateChange: ((PlaybackState) -> Unit)? = null

    /** Callback invoked when a TTS result is generated (for benchmarking UI). */
    var onTTSResult: ((TTSResult) -> Unit)? = null

    /** Callback invoked on error. */
    var onError: ((TTSException) -> Unit)? = null

    /** Currently playing message (null when idle). */
    var currentMessage: ProcessedMessage? = null
        private set

    // ── Lifecycle ────────────────────────────────────────────────────

    /** Initialises the engine. Call once before [speakMessage]. */
    fun initialize(): Boolean {
        val ok = engine.initialize(config)
        if (ok) {
            // Wire emergency callback
            queue.onEmergencyEnqueued = { msg ->
                scope.launch { emergencyManager.handleEmergency(msg) }
            }
            startConsuming()
        }
        return ok
    }

    /** Releases all resources. */
    fun release() {
        consumeJob?.cancel()
        scope.cancel()
        engine.release()
        audioPlayer.release()
        audioFocusManager.abandonFocus()
        queue.clear()
        setState(PlaybackState.IDLE)
        Log.i(TAG, "TTSPlaybackManager released")
    }

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Accepts a [ProcessedMessage] for TTS playback.
     *
     * The message is queued by priority and will be synthesised and
     * played in order. EMERGENCY/CRITICAL messages interrupt normal flow.
     */
    fun speakMessage(message: ProcessedMessage) {
        Log.i(TAG, "Received message: id=${message.messageId}, lang=${message.language}, " +
                "type=${message.messageType}, priority=${message.priority}")

        // Route emergency messages directly
        if (message.messageType == MessageType.EMERGENCY ||
            message.priority == MessagePriority.CRITICAL
        ) {
            scope.launch {
                emergencyManager.handleEmergency(message)
            }
            return
        }

        queue.enqueue(message)
        setState(PlaybackState.QUEUED)
    }

    /** Stops all playback and clears the queue. */
    fun stopAll() {
        queue.clear()
        engine.stop()
        audioPlayer.stop()
        audioFocusManager.abandonFocus()
        emergencyManager.stop()
        setState(PlaybackState.IDLE)
    }

    /** Pauses current playback. */
    fun pause() {
        audioPlayer.pause()
        engine.pause()
        setState(PlaybackState.PAUSED)
    }

    /** Resumes paused playback. */
    fun resume() {
        audioPlayer.resume()
        engine.resume()
        setState(PlaybackState.PLAYING)
    }

    /** Number of messages waiting in the queue. */
    fun queueSize(): Int = queue.size()

    /** Replays the current/last message. */
    fun replay() {
        currentMessage?.let { speakMessage(it) }
    }

    // ── Queue consumer ───────────────────────────────────────────────

    /**
     * Starts a coroutine that continuously drains the queue.
     * Each message is synthesised and played sequentially.
     */
    private fun startConsuming() {
        consumeJob = scope.launch {
            while (true) {
                val message = queue.dequeue()
                if (message != null) {
                    processMessage(message)
                } else {
                    // No messages — brief sleep before polling again
                    kotlinx.coroutines.delay(100)
                }
            }
        }
    }

    /**
     * Processes a single message through the full pipeline:
     * resolve language → preprocess → synthesise → play.
     */
    private suspend fun processMessage(message: ProcessedMessage) {
        currentMessage = message

        val language = TTSLanguage.fromCode(message.language)
        if (language == null) {
            val error = TTSException.LanguageUnsupportedException(message.language)
            Log.e(TAG, error.message ?: "Unsupported language")
            onError?.invoke(error)
            setState(PlaybackState.ERROR)
            return
        }

        try {
            // Request audio focus
            setState(PlaybackState.PREPROCESSING)
            audioFocusManager.requestFocus(isEmergency = false)

            // Synthesise
            setState(PlaybackState.SYNTHESIZING)
            val result = engine.synthesize(message.text, language)
            onTTSResult?.invoke(result)

            // Play
            setState(PlaybackState.PLAYING)
            audioPlayer.playStreaming(result.audioData, result.sampleRate)
            audioPlayer.awaitCompletion()

            Log.i(TAG, "Played message ${message.messageId}: " +
                    "RTF=${result.realTimeFactor}, " +
                    "processingMs=${result.processingTimeMs}, " +
                    "audioMs=${result.audioDurationMs}")

        } catch (e: TTSException) {
            Log.e(TAG, "TTS error: ${e.message}")
            onError?.invoke(e)
            setState(PlaybackState.ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
            onError?.invoke(TTSException.InferenceException("Unexpected error: ${e.message}", e))
            setState(PlaybackState.ERROR)
        } finally {
            audioFocusManager.abandonFocus()
            if (queue.isEmpty()) {
                setState(PlaybackState.IDLE)
                currentMessage = null
            }
        }
    }

    private fun setState(newState: PlaybackState) {
        if (playbackState != newState) {
            playbackState = newState
            onStateChange?.invoke(newState)
        }
    }
}
