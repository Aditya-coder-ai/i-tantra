package com.itantra.offlinevoice.ui.mock

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.itantra.offlinevoice.audio.AudioChunk
import com.itantra.offlinevoice.audio.AudioRecorder
import com.itantra.offlinevoice.audio.RecordingState
import com.itantra.offlinevoice.audio.debug.AudioDebugHelper
import com.itantra.offlinevoice.audio.debug.AudioDiagnostics
import com.itantra.offlinevoice.audio.stt.AndroidSpeechRecognizerEngine
import com.itantra.offlinevoice.audio.stt.STTLanguage
import com.itantra.offlinevoice.audio.stt.STTResult
import com.itantra.offlinevoice.audio.stt.VoskSttEngine
import com.itantra.offlinevoice.audio.tts.TtsEngine
import com.itantra.offlinevoice.audio.vad.SpeechSegment
import com.itantra.offlinevoice.audio.vad.VoiceActivityDetector
import com.itantra.offlinevoice.communication.manager.ITantraCommunicationManager
import com.itantra.offlinevoice.communication.manager.TantraCommunicationManagerImpl
import com.itantra.offlinevoice.communication.model.DeliveryState
import com.itantra.offlinevoice.communication.model.PeerIdentity
import com.itantra.offlinevoice.security.CryptoManager
import com.itantra.offlinevoice.security.SecurityController
import com.itantra.offlinevoice.text.MessageBuilder
import com.itantra.offlinevoice.text.MessageClassifier
import com.itantra.offlinevoice.text.MessagePriority
import com.itantra.offlinevoice.text.MessageType
import com.itantra.offlinevoice.text.TextProcessor
import com.itantra.offlinevoice.network.ConnectionState
import com.itantra.offlinevoice.network.DeliveryUpdate
import com.itantra.offlinevoice.network.MessageDeliveryStatus
import com.itantra.offlinevoice.network.NetworkMetrics
import com.itantra.offlinevoice.network.PermissionHelper
import com.itantra.offlinevoice.network.TransportManager
import com.itantra.offlinevoice.network.TransportType
import com.itantra.offlinevoice.network.VoiceLinkDevice
import com.itantra.offlinevoice.security.EncryptedMessagePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CommunicationState { IDLE, LISTENING, PROCESSING, SENDING, RECEIVED }
enum class LinkState { DISCONNECTED, SEARCHING, DEVICE_FOUND, CONNECTING, CONNECTED, FAILED }

data class VoiceMessage(
    val id: Int,
    val text: String,
    val isMine: Boolean,
    val language: String,
    val time: String,
    val delivered: Boolean = true,
    val emergency: Boolean = false,
    val hopCount: Int = 0,
    val latencyMs: Long = 0L
)

data class NearbyDevice(
    val name: String,
    val detail: String,
    val signal: Int,
    val paired: Boolean = false,
    val publicKeyHex: String = "4a8e29bf10c7",
    val nativeAddress: String = "",
    val transportType: TransportType = TransportType.WIFI_DIRECT
)

data class VoiceSettingsState(
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val micSensitivity: Float = 0.8f,
    val autoPlayIncoming: Boolean = true
)

data class AudioSettingsState(
    val sampleRate: String = "16 kHz Mono PCM",
    val noiseReduction: Boolean = true,
    val preRollBufferMs: Int = 300
)

data class EmergencySettingsState(
    val customAlertPhrase: String = "Emergency assistance required. My location should be checked.",
    val autoBroadcastSos: Boolean = true,
    val ttlHops: Int = 15
)

data class SystemHardwareStats(
    val batteryPercent: Int = 85,
    val ramUsageMb: Long = 120L,
    val maxRamMb: Long = 512L,
    val sttStatus: String = "Ready (On-Device Vosk)",
    val ttsStatus: String = "Ready (Android Speech)",
    val packetsRelayed: Long = 0L,
    val messagesDelivered: Long = 0L
)

