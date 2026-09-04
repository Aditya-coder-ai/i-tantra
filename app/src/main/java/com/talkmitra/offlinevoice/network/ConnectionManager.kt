package com.talkmitra.offlinevoice.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages active transport selection, failover between LAN/Hotspot, Wi-Fi Direct and Bluetooth,
 * and connection lifecycle state.
 */
class ConnectionManager(
    val wifiDirectTransport: WiFiDirectTransport,
    val bluetoothTransport: BluetoothTransport,
    val lanSocketTransport: LanSocketTransport,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val _preferredTransport = MutableStateFlow(TransportType.WIFI_DIRECT)
    val preferredTransport: StateFlow<TransportType> = _preferredTransport.asStateFlow()

    private val _activeTransport = MutableStateFlow<Transport>(lanSocketTransport)
    val activeTransport: StateFlow<Transport> = _activeTransport.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<VoiceLinkDevice?>(null)
    val connectedDevice: StateFlow<VoiceLinkDevice?> = _connectedDevice.asStateFlow()

    private var lastTargetDevice: VoiceLinkDevice? = null
    private var stateSyncJob: Job? = null

    init {
        syncStateWithAllTransports()
    }

    /**
     * Changes the preferred radio transport (e.g. user toggles Wi-Fi Direct or Bluetooth).
     */
    fun setPreferredTransport(type: TransportType) {
        _preferredTransport.value = type
        val currentTransport = when {
            type == TransportType.BLUETOOTH -> bluetoothTransport
            lanSocketTransport.isAvailable -> lanSocketTransport
            else -> wifiDirectTransport
        }
        _activeTransport.value = currentTransport
        Log.i(TAG, "Preferred transport switched to ${type.displayName}")
    }

    private fun syncStateWithAllTransports() {
        stateSyncJob?.cancel()
        stateSyncJob = scope.launch {
            val transports = listOf(lanSocketTransport, wifiDirectTransport, bluetoothTransport)
            for (t in transports) {
                launch {
                    t.connectionState.collect { state ->
                        if (state == ConnectionState.CONNECTED) {
                            _activeTransport.value = t
                            _connectionState.value = ConnectionState.CONNECTED
                        } else if (_activeTransport.value == t) {
                            _connectionState.value = state
                        }
                    }
                }
                launch {
                    t.connectedDevice.collect { device ->
                        if (device != null) {
                            _activeTransport.value = t
                            _connectedDevice.value = device
                            lastTargetDevice = device
                        } else if (_activeTransport.value == t) {
                            _connectedDevice.value = null
                        }
                    }
                }
            }
        }
    }

    /**
     * Connects to [device] using the appropriate transport driver.
     */
    suspend fun connect(device: VoiceLinkDevice): Boolean = withContext(Dispatchers.IO) {
        lastTargetDevice = device

        val transport: Transport = when {
            device.transportType == TransportType.BLUETOOTH -> bluetoothTransport
            device.nativeAddress.contains(".") -> lanSocketTransport // IP Address (Hotspot/LAN)
            else -> wifiDirectTransport
        }

        _activeTransport.value = transport
        Log.i(TAG, "Initiating connection to ${device.displayName} via ${transport.javaClass.simpleName} (${device.nativeAddress})")
        return@withContext transport.connect(device)
    }

    /**
     * Disconnects the active transport.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        lanSocketTransport.disconnect()
        wifiDirectTransport.disconnect()
        bluetoothTransport.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
    }

    /**
     * Reconnects to the last known peer device.
     */
    suspend fun reconnect(): Boolean = withContext(Dispatchers.IO) {
        val lastDevice = lastTargetDevice ?: return@withContext false
        Log.i(TAG, "Attempting reconnect to last device: ${lastDevice.displayName}")
        return@withContext connect(lastDevice)
    }

    companion object {
        private const val TAG = "ConnectionManager"
    }
}
