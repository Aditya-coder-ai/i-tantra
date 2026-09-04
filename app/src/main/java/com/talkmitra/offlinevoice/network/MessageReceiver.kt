package com.talkmitra.offlinevoice.network

import android.util.Log
import com.talkmitra.offlinevoice.security.EncryptedMessagePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Handles incoming raw frames from the active transport stream, extracts EncryptedMessagePackets,
 * dispatches automatic ACK replies, and updates delivery states.
 */
class MessageReceiver(
    private val deliveryManager: DeliveryManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private val _incomingEncryptedPackets = MutableSharedFlow<EncryptedMessagePacket>(extraBufferCapacity = 64)
    val incomingEncryptedPackets: Flow<EncryptedMessagePacket> = _incomingEncryptedPackets.asSharedFlow()

    private val _peerHandshake = MutableSharedFlow<HandshakePayload>(extraBufferCapacity = 16)
    val peerHandshake: Flow<HandshakePayload> = _peerHandshake.asSharedFlow()

    private var activeListenerJob: Job? = null
    private var totalReceivedCount = 0L

    /**
     * Binds receiver to the incoming frames stream of [transport].
     */
    fun bindTransport(transport: Transport) {
        activeListenerJob?.cancel()
        activeListenerJob = scope.launch {
            Log.i(TAG, "MessageReceiver bound to ${transport.transportType.displayName}")
            transport.incomingFrames.collect { frame ->
                handleIncomingFrame(frame, transport)
            }
        }
    }

    suspend fun incomingFramesReceived(frame: RawFrame, transport: Transport) {
        handleIncomingFrame(frame, transport)
    }

    private suspend fun handleIncomingFrame(frame: RawFrame, transport: Transport) {
        when (frame.type) {
            PacketType.DATA -> {
                try {
                    val encryptedPacket = PacketSerializer.deserializeEncryptedPacket(frame.payload)
                    totalReceivedCount++
                    Log.i(TAG, "★ Received DATA EncryptedMessagePacket: msgId=${encryptedPacket.messageId}, sender=${encryptedPacket.senderId}, cipherLen=${encryptedPacket.ciphertext.length}")

                    // 1. Immediately dispatch application-level delivery ACK back to sender
                    val ackPayload = AckPayload(messageId = encryptedPacket.messageId, status = "DELIVERED")
                    val ackBytes = PacketSerializer.serializeAck(ackPayload)
                    transport.sendRawBytes(ackBytes)
                    Log.i(TAG, "Sent delivery ACK for message ${encryptedPacket.messageId}")

                    // 2. Deliver encrypted packet to upper security layer for decryption and TTS
                    _incomingEncryptedPackets.emit(encryptedPacket)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to deserialize incoming DATA packet: ${e.message}", e)
                }
            }

            PacketType.ACK -> {
                try {
                    val ack = PacketSerializer.deserializeAck(frame.payload)
                    Log.i(TAG, "✓ Received ACK for message ${ack.messageId}")
                    deliveryManager.handleAckReceived(ack)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse ACK frame: ${e.message}", e)
                }
            }

            PacketType.HANDSHAKE -> {
                try {
                    val handshake = PacketSerializer.deserializeHandshake(frame.payload)
                    Log.i(TAG, "★ Received HANDSHAKE from peer: ${handshake.displayName} (${handshake.deviceId})")
                    _peerHandshake.emit(handshake)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse HANDSHAKE frame: ${e.message}", e)
                }
            }

            PacketType.PING -> {
                val pongBytes = PacketSerializer.serializePong()
                transport.sendRawBytes(pongBytes)
            }

            PacketType.PONG -> {
                Log.d(TAG, "PONG received")
            }
        }
    }

    val messagesReceivedCount: Long get() = totalReceivedCount

    fun unbind() {
        activeListenerJob?.cancel()
        activeListenerJob = null
    }

    companion object {
        private const val TAG = "MessageReceiver"
    }
}