data class VoiceLinkUiState(
    val language: String = "Hindi · हिन्दी",
    val communicationState: CommunicationState = CommunicationState.IDLE,
    val linkState: LinkState = LinkState.DISCONNECTED,
    val connectionType: String = "Wi‑Fi Direct",
    val connectedDevice: String = "No Device Connected",
    val mode: String = "Push-to-Talk",
    val lastMessage: String = "Ready to communicate",
    val messages: List<VoiceMessage> = emptyList(),
    val devices: List<NearbyDevice> = emptyList(),
    val realDiscoveredDevices: List<VoiceLinkDevice> = emptyList(),
    val voiceSettings: VoiceSettingsState = VoiceSettingsState(),
    val audioSettings: AudioSettingsState = AudioSettingsState(),
    val emergencySettings: EmergencySettingsState = EmergencySettingsState(),
    val systemStats: SystemHardwareStats = SystemHardwareStats(),
    val networkMetrics: NetworkMetrics = NetworkMetrics(),
    val localDeviceId: String = "VL-LOCAL",
    val isDiscovering: Boolean = false,
    val transcribedText: String = "",
    val isTranscribing: Boolean = false,
    val recordingDurationMs: Long = 0L
)

class MockVoiceLinkController(private val context: Context) : AudioRecorder.Listener, VoiceActivityDetector.Listener {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // Core Engine Instances
    val ttsEngine: TtsEngine = TtsEngine(context)
    private val sttEngine: VoskSttEngine = VoskSttEngine(context)
    private val textProcessor: TextProcessor = TextProcessor()
    val communicationManager: ITantraCommunicationManager = TantraCommunicationManagerImpl()
    
    val securityController: SecurityController = SecurityController()
    val transportManager: TransportManager = TransportManager(context)
    
    private val recorder = AudioRecorder(context, listener = this)
    private val vad = VoiceActivityDetector(listener = this)
    private val androidSpeechRecognizer = AndroidSpeechRecognizerEngine(context)
    private var isUsingAndroidSpeech = false

    // Audio Diagnostics & Workbench state
    val audioDebugHelper: AudioDebugHelper = AudioDebugHelper(context)
    val vadEngine: VoiceActivityDetector get() = vad
    val audioRecorderEngine: AudioRecorder get() = recorder
    val voskEngine: VoskSttEngine get() = sttEngine
    var lastCapturedRawAudio: ShortArray by mutableStateOf(ShortArray(0))
        private set
    var lastCapturedVadAudio: ShortArray by mutableStateOf(ShortArray(0))
        private set
    var lastDiagnostics: AudioDiagnostics by mutableStateOf(AudioDiagnostics())
        private set
    var isPlayingDebugAudio: Boolean by mutableStateOf(false)
        private set
    var bypassVadInStt: Boolean by mutableStateOf(false)
    
    // Recording timing
    private var recordingStartTimeMs: Long = 0L

    var ui by mutableStateOf(VoiceLinkUiState())
        private set

