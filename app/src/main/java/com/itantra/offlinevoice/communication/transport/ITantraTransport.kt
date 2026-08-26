package com.itantra.offlinevoice.communication.transport

import com.itantra.offlinevoice.communication.model.RawPacket
import com.itantra.offlinevoice.communication.model.TransportTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common hardware/driver abstraction for physical radios.
 */
interface ITantraTransport {
    val tier: TransportTier
    val isAvailable: StateFlow<Boolean>
    val incomingPackets: Flow<RawPacket>

    suspend fun start()
    suspend fun stop()

    /**
     * Sends an opaque raw packet over this transport.
     * @return true if successfully transmitted or enqueued to the radio buffer.
     */
    suspend fun sendPacket(packet: RawPacket): Boolean
}
