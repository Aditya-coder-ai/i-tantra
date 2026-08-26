package com.itantra.offlinevoice.communication.manager

import com.itantra.offlinevoice.communication.crypto.CryptoEngine
import com.itantra.offlinevoice.communication.crypto.DefaultCryptoEngine
import com.itantra.offlinevoice.communication.model.DecryptedMessage
import com.itantra.offlinevoice.communication.model.DeliveryState
import com.itantra.offlinevoice.communication.model.MeshPacket
import com.itantra.offlinevoice.communication.model.PeerIdentity
import com.itantra.offlinevoice.communication.model.RawPacket
import com.itantra.offlinevoice.communication.model.TransportTier
import com.itantra.offlinevoice.communication.transport.ITantraTransport
import com.itantra.offlinevoice.communication.transport.ble.BleTransport
import com.itantra.offlinevoice.communication.transport.mesh.MeshRouter
import com.itantra.offlinevoice.communication.transport.mesh.RoutingDecision
import com.itantra.offlinevoice.communication.transport.wifidirect.WifiDirectTransport
import com.itantra.offlinevoice.text.MessagePriority
import com.itantra.offlinevoice.text.MessageType
import com.itantra.offlinevoice.text.ProcessedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID

/**
 * Concrete implementation of the iTantra communication subsystem.
 */