    init {
        scope.launch {
            // 1. Initialize Real Security Identity
            val localDevName = android.os.Build.MODEL.ifBlank { "VoiceLink Device" }
            val identity = securityController.initializeIdentity(localDevName)
            update { copy(localDeviceId = "VL-${identity.deviceId.takeLast(6).uppercase()}") }

            // 2. Pre-pair default mock contacts for backward compatibility
            try {
                val demoKey1 = CryptoManager.encodePublicKey(CryptoManager.generateEcKeyPair().public)
                val demoKey2 = CryptoManager.encodePublicKey(CryptoManager.generateEcKeyPair().public)
                securityController.pairPreSharedDevice("Rescue Team 01", "Rescue Team 01", demoKey1)
                securityController.pairPreSharedDevice("Medical Unit 04", "Medical Unit 04", demoKey2)
            } catch (_: Exception) {}

            // 3. Start Real Transport Manager
            transportManager.start()

            // 4. Observe Real Transport Connection State
            transportManager.connectionState.collect { state ->
                val mappedLinkState = when (state) {
                    ConnectionState.DISCONNECTED -> LinkState.SEARCHING
                    ConnectionState.DISCOVERING -> LinkState.SEARCHING
                    ConnectionState.DEVICE_FOUND -> LinkState.DEVICE_FOUND
                    ConnectionState.CONNECTING -> LinkState.CONNECTING
                    ConnectionState.CONNECTED -> LinkState.CONNECTED
                    ConnectionState.RECONNECTING -> LinkState.CONNECTING
                    ConnectionState.CONNECTION_LOST, ConnectionState.FAILED -> LinkState.FAILED
                }
                update {
                    copy(
                        linkState = mappedLinkState,
                        lastMessage = when (state) {
                            ConnectionState.CONNECTED -> "Connected to ${ui.connectedDevice} 🔐"
                            ConnectionState.CONNECTING -> "Handshaking with peer..."
                            ConnectionState.DISCOVERING -> "Searching for nearby devices..."
                            ConnectionState.DEVICE_FOUND -> "Nearby devices discovered"
                            ConnectionState.CONNECTION_LOST -> "Connection lost — attempting reconnection"
                            ConnectionState.FAILED -> "Connection failed"
                            ConnectionState.DISCONNECTED -> "Disconnected"
                            ConnectionState.RECONNECTING -> "Reconnecting..."
                        }
                    )
                }
            }
        }

        // 5. Observe Connected Peer
        scope.launch {
            transportManager.connectedDevice.collect { device ->
                if (device != null) {
                    // Auto-establish a trusted session with this peer identity
                    try {
                        val peerKey = CryptoManager.encodePublicKey(CryptoManager.generateEcKeyPair().public)
                        securityController.pairPreSharedDevice(device.deviceId, device.displayName, peerKey)
                    } catch (_: Exception) {}

                    update {
                        copy(
                            connectedDevice = device.displayName,
                            connectionType = device.transportType.displayName,
                            linkState = LinkState.CONNECTED
                        )
                    }
                } else {
                    update {
                        copy(
                            connectedDevice = "No Device Connected"
                        )
                    }
                }
            }
        }

        // 6. Observe Discovered Peers
        scope.launch {
            transportManager.discoveredDevices.collect { peerList ->
                val converted = peerList.map { dev ->
                    NearbyDevice(
                        name = dev.displayName,
                        detail = "${dev.transportType.displayName} · ${dev.formattedId}",
                        signal = dev.signalStrength,
                        paired = dev.isPaired,
                        publicKeyHex = dev.deviceId.takeLast(8),
                        nativeAddress = dev.nativeAddress,
                        transportType = dev.transportType
                    )
                }
                update {
                    copy(
                        devices = converted,
                        realDiscoveredDevices = peerList
                    )
                }
            }
        }

        // 7. Observe Real Network Metrics
        scope.launch {
            transportManager.networkMetrics.collect { metrics ->
                update { copy(networkMetrics = metrics) }
            }
        }

        // 8. Listen for Incoming Decrypted Messages over Real Transport
        scope.launch {
            transportManager.incomingEncryptedMessages.collect { encryptedPacket ->
                try {
                    Log.i("VoiceLinkController", "★ Incoming Encrypted Message from network: ${encryptedPacket.messageId}")
                    val decryptedMessage = securityController.decryptIncoming(encryptedPacket)

                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    val newMessage = VoiceMessage(
                        id = (ui.messages.maxOfOrNull { it.id } ?: 0) + 1,
                        text = decryptedMessage.text,
                        isMine = false,
                        language = decryptedMessage.language,
                        time = timeStr,
                        delivered = true,
                        emergency = decryptedMessage.messageType == MessageType.EMERGENCY
                    )

                    update {
                        copy(
                            messages = messages + newMessage,
                            lastMessage = "Received: ${decryptedMessage.text}",
                            communicationState = CommunicationState.RECEIVED
                        )
                    }

                    // Automatic Offline TTS Playback on Receiving Phone
                    if (ui.voiceSettings.autoPlayIncoming) {
                        Log.i("VoiceLinkController", "🔊 Auto-playing incoming voice message: '${decryptedMessage.text}' (${decryptedMessage.language})")
                        ttsEngine.speak(decryptedMessage.text, decryptedMessage.language)
                    }
                } catch (e: Exception) {
                    Log.e("VoiceLinkController", "Failed to decrypt incoming packet: ${e.message}", e)
                }
            }
        }

        // 9. Sync hardware stats periodically
        scope.launch {
            while (true) {
                refreshSystemStats()
                delay(3000)
            }
        }
    }

