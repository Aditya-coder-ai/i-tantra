package com.talkmitra.offlinevoice.tts.queue

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.talkmitra.offlinevoice.text.ProcessedMessage
import com.talkmitra.offlinevoice.tts.TTSConfig
import com.talkmitra.offlinevoice.tts.TTSEngine
import com.talkmitra.offlinevoice.tts.TTSLanguage
import com.talkmitra.offlinevoice.tts.audio.AudioFocusManager
import com.talkmitra.offlinevoice.tts.audio.TTSAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Dedicated emergency playback path for CRITICAL / EMERGENCY messages.
 *
 * When an emergency message arrives:
 * 1. Current normal playback is interrupted.
 * 2. High-priority audio focus is requested (USAGE_ALARM).
 * 3. A brief alert tone is played.
 * 4. The emergency text is synthesised and played.
 * 5. The message is repeated [TTSConfig.emergencyRepeatCount] times.
 * 6. Haptic feedback is triggered (if VIBRATE permission is granted).
 *
 * This class does NOT:
 * - Force device volume to maximum (respects user settings).
 * - Bypass Do Not Disturb (uses USAGE_ALARM which Android may respect).
 * - Handle decryption (receives only authenticated ProcessedMessage).
 */
class EmergencyTTSManager(
    private val context: Context,
    private val engine: TTSEngine,
    private val audioFocusManager: AudioFocusManager,
    private val config: TTSConfig = TTSConfig()
) {
    companion object {
        private const val TAG = "EmergencyTTS"
        /** Duration of the alert tone before the message (ms). */
        private const val ALERT_TONE_DURATION_MS = 1500L
        /** Pause between message repeats (ms). */
        private const val REPEAT_PAUSE_MS = 500L
    }

    private val audioPlayer = TTSAudioPlayer()

    @Volatile
    private var isHandlingEmergency = false

    /** Callback invoked when emergency handling starts/ends. */
    var onEmergencyStateChange: ((Boolean) -> Unit)? = null

    /**
     * Handles an emergency message with maximum urgency.
     *
     * This is a suspend function — it blocks until the full emergency
     * sequence (tone + message × repeats) completes or is interrupted.
     */
    suspend fun handleEmergency(message: ProcessedMessage) {
        if (isHandlingEmergency) {
            Log.w(TAG, "Already handling an emergency — queueing")
            return
        }

        isHandlingEmergency = true
        onEmergencyStateChange?.invoke(true)

        Log.w(TAG, "🚨 EMERGENCY: ${message.messageId} — \"${message.text.take(50)}\"")

        try {
            // 1. Request high-priority audio focus
            audioFocusManager.requestFocus(isEmergency = true)

            // 2. Trigger haptic feedback
            triggerHapticFeedback()

            // 3. Play alert tone
            playAlertTone()

            // 4. Resolve language
            val language = TTSLanguage.fromCode(message.language) ?: TTSLanguage.ENGLISH

            // 5. Synthesise and repeat the message
            val repeatCount = config.emergencyRepeatCount.coerceAtLeast(1)
            for (i in 1..repeatCount) {
                Log.i(TAG, "Emergency playback: repeat $i/$repeatCount")

                val result = engine.synthesize(message.text, language)
                audioPlayer.play(result.audioData, result.sampleRate, isEmergency = true)
                audioPlayer.awaitCompletion()

                if (i < repeatCount) {
                    delay(REPEAT_PAUSE_MS)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Emergency playback failed: ${e.message}", e)
        } finally {
            isHandlingEmergency = false
            audioFocusManager.abandonFocus()
            onEmergencyStateChange?.invoke(false)
        }
    }

    /** Stops emergency playback immediately. */
    fun stop() {
        audioPlayer.stop()
        isHandlingEmergency = false
    }

    /** Returns `true` if an emergency is currently being handled. */
    fun isActive(): Boolean = isHandlingEmergency

    // ── Alert tone ───────────────────────────────────────────────────

    /**
     * Plays the system alarm ringtone for [ALERT_TONE_DURATION_MS].
     * Falls back to a generated beep if no system tone is available.
     */
    private suspend fun playAlertTone() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (alarmUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                ringtone?.play()
                delay(ALERT_TONE_DURATION_MS)
                ringtone?.stop()
            } else {
                // Fallback: generate a 880Hz beep
                playGeneratedAlertBeep()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not play alert tone: ${e.message}")
            playGeneratedAlertBeep()
        }
    }

    /**
     * Generates and plays a brief, attention-grabbing beep.
     */
    private suspend fun playGeneratedAlertBeep() {
        val sampleRate = 22050
        val durationSec = 0.8
        val numSamples = (durationSec * sampleRate).toInt()

        // Two-tone alert: 880Hz → 1100Hz
        val samples = ShortArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            val freq = if (t < durationSec / 2) 880.0 else 1100.0
            val envelope = if (t < 0.01) (t / 0.01) else if (t > durationSec - 0.01) ((durationSec - t) / 0.01) else 1.0
            (Short.MAX_VALUE * 0.3 * envelope * kotlin.math.sin(2 * Math.PI * freq * t)).toInt().toShort()
        }

        audioPlayer.play(samples, sampleRate, isEmergency = true)
        audioPlayer.awaitCompletion()
        delay(200) // Brief pause after beep
    }

    // ── Haptic feedback ──────────────────────────────────────────────

    private fun triggerHapticFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Three short bursts
                    val pattern = longArrayOf(0, 200, 100, 200, 100, 200)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    it.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(longArrayOf(0, 200, 100, 200, 100, 200), -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic feedback failed: ${e.message}")
        }
    }
}
