package com.itantra.offlinevoice.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Production-grade Wi-Fi Direct (Wi-Fi P2P) transport driver using Android's official WifiP2pManager APIs.
 *
 * Implements:
 * - Peer discovery & DNS-SD Local Service Discovery
 * - Autonomous and standard group negotiation
 * - Non-blocking TCP Server/Client streams on Dispatchers.IO
 * - Robust binary framing reader/writer loops
 */
class WiFiDirectTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : Transport {

    override val transportType: TransportType = TransportType.WIFI_DIRECT

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<VoiceLinkDevice?>(null)
    override val connectedDevice: StateFlow<VoiceLinkDevice?> = _connectedDevice.asStateFlow()

    private val _incomingFrames = MutableSharedFlow<RawFrame>(extraBufferCapacity = 64)
    override val incomingFrames: Flow<RawFrame> = _incomingFrames.asSharedFlow()

    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    override val isAvailable: Boolean
        get() = wifiP2pManager != null && PermissionHelper.hasWifiDirectPermissions(context)

    // Socket I/O
    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var activeOutputStream: OutputStream? = null
    private var socketReaderJob: Job? = null
    private var socketServerJob: Job? = null

    private val discoveredDevicesMap = mutableMapOf<String, VoiceLinkDevice>()
    private var onPeersFoundCallback: ((List<VoiceLinkDevice>) -> Unit)? = null
    private var onDiscoveryErrorCallback: ((String) -> Unit)? = null

