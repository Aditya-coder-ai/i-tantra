package com.itantra.offlinevoice.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates unified peer discovery across Local Hotspot/Wi-Fi, Wi-Fi Direct, and Bluetooth.
 * Merges and deduplicates discovered devices into a single live stream.
 */
class DeviceDiscoveryManager(
    private val wifiDirectTransport: WiFiDirectTransport,
    private val bluetoothTransport: BluetoothTransport,
    private val lanSocketTransport: LanSocketTransport,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val _discoveredDevices = MutableStateFlow<List<VoiceLinkDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<VoiceLinkDevice>> = _discoveredDevices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val deviceMap = mutableMapOf<String, VoiceLinkDevice>()
    private var discoveryTimeoutJob: Job? = null

    /**
     * Starts active discovery across all available offline radios.
     */
    suspend fun startDiscovery(
        preferredTransport: TransportType = TransportType.WIFI_DIRECT,
        timeoutMs: Long = 30000L
    ) = withContext(Dispatchers.Default) {
        _isDiscovering.value = true
        deviceMap.clear()
        _discoveredDevices.value = emptyList()

        // 1. Local Hotspot / LAN UDP Beacon Discovery (Fastest & Most Reliable)
        try {
            lanSocketTransport.discoverPeers(
                onPeersFound = { peers ->
                    updateDeviceMap(peers)
                },
                onError = { err ->
                    Log.d(TAG, "LAN discovery message: $err")
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "LAN discovery exception: ${e.message}")
        }

        // 2. Wi-Fi Direct discovery
        try {
            wifiDirectTransport.discoverPeers(
                onPeersFound = { peers ->
                    updateDeviceMap(peers)
                },
                onError = { err ->
                    Log.d(TAG, "Wi-Fi Direct discovery message: $err")
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Wi-Fi Direct discovery exception: ${e.message}")
        }

        // 3. Bluetooth discovery (BLE + Classic)
        try {
            bluetoothTransport.discoverPeers(
                onPeersFound = { peers ->
                    updateDeviceMap(peers)
                },
                onError = { err ->
                    Log.d(TAG, "Bluetooth discovery message: $err")
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth discovery exception: ${e.message}")
        }

        // Discovery timeout timer
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = scope.launch {
            delay(timeoutMs)
            stopDiscovery()
        }
    }

    /**
     * Stops all active discovery.
     */
    suspend fun stopDiscovery() = withContext(Dispatchers.Default) {
        discoveryTimeoutJob?.cancel()
        _isDiscovering.value = false
        lanSocketTransport.stopDiscovery()
        wifiDirectTransport.stopDiscovery()
        bluetoothTransport.stopDiscovery()
        Log.i(TAG, "Discovery stopped. Total found: ${deviceMap.size} peers.")
    }

    @Synchronized
    private fun updateDeviceMap(newDevices: List<VoiceLinkDevice>) {
        for (device in newDevices) {
            val key = "${device.transportType}_${device.nativeAddress}"
            deviceMap[key] = device
        }
        _discoveredDevices.value = deviceMap.values.toList()
    }

    companion object {
        private const val TAG = "DeviceDiscoveryManager"
    }
}
