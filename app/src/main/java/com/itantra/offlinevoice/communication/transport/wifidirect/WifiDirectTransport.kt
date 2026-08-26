package com.itantra.offlinevoice.communication.transport.wifidirect

import com.itantra.offlinevoice.communication.model.RawPacket
import com.itantra.offlinevoice.communication.model.TransportTier
import com.itantra.offlinevoice.communication.transport.ITantraTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local wireless Wi-Fi Direct (P2P Service Discovery + TCP Socket) transport driver.
 */
class WifiDirectTransport : ITantraTransport {

    override val tier: TransportTier = TransportTier.WIFI_DIRECT

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<RawPacket>(extraBufferCapacity = 64)
    override val incomingPackets: Flow<RawPacket> = _incomingPackets.asSharedFlow()

    override suspend fun start() {
        // Register WifiP2pManager, DNS-SD service, start peer discovery
        _isAvailable.value = true
    }

    override suspend fun stop() {
        // Remove P2P groups, close server sockets
        _isAvailable.value = false
    }

    override suspend fun sendPacket(packet: RawPacket): Boolean {
        if (!_isAvailable.value) return false
        // Write binary frame to open TCP socket connection
        return true
    }

    suspend fun onPacketReceived(bytes: ByteArray, peerIp: String) {
        _incomingPackets.emit(
            RawPacket(
                data = bytes,
                sourceAddress = peerIp,
                transportTier = TransportTier.WIFI_DIRECT
            )
        )
    }

    companion object {
        const val P2P_SERVICE_NAME = "_itantra_voice._tcp"
        const val P2P_PORT = 45892
    }
}
