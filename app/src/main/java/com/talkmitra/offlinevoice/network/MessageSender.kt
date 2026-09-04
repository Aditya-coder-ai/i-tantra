package com.talkmitra.offlinevoice.network

import android.util.Log
import com.talkmitra.offlinevoice.security.EncryptedMessagePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles serializing, framing, queuing, and transmitting EncryptedMessagePackets
 * over the active radio transport.
 */
class MessageSender(
    private val queue: MessageQueue,
    private val deliveryManager: DeliveryManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    /**
     * Sends an encrypted packet over [transport] or queues it if disconnected.
     */
    suspend fun sendEncryptedPacket(
        packet: EncryptedMessagePacket,
        transport: Transport?
    ): Boolean = withContext(Dispatchers.IO) {
        val isConnected = transport?.connectionState?.value == ConnectionState.CONNECTED

        if (!isConnected || transport == null) {
            Log.i(TAG, "Transport not connected — buffering packet ${packet.messageId} in offline queue (size=${queue.size + 1})")
            queue.enqueue(packet)
            deliveryManager.markQueued(packet.messageId)
            return@withContext true
        }

        return@withContext transmitNow(packet, transport)
    }

    /**
     * Transmits an encrypted packet immediately across the active socket connection.
     */
    private suspend fun transmitNow(
        packet: EncryptedMessagePacket,
        transport: Transport
    ): Boolean {
        val framedBytes = PacketSerializer.serializeEncryptedPacket(packet)
        Log.i(TAG, "Transmitting EncryptedMessagePacket ${packet.messageId} (${framedBytes.size} framed bytes)...")

        val sendSuccess = transport.sendRawBytes(framedBytes)
        if (sendSuccess) {
            deliveryManager.registerSent(packet.messageId) {
                Log.w(TAG, "Message ${packet.messageId} transmission unacknowledged — re-queuing for retry")
                queue.enqueue(packet)
            }
            return true
        } else {
            Log.e(TAG, "Socket write failed for message ${packet.messageId} — queuing for reconnect")
            queue.enqueue(packet)
            deliveryManager.markQueued(packet.messageId)
            return false
        }
    }

    /**
     * Drains all queued offline messages once a connection is re-established.
     */
    suspend fun drainQueueOnConnected(transport: Transport) = withContext(Dispatchers.IO) {
        if (queue.isEmpty) return@withContext
        val queuedPackets = queue.drainAll()
        Log.i(TAG, "Connection restored: draining ${queuedPackets.size} offline queued packet(s)...")

        for (packet in queuedPackets) {
            transmitNow(packet, transport)
        }
    }

    companion object {
        private const val TAG = "MessageSender"
    }
}
