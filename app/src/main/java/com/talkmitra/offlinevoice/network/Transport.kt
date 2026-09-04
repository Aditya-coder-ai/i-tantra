package com.talkmitra.offlinevoice.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common abstraction for hardware radio transports (Wi-Fi Direct, Bluetooth).
 */
interface Transport {
    val transportType: TransportType
    val connectionState: StateFlow<ConnectionState>
    val connectedDevice: StateFlow<VoiceLinkDevice?>
    val incomingFrames: Flow<RawFrame>
    val isAvailable: Boolean

    suspend fun start()
    suspend fun stop()
    suspend fun discoverPeers(onPeersFound: (List<VoiceLinkDevice>) -> Unit, onError: (String) -> Unit)
    suspend fun stopDiscovery()
    suspend fun connect(device: VoiceLinkDevice): Boolean
    suspend fun disconnect()
    suspend fun sendFrame(frame: RawFrame): Boolean
    suspend fun sendRawBytes(bytes: ByteArray): Boolean
}
