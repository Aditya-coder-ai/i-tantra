package com.itantra.offlinevoice.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
 * Production-grade Bluetooth RFCOMM / SPP Transport driver.
 * Serves as secondary fallback when Wi-Fi Direct is disabled or unavailable.
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
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
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
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, VOICELINK_BT_UUID)
                Log.i(TAG, "Bluetooth RFCOMM Server listening for incoming connections...")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    Log.i(TAG, "Bluetooth connection accepted from ${socket.remoteDevice.name ?: socket.remoteDevice.address}")
                    handleConnectedSocket(socket, isHost = true)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "Bluetooth Server socket closed: ${e.message}")
                }
            }
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
            onError("Bluetooth is turned off")
            return@withContext
        }

        onPeersFoundCallback = onPeersFound
        onDiscoveryErrorCallback = onError
        discoveredDevicesMap.clear()
        _connectionState.value = ConnectionState.DISCOVERING

        // 1. Add already bonded (paired) devices first
        try {
            val bonded = adapter.bondedDevices ?: emptySet()
            for (dev in bonded) {
                val vld = VoiceLinkDevice(
                    deviceId = "VL-${dev.address.replace(":", "").takeLast(6).uppercase()}",
                    displayName = dev.name ?: "Bluetooth Device (${dev.address.takeLast(5)})",
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
            Log.w(TAG, "Failed to read bonded Bluetooth devices: ${e.message}")
        }

        // 2. Start active discovery for unbonded devices
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
                                    displayName = name ?: "Nearby Bluetooth (${device.address.takeLast(5)})",
                                    transportType = TransportType.BLUETOOTH,
                                    nativeAddress = device.address,
                                    signalStrength = 3,
                                    isPaired = false
                                )
                                discoveredDevicesMap[device.address] = vld
                                _connectionState.value = ConnectionState.DEVICE_FOUND
                                onPeersFoundCallback?.invoke(discoveredDevicesMap.values.toList())
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            Log.d(TAG, "Bluetooth discovery completed")
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
                Log.e(TAG, "Failed to register Bluetooth discovery receiver: ${e.message}", e)
            }
        }

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
            Log.i(TAG, "Bluetooth discovery initiated")
        } catch (e: Exception) {
            Log.e(TAG, "startDiscovery error: ${e.message}", e)
            onError("Failed to start Bluetooth scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopDiscovery() = withContext(Dispatchers.Main) {
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

        try {
            adapter.cancelDiscovery()
            val remoteDevice = adapter.getRemoteDevice(device.nativeAddress)
            val socket = remoteDevice.createRfcommSocketToServiceRecord(VOICELINK_BT_UUID)

            socket.connect()
            Log.i(TAG, "Bluetooth RFCOMM socket connected successfully!")
            handleConnectedSocket(socket, isHost = false)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth connect failed: ${e.message}", e)
            _connectionState.value = ConnectionState.FAILED
            return@withContext false
        }
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
        Log.i(TAG, "Bluetooth session active with ${peerDevice.displayName}")

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
