package com.itantra.offlinevoice.network

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
 * Manages active transport selection, failover between Wi-Fi Direct and Bluetooth,
 * and connection lifecycle state.
 */
class ConnectionManager(
    val wifiDirectTransport: WiFiDirectTransport,
    val bluetoothTransport: BluetoothTransport,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val _preferredTransport = MutableStateFlow(TransportType.WIFI_DIRECT)
    val preferredTransport: StateFlow<TransportType> = _preferredTransport.asStateFlow()

    private val _activeTransport = MutableStateFlow<Transport>(wifiDirectTransport)
    val activeTransport: StateFlow<Transport> = _activeTransport.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<VoiceLinkDevice?>(null)
    val connectedDevice: StateFlow<VoiceLinkDevice?> = _connectedDevice.asStateFlow()

    private var lastTargetDevice: VoiceLinkDevice? = null
    private var stateSyncJob: Job? = null

    init {
        syncStateWithActiveTransport()
    }

    /**
     * Changes the preferred radio transport (e.g. user toggles Wi-Fi Direct or Bluetooth).
     */
    fun setPreferredTransport(type: TransportType) {
        _preferredTransport.value = type
        val currentTransport = if (type == TransportType.WIFI_DIRECT) wifiDirectTransport else bluetoothTransport
        _activeTransport.value = currentTransport
        syncStateWithActiveTransport()
        Log.i(TAG, "Preferred transport switched to ${type.displayName}")
    }

    private fun syncStateWithActiveTransport() {
        stateSyncJob?.cancel()
        stateSyncJob = scope.launch {
            val transport = _activeTransport.value
            launch {
                transport.connectionState.collect { state ->
                    _connectionState.value = state
                }
            }
            launch {
                transport.connectedDevice.collect { device ->
                    _connectedDevice.value = device
                    if (device != null) {
                        lastTargetDevice = device
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
        val transport = if (device.transportType == TransportType.WIFI_DIRECT) {
            wifiDirectTransport
        } else {
            bluetoothTransport
        }

        _activeTransport.value = transport
        syncStateWithActiveTransport()

        Log.i(TAG, "Initiating connection to ${device.displayName} via ${device.transportType.displayName}")
        return@withContext transport.connect(device)
    }

    /**
     * Disconnects the active transport.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        _activeTransport.value.disconnect()
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
