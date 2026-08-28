package com.itantra.offlinevoice.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Enhanced Bluetooth transport with BLE Advertising + BLE Scanning + Classic Discovery.
 * Enables zero-configuration discovery between two Android phones running VoiceLink.
 */
class BluetoothTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : Transport {

    override val transportType: TransportType = TransportType.BLUETOOTH

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<VoiceLinkDevice?>(null)
    override val connectedDevice: StateFlow<VoiceLinkDevice?> = _connectedDevice.asStateFlow()

    private val _incomingFrames = MutableSharedFlow<RawFrame>(extraBufferCapacity = 64)
    override val incomingFrames: Flow<RawFrame> = _incomingFrames.asSharedFlow()

    private val bluetoothManager: BluetoothManager? = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    override val isAvailable: Boolean
        get() = bluetoothAdapter?.isEnabled == true && PermissionHelper.hasBluetoothPermissions(context)

    // BLE Subsystems
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var isBleAdvertising = false
    private var isBleScanning = false

    // Sockets & I/O
    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var activeOutputStream: OutputStream? = null
    private var socketReaderJob: Job? = null
    private var serverListenerJob: Job? = null

    private var discoveryReceiver: BroadcastReceiver? = null
    private var isDiscoveryRegistered = false
    private val discoveredDevicesMap = mutableMapOf<String, VoiceLinkDevice>()
    private var onPeersFoundCallback: ((List<VoiceLinkDevice>) -> Unit)? = null
    private var onDiscoveryErrorCallback: ((String) -> Unit)? = null

