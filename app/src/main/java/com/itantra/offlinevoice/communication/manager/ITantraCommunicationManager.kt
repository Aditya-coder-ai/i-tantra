package com.itantra.offlinevoice.communication.manager

import com.itantra.offlinevoice.communication.model.DecryptedMessage
import com.itantra.offlinevoice.communication.model.DeliveryState
import com.itantra.offlinevoice.communication.model.PeerIdentity
import com.itantra.offlinevoice.text.MessagePriority
import com.itantra.offlinevoice.text.MessageType
import com.itantra.offlinevoice.text.ProcessedMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class DiscoveredPeer(
    val peerIdentity: PeerIdentity,
    val rssi: Int = -60,
    val isDirectlyReachable: Boolean = true,
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class EngineState(
    val isRunning: Boolean = false,
    val isBleActive: Boolean = false,
    val isWifiDirectActive: Boolean = false,
    val totalPacketsRelayed: Long = 0L,
    val totalMessagesDelivered: Long = 0L
)

/**
 * High-level facade for the iTantra communication subsystem.
 * Encapsulates encryption, transport arbitration, fallback escalation, and mesh relaying.
 */
interface ITantraCommunicationManager {
    /** Decrypted incoming messages for UI presentation and TTS synthesis */
    val incomingMessages: Flow<DecryptedMessage>

    /** Currently reachable direct and multi-hop peers */
    val discoveredPeers: StateFlow<List<DiscoveredPeer>>

    /** Current communication engine health and active radio statuses */
    val engineState: StateFlow<EngineState>

    /**
     * Sends an offline text utterance to an intended recipient.
     */
    fun sendMessage(
        recipient: PeerIdentity,
        text: String,
        languageCode: String,
        messageType: MessageType = MessageType.NORMAL,
        priority: MessagePriority = MessagePriority.NORMAL
    ): Flow<DeliveryState>

    /**
     * Convenience method to send a ProcessedMessage produced by the text-processing pipeline.
     */
    fun sendProcessedMessage(
        recipient: PeerIdentity,
        processedMessage: ProcessedMessage
    ): Flow<DeliveryState>

    /**
     * Broadcasts an unaddressed emergency alert across all local radios with maximum TTL.
     */
    fun broadcastEmergency(
        alertText: String,
        languageCode: String
    ): Flow<DeliveryState>

    /** Starts all radio listeners and mesh routing engines */
    suspend fun start()

    /** Stops all radio listeners and flushes transient queues */
    suspend fun stop()
}
