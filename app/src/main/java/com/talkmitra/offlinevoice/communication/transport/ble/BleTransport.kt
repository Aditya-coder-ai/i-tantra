package com.talkmitra.offlinevoice.communication.transport.ble

import com.talkmitra.offlinevoice.communication.model.RawPacket
import com.talkmitra.offlinevoice.communication.model.TransportTier
import com.talkmitra.offlinevoice.communication.transport.ITantraTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Short-range Bluetooth Low Energy (GATT & L2CAP CoC) transport driver.
 */
class BleTransport : ITantraTransport {

    override val tier: TransportTier = TransportTier.BLUETOOTH_DIRECT

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _incomingPackets = MutableSharedFlow<RawPacket>(extraBufferCapacity = 64)
    override val incomingPackets: Flow<RawPacket> = _incomingPackets.asSharedFlow()

    override suspend fun start() {
        // Initialize BluetoothAdapter, start BLE advertising & scanning
        _isAvailable.value = true
    }

    override suspend fun stop() {
        // Stop scanning, tear down active GATT connections and L2CAP sockets
        _isAvailable.value = false
    }

    override suspend fun sendPacket(packet: RawPacket): Boolean {
        if (!_isAvailable.value) return false
        // Write packet across active L2CAP channels or GATT characteristics
        return true
    }

    /**
     * Simulates or injects an incoming packet from physical BLE radio.
     */
    suspend fun onPacketReceived(bytes: ByteArray, senderAddress: String) {
        _incomingPackets.emit(
            RawPacket(
                data = bytes,
                sourceAddress = senderAddress,
                transportTier = TransportTier.BLUETOOTH_DIRECT
            )
        )
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("000017A4-0000-1000-8000-00805F9B34FB")
        val RX_CHAR_UUID: UUID = UUID.fromString("000017B1-0000-1000-8000-00805F9B34FB")
        val TX_CHAR_UUID: UUID = UUID.fromString("000017B2-0000-1000-8000-00805F9B34FB")
    }
}