    override suspend fun start() = withContext(Dispatchers.IO) {
        startServerListener()
        startBleAdvertising()
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        stopBleAdvertising()
        stopDiscovery()
        disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun startServerListener() {
        serverListenerJob?.cancel()
        serverListenerJob = scope.launch(Dispatchers.IO) {
            val adapter = bluetoothAdapter ?: return@launch
            if (!PermissionHelper.hasBluetoothPermissions(context)) return@launch

            try {
                serverSocket?.close()
                // Use Insecure RFCOMM so connection succeeds without requiring OS-level PIN pairing
                serverSocket = try {
                    adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, VOICELINK_BT_UUID)
                } catch (_: Exception) {
                    adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, VOICELINK_BT_UUID)
                }
                Log.i(TAG, "✓ Bluetooth RFCOMM Server listening on UUID $VOICELINK_BT_UUID...")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    Log.i(TAG, "★ Bluetooth connection ACCEPTED from ${socket.remoteDevice.name ?: socket.remoteDevice.address}")
                    handleConnectedSocket(socket, isHost = true)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "Bluetooth Server socket exception: ${e.message}")
                }
            }
        }
    }

    /**
     * Starts BLE advertising so nearby phones running VoiceLink can discover this device immediately.
     */
    @SuppressLint("MissingPermission")
    fun startBleAdvertising() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !PermissionHelper.hasBluetoothPermissions(context)) return

        try {
            bleAdvertiser = adapter.bluetoothLeAdvertiser
            if (bleAdvertiser == null) {
                Log.w(TAG, "BLE Advertiser not supported on this hardware")
                return
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(VOICELINK_BT_UUID))
                .build()

            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start BLE advertising: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleAdvertising() {
        if (isBleAdvertising) {
            try {
                bleAdvertiser?.stopAdvertising(advertiseCallback)
                isBleAdvertising = false
                Log.d(TAG, "BLE advertising stopped")
            } catch (_: Exception) {}
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isBleAdvertising = true
            Log.i(TAG, "✓ BLE Advertising VoiceLink Service successfully started!")
        }

        override fun onStartFailure(errorCode: Int) {
            isBleAdvertising = false
            Log.w(TAG, "BLE Advertising failed with error code: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun discoverPeers(
        onPeersFound: (List<VoiceLinkDevice>) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.Main) {
        if (!PermissionHelper.hasBluetoothPermissions(context)) {
            onError("Bluetooth permissions required for scanning")
            return@withContext
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onError("Bluetooth is turned off. Please turn Bluetooth ON.")
            return@withContext
        }

        onPeersFoundCallback = onPeersFound
        onDiscoveryErrorCallback = onError
        discoveredDevicesMap.clear()
        _connectionState.value = ConnectionState.DISCOVERING

        // 1. Immediately ensure BLE Advertising is active
        startBleAdvertising()

        // 2. Add already bonded (paired) devices first
        try {
            val bonded = adapter.bondedDevices ?: emptySet()
            for (dev in bonded) {
                val name = dev.name ?: "Paired Device (${dev.address.takeLast(5)})"
                val vld = VoiceLinkDevice(
                    deviceId = "VL-${dev.address.replace(":", "").takeLast(6).uppercase()}",
                    displayName = name,
                    transportType = TransportType.BLUETOOTH,
                    nativeAddress = dev.address,
                    signalStrength = 4,
                    isPaired = true
                )
                discoveredDevicesMap[dev.address] = vld
            }
            if (discoveredDevicesMap.isNotEmpty()) {
                onPeersFoundCallback?.invoke(discoveredDevicesMap.values.toList())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read bonded devices: ${e.message}")
        }

        // 3. Start BLE Scanner
        startBleScan()

        // 4. Start Classic Bluetooth Discovery as supplemental scanner
        startClassicDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        val adapter = bluetoothAdapter ?: return
        bleScanner = adapter.bluetoothLeScanner

        if (bleScanner != null && !isBleScanning) {
            try {
                val filters = listOf(
                    ScanFilter.Builder().setServiceUuid(ParcelUuid(VOICELINK_BT_UUID)).build()
                )
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                bleScanner?.startScan(filters, settings, bleScanCallback)
                // Also start general scan with null filters if specific UUID filter is restrictive on some OEM chipsets
                bleScanner?.startScan(bleScanCallback)
                isBleScanning = true
                Log.i(TAG, "✓ BLE Scanning for VoiceLink peers started")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start BLE scan: ${e.message}")
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { dev ->
                val name = try { dev.name } catch (_: SecurityException) { null }
                    ?: try { result.scanRecord?.deviceName } catch (_: Exception) { null }

                // Check if this device has our service UUID or has a valid name
                val uuids = result.scanRecord?.serviceUuids ?: emptyList()
                val hasVoiceLinkUuid = uuids.any { it.uuid == VOICELINK_BT_UUID }

                if (hasVoiceLinkUuid || !name.isNullOrBlank()) {
                    val displayName = name ?: "VoiceLink Peer (${dev.address.takeLast(5)})"
                    val vld = VoiceLinkDevice(
                        deviceId = "VL-${dev.address.replace(":", "").takeLast(6).uppercase()}",
                        displayName = displayName,
                        transportType = TransportType.BLUETOOTH,
                        nativeAddress = dev.address,
                        signalStrength = 4,
                        isPaired = dev.bondState == BluetoothDevice.BOND_BONDED
                    )
                    discoveredDevicesMap[dev.address] = vld
                    _connectionState.value = ConnectionState.DEVICE_FOUND
                    onPeersFoundCallback?.invoke(discoveredDevicesMap.values.toList())
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE Scan failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startClassicDiscovery() {
        val adapter = bluetoothAdapter ?: return

        if (!isDiscoveryRegistered) {
            discoveryReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            if (device != null) {
                                val name = try { device.name } catch (_: SecurityException) { null }
                                val vld = VoiceLinkDevice(
                                    deviceId = "VL-${device.address.replace(":", "").takeLast(6).uppercase()}",
                                    displayName = name ?: "Bluetooth (${device.address.takeLast(5)})",
                                    transportType = TransportType.BLUETOOTH,
                                    nativeAddress = device.address,
                                    signalStrength = 3,
                                    isPaired = device.bondState == BluetoothDevice.BOND_BONDED
                                )
                                discoveredDevicesMap[device.address] = vld
                                _connectionState.value = ConnectionState.DEVICE_FOUND
                                onPeersFoundCallback?.invoke(discoveredDevicesMap.values.toList())
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            Log.d(TAG, "Classic Bluetooth discovery cycle finished")
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            try {
                context.registerReceiver(discoveryReceiver, filter)
                isDiscoveryRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register classic discovery receiver: ${e.message}", e)
            }
        }

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Classic startDiscovery error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopDiscovery() = withContext(Dispatchers.Main) {
        if (isBleScanning) {
            try {
                bleScanner?.stopScan(bleScanCallback)
                isBleScanning = false
            } catch (_: Exception) {}
        }

        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (_: Exception) {}

        if (isDiscoveryRegistered && discoveryReceiver != null) {
            try {
                context.unregisterReceiver(discoveryReceiver)
                isDiscoveryRegistered = false
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: VoiceLinkDevice): Boolean = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter ?: return@withContext false
        if (!PermissionHelper.hasBluetoothPermissions(context)) return@withContext false

        _connectionState.value = ConnectionState.CONNECTING
        Log.i(TAG, "Connecting to Bluetooth peer: ${device.displayName} (${device.nativeAddress})")

        // Stop scanning to improve radio bandwidth & avoid connection collision
        try {
            stopDiscovery()
        } catch (_: Exception) {}

        val remoteDevice = try {
            adapter.getRemoteDevice(device.nativeAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Bluetooth address ${device.nativeAddress}: ${e.message}")
            _connectionState.value = ConnectionState.FAILED
            return@withContext false
        }

        // Strategy 1: Insecure RFCOMM with UUID
        try {
            val socket = remoteDevice.createInsecureRfcommSocketToServiceRecord(VOICELINK_BT_UUID)
            socket.connect()
            Log.i(TAG, "✓ Bluetooth Strategy 1 (Insecure UUID) connected successfully to ${device.displayName}!")
            handleConnectedSocket(socket, isHost = false)
            return@withContext true
        } catch (e1: Exception) {
            Log.w(TAG, "Strategy 1 (Insecure UUID) failed: ${e1.message}")
        }

        // Strategy 2: Secure RFCOMM with UUID
        try {
            val socket = remoteDevice.createRfcommSocketToServiceRecord(VOICELINK_BT_UUID)
            socket.connect()
            Log.i(TAG, "✓ Bluetooth Strategy 2 (Secure UUID) connected successfully to ${device.displayName}!")
            handleConnectedSocket(socket, isHost = false)
            return@withContext true
        } catch (e2: Exception) {
            Log.w(TAG, "Strategy 2 (Secure UUID) failed: ${e2.message}")
        }

        // Strategy 3: Insecure Reflection Channel 1
        try {
            val method = remoteDevice.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
            val socket = method.invoke(remoteDevice, 1) as BluetoothSocket
            socket.connect()
            Log.i(TAG, "✓ Bluetooth Strategy 3 (Insecure Reflection Channel 1) connected to ${device.displayName}!")
            handleConnectedSocket(socket, isHost = false)
            return@withContext true
        } catch (e3: Exception) {
            Log.w(TAG, "Strategy 3 (Insecure Reflection) failed: ${e3.message}")
        }

        // Strategy 4: Secure Reflection Channel 1
        try {
            val method = remoteDevice.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val socket = method.invoke(remoteDevice, 1) as BluetoothSocket
            socket.connect()
            Log.i(TAG, "✓ Bluetooth Strategy 4 (Secure Reflection Channel 1) connected to ${device.displayName}!")
            handleConnectedSocket(socket, isHost = false)
            return@withContext true
        } catch (e4: Exception) {
            Log.e(TAG, "All 4 Bluetooth connection strategies failed: ${e4.message}")
        }

        _connectionState.value = ConnectionState.FAILED
        return@withContext false
    }

    @SuppressLint("MissingPermission")
    private fun handleConnectedSocket(socket: BluetoothSocket, isHost: Boolean) {
        activeSocket = socket
        activeOutputStream = socket.outputStream

        val devName = try { socket.remoteDevice.name } catch (_: SecurityException) { null }
            ?: "Bluetooth Peer (${socket.remoteDevice.address.takeLast(5)})"

        val peerDevice = VoiceLinkDevice(
            deviceId = "VL-${socket.remoteDevice.address.replace(":", "").takeLast(6).uppercase()}",
            displayName = devName,
            transportType = TransportType.BLUETOOTH,
            nativeAddress = socket.remoteDevice.address,
            signalStrength = 4,
            isGroupOwner = isHost
        )

        _connectedDevice.value = peerDevice
        _connectionState.value = ConnectionState.CONNECTED
        Log.i(TAG, "★ Bluetooth session ACTIVE with ${peerDevice.displayName} (${peerDevice.nativeAddress})")

        // Start binary framed reader loop
        socketReaderJob?.cancel()
        socketReaderJob = scope.launch(Dispatchers.IO) {
            val framer = PacketFramer.StreamFramer()
            val buffer = ByteArray(2048)
            val inputStream: InputStream = socket.inputStream

            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) {
                        Log.w(TAG, "Bluetooth stream EOF reached")
                        break
                    }
                    val extractedFrames = framer.pushBytes(buffer, bytesRead)
                    for (frame in extractedFrames) {
                        _incomingFrames.emit(frame)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "Bluetooth socket reader exception: ${e.message}")
                }
            } finally {
                handleConnectionLost()
            }
        }
    }

    override suspend fun sendFrame(frame: RawFrame): Boolean {
        val framedBytes = PacketFramer.frame(frame.type, frame.payload)
        return sendRawBytes(framedBytes)
    }

    override suspend fun sendRawBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val os = activeOutputStream ?: return@withContext false
        return@withContext try {
            os.write(bytes)
            os.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to Bluetooth socket: ${e.message}", e)
            false
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Disconnecting Bluetooth transport")
        socketReaderJob?.cancel()
        try { activeOutputStream?.close() } catch (_: Exception) {}
        try { activeSocket?.close() } catch (_: Exception) {}
        activeOutputStream = null
        activeSocket = null
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun handleConnectionLost() {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            Log.w(TAG, "Bluetooth connection lost")
            _connectionState.value = ConnectionState.CONNECTION_LOST
            _connectedDevice.value = null
        }
    }

    companion object {
        private const val TAG = "BluetoothTransport"
        private const val SERVICE_NAME = "VoiceLink_P2P"
        val VOICELINK_BT_UUID: UUID = UUID.fromString("000017A4-0000-1000-8000-00805F9B34FB")
    }
}
