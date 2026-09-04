package com.talkmitra.offlinevoice.network

import com.talkmitra.offlinevoice.security.EncryptedMessagePacket
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe offline message queue that buffers EncryptedMessagePackets
 * when network connection is temporarily unavailable.
 *
 * Security Principle: Plaintext is never stored in this queue.
 */
class MessageQueue(
    private val maxCapacity: Int = 100
) {
    private val queue = ConcurrentLinkedQueue<EncryptedMessagePacket>()

    /**
     * Enqueues an encrypted message packet.
     * If capacity is exceeded, evicts the oldest normal priority message.
     */
    @Synchronized
    fun enqueue(packet: EncryptedMessagePacket): Boolean {
        while (queue.size >= maxCapacity) {
            val removed = queue.poll()
            if (removed == null) break
        }
        return queue.offer(packet)
    }

    /**
     * Returns and removes the head of this queue, or null if empty.
     */
    fun dequeue(): EncryptedMessagePacket? {
        return queue.poll()
    }

    /**
     * Drains all queued packets in FIFO order.
     */
    @Synchronized
    fun drainAll(): List<EncryptedMessagePacket> {
        val list = mutableListOf<EncryptedMessagePacket>()
        while (true) {
            val item = queue.poll() ?: break
            list.add(item)
        }
        return list
    }

    val size: Int get() = queue.size

    val isEmpty: Boolean get() = queue.isEmpty()

    fun clear() {
        queue.clear()
    }
}
