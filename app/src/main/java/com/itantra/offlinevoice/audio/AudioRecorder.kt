package com.itantra.offlinevoice.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.os.Build
import android.os.Process
import android.util.Log

/**
 * Offline microphone source that owns one [AudioRecord] and emits fixed-size PCM chunks.
 *
 * Callbacks run on the recorder's audio thread. A future VAD should consume a chunk synchronously
 * in [Listener.onAudioChunk], or copy/enqueue it itself if it needs asynchronous processing.
 */
class AudioRecorder(
    private val context: Context,
    private val config: AudioConfig = AudioConfig(),
    private val listener: Listener
) {
    interface Listener {
        fun onStateChanged(state: RecordingState)
        fun onAudioChunk(chunk: AudioChunk)
        fun onAudioLevel(rms: Float, peak: Float)
        fun onError(message: String)
    }

    private val lock = Object()
    private val levelAnalyzer = AudioLevelAnalyzer()

    @Volatile
    var state: RecordingState = RecordingState.IDLE
        private set

    /** True once the capture thread has released the system microphone resource. */
    val isMicrophoneReleased: Boolean
        get() = synchronized(lock) { audioRecord == null && recordingThread == null }

    @Volatile private var captureRequested = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    fun startRecording(): Boolean = synchronized(lock) {
        if (state == RecordingState.RECORDING || state == RecordingState.PAUSED) return true
        if (audioRecord != null || recordingThread != null) {
            reportErrorLocked("Microphone is still being released. Please try again.")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            reportErrorLocked("Microphone permission has not been granted.")
            return false
        }

        val minBufferBytes = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            config.channelConfig,
            config.encoding
        )
        if (minBufferBytes <= 0) {
            reportErrorLocked("This device does not support 16 kHz mono PCM microphone input.")
            return false
        }

        // Keep at least one practical 20 ms chunk, but never below AudioRecord's requirement.
        val bufferBytes = maxOf(minBufferBytes, config.chunkSamples * config.bytesPerSample)
        val recorder = try {
            AudioRecord(
                config.audioSource,
                config.sampleRateHz,
                config.channelConfig,
                config.encoding,
                bufferBytes
            )
        } catch (error: IllegalArgumentException) {
            reportErrorLocked("Microphone configuration failed: ${error.message ?: "invalid audio settings"}")
            return false
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            reportErrorLocked("Microphone could not be initialized. It may be in use by another app.")
            return false
        }

        try {
            recorder.startRecording()
        } catch (error: IllegalStateException) {
            recorder.release()
            reportErrorLocked("Microphone could not start. It may be unavailable or in use.")
            return false
        } catch (error: SecurityException) {
            recorder.release()
            reportErrorLocked("Microphone permission was rejected by the system.")
            return false
        }

        // AudioRecord retains its API-required internal buffer; reads stay at a VAD-friendly 20 ms.
        val readBuffer = ShortArray(config.chunkSamples)
        val reusableChunk = AudioChunk(readBuffer)
        audioRecord = recorder
        captureRequested = true
        setStateLocked(RecordingState.RECORDING)
        val thread = Thread({ recordingLoop(recorder, readBuffer, reusableChunk) }, "OfflinePcmCapture")
        recordingThread = thread
        thread.start()
        true
    }

    fun pauseRecording(): Boolean = synchronized(lock) {
        if (state != RecordingState.RECORDING) return false
        setStateLocked(RecordingState.PAUSED)
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // The capture loop will release the recorder and publish the actual state.
        }
        true
    }

    fun resumeRecording(): Boolean = synchronized(lock) {
        if (state != RecordingState.PAUSED) return false
        val recorder = audioRecord ?: run {
            reportErrorLocked("Microphone is no longer available.")
            return false
        }
        try {
            recorder.startRecording()
        } catch (error: IllegalStateException) {
            captureRequested = false
            reportErrorLocked("Microphone could not resume.")
            lock.notifyAll()
            return false
        }
        setStateLocked(RecordingState.RECORDING)
        lock.notifyAll()
        true
    }

    fun stopRecording() {
        val recorder: AudioRecord?
        synchronized(lock) {
            if (state == RecordingState.IDLE || state == RecordingState.STOPPED) return
            captureRequested = false
            recorder = audioRecord
            if (state != RecordingState.ERROR) setStateLocked(RecordingState.STOPPED)
            lock.notifyAll()
        }
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {
            // It may already have been stopped during pause or cleanup.
        }
    }

    /** Stops capture and waits briefly for its thread to free the microphone. */
    fun release() {
        stopRecording()
        val thread = synchronized(lock) { recordingThread }
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(RELEASE_WAIT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        synchronized(lock) {
            // A thread that could not start is released here; normal cleanup happens in finally.
            if (recordingThread == null) {
                audioRecord?.release()
                audioRecord = null
            }
        }
    }

    private fun recordingLoop(recorder: AudioRecord, buffer: ShortArray, chunk: AudioChunk) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val level = AudioLevel()
        var lastLevelNotificationNanos = 0L
        try {
            while (captureRequested) {
                val shouldExit = synchronized(lock) {
                    while (captureRequested && state == RecordingState.PAUSED) lock.wait()
                    !captureRequested
                }
                if (shouldExit) break

                val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    val now = System.nanoTime()
                    chunk.sampleCount = read
                    chunk.timestampNanos = now
                    listener.onAudioChunk(chunk)

                    // UI metering at 10 Hz avoids flooding the main thread.
                    if (now - lastLevelNotificationNanos >= LEVEL_UPDATE_INTERVAL_NANOS) {
                        levelAnalyzer.analyze(buffer, read, level)
                        listener.onAudioLevel(level.rms, level.peak)
                        lastLevelNotificationNanos = now
                    }
                } else if (captureRequested && state != RecordingState.PAUSED) {
                    failFromCaptureThread("Microphone read failed (code $read).")
                }
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: SecurityException) {
            failFromCaptureThread("Microphone permission was lost while recording.")
        } catch (error: Exception) {
            Log.e(TAG, "Audio capture failed", error)
            failFromCaptureThread("Microphone capture failed: ${error.message ?: "unknown error"}")
        } finally {
            try {
                recorder.stop()
            } catch (_: IllegalStateException) {
                // Already stopped.
            }
            recorder.release()
            synchronized(lock) {
                if (audioRecord === recorder) audioRecord = null
                recordingThread = null
                captureRequested = false
                if (state != RecordingState.ERROR && state != RecordingState.STOPPED) {
                    setStateLocked(RecordingState.STOPPED)
                }
                lock.notifyAll()
            }
        }
    }

    private fun failFromCaptureThread(message: String) = synchronized(lock) {
        captureRequested = false
        reportErrorLocked(message)
        lock.notifyAll()
    }

    private fun reportErrorLocked(message: String) {
        setStateLocked(RecordingState.ERROR)
        listener.onError(message)
    }

    private fun setStateLocked(newState: RecordingState) {
        state = newState
        listener.onStateChanged(newState)
    }

    private companion object {
        const val TAG = "AudioRecorder"
        const val RELEASE_WAIT_MS = 750L
        const val LEVEL_UPDATE_INTERVAL_NANOS = 100_000_000L
    }
}
