package com.itantra.offlinevoice.network

/**
 * Real-time diagnostic and operational metrics for the active device-to-device transport session.
 */
data class NetworkMetrics(
    val activeTransport: TransportType? = TransportType.WIFI_DIRECT,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val messagesSent: Long = 0L,
    val messagesReceived: Long = 0L,
    val lastLatencyMs: Long = 0L,
    val avgLatencyMs: Long = 0L,
    val packetLossPercent: Float = 0.0f,
    val queueSize: Int = 0,
    val signalStrengthPercent: Int = 95,
    val connectedPeerName: String = "",
    val connectedPeerAddress: String = "",
    val uptimeSeconds: Long = 0L
)
