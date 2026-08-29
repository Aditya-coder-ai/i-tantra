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
import com.itantra.offlinevoice.translation.OfflineTranslationEngine
import com.itantra.offlinevoice.translation.SupportedLanguage
import com.itantra.offlinevoice.translation.TextLanguageDetector
import com.itantra.offlinevoice.translation.TranslationEngine
import com.itantra.offlinevoice.translation.TranslationPath
import com.itantra.offlinevoice.translation.TranslationQueue
import com.itantra.offlinevoice.translation.TranslationResult
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
    val latencyMs: Long = 0L,
    val originalText: String = text,
    val originalLanguage: String = language,
    val translatedText: String? = null,
    val targetLanguage: String? = null,
    val isTranslated: Boolean = false,
    val translationPath: String? = null,
    val translationLatencyMs: Long = 0L
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
    val autoPlayIncoming: Boolean = true,
    val mySpeakingLanguage: String = "en",
    val myListeningLanguage: String = "hi",
    val autoTranslateEnabled: Boolean = true
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
    val translationEngine: OfflineTranslationEngine = OfflineTranslationEngine(context)
    val translationQueue: TranslationQueue = TranslationQueue(translationEngine)
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

        // Surface Wi-Fi Direct requirements and framework failures in the app
        // instead of leaving the user on an indefinite "Searching" state.
        scope.launch {
            transportManager.wifiDirectTransport.lastError.collect { error ->
                if (!error.isNullOrBlank()) {
                    update {
                        copy(
                            linkState = LinkState.FAILED,
                            lastMessage = error
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
                    Log.i("VoiceLinkController", "★ Incoming Encrypted Message from network: ${encryptedPacket.messageId} from ${encryptedPacket.senderId}")
                    val decryptedMessage = try {
                        securityController.decryptIncoming(encryptedPacket)
                    } catch (e: Exception) {
                        Log.w("VoiceLinkController", "Primary decrypt failed (${e.message}), attempting direct channel decrypt...")
                        val defaultKey = CryptoManager.deriveDefaultSessionKey()
                        val nonceBytes = java.util.Base64.getDecoder().decode(encryptedPacket.nonce)
                        val cipherBytes = encryptedPacket.getCiphertextWithTagBytes()
                        val aadBytes = encryptedPacket.computeAadBytes()
                        val rawBytes = CryptoManager.decryptAesGcm(cipherBytes, defaultKey, nonceBytes, aadBytes)
                        EncryptedMessagePacket.deserializeProcessedMessage(rawBytes)
                    }

                    val sourceLang = SupportedLanguage.fromCode(decryptedMessage.language)
                    val listeningLang = SupportedLanguage.fromCode(ui.voiceSettings.myListeningLanguage)

                    // Execute Offline Translation if Auto-Translate is enabled and source != target
                    val translationResult = if (ui.voiceSettings.autoTranslateEnabled && sourceLang != listeningLang) {
                        update {
                            copy(
                                communicationState = CommunicationState.PROCESSING,
                                lastMessage = "Translating ${sourceLang.displayName} → ${listeningLang.displayName}..."
                            )
                        }
                        translationQueue.enqueueAndWait(decryptedMessage, listeningLang)
                    } else {
                        TranslationResult.sameLanguagePassthrough(decryptedMessage.text, sourceLang)
                    }

                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    val finalDisplayedText = if (translationResult.isTranslationRequired) translationResult.translatedText else decryptedMessage.text
                    val finalLanguageCode = if (translationResult.isTranslationRequired) translationResult.targetLanguage.code else decryptedMessage.language

                    val newMessage = VoiceMessage(
                        id = (ui.messages.maxOfOrNull { it.id } ?: 0) + 1,
                        text = finalDisplayedText,
                        isMine = false,
                        language = finalLanguageCode,
                        time = timeStr,
                        delivered = true,
                        emergency = decryptedMessage.messageType == MessageType.EMERGENCY,
                        originalText = decryptedMessage.text,
                        originalLanguage = sourceLang.displayName,
                        translatedText = if (translationResult.isTranslationRequired) translationResult.translatedText else null,
                        targetLanguage = if (translationResult.isTranslationRequired) translationResult.targetLanguage.displayName else null,
                        isTranslated = translationResult.isTranslationRequired,
                        translationPath = translationResult.translationPath.displayName,
                        translationLatencyMs = translationResult.translationTimeMs
                    )

                    update {
                        copy(
                            messages = messages + newMessage,
                            lastMessage = if (newMessage.isTranslated) "Translated to ${listeningLang.displayName}: ${newMessage.text}" else "Received: ${newMessage.text}",
                            communicationState = CommunicationState.RECEIVED
                        )
                    }

                    // Automatic Offline TTS Playback in Receiver's Preferred Language
                    if (ui.voiceSettings.autoPlayIncoming) {
                        Log.i("VoiceLinkController", "🔊 Auto-playing incoming voice message in $finalLanguageCode: '$finalDisplayedText'")
                        ttsEngine.speak(finalDisplayedText, finalLanguageCode)
                    }
                } catch (e: Exception) {
                    Log.e("VoiceLinkController", "Failed to process incoming packet: ${e.message}", e)
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
            // Signal the SpeechRecognizer that the user released the button and speech is complete
            androidSpeechRecognizer.stopListening()
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

            // Local Translation for Pocket Translator mode
            // The configured speaking language guides STT, but it can be stale when
            // someone types a message or switches languages mid-conversation.  Do
            // not let clearly different-script text be tagged with a stale source
            // language and bypass translation.
            val configuredSourceLang = SupportedLanguage.fromCode(sttLang.code)
            val sourceLang = TextLanguageDetector.detectUnambiguousLanguage(processed.text)
                ?: configuredSourceLang
            val targetLang = SupportedLanguage.fromCode(ui.voiceSettings.myListeningLanguage)

            val translationResult = if (ui.voiceSettings.autoTranslateEnabled && sourceLang != targetLang) {
                translationEngine.translate(processed.text, sourceLang, targetLang)
            } else {
                TranslationResult.sameLanguagePassthrough(processed.text, sourceLang)
            }

            val finalDisplayedText = if (translationResult.isTranslationRequired) translationResult.translatedText else processed.text
            val finalLanguageCode = if (translationResult.isTranslationRequired) translationResult.targetLanguage.code else sttLang.code

            // Let the user see the transcription for 1 second
            delay(1000)

            // Play the translated text locally so it acts like a pocket translator
            if (translationResult.isTranslationRequired && ui.voiceSettings.autoPlayIncoming) {
                ttsEngine.speak(finalDisplayedText, finalLanguageCode)
            }

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
                text = finalDisplayedText,
                isMine = true,
                language = finalLanguageCode,
                time = timeStr,
                delivered = false,
                emergency = processed.messageType == MessageType.EMERGENCY,
                originalText = processed.text,
                originalLanguage = sourceLang.displayName,
                translatedText = if (translationResult.isTranslationRequired) translationResult.translatedText else null,
                targetLanguage = if (translationResult.isTranslationRequired) translationResult.targetLanguage.displayName else null,
                isTranslated = translationResult.isTranslationRequired,
                translationPath = translationResult.translationPath.displayName,
                translationLatencyMs = translationResult.translationTimeMs
            )

            update { copy(messages = messages + pendingMessage) }

            // Real Encryption via SecurityController (sends the original processed message)
            val targetPeer = transportManager.connectedDevice.value
            val recipientId = targetPeer?.deviceId ?: "VL-PEER"
            try {
                val encryptedPacket = securityController.encryptOutgoing(processed, recipientId)

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

    fun simulateIncomingMessage(
        text: String? = null,
        languageCode: String? = null
    ) {
        scope.launch {
            val listeningLang = SupportedLanguage.fromCode(ui.voiceSettings.myListeningLanguage)
            val isListeningHindi = listeningLang.code == "hi"
            val actualLangCode = languageCode ?: if (isListeningHindi) "en" else "hi"
            val actualText = text ?: if (isListeningHindi) "I need help. There is a fire." else "मुझे मदद चाहिए। वहाँ आग लगी है।"
            val sourceLang = SupportedLanguage.fromCode(actualLangCode)

            val translationResult = if (ui.voiceSettings.autoTranslateEnabled && sourceLang != listeningLang) {
                translationEngine.translate(actualText, sourceLang, listeningLang)
            } else {
                TranslationResult.sameLanguagePassthrough(actualText, sourceLang)
            }

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val finalDisplayedText = if (translationResult.isTranslationRequired) translationResult.translatedText else actualText
            val finalLanguageCode = if (translationResult.isTranslationRequired) translationResult.targetLanguage.code else actualLangCode

            val newMessage = VoiceMessage(
                id = (ui.messages.maxOfOrNull { it.id } ?: 0) + 1,
                text = finalDisplayedText,
                isMine = false,
                language = finalLanguageCode,
                time = timeStr,
                delivered = true,
                emergency = actualText.contains("आग") || actualText.contains("fire") || actualText.contains("help") || actualText.contains("मदद"),
                originalText = actualText,
                originalLanguage = sourceLang.displayName,
                translatedText = if (translationResult.isTranslationRequired) translationResult.translatedText else null,
                targetLanguage = if (translationResult.isTranslationRequired) translationResult.targetLanguage.displayName else null,
                isTranslated = translationResult.isTranslationRequired,
                translationPath = translationResult.translationPath.displayName,
                translationLatencyMs = translationResult.translationTimeMs
            )

            update {
                copy(
                    messages = messages + newMessage,
                    lastMessage = if (newMessage.isTranslated) "Translated to ${listeningLang.displayName}: ${newMessage.text}" else "Received: ${newMessage.text}",
                    communicationState = CommunicationState.RECEIVED
                )
            }

            if (ui.voiceSettings.autoPlayIncoming) {
                ttsEngine.speak(finalDisplayedText, finalLanguageCode)
            }
        }
    }

    fun chooseLanguage(language: String) {
        val lang = SupportedLanguage.fromCode(language)
        update {
            copy(
                language = "${lang.displayName} · ${lang.nativeName}",
                voiceSettings = voiceSettings.copy(mySpeakingLanguage = lang.code),
                lastMessage = "Speaking language set to ${lang.displayName}"
            )
        }
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

    fun connectDirectIp(ip: String) {
        scope.launch {
            val cleanIp = ip.trim()
            if (cleanIp.isBlank()) return@launch
            update {
                copy(
                    linkState = LinkState.CONNECTING,
                    lastMessage = "Connecting directly to $cleanIp:45892..."
                )
            }
            val dev = VoiceLinkDevice(
                deviceId = "VL-${cleanIp.replace(".", "").takeLast(6)}",
                displayName = "VoiceLink Phone ($cleanIp)",
                transportType = TransportType.WIFI_DIRECT,
                nativeAddress = cleanIp,
                signalStrength = 4
            )
            val success = transportManager.connect(dev)
            if (!success) {
                update {
                    copy(
                        linkState = LinkState.FAILED,
                        lastMessage = "Failed to connect to $cleanIp"
                    )
                }
            }
        }
    }

    fun createWifiDirectHostGroup() {
        scope.launch {
            update {
                copy(
                    linkState = LinkState.CONNECTING,
                    lastMessage = "Hosting Wi-Fi Direct Group..."
                )
            }
            val success = transportManager.wifiDirectTransport.createDirectGroup()
            if (!success) {
                update {
                    copy(
                        linkState = LinkState.FAILED,
                        lastMessage = "Failed to create Wi-Fi Direct Group"
                    )
                }
            }
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
                voiceSettings = voiceSettings.copy(
                    ttsSpeed = speed,
                    ttsPitch = pitch,
                    micSensitivity = sensitivity,
                    autoPlayIncoming = autoPlay
                )
            )
        }
    }

    fun setSpeakingLanguage(langCode: String) {
        val lang = SupportedLanguage.fromCode(langCode)
        update {
            copy(
                language = "${lang.displayName} · ${lang.nativeName}",
                voiceSettings = voiceSettings.copy(mySpeakingLanguage = lang.code),
                lastMessage = "Speaking language set to ${lang.displayName}"
            )
        }
    }

    fun setListeningLanguage(langCode: String) {
        val lang = SupportedLanguage.fromCode(langCode)
        update {
            copy(
                voiceSettings = voiceSettings.copy(myListeningLanguage = lang.code),
                lastMessage = "Preferred listening language set to ${lang.displayName}"
            )
        }
    }

    fun toggleAutoTranslate(enabled: Boolean) {
        update {
            copy(
                voiceSettings = voiceSettings.copy(autoTranslateEnabled = enabled),
                lastMessage = if (enabled) "Offline Auto-Translate enabled" else "Offline Auto-Translate disabled"
            )
        }
    }

    fun replayMessageVoice(message: VoiceMessage, useOriginal: Boolean = false) {
        val textToSpeak = if (useOriginal || !message.isTranslated) message.originalText else (message.translatedText ?: message.text)
        val langCode = if (useOriginal || !message.isTranslated) {
            SupportedLanguage.fromCode(message.originalLanguage).code
        } else {
            SupportedLanguage.fromCode(message.targetLanguage ?: message.language).code
        }
        Log.i("VoiceLinkController", "🔊 Replaying voice message in $langCode: '$textToSpeak'")
        if (!ttsEngine.speak(textToSpeak, langCode)) {
            update { copy(lastMessage = ttsEngine.lastError ?: "Unable to play voice message") }
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

    private fun getLangCode(): String {
        return ui.voiceSettings.mySpeakingLanguage.ifBlank {
            SupportedLanguage.fromCode(ui.language).code
        }
    }

    private fun getLangCodeForName(name: String): String {
        return SupportedLanguage.fromCode(name).code
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
