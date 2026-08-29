package com.itantra.offlinevoice.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * Ultra-reliable Local Hotspot / LAN TCP Socket Transport with UDP Auto-Beaconing.
 *
 * Works 100% reliably when:
 * 1. Phone A turns on Portable Hotspot (no SIM/mobile data needed) and Phone B connects to it.
 * 2. Both phones are connected to any offline local Wi-Fi router.
 *
 * Automatically discovers peers on the local subnet using UDP broadcast beacons
 * and establishes direct high-speed TCP socket connections on port 45892.
 */
class LanSocketTransport(
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

    override val isAvailable: Boolean get() = getLocalIpAddress() != null

    // Socket I/O
    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var activeOutputStream: OutputStream? = null
    private var socketReaderJob: Job? = null
    private var serverJob: Job? = null
    private var beaconSenderJob: Job? = null
    private var beaconReceiverJob: Job? = null

    private val discoveredLanPeers = mutableMapOf<String, VoiceLinkDevice>()
    private var onPeersFoundCallback: ((List<VoiceLinkDevice>) -> Unit)? = null

    override suspend fun start() = withContext(Dispatchers.IO) {
        startServer()
        startBeaconReceiver()
        startBeaconSender()
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        stopBeaconSender()
        stopBeaconReceiver()
        disconnect()
        stopServer()
    }

    /**
     * Starts listening for incoming direct TCP socket connections from peers on port 45892.
     */
    private fun startServer() {
        serverJob?.cancel()
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket?.close()
                serverSocket = ServerSocket(LAN_PORT).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "✓ LAN TCP Server listening on port $LAN_PORT (Local IP: ${getLocalIpAddress()})")

                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    Log.i(TAG, "★ LAN TCP Client connected from ${client.inetAddress.hostAddress}")
                    handleConnectedSocket(client, isHost = true)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "LAN TCP Server stopped: ${e.message}")
                }
            }
        }
    }

    private fun stopServer() {
        serverJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    /**
     * Periodically broadcasts UDP beacon packet to 255.255.255.255 so any phone on the hotspot/Wi-Fi
     * discovers this device automatically in real-time.
     */
    private fun startBeaconSender() {
        beaconSenderJob?.cancel()
        beaconSenderJob = scope.launch(Dispatchers.IO) {
            val localName = android.os.Build.MODEL.ifBlank { "VoiceLink Phone" }
            val beaconMsg = "VOICELINK_BEACON:$localName:$LAN_PORT"
            val beaconData = beaconMsg.toByteArray(Charsets.UTF_8)

            while (isActive) {
                try {
                    val localIp = getLocalIpAddress()
                    if (localIp != null) {
                        val socket = DatagramSocket()
                        socket.broadcast = true
                        
                        // Android hotspot subnets vary by vendor. Broadcast to the
                        // active interface's real subnet as well as the global
                        // broadcast address, instead of assuming 192.168.43.x.
                        val targets = (getBroadcastAddresses() + InetAddress.getByName("255.255.255.255"))
                            .distinctBy { it.hostAddress }
                        for (target in targets) {
                            socket.send(DatagramPacket(beaconData, beaconData.size, target, BEACON_PORT))
                        }

                        socket.close()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "UDP Beacon send exception: ${e.message}")
                }
                delay(2000)
            }
        }
    }

    private fun stopBeaconSender() {
        beaconSenderJob?.cancel()
    }

    /**
     * Listens for UDP broadcast beacons from other phones on the local network/hotspot.
     */
    private fun startBeaconReceiver() {
        beaconReceiverJob?.cancel()
        beaconReceiverJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(BEACON_PORT).apply {
                    reuseAddress = true
                    broadcast = true
                }
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val senderIp = packet.address.hostAddress ?: continue
                    val myIp = getLocalIpAddress()

                    // Ignore own broadcast
                    if (senderIp == myIp || senderIp == "127.0.0.1") continue

                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (message.startsWith("VOICELINK_BEACON:")) {
                        val parts = message.split(":")
                        val deviceName = if (parts.size >= 2) parts[1] else "VoiceLink Peer ($senderIp)"
                        
                        val vld = VoiceLinkDevice(
                            deviceId = "VL-${senderIp.replace(".", "").takeLast(6)}",
                            displayName = "$deviceName (Hotspot/Wi-Fi)",
                            transportType = TransportType.WIFI_DIRECT,
                            nativeAddress = senderIp,
                            signalStrength = 4
                        )

                        discoveredLanPeers[senderIp] = vld
                        _connectionState.value = ConnectionState.DEVICE_FOUND
                        onPeersFoundCallback?.invoke(discoveredLanPeers.values.toList())
                        Log.i(TAG, "★ Discovered VoiceLink peer via UDP Beacon: $deviceName at $senderIp")
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.d(TAG, "Beacon receiver socket closed: ${e.message}")
                }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun stopBeaconReceiver() {
        beaconReceiverJob?.cancel()
    }

    override suspend fun discoverPeers(
        onPeersFound: (List<VoiceLinkDevice>) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        onPeersFoundCallback = onPeersFound
        discoveredLanPeers.clear()
        _connectionState.value = ConnectionState.DISCOVERING

        val localIp = getLocalIpAddress()
        if (localIp == null) {
            onError("No local Wi-Fi or Hotspot network detected. Please turn ON Hotspot or connect to Wi-Fi.")
            return@withContext
        }

        // Add Default Gateway / Hotspot Host (192.168.43.1) as quick 1-tap peer if connected to a hotspot
        if (localIp.startsWith("192.168.43.") && localIp != "192.168.43.1") {
            val hostDevice = VoiceLinkDevice(
                deviceId = "VL-HOTSPOT",
                displayName = "Hotspot Host Phone (192.168.43.1)",
                transportType = TransportType.WIFI_DIRECT,
                nativeAddress = "192.168.43.1",
                signalStrength = 4
            )
            discoveredLanPeers["192.168.43.1"] = hostDevice
            onPeersFound(discoveredLanPeers.values.toList())
        }

        startBeaconSender()
        startBeaconReceiver()
    }

    override suspend fun stopDiscovery() = withContext(Dispatchers.IO) {
        stopBeaconSender()
    }

    override suspend fun connect(device: VoiceLinkDevice): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.CONNECTING
        val targetIp = device.nativeAddress
        Log.i(TAG, "Connecting to LAN/Hotspot peer: ${device.displayName} at $targetIp:$LAN_PORT")

        try {
            val socket = Socket()
            socket.bind(null)
            socket.connect(InetSocketAddress(targetIp, LAN_PORT), 5000)
            Log.i(TAG, "✓ LAN TCP Socket connected successfully to $targetIp:$LAN_PORT")
            handleConnectedSocket(socket, isHost = false)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "LAN TCP Socket connection failed: ${e.message}", e)
            _connectionState.value = ConnectionState.FAILED
            return@withContext false
        }
    }

    private fun handleConnectedSocket(socket: Socket, isHost: Boolean) {
        activeSocket = socket
        activeOutputStream = socket.getOutputStream()

        val peerIp = socket.inetAddress.hostAddress ?: "Unknown IP"
        val peerDevice = VoiceLinkDevice(
            deviceId = "VL-${peerIp.replace(".", "").takeLast(6)}",
            displayName = if (isHost) "VoiceLink Peer ($peerIp)" else "VoiceLink Host ($peerIp)",
            transportType = TransportType.WIFI_DIRECT,
            nativeAddress = peerIp,
            signalStrength = 4,
            isGroupOwner = isHost
        )

        _connectedDevice.value = peerDevice
        _connectionState.value = ConnectionState.CONNECTED
        Log.i(TAG, "★ LAN TCP Session ACTIVE with ${peerDevice.displayName} (${peerDevice.nativeAddress})")

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
                        Log.w(TAG, "LAN socket stream EOF reached")
                        break
                    }
                    val extractedFrames = framer.pushBytes(buffer, bytesRead)
                    for (frame in extractedFrames) {
                        _incomingFrames.emit(frame)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.w(TAG, "LAN socket reader exception: ${e.message}")
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
            Log.e(TAG, "Failed to send bytes across LAN socket: ${e.message}", e)
            false
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Disconnecting LAN transport")
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
            Log.w(TAG, "LAN connection lost")
            _connectionState.value = ConnectionState.CONNECTION_LOST
            _connectedDevice.value = null
        }
    }

    companion object {
        private const val TAG = "LanSocketTransport"
        const val LAN_PORT = 45892
        const val BEACON_PORT = 45893

        /**
         * Resolves the device's local IPv4 address across active Wi-Fi, Hotspot (wlan0/ap0), or P2P interfaces.
         */
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                val activeInterfaces = mutableListOf<NetworkInterface>()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (!networkInterface.isLoopback && networkInterface.isUp) {
                        activeInterfaces += networkInterface
                    }
                }
                val wifiFirst = activeInterfaces.sortedByDescending { intf ->
                    val name = intf.name.lowercase()
                    name.contains("wlan") || name.contains("wifi") || name.contains("p2p") || name.contains("ap")
                }

                for (intf in wifiFirst) {
                    val addresses = intf.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is java.net.Inet4Address &&
                            !address.isLoopbackAddress &&
                            address.isSiteLocalAddress
                        ) {
                            return address.hostAddress
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }

        /** Returns broadcast addresses for every active Wi-Fi, hotspot, or P2P interface. */
        fun getBroadcastAddresses(): List<InetAddress> = try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            buildList {
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (!networkInterface.isLoopback && networkInterface.isUp) {
                        addAll(
                            networkInterface.interfaceAddresses
                                .mapNotNull { it.broadcast }
                                .filterIsInstance<java.net.Inet4Address>()
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