class TantraCommunicationManagerImpl(
    private val cryptoEngine: CryptoEngine = DefaultCryptoEngine(),
    private val bleTransport: ITantraTransport = BleTransport(),
    private val wifiDirectTransport: ITantraTransport = WifiDirectTransport(),
    private val meshRouter: MeshRouter = MeshRouter(cryptoEngine),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) : ITantraCommunicationManager {

    private val secureRandom = SecureRandom()

    private val _incomingMessages = MutableSharedFlow<DecryptedMessage>(extraBufferCapacity = 64)
    override val incomingMessages: Flow<DecryptedMessage> = _incomingMessages.asSharedFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    override val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _engineState = MutableStateFlow(EngineState())
    override val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private var packetListenerJob: Job? = null

    override suspend fun start() {
        cryptoEngine.initializeIdentity()
        bleTransport.start()
        wifiDirectTransport.start()

        _engineState.value = _engineState.value.copy(
            isRunning = true,
            isBleActive = true,
            isWifiDirectActive = true
        )

        // Merge incoming streams from all transport interfaces
        packetListenerJob = scope.launch {
            merge(bleTransport.incomingPackets, wifiDirectTransport.incomingPackets).collect { rawPacket ->
                handleIncomingRawPacket(rawPacket)
            }
        }
    }

    override suspend fun stop() {
        packetListenerJob?.cancel()
        bleTransport.stop()
        wifiDirectTransport.stop()
        _engineState.value = _engineState.value.copy(
            isRunning = false,
            isBleActive = false,
            isWifiDirectActive = false
        )
    }

    override fun sendMessage(
        recipient: PeerIdentity,
        text: String,
        languageCode: String,
        messageType: MessageType,
        priority: MessagePriority
    ): Flow<DeliveryState> = flow {
        emit(DeliveryState.Queued)

        val startTime = System.currentTimeMillis()

        // 1. Construct serialized inner payload
        val innerJson = JSONObject().apply {
            put("text", text)
            put("lang", languageCode)
            put("type", messageType.name)
            put("priority", priority.name)
            put("timestamp", startTime)
        }.toString()
        val plaintextBytes = innerJson.toByteArray(Charsets.UTF_8)

        // 2. Encrypt envelope with forward secrecy
        val envelope = cryptoEngine.encrypt(recipient.publicKey, plaintextBytes)
        val messageIdBytes = generateRandom16Bytes()

        // 3. Assemble binary wire packet
        val meshPacket = MeshPacket(
            version = MeshPacket.PROTOCOL_VERSION,
            type = if (messageType == MessageType.EMERGENCY) MeshPacket.TYPE_EMERGENCY_SOS else MeshPacket.TYPE_DATA,
            ttl = if (messageType == MessageType.EMERGENCY) MeshPacket.MAX_TTL else MeshPacket.DEFAULT_TTL,
            hopCount = 0,
            messageId = messageIdBytes,
            ephemeralSenderPubKey = envelope.ephemeralPublicKey,
            blindRecipientTag = envelope.blindRecipientTag,
            ciphertext = envelope.ciphertext,
            authTag = envelope.authTag
        )

        val rawPacket = RawPacket(meshPacket.toByteArray())
        meshRouter.markMessageSent(messageIdBytes)

        // 4. Adaptive Tier Escalation
        // Step 1: Attempt Direct BLE
        emit(DeliveryState.Transmitting(TransportTier.BLUETOOTH_DIRECT))
        val bleSuccess = bleTransport.sendPacket(rawPacket)

        if (bleSuccess) {
            emit(DeliveryState.Delivered(System.currentTimeMillis() - startTime))
            return@flow
        }

        // Step 2: Escalate to Wi-Fi Direct
        emit(DeliveryState.Transmitting(TransportTier.WIFI_DIRECT))
        val wifiSuccess = wifiDirectTransport.sendPacket(rawPacket)

        if (wifiSuccess) {
            emit(DeliveryState.Delivered(System.currentTimeMillis() - startTime))
            return@flow
        }

        // Step 3: Escalate to Extended Range Multi-Hop Mesh Flooding
        emit(DeliveryState.Transmitting(TransportTier.MESH_RELAY))
        val relayedBle = bleTransport.sendPacket(rawPacket)
        val relayedWifi = wifiDirectTransport.sendPacket(rawPacket)

        if (relayedBle || relayedWifi) {
            emit(DeliveryState.Relayed(hopCount = 1))
        } else {
            emit(DeliveryState.Failed("All radio transports unavailable"))
        }
    }

    override fun sendProcessedMessage(
        recipient: PeerIdentity,
        processedMessage: ProcessedMessage
    ): Flow<DeliveryState> {
        return sendMessage(
            recipient = recipient,
            text = processedMessage.text,
            languageCode = processedMessage.language,
            messageType = processedMessage.messageType,
            priority = processedMessage.priority
        )
    }

    override fun broadcastEmergency(
        alertText: String,
        languageCode: String
    ): Flow<DeliveryState> {
        val dummyBroadcastIdentity = PeerIdentity(
            alias = "EMERGENCY_BROADCAST",
            publicKey = ByteArray(32) { 0xFF.toByte() },
            isVerified = true
        )
        return sendMessage(
            recipient = dummyBroadcastIdentity,
            text = alertText,
            languageCode = languageCode,
            messageType = MessageType.EMERGENCY,
            priority = MessagePriority.CRITICAL
        )
    }

    private suspend fun handleIncomingRawPacket(rawPacket: RawPacket) {
        when (val decision = meshRouter.processIncomingPacket(rawPacket)) {
            is RoutingDecision.Consume -> {
                consumeIncomingPacket(decision.packet, rawPacket.transportTier)
            }
            is RoutingDecision.Forward -> {
                forwardOpaquePacket(decision.updatedPacket)
            }
            is RoutingDecision.Drop -> {
                // Silently dropped (duplicate or expired TTL)
            }
        }
    }

    private suspend fun consumeIncomingPacket(packet: MeshPacket, tier: TransportTier) {
        val decryptedBytes = cryptoEngine.decrypt(
            ephemeralPublicKey = packet.ephemeralSenderPubKey,
            ciphertext = packet.ciphertext,
            authTag = packet.authTag
        ) ?: return

        try {
            val jsonStr = String(decryptedBytes, Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            val text = json.getString("text")
            val lang = json.optString("lang", "en")
            val typeStr = json.optString("type", MessageType.NORMAL.name)
            val priorityStr = json.optString("priority", MessagePriority.NORMAL.name)
            val timestamp = json.optLong("timestamp", System.currentTimeMillis())

            val msg = DecryptedMessage(
                messageId = packet.messageIdUuid.toString(),
                sender = null, // Can be matched against known contacts via public key in real use
                text = text,
                languageCode = lang,
                timestampMs = timestamp,
                transportTier = tier,
                hopCount = packet.hopCount.toInt(),
                messageType = MessageType.valueOf(typeStr),
                priority = MessagePriority.valueOf(priorityStr)
            )

            _incomingMessages.emit(msg)
            _engineState.value = _engineState.value.copy(
                totalMessagesDelivered = _engineState.value.totalMessagesDelivered + 1
            )
        } catch (e: Exception) {
            // Malformed inner payload
        }
    }

    private suspend fun forwardOpaquePacket(packet: MeshPacket) {
        val raw = RawPacket(packet.toByteArray())
        bleTransport.sendPacket(raw)
        wifiDirectTransport.sendPacket(raw)
        _engineState.value = _engineState.value.copy(
            totalPacketsRelayed = _engineState.value.totalPacketsRelayed + 1
        )
    }

    private fun generateRandom16Bytes(): ByteArray {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes
    }
}
