package com.itantra.offlinevoice.tts.queue

import android.util.Log
import com.itantra.offlinevoice.text.MessagePriority
import com.itantra.offlinevoice.text.MessageType
import com.itantra.offlinevoice.text.ProcessedMessage
import java.util.PriorityQueue

/**
 * Priority queue for TTS playback requests.
 *
 * Messages are ordered by priority (CRITICAL > HIGH > NORMAL) and then
 * by arrival order within the same priority level.
 *
 * Emergency / CRITICAL messages are automatically moved to the front,
 * interrupting normal playback order.
 *
 * Thread-safe: all public methods are synchronised.
 */
class TTSQueue(
    /** Maximum number of queued messages. Oldest NORMAL messages are evicted when full. */
    private val maxSize: Int = 50
) {
    companion object {
        private const val TAG = "TTSQueue"
    }

    /** Internal entry with a monotonic sequence number for stable ordering. */
    private data class QueueEntry(
        val message: ProcessedMessage,
        val enqueuedAt: Long,
        val sequenceId: Long
    ) : Comparable<QueueEntry> {
        /**
         * Ordering: higher priority first, then older sequence first.
         * PriorityQueue is a min-heap, so we negate priority to get
         * highest-priority-first behaviour.
         */
        override fun compareTo(other: QueueEntry): Int {
            val priorityCompare = other.message.priority.ordinal - this.message.priority.ordinal
            if (priorityCompare != 0) return priorityCompare
            return this.sequenceId.compareTo(other.sequenceId)
        }
    }

    private val queue = PriorityQueue<QueueEntry>()
    private var sequenceCounter = 0L

    /** Callback invoked when a CRITICAL/EMERGENCY message is enqueued. */
    var onEmergencyEnqueued: ((ProcessedMessage) -> Unit)? = null

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Adds a message to the queue.
     *
     * If the message is EMERGENCY/CRITICAL, [onEmergencyEnqueued] is invoked.
     * If the queue is full, the oldest NORMAL-priority message is evicted.
     */
    @Synchronized
    fun enqueue(message: ProcessedMessage) {
        // Evict oldest NORMAL message if at capacity
        if (queue.size >= maxSize) {
            evictLowestPriority()
        }

        val entry = QueueEntry(
            message = message,
            enqueuedAt = System.currentTimeMillis(),
            sequenceId = sequenceCounter++
        )
        queue.add(entry)

        Log.d(TAG, "Enqueued: id=${message.messageId}, priority=${message.priority}, " +
                "type=${message.messageType}, queue size=${queue.size}")

        // Notify for emergency messages
        if (message.messageType == MessageType.EMERGENCY ||
            message.priority == MessagePriority.CRITICAL
        ) {
            onEmergencyEnqueued?.invoke(message)
        }
    }

    /**
     * Removes and returns the highest-priority message.
     * Returns `null` if the queue is empty.
     */
    @Synchronized
    fun dequeue(): ProcessedMessage? {
        val entry = queue.poll() ?: return null
        Log.d(TAG, "Dequeued: id=${entry.message.messageId}, remaining=${queue.size}")
        return entry.message
    }

    /**
     * Peeks at the next message without removing it.
     */
    @Synchronized
    fun peek(): ProcessedMessage? = queue.peek()?.message

    /** Number of messages waiting in the queue. */
    @Synchronized
    fun size(): Int = queue.size

    /** Returns `true` if the queue is empty. */
    @Synchronized
    fun isEmpty(): Boolean = queue.isEmpty()

    /** Returns `true` if the queue contains any CRITICAL/EMERGENCY messages. */
    @Synchronized
    fun hasEmergencyMessages(): Boolean {
        return queue.any {
            it.message.priority == MessagePriority.CRITICAL ||
            it.message.messageType == MessageType.EMERGENCY
        }
    }

    /** Clears all messages from the queue. */
    @Synchronized
    fun clear() {
        queue.clear()
        Log.d(TAG, "Queue cleared")
    }

    /**
     * Returns a snapshot of all queued messages in priority order.
     * Does not modify the queue.
     */
    @Synchronized
    fun snapshot(): List<ProcessedMessage> {
        return queue.toSortedSet().map { it.message }
    }

    // ── Internal ─────────────────────────────────────────────────────

    /**
     * Evicts the oldest NORMAL-priority message.
     * If all messages are HIGH/CRITICAL, evicts the oldest of any priority.
     */
    private fun evictLowestPriority() {
        val normalEntries = queue.filter { it.message.priority == MessagePriority.NORMAL }
        val toEvict = if (normalEntries.isNotEmpty()) {
            normalEntries.minByOrNull { it.sequenceId }
        } else {
            queue.minByOrNull { it.sequenceId }
        }

        toEvict?.let {
            queue.remove(it)
            Log.w(TAG, "Evicted message ${it.message.messageId} (priority=${it.message.priority}) — queue full")
        }
    }
}
