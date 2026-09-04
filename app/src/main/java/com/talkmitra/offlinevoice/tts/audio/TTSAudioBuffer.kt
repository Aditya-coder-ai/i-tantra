package com.talkmitra.offlinevoice.tts.audio

import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe ring buffer for streaming PCM audio from the TTS engine
 * to the audio player.
 *
 * The TTS thread writes chunks via [write], and the AudioTrack thread
 * reads via [read]. If the buffer is full, [write] blocks until space
 * is available. If the buffer is empty, [read] returns 0 samples.
 *
 * @param capacity Maximum number of PCM samples the buffer can hold.
 */
class TTSAudioBuffer(private val capacity: Int = DEFAULT_CAPACITY) {

    companion object {
        private const val TAG = "TTSAudioBuf"
        /** Default capacity: ~2 seconds at 22050 Hz. */
        const val DEFAULT_CAPACITY = 22050 * 2
    }

    private val buffer = ShortArray(capacity)
    private var readPos = 0
    private var writePos = 0
    private var available = 0  // number of samples currently in buffer

    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val notFull = lock.newCondition()

    @Volatile
    private var closed = false

    /**
     * Writes samples into the buffer. Blocks if the buffer is full.
     *
     * @return Number of samples actually written, or -1 if the buffer is closed.
     */
    fun write(data: ShortArray, offset: Int = 0, length: Int = data.size): Int {
        if (closed) return -1

        var written = 0
        lock.withLock {
            while (written < length) {
                if (closed) return -1

                // Wait for space
                while (available >= capacity && !closed) {
                    notFull.await()
                }
                if (closed) return -1

                // Copy as many samples as fit
                val toCopy = minOf(length - written, capacity - available)
                for (i in 0 until toCopy) {
                    buffer[writePos] = data[offset + written + i]
                    writePos = (writePos + 1) % capacity
                }
                available += toCopy
                written += toCopy

                notEmpty.signal()
            }
        }
        return written
    }

    /**
     * Reads samples from the buffer into [dest].
     * Returns the number of samples read (may be 0 if buffer is empty and not closed,
     * or -1 if closed and empty).
     */
    fun read(dest: ShortArray, offset: Int = 0, length: Int = dest.size): Int {
        lock.withLock {
            if (available == 0 && closed) return -1
            if (available == 0) return 0

            val toRead = minOf(length, available)
            for (i in 0 until toRead) {
                dest[offset + i] = buffer[readPos]
                readPos = (readPos + 1) % capacity
            }
            available -= toRead

            notFull.signal()
            return toRead
        }
    }

    /**
     * Blocking read: waits until at least [minSamples] are available or
     * the buffer is closed.
     *
     * @return Number of samples read, or -1 if closed.
     */
    fun readBlocking(dest: ShortArray, offset: Int = 0, length: Int = dest.size, minSamples: Int = 1): Int {
        lock.withLock {
            while (available < minSamples && !closed) {
                notEmpty.await()
            }
            return read(dest, offset, length)
        }
    }

    /** Number of samples currently in the buffer. */
    fun available(): Int = lock.withLock { available }

    /** Resets the buffer to empty. */
    fun clear() = lock.withLock {
        readPos = 0
        writePos = 0
        available = 0
        closed = false
        notFull.signalAll()
    }

    /**
     * Closes the buffer, signalling to readers that no more data will arrive.
     * Any blocked [write] calls will return -1.
     */
    fun close() {
        lock.withLock {
            closed = true
            notEmpty.signalAll()
            notFull.signalAll()
        }
    }

    /** Returns `true` if the buffer has been closed. */
    fun isClosed(): Boolean = closed
}