    fun startTalking() {
        recordingStartTimeMs = SystemClock.elapsedRealtime()
        val langCode = getLangCode()
        val sttLang = STTLanguage.fromCode(langCode)

        val voskAvailable = sttEngine.isModelAvailable(sttLang)
        Log.i("VoiceLinkController", "★ startTalking: lang=$langCode, voskAvailable=$voskAvailable")

        if (voskAvailable) {
            isUsingAndroidSpeech = false
            val started = recorder.startRecording()
            if (!started) return
        } else {
            isUsingAndroidSpeech = true
            Log.i("VoiceLinkController", "→ Using AndroidSpeechRecognizer (no Vosk model)")
            androidSpeechRecognizer.startListening(
                language = sttLang,
                onResult = { text ->
                    val duration = SystemClock.elapsedRealtime() - recordingStartTimeMs
                    Log.i("VoiceLinkController", "✓ AndroidSpeech onResult: '$text' (${duration}ms)")
                    processRecognizedText(text, sttLang, duration)
                },
                onError = { errorMsg ->
                    Log.w("VoiceLinkController", "✗ AndroidSpeech onError: '$errorMsg'")
                    update {
                        copy(
                            communicationState = CommunicationState.IDLE,
                            lastMessage = errorMsg,
                            isTranscribing = false
                        )
                    }
                }
            )
        }

        update {
            copy(
                communicationState = CommunicationState.LISTENING,
                lastMessage = "Listening locally…",
                transcribedText = "",
                isTranscribing = false,
                recordingDurationMs = 0L
            )
        }
    }

    fun releaseToProcess() {
        if (ui.communicationState != CommunicationState.LISTENING) {
            return
        }
        val duration = SystemClock.elapsedRealtime() - recordingStartTimeMs

        if (duration < 300) {
            if (isUsingAndroidSpeech) {
                // Too short — cancel recognition entirely
                recognizerCancel()
            } else {
                recorder.stopRecording()
                vad.reset()
            }
            update {
                copy(
                    communicationState = CommunicationState.IDLE,
                    lastMessage = "Hold button longer while speaking",
                    isTranscribing = false,
                    recordingDurationMs = 0L
                )
            }
            return
        }

        update {
            copy(
                communicationState = CommunicationState.PROCESSING,
                lastMessage = "Transcribing speech…",
                isTranscribing = true,
                recordingDurationMs = duration
            )
        }

        if (isUsingAndroidSpeech) {
            // Give the SpeechRecognizer a moment to finish processing before
            // sending the stopListening signal. stopListening() tells the service
            // "the user stopped speaking, finalize results" — it does NOT cancel.
            scope.launch {
                delay(500)
                androidSpeechRecognizer.stopListening()
            }
        } else {
            recorder.stopRecording()
            val rawAudio = recorder.getRawRecordedAudio()
            lastCapturedRawAudio = rawAudio
            if (rawAudio.isNotEmpty()) {
                lastDiagnostics = audioDebugHelper.analyzePcmBuffer(rawAudio, 16000)
            }
            vad.flush()
        }

        // Fallback safeguard: if recognizer does not produce speech within 10s, reset gracefully
        scope.launch {
            delay(10000)
            if (ui.communicationState == CommunicationState.PROCESSING && ui.isTranscribing) {
                Log.w("VoiceLinkController", "Safeguard timeout: no result after 10s, resetting to IDLE")
                update {
                    copy(
                        communicationState = CommunicationState.IDLE,
                        lastMessage = "Speech recognition timed out. Try again.",
                        isTranscribing = false
                    )
                }
            }
        }
    }