    init {
        try {
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            channel = wifiP2pManager?.initialize(context, context.mainLooper, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WifiP2pManager: ${e.message}", e)
        }
    }

    override suspend fun start() = withContext(Dispatchers.Main) {
        if (isReceiverRegistered || wifiP2pManager == null || channel == null) return@withContext

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        val isP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        Log.d(TAG, "WIFI_P2P_STATE_CHANGED: enabled=$isP2pEnabled")
                        if (!isP2pEnabled && _connectionState.value == ConnectionState.CONNECTED) {
                            scope.launch { disconnect() }
                        }
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        handlePeersChanged()
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }
                        Log.d(TAG, "WIFI_P2P_CONNECTION_CHANGED: isConnected=${networkInfo?.isConnected}")
                        if (networkInfo?.isConnected == true) {
                            requestConnectionInfo()
                        } else {
                            if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING) {
                                handleConnectionLost()
                            }
                        }
                    }

                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        }
                        Log.d(TAG, "WIFI_P2P_THIS_DEVICE_CHANGED: name=${device?.deviceName}, address=${device?.deviceAddress}, status=${device?.status}")
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        try {
            context.registerReceiver(receiver, filter)
            isReceiverRegistered = true
            Log.i(TAG, "Wi-Fi Direct BroadcastReceiver registered")
            
            // Register local DNS-SD service so other phones running VoiceLink discover this phone
            registerLocalDnsSdService()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Wi-Fi Direct receiver: ${e.message}", e)
        }
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        disconnect()
        if (isReceiverRegistered && receiver != null) {
            try {
                context.unregisterReceiver(receiver)
                isReceiverRegistered = false
                Log.i(TAG, "Wi-Fi Direct BroadcastReceiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerLocalDnsSdService() {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return
        if (!PermissionHelper.hasWifiDirectPermissions(context)) return

        val record = mapOf(
            "service" to "voicelink",
            "model" to Build.MODEL,
            "port" to P2P_PORT.toString()
        )
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            "VoiceLink_${Build.MODEL.replace(" ", "_")}",
            SERVICE_TYPE,
            record
        )

        mgr.addLocalService(ch, serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "✓ Wi-Fi Direct DNS-SD Local Service registered successfully")
            }
            override fun onFailure(code: Int) {
                Log.w(TAG, "Wi-Fi Direct addLocalService failed ($code)")
            }
        })
    }

    @SuppressLint("MissingPermission")
    override suspend fun discoverPeers(
        onPeersFound: (List<VoiceLinkDevice>) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasWifiDirectPermissions(context)) {
            onError("Wi-Fi Direct / Nearby Devices permissions required")
            return@withContext
        }

        val mgr = wifiP2pManager ?: run {
            onError("Wi-Fi Direct not supported on this device")
            return@withContext
        }
        val ch = channel ?: run {
            onError("Wi-Fi Direct channel not initialized")
            return@withContext
        }

        onPeersFoundCallback = onPeersFound
        onDiscoveryErrorCallback = onError
        discoveredDevicesMap.clear()
        _connectionState.value = ConnectionState.DISCOVERING

        // 1. Setup DNS-SD Service Discovery listeners
        setupDnsSdDiscovery(mgr, ch)

        // 2. Start Standard Wi-Fi Direct Peer Discovery
        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "✓ Wi-Fi Direct discoverPeers initiated")
            }

            override fun onFailure(reasonCode: Int) {
                val errorMsg = when (reasonCode) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is unsupported"
                    WifiP2pManager.BUSY -> "Wi-Fi Direct system busy. Please ensure Wi-Fi is ON."
                    WifiP2pManager.ERROR -> "Internal Wi-Fi Direct error"
                    else -> "Discovery error ($reasonCode)"
                }
                Log.w(TAG, "discoverPeers failed: $errorMsg")
                onDiscoveryErrorCallback?.invoke(errorMsg)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun setupDnsSdDiscovery(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
        mgr.setDnsSdResponseListeners(
            ch,
            { instanceName, registrationType, srcDevice ->
                Log.i(TAG, "★ Wi-Fi Direct DNS-SD Service found: $instanceName from ${srcDevice.deviceName} (${srcDevice.deviceAddress})")
                val cleanName = instanceName.removePrefix("VoiceLink_").replace("_", " ")
                val vld = VoiceLinkDevice(
                    deviceId = "VL-${srcDevice.deviceAddress.replace(":", "").takeLast(6).uppercase()}",
                    displayName = if (cleanName.isNotBlank()) cleanName else srcDevice.deviceName,
                    transportType = TransportType.WIFI_DIRECT,
                    nativeAddress = srcDevice.deviceAddress,
                    signalStrength = 4,
                    isPaired = srcDevice.status == WifiP2pDevice.CONNECTED,
                    isGroupOwner = srcDevice.isGroupOwner
                )
                discoveredDevicesMap[srcDevice.deviceAddress] = vld
                _connectionState.value = ConnectionState.DEVICE_FOUND
                onPeersFoundCallback?.invoke(discoveredDevicesMap.values.toList())
            },
            { fullDomainName, txtRecordMap, srcDevice ->
                Log.d(TAG, "DNS-SD TXT Record: $fullDomainName, map=$txtRecordMap")
            }
        )

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        mgr.addServiceRequest(ch, serviceRequest, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                mgr.discoverServices(ch, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(TAG, "✓ Wi-Fi Direct discoverServices initiated")
                    }
                    override fun onFailure(code: Int) {
                        Log.w(TAG, "discoverServices failed ($code)")
                    }
                })
            }
            override fun onFailure(code: Int) {}
        })
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopDiscovery() = withContext(Dispatchers.IO) {
        val mgr = wifiP2pManager ?: return@withContext
        val ch = channel ?: return@withContext
        mgr.stopPeerDiscovery(ch, null)
        mgr.clearServiceRequests(ch, null)
    }

    @SuppressLint("MissingPermission")
    private fun handlePeersChanged() {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return

        mgr.requestPeers(ch) { peerList: WifiP2pDeviceList? ->
            val deviceList = peerList?.deviceList ?: emptyList()
            Log.d(TAG, "Wi-Fi Direct peers found: ${deviceList.size}")

            for (p2pDevice in deviceList) {
                val vld = VoiceLinkDevice(
                    deviceId = "VL-${p2pDevice.deviceAddress.replace(":", "").takeLast(6).uppercase()}",
                    displayName = p2pDevice.deviceName.ifBlank { "VoiceLink Peer (${p2pDevice.deviceAddress.takeLast(5)})" },
                    transportType = TransportType.WIFI_DIRECT,
                    nativeAddress = p2pDevice.deviceAddress,
                    signalStrength = 4,
                    isPaired = p2pDevice.status == WifiP2pDevice.CONNECTED,
                    isGroupOwner = p2pDevice.isGroupOwner
                )
                discoveredDevicesMap[p2pDevice.deviceAddress] = vld
            }

            if (discoveredDevicesMap.isNotEmpty()) {
                if (_connectionState.value == ConnectionState.DISCOVERING) {
                    _connectionState.value = ConnectionState.DEVICE_FOUND
                }
            }

            onPeersFoundCallback?.invoke(discoveredDevicesMap.values.toList())
        }
    }

    /**
     * Creates an Autonomous Wi-Fi Direct Group (Host Group Owner).
     * Phone B can immediately discover and join this group!
     */
    @SuppressLint("MissingPermission")
    suspend fun createDirectGroup(): Boolean = withContext(Dispatchers.IO) {
        val mgr = wifiP2pManager ?: return@withContext false
        val ch = channel ?: return@withContext false
        if (!PermissionHelper.hasWifiDirectPermissions(context)) return@withContext false

        _connectionState.value = ConnectionState.CONNECTING
        Log.i(TAG, "Creating autonomous Wi-Fi Direct Group...")

        val deferred = CompletableDeferred<Boolean>()
        mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "✓ Autonomous Wi-Fi Direct Group created successfully!")
                requestConnectionInfo()
                deferred.complete(true)
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "createGroup failed: $reason")
                _connectionState.value = ConnectionState.FAILED
                deferred.complete(false)
            }
        })

        return@withContext withTimeoutOrNull(10000L) {
            deferred.await()
        } ?: false
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: VoiceLinkDevice): Boolean = withContext(Dispatchers.IO) {
        val mgr = wifiP2pManager ?: return@withContext false
        val ch = channel ?: return@withContext false

        _connectionState.value = ConnectionState.CONNECTING
        Log.i(TAG, "Connecting to Wi-Fi Direct peer: ${device.displayName} (${device.nativeAddress})")

        val config = WifiP2pConfig().apply {
            deviceAddress = device.nativeAddress
            wps.setup = WpsInfo.PBC
        }

        val deferred = CompletableDeferred<Boolean>()
        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "✓ Wi-Fi Direct connect invitation accepted by framework")
                deferred.complete(true)
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Wi-Fi Direct connect failed with reason: $reason")
                _connectionState.value = ConnectionState.FAILED
                deferred.complete(false)
            }
        })

        return@withContext withTimeoutOrNull(15000L) {
            deferred.await()
        } ?: false
    }

    @SuppressLint("MissingPermission")
    private fun requestConnectionInfo() {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return

        mgr.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
            if (info == null || !info.groupFormed) return@requestConnectionInfo

            Log.i(TAG, "★ Wi-Fi Direct connection established: isGroupOwner=${info.isGroupOwner}, groupOwnerAddress=${info.groupOwnerAddress?.hostAddress}")

            if (info.isGroupOwner) {
                startTcpServer()
            } else {
                val ownerIp = info.groupOwnerAddress?.hostAddress
                if (!ownerIp.isNullOrBlank()) {
                    startTcpClient(ownerIp)
                }
            }
        }
    }

    private fun startTcpServer() {
        socketServerJob?.cancel()
        socketServerJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket?.close()
                serverSocket = ServerSocket(P2P_PORT).apply { reuseAddress = true }
                Log.i(TAG, "Wi-Fi Direct TCP Server listening on port $P2P_PORT...")

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    Log.i(TAG, "Wi-Fi Direct TCP Client connected from ${clientSocket.inetAddress.hostAddress}")
                    handleActiveSocket(clientSocket, isOwner = true)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "TCP Server error: ${e.message}", e)
                }
            }
        }
    }

    private fun startTcpClient(hostIp: String) {
        socketServerJob?.cancel()
        socketServerJob = scope.launch(Dispatchers.IO) {
            var connected = false
            var attempts = 0
            while (!connected && attempts < 12 && isActive) {
                attempts++
                try {
                    val socket = Socket()
                    socket.bind(null)
                    socket.connect(InetSocketAddress(hostIp, P2P_PORT), 4000)
                    Log.i(TAG, "Wi-Fi Direct TCP Client successfully connected to Group Owner at $hostIp:$P2P_PORT")
                    handleActiveSocket(socket, isOwner = false)
                    connected = true
                } catch (e: Exception) {
                    Log.w(TAG, "TCP Client connect attempt $attempts failed (${e.message}), retrying in 1s...")
                    kotlinx.coroutines.delay(1000)
                }
            }

            if (!connected && isActive) {
                Log.e(TAG, "Failed to connect to Wi-Fi Direct Group Owner after 12 attempts")
                _connectionState.value = ConnectionState.FAILED
            }
        }
    }

    private fun handleActiveSocket(socket: Socket, isOwner: Boolean) {
        activeSocket = socket
        activeOutputStream = socket.getOutputStream()

        val peerIp = socket.inetAddress.hostAddress ?: "Unknown IP"
        val peerDevice = VoiceLinkDevice(
            deviceId = "VL-${peerIp.replace(".", "").takeLast(6)}",
            displayName = if (isOwner) "VoiceLink Client ($peerIp)" else "VoiceLink Host ($peerIp)",
            transportType = TransportType.WIFI_DIRECT,
            nativeAddress = peerIp,
            signalStrength = 4,
            isGroupOwner = !isOwner
        )

        _connectedDevice.value = peerDevice
        _connectionState.value = ConnectionState.CONNECTED
        Log.i(TAG, "★ Wi-Fi Direct session ACTIVE with ${peerDevice.displayName}")

        // Start binary framed reader loop
        socketReaderJob?.cancel()
        socketReaderJob = scope.launch(Dispatchers.IO) {
            val framer = PacketFramer.StreamFramer()
            val buffer = ByteArray(4096)
            val inputStream: InputStream = socket.getInputStream()

            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) {
                        Log.w(TAG, "Wi-Fi Direct socket stream EOF reached")
                        break
                    }
                    val extractedFrames = framer.pushBytes(buffer, bytesRead)
                    for (frame in extractedFrames) {
                        _incomingFrames.emit(frame)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "Wi-Fi Direct socket reader exception: ${e.message}")
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
            Log.e(TAG, "Failed to send bytes across Wi-Fi Direct socket: ${e.message}", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Disconnecting Wi-Fi Direct transport")
        socketReaderJob?.cancel()
        socketServerJob?.cancel()

        try { activeOutputStream?.close() } catch (_: Exception) {}
        try { activeSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}

        activeOutputStream = null
        activeSocket = null
        serverSocket = null

        val mgr = wifiP2pManager
        val ch = channel
        if (mgr != null && ch != null) {
            mgr.removeGroup(ch, null)
        }

        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun handleConnectionLost() {
        if (_connectionState.value == ConnectionState.CONNECTED) {
            Log.w(TAG, "Wi-Fi Direct connection lost")
            _connectionState.value = ConnectionState.CONNECTION_LOST
            _connectedDevice.value = null
        }
    }

    companion object {
        private const val TAG = "WiFiDirectTransport"
        const val P2P_PORT = 45892
        const val SERVICE_TYPE = "_voicelink._tcp"
    }
}
