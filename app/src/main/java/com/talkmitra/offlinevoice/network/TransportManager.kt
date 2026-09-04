package com.talkmitra.offlinevoice.network

import android.content.Context
import android.util.Log
import com.talkmitra.offlinevoice.security.EncryptedMessagePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified Top-Level Transport Manager Facade for VoiceLink Device-to-Device Communication.
 *
 * Coordinates:
 * - LAN / Hotspot, Wi-Fi Direct and Bluetooth Transports
 * - Unified Discovery
 * - Connection State Machine & Failover
 * - Framing & Packet Serialization
 * - Offline Message Queuing
 * - Delivery State Tracking & ACKs
 * - Real Network Metrics Monitoring
 */
class TransportManager(
    private val context: Context,
    val lanSocketTransport: LanSocketTransport = LanSocketTransport(context),
    val wifiDirectTransport: WiFiDirectTransport = WiFiDirectTransport(context),
    val bluetoothTransport: BluetoothTransport = BluetoothTransport(context),
    val messageQueue: MessageQueue = MessageQueue(),
    val deliveryManager: DeliveryManager = DeliveryManager(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    val discoveryManager: DeviceDiscoveryManager = DeviceDiscoveryManager(wifiDirectTransport, bluetoothTransport, lanSocketTransport)
    val connectionManager: ConnectionManager = ConnectionManager(wifiDirectTransport, bluetoothTransport, lanSocketTransport)
    val messageSender: MessageSender = MessageSender(messageQueue, deliveryManager)
    val messageReceiver: MessageReceiver = MessageReceiver(deliveryManager)

    val connectionState: StateFlow<ConnectionState> get() = connectionManager.connectionState
    val connectedDevice: StateFlow<VoiceLinkDevice?> get() = connectionManager.connectedDevice
    val discoveredDevices: StateFlow<List<VoiceLinkDevice>> get() = discoveryManager.discoveredDevices
    val isDiscovering: StateFlow<Boolean> get() = discoveryManager.isDiscovering
    val preferredTransport: StateFlow<TransportType> get() = connectionManager.preferredTransport
    val deliveryUpdates: Flow<DeliveryUpdate> get() = deliveryManager.deliveryUpdates
    val incomingEncryptedMessages: Flow<EncryptedMessagePacket> get() = messageReceiver.incomingEncryptedPackets

    private val _networkMetrics = MutableStateFlow(NetworkMetrics())
    val networkMetrics: StateFlow<NetworkMetrics> = _networkMetrics.asStateFlow()

    private var metricsJob: Job? = null
    private var sessionStartTimeMs: Long = 0L

    suspend fun start() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting TransportManager across all radio transports...")
        lanSocketTransport.start()
        wifiDirectTransport.start()
        bluetoothTransport.start()

        // Bind receiver to all active transports so incoming messages are caught from any radio
        scope.launch {
            lanSocketTransport.incomingFrames.collect { frame ->
                messageReceiver.incomingFramesReceived(frame, lanSocketTransport)
            }
        }
        scope.launch {
            wifiDirectTransport.incomingFrames.collect { frame ->
                messageReceiver.incomingFramesReceived(frame, wifiDirectTransport)
            }
        }
        scope.launch {
            bluetoothTransport.incomingFrames.collect { frame ->
                messageReceiver.incomingFramesReceived(frame, bluetoothTransport)
            }
        }

        // Observe connection state to drain offline queue and track uptime
        scope.launch {
            connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    sessionStartTimeMs = System.currentTimeMillis()
                    val transport = connectionManager.activeTransport.value
                    messageSender.drainQueueOnConnected(transport)
                }
            }
        }

        // Start periodic metrics updater
        startMetricsTracker()
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        metricsJob?.cancel()
        discoveryManager.stopDiscovery()
        messageReceiver.unbind()
        lanSocketTransport.stop()
        wifiDirectTransport.stop()
        bluetoothTransport.stop()
        Log.i(TAG, "TransportManager stopped")
    }

    /**
     * Discovers nearby VoiceLink peers over Hotspot/LAN, Wi-Fi Direct and Bluetooth.
     */
    suspend fun startDiscovery(timeoutMs: Long = 30000L) {
        discoveryManager.startDiscovery(connectionManager.preferredTransport.value, timeoutMs)
    }

    suspend fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }

    /**
     * Connects to a target peer device.
     */
    suspend fun connect(device: VoiceLinkDevice): Boolean {
        return connectionManager.connect(device)
    }

    /**
     * Disconnects active connection.
     */
    suspend fun disconnect() {
        connectionManager.disconnect()
    }

    /**
     * Switches preferred transport between Wi-Fi Direct and Bluetooth.
     */
    fun selectPreferredTransport(type: TransportType) {
        connectionManager.setPreferredTransport(type)
    }

    /**
     * Sends an EncryptedMessagePacket to the peer (or buffers in offline queue).
     */
    suspend fun sendEncryptedPacket(packet: EncryptedMessagePacket): Boolean {
        val transport = connectionManager.activeTransport.value
        return messageSender.sendEncryptedPacket(packet, transport)
    }

    private fun startMetricsTracker() {
        metricsJob?.cancel()
        metricsJob = scope.launch {
            while (isActive) {
                val dev = connectedDevice.value
                val state = connectionState.value
                val uptime = if (state == ConnectionState.CONNECTED && sessionStartTimeMs > 0) {
                    (System.currentTimeMillis() - sessionStartTimeMs) / 1000
                } else {
                    0L
                }

                _networkMetrics.value = NetworkMetrics(
                    activeTransport = connectionManager.activeTransport.value.transportType,
                    connectionState = state,
                    messagesSent = deliveryManager.messagesSentCount,
                    messagesReceived = messageReceiver.messagesReceivedCount,
                    lastLatencyMs = deliveryManager.lastLatency,
                    avgLatencyMs = deliveryManager.averageLatency,
                    packetLossPercent = 0.0f,
                    queueSize = messageQueue.size,
                    signalStrengthPercent = dev?.signalStrength?.times(25) ?: 0,
                    connectedPeerName = dev?.displayName ?: "",
                    connectedPeerAddress = dev?.nativeAddress ?: "",
                    uptimeSeconds = uptime
                )

                delay(1000)
            }
        }
    }

    companion object {
        private const val TAG = "TransportManager"
    }
}
