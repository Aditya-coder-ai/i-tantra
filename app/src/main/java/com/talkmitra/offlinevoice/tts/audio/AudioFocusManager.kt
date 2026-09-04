package com.talkmitra.offlinevoice.tts.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages Android audio focus for TTS playback.
 *
 * Normal messages request standard focus; emergency messages request
 * high-priority focus with ducking so the alert is audible even when
 * other audio is playing.
 *
 * Audio-focus behaviour is configurable via [FocusPolicy].
 */
class AudioFocusManager(context: Context) {

    companion object {
        private const val TAG = "AudioFocusMgr"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    /** Callback invoked when audio focus changes. */
    var onFocusChange: ((FocusState) -> Unit)? = null

    /** Current policy for handling focus-loss events. */
    var focusPolicy = FocusPolicy.PAUSE_ON_LOSS

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Requests audio focus.
     *
     * @param isEmergency If `true`, requests with USAGE_ALARM for higher priority.
     * @return `true` if focus was granted.
     */
    fun requestFocus(isEmergency: Boolean = false): Boolean {
        val usage = if (isEmergency) {
            AudioAttributes.USAGE_ALARM
        } else {
            AudioAttributes.USAGE_MEDIA
        }

        val contentType = if (isEmergency) {
            AudioAttributes.CONTENT_TYPE_SONIFICATION
        } else {
            AudioAttributes.CONTENT_TYPE_SPEECH
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(contentType)
            .build()

        val focusGain = if (isEmergency) {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        } else {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAcceptsDelayedFocusGain(false)
                .build()

            val result = audioManager.requestAudioFocus(request)
            hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            if (hasFocus) focusRequest = request

            Log.d(TAG, "Focus request (API 26+): granted=$hasFocus, emergency=$isEmergency")
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                focusChangeListenerLegacy,
                AudioManager.STREAM_MUSIC,
                focusGain
            )
            hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            Log.d(TAG, "Focus request (legacy): granted=$hasFocus")
        }

        if (hasFocus) onFocusChange?.invoke(FocusState.GAINED)
        return hasFocus
    }

    /** Releases any held audio focus. */
    fun abandonFocus() {
        if (!hasFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListenerLegacy)
        }

        hasFocus = false
        onFocusChange?.invoke(FocusState.ABANDONED)
        Log.d(TAG, "Audio focus abandoned")
    }

    /** Returns `true` if we currently hold audio focus. */
    fun hasFocus(): Boolean = hasFocus

    // ── Listeners ────────────────────────────────────────────────────

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        handleFocusChange(change)
    }

    @Suppress("DEPRECATION")
    private val focusChangeListenerLegacy = AudioManager.OnAudioFocusChangeListener { change ->
        handleFocusChange(change)
    }

    private fun handleFocusChange(change: Int) {
        val state = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                FocusState.GAINED
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                FocusState.LOST_PERMANENTLY
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false
                FocusState.LOST_TRANSIENT
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                FocusState.DUCKED
            }
            else -> return
        }
        Log.d(TAG, "Focus change: $state")
        onFocusChange?.invoke(state)
    }

    /** Audio focus states reported to the callback. */
    enum class FocusState {
        GAINED,
        LOST_PERMANENTLY,
        LOST_TRANSIENT,
        DUCKED,
        ABANDONED
    }

    /** Policy for what the player should do on focus loss. */
    enum class FocusPolicy {
        /** Pause playback and resume when focus is regained. */
        PAUSE_ON_LOSS,
        /** Lower volume when focus is ducked; stop on permanent loss. */
        DUCK_ON_LOSS,
        /** Ignore focus changes (not recommended). */
        IGNORE
    }
}