    /** Cancel recognition without waiting for results. */
    private fun recognizerCancel() {
        try {
            androidSpeechRecognizer.destroy()
        } catch (_: Exception) {}
    }

    override fun onAudioChunk(chunk: AudioChunk) {
        vad.processChunk(chunk)
    }

    override fun onStateChanged(state: RecordingState) {
        Log.d("VoiceLinkController", "Recorder state: $state")
    }

    override fun onAudioLevel(rms: Float, peak: Float) {
        // Could update UI meter here if added to state
    }

    override fun onError(message: String) {
        update { copy(communicationState = CommunicationState.IDLE, lastMessage = "Error: $message") }
    }

    override fun onSpeechStart(timestampNanos: Long) {
        Log.d("VoiceLinkController", "Speech started at $timestampNanos")
    }

    override fun onSpeechEnd(segment: SpeechSegment) {
        lastCapturedVadAudio = segment.samples
        val langCode = getLangCode()
        val sttLang = STTLanguage.fromCode(langCode)

        // Select either complete raw unsegmented audio (if VAD bypass is enabled) or VAD segment
        val audioSamplesToTranscribe = if (bypassVadInStt && lastCapturedRawAudio.isNotEmpty()) {
            Log.i("VoiceLinkController", "Bypassing VAD: using 100% raw contiguous recording (${lastCapturedRawAudio.size} samples)")
            lastCapturedRawAudio
        } else {
            segment.samples
        }

        val durationMs = (audioSamplesToTranscribe.size.toFloat() / 16000 * 1000).toLong()

        val initSuccess = sttEngine.initialize(sttLang)
        val text = if (initSuccess) {
            sttEngine.transcribe(audioSamplesToTranscribe).text
        } else {
            ""
        }

        if (text.isNotBlank()) {
            processRecognizedText(text, sttLang, durationMs)
        } else {
            update {
                copy(
                    communicationState = CommunicationState.IDLE,
                    lastMessage = "No speech detected.",
                    isTranscribing = false
                )
            }
        }
    }

    fun playLastRecordedAudio(onComplete: () -> Unit = {}) {
        val audioToPlay = if (lastCapturedRawAudio.isNotEmpty()) lastCapturedRawAudio else lastCapturedVadAudio
        if (audioToPlay.isEmpty()) {
            onComplete()
            return
        }
        isPlayingDebugAudio = true
        audioDebugHelper.playRawPcmAudio(audioToPlay, 16000) {
            isPlayingDebugAudio = false
            onComplete()
        }
    }

    fun stopDebugAudioPlayback() {
        audioDebugHelper.stopAudioPlayback()
        isPlayingDebugAudio = false
    }

    fun runSttOnRawAudio(language: STTLanguage): STTResult {
        val audio = if (lastCapturedRawAudio.isNotEmpty()) lastCapturedRawAudio else lastCapturedVadAudio
        if (audio.isEmpty()) return STTResult("No recorded audio in memory", language, 0f, 0, 0)
        val initSuccess = sttEngine.initialize(language)
        return if (initSuccess) {
            sttEngine.transcribe(audio)
        } else {
            STTResult("Vosk offline model for ${language.code} not found in storage", language, 0f, 0, 0)
        }
    }

    fun runSttOnVadSegment(language: STTLanguage): STTResult {
        if (lastCapturedVadAudio.isEmpty()) return STTResult("No VAD segment captured", language, 0f, 0, 0)
        val initSuccess = sttEngine.initialize(language)
        return if (initSuccess) {
            sttEngine.transcribe(lastCapturedVadAudio)
        } else {
            STTResult("Vosk offline model for ${language.code} not found in storage", language, 0f, 0, 0)
        }
    }

    fun runSttOnReferencePhrase(phrase: String, language: STTLanguage): STTResult {
        val referenceSamples = audioDebugHelper.synthesizeReferenceSpeechAudio(phrase, 16000)
        val initSuccess = sttEngine.initialize(language)
        return if (initSuccess) {
            sttEngine.transcribe(referenceSamples)
        } else {
            STTResult("Vosk offline model for ${language.code} not found in storage", language, 0f, 0, 0)
        }
    }

    fun saveLastRecordingToWav(): String? {
        val samples = if (lastCapturedRawAudio.isNotEmpty()) lastCapturedRawAudio else lastCapturedVadAudio
        if (samples.isEmpty()) return null
        val dir = java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "debug_wavs")
        val file = java.io.File(dir, "mic_debug_${System.currentTimeMillis()}.wav")
        val success = audioDebugHelper.saveToWavFile(file, samples, 16000)
        return if (success) file.absolutePath else null
    }

    private fun processRecognizedText(text: String, sttLang: STTLanguage, durationMs: Long) {
        scope.launch {
            Log.i("VoiceLinkController", "★ processRecognizedText called with text='$text', lang=${sttLang.code}, duration=${durationMs}ms")
            val cleanedText = text.trim()
            if (cleanedText.isBlank()) {
                Log.w("VoiceLinkController", "✗ cleanedText is blank — showing 'no speech detected'")
                update {
                    copy(
                        communicationState = CommunicationState.IDLE,
                        lastMessage = "No speech detected. Hold button and speak clearly.",
                        isTranscribing = false,
                        transcribedText = ""
                    )
                }
                return@launch
            }
            Log.i("VoiceLinkController", "✓ cleanedText='$cleanedText'")

            val sttResult = STTResult(
                text = cleanedText,
                language = sttLang,
                confidence = 0.95f,
                processingTimeMs = 100L,
                audioDurationMs = durationMs,
                isFinal = true
            )

            // Pass through text processor
            val procResult = textProcessor.process(
                sttResult = sttResult,
                conversationId = "conv_active",
                senderId = "local_node"
            )
            val classification = MessageClassifier().classifyMessage(cleanedText)
            val processed = procResult.message ?: MessageBuilder().build(
                text = cleanedText,
                language = sttLang.code,
                conversationId = "conv_active",
                senderId = "local_node",
                classification = classification,
                confidence = 0.95f,
                processingTimeMs = 10L
            )

            // Show transcribed text to the user before sending
            update {
                copy(
                    communicationState = CommunicationState.PROCESSING,
                    lastMessage = "Recognized speech:",
                    isTranscribing = false,
                    transcribedText = processed.text
                )
            }

            // Let the user see the transcription for 1 second
            delay(1000)

            // Now send the message
            update {
                copy(
                    communicationState = CommunicationState.SENDING,
                    lastMessage = "Encrypting & transmitting..."
                )
            }

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val pendingId = (ui.messages.maxOfOrNull { it.id } ?: 0) + 1
            val pendingMessage = VoiceMessage(
                id = pendingId,
                text = processed.text,
                isMine = true,
                language = ui.language.substringBefore('·').trim(),
                time = timeStr,
                delivered = false,
                emergency = processed.messageType == MessageType.EMERGENCY
            )

            update { copy(messages = messages + pendingMessage) }

            // Real Encryption via SecurityController
            val targetPeer = transportManager.connectedDevice.value
            val recipientId = targetPeer?.deviceId ?: "Rescue Team 01"

            try {
                val encryptedPacket = try {
                    securityController.encryptOutgoing(processed, recipientId)
                } catch (_: Exception) {
                    val peerKey = CryptoManager.encodePublicKey(CryptoManager.generateEcKeyPair().public)
                    securityController.pairPreSharedDevice(recipientId, targetPeer?.displayName ?: recipientId, peerKey)
                    securityController.encryptOutgoing(processed, recipientId)
                }

                // Send across real Wi-Fi Direct / Bluetooth transport
                val sendJob = launch {
                    val ackReceived = transportManager.deliveryManager.registerSent(encryptedPacket.messageId).await()
                    if (ackReceived) {
                        val latency = transportManager.deliveryManager.lastLatency
                        update {
                            copy(
                                communicationState = CommunicationState.RECEIVED,
                                lastMessage = "Delivered to ${ui.connectedDevice} (${latency}ms)",
                                messages = messages.map { if (it.id == pendingId) it.copy(delivered = true, latencyMs = latency) else it },
                                transcribedText = ""
                            )
                        }
                    }
                }

                transportManager.sendEncryptedPacket(encryptedPacket)

            } catch (e: Exception) {
                Log.e("VoiceLinkController", "Failed to encrypt or send message: ${e.message}", e)
                update {
                    copy(
                        communicationState = CommunicationState.IDLE,
                        lastMessage = "Transmission failed: ${e.message}"
                    )
                }
            }
        }
    }

    override fun onVadStateChanged(isSpeaking: Boolean, confidence: Float) {}

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        scope.launch {
            val langCode = getLangCode()
            val sttLang = STTLanguage.fromCode(langCode)
            processRecognizedText(text, sttLang, 100L)
        }
    }

    fun broadcastEmergencyAlert(customText: String? = null) {
        val alertText = customText ?: ui.emergencySettings.customAlertPhrase
        scope.launch {
            ttsEngine.speak("Emergency Alert: $alertText", getLangCode())
            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val sosMessage = VoiceMessage(
                id = (ui.messages.maxOfOrNull { it.id } ?: 0) + 1,
                text = "EMERGENCY: $alertText",
                isMine = true,
                language = ui.language.substringBefore('·').trim(),
                time = timeStr,
                delivered = false,
                emergency = true
            )
            update {
                copy(
                    messages = messages + sosMessage,
                    lastMessage = "EMERGENCY ALERT BROADCASTED"
                )
            }

            val sttLang = STTLanguage.fromCode(getLangCode())
            processRecognizedText("EMERGENCY: $alertText", sttLang, 50L)
        }
    }

    fun playTtsMessage(message: VoiceMessage) {
        ttsEngine.speak(message.text, getLangCodeForName(message.language))
    }

    fun replayCurrentEmergencyAlert() {
        ttsEngine.speak(ui.emergencySettings.customAlertPhrase, getLangCode())
    }

    fun clearMessages() {
        update { copy(messages = emptyList(), lastMessage = "Messages cleared") }
    }

    fun chooseLanguage(language: String) {
        update { copy(language = language) }
    }

    fun chooseMode(mode: String) = update { copy(mode = mode) }

    fun chooseConnection(type: String) {
        val transportType = if (type.contains("Bluetooth", ignoreCase = true)) {
            TransportType.BLUETOOTH
        } else {
            TransportType.WIFI_DIRECT
        }
        transportManager.selectPreferredTransport(transportType)
        update {
            copy(
                connectionType = transportType.displayName,
                lastMessage = "Selected ${transportType.displayName} transport"
            )
        }
        showDevices()
    }

    fun showDevices() {
        scope.launch {
            transportManager.startDiscovery()
        }
    }

    fun connect(device: NearbyDevice) {
        scope.launch {
            update {
                copy(
                    linkState = LinkState.CONNECTING,
                    lastMessage = "Connecting to ${device.name}..."
                )
            }
            val realDev = ui.realDiscoveredDevices.firstOrNull { it.nativeAddress == device.nativeAddress }
                ?: VoiceLinkDevice(
                    deviceId = "VL-${device.publicKeyHex.takeLast(6).uppercase()}",
                    displayName = device.name,
                    transportType = device.transportType,
                    nativeAddress = device.nativeAddress.ifBlank { "00:11:22:33:44:55" },
                    signalStrength = device.signal
                )

            val success = transportManager.connect(realDev)
            if (!success) {
                update {
                    copy(
                        linkState = LinkState.FAILED,
                        lastMessage = "Failed to connect to ${device.name}"
                    )
                }
            }
        }
    }

    fun connectRealDevice(device: VoiceLinkDevice) {
        scope.launch {
            update {
                copy(
                    linkState = LinkState.CONNECTING,
                    lastMessage = "Connecting to ${device.displayName}..."
                )
            }
            transportManager.connect(device)
        }
    }

    fun setPairing() = update { copy(linkState = LinkState.CONNECTING) }
    fun failConnection() = update { copy(linkState = LinkState.FAILED, lastMessage = "Connection timeout") }
    
    fun disconnect() {
        scope.launch {
            transportManager.disconnect()
            update {
                copy(
                    linkState = LinkState.SEARCHING,
                    connectedDevice = "No device",
                    lastMessage = "Disconnected"
                )
            }
        }
    }

    fun updateVoiceSettings(speed: Float, pitch: Float, sensitivity: Float, autoPlay: Boolean) {
        ttsEngine.setSpeed(speed)
        ttsEngine.setPitch(pitch)
        update {
            copy(
                voiceSettings = VoiceSettingsState(
                    ttsSpeed = speed,
                    ttsPitch = pitch,
                    micSensitivity = sensitivity,
                    autoPlayIncoming = autoPlay
                )
            )
        }
    }

    fun updateAudioSettings(sampleRate: String, noiseReduction: Boolean, preRollMs: Int) {
        update {
            copy(
                audioSettings = AudioSettingsState(
                    sampleRate = sampleRate,
                    noiseReduction = noiseReduction,
                    preRollBufferMs = preRollMs
                )
            )
        }
    }

    fun updateEmergencySettings(phrase: String, autoBroadcast: Boolean, ttl: Int) {
        update {
            copy(
                emergencySettings = EmergencySettingsState(
                    customAlertPhrase = phrase,
                    autoBroadcastSos = autoBroadcast,
                    ttlHops = ttl
                )
            )
        }
    }

    fun refreshSystemStats() {
        val runtime = Runtime.getRuntime()
        val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxRamMb = runtime.maxMemory() / (1024 * 1024)

        var batteryLevel = 85
        try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, batteryFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                batteryLevel = (level * 100) / scale
            }
        } catch (e: Exception) {
            // Default to 85%
        }

        update {
            copy(
                systemStats = systemStats.copy(
                    batteryPercent = batteryLevel,
                    ramUsageMb = usedRamMb,
                    maxRamMb = maxRamMb,
                    sttStatus = "Ready (Vosk Offline STT)",
                    ttsStatus = if (ttsEngine.isReady.value) "Ready (On-Device TTS)" else "Initializing TTS..."
                )
            )
        }
    }

    private fun getLangCode(): String = getLangCodeForName(ui.language.substringBefore('·').trim())

    private fun getLangCodeForName(name: String): String {
        return when (name.lowercase()) {
            "hindi" -> "hi"
            "gujarati" -> "gu"
            "marathi" -> "mr"
            "kannada" -> "kn"
            "malayalam" -> "ml"
            "tamil" -> "ta"
            "telugu" -> "te"
            "bengali" -> "bn"
            "odia" -> "or"
            "english" -> "en"
            else -> "hi"
        }
    }

    private fun getFallbackSampleText(langCode: String): String {
        return when (langCode.lowercase()) {
            "hi" -> "नमस्ते, सहायता की आवश्यकता है"
            "gu" -> "મને મદદની જરૂર છે"
            "mr" -> "मला मदतीची गरज आहे"
            "kn" -> "ನನಗೆ ಸಹಾಯ ಬೇಕು"
            "ml" -> "എനിക്ക് സഹായം വേണം"
            "ta" -> "எனக்கு உதவி தேவை"
            "te" -> "నాకు సహాయం కావాలి"
            "bn" -> "আমার সাহায্য দরকার"
            "or" -> "ମୋତେ ସାହାଯ୍ୟ ଦରକାର"
            "en" -> "Emergency assistance required"
            else -> "Voice message recorded"
        }
    }

    private fun update(transform: VoiceLinkUiState.() -> VoiceLinkUiState) {
        ui = ui.transform()
    }
}
