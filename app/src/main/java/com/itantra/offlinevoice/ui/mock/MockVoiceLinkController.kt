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
enum class LinkState { SEARCHING, DEVICE_FOUND, CONNECTING, CONNECTED, FAILED }

data class VoiceMessage(
    val id: Int,
    val text: String,
    val isMine: Boolean,
    val language: String,
    val time: String,
    val delivered: Boolean = true,
    val emergency: Boolean = false,
    val hopCount: Int = 0
)

data class NearbyDevice(
    val name: String,
    val detail: String,
    val signal: Int,
    val paired: Boolean = false,
    val publicKeyHex: String = "4a8e29bf10c7"
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
    val linkState: LinkState = LinkState.CONNECTED,
    val connectionType: String = "Wi‑Fi Direct",
    val connectedDevice: String = "Rescue Team 01",
    val mode: String = "Push-to-Talk",
    val lastMessage: String = "Ready to communicate",
    val messages: List<VoiceMessage> = defaultMessages,
    val devices: List<NearbyDevice> = defaultDevices,
    val voiceSettings: VoiceSettingsState = VoiceSettingsState(),
    val audioSettings: AudioSettingsState = AudioSettingsState(),
    val emergencySettings: EmergencySettingsState = EmergencySettingsState(),
    val systemStats: SystemHardwareStats = SystemHardwareStats(),
    val transcribedText: String = "",
    val isTranscribing: Boolean = false,
    val recordingDurationMs: Long = 0L
)

private val defaultMessages = listOf(
    VoiceMessage(1, "मुझे सहायता की आवश्यकता है", true, "Hindi", "10:42 PM", true, false, 0),
    VoiceMessage(2, "बचाव दल आपकी ओर बढ़ रहा है", false, "Hindi", "10:43 PM", true, false, 1),
    VoiceMessage(3, "Safe shelter point confirmed. All clear.", false, "English", "10:45 PM", true, false, 2)
)

private val defaultDevices = listOf(
    NearbyDevice("Rescue Team 01", "Wi‑Fi Direct · 95% signal", 4, true, "a1b2c3d4e5f6"),
    NearbyDevice("Medical Unit 04", "Bluetooth · 78% signal", 3, false, "f6e5d4c3b2a1"),
    NearbyDevice("Field Relay Node 12", "Mesh Relay · 62% signal", 2, true, "1029384756af")
)

class MockVoiceLinkController(private val context: Context) : AudioRecorder.Listener, VoiceActivityDetector.Listener {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // Core Engine Instances
    val ttsEngine: TtsEngine = TtsEngine(context)
    private val sttEngine: VoskSttEngine = VoskSttEngine(context)
    private val textProcessor: TextProcessor = TextProcessor()
    val communicationManager: ITantraCommunicationManager = TantraCommunicationManagerImpl()
    
    val securityController: SecurityController = SecurityController()
    
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
            communicationManager.start()
            securityController.initializeIdentity("i-tantra Local Device")
            try {
                val demoKey1 = CryptoManager.encodePublicKey(CryptoManager.generateEcKeyPair().public)
                val demoKey2 = CryptoManager.encodePublicKey(CryptoManager.generateEcKeyPair().public)
                securityController.pairPreSharedDevice("Rescue Team 01", "Rescue Team 01", demoKey1)
                securityController.pairPreSharedDevice("Medical Unit 04", "Medical Unit 04", demoKey2)
            } catch (e: Exception) {
                // Identity already prepared
            }
            refreshSystemStats()
        }

        // Listen for decrypted incoming messages from the mesh/radios
        communicationManager.incomingMessages
            .onEach { msg ->
                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.timestampMs))
                val newMessage = VoiceMessage(
                    id = (ui.messages.maxOfOrNull { it.id } ?: 0) + 1,
                    text = msg.text,
                    isMine = false,
                    language = msg.languageCode,
                    time = timeStr,
                    delivered = true,
                    emergency = msg.messageType == MessageType.EMERGENCY,
                    hopCount = msg.hopCount
                )
                update {
                    copy(
                        messages = messages + newMessage,
                        lastMessage = "Received: ${msg.text}"
                    )
                }
                if (ui.voiceSettings.autoPlayIncoming) {
                    ttsEngine.speak(msg.text, msg.languageCode)
                }
            }
            .launchIn(scope)

        // Sync communication engine state
        communicationManager.engineState
            .onEach { engine ->
                update {
                    copy(
                        systemStats = systemStats.copy(
                            packetsRelayed = engine.totalPacketsRelayed,
                            messagesDelivered = engine.totalMessagesDelivered
                        )
                    )
                }
            }
            .launchIn(scope)
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

            // Let the user see the transcription for 1.5 seconds
            delay(1500)

            // Now send the message
            update {
                copy(
                    communicationState = CommunicationState.SENDING,
                    lastMessage = "Transmitting: ${processed.text}"
                )
            }

            val dummyRecipient = PeerIdentity(
                alias = ui.connectedDevice,
                publicKey = ByteArray(32) { 0x42.toByte() },
                isVerified = true
            )

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val pendingId = (ui.messages.maxOfOrNull { it.id } ?: 0) + 1
            val pendingMessage = VoiceMessage(
                id = pendingId,
                text = processed.text,
                isMine = true,
                language = ui.language.substringBefore('·').trim(),
                time = timeStr,
                delivered = false,
                emergency = false
            )

            update { copy(messages = messages + pendingMessage) }

            communicationManager.sendProcessedMessage(dummyRecipient, processed)
                .collect { state ->
                    when (state) {
                        is DeliveryState.Queued -> update { copy(lastMessage = "Queued...") }
                        is DeliveryState.Transmitting -> update { copy(lastMessage = "Transmitting via ${state.tier}...") }
                        is DeliveryState.Delivered -> {
                            update {
                                copy(
                                    communicationState = CommunicationState.RECEIVED,
                                    lastMessage = "Delivered to ${ui.connectedDevice}",
                                    messages = messages.map { if (it.id == pendingId) it.copy(delivered = true) else it },
                                    transcribedText = ""
                                )
                            }
                        }
                        is DeliveryState.Relayed -> {
                            update {
                                copy(
                                    communicationState = CommunicationState.RECEIVED,
                                    lastMessage = "Relayed via ${state.hopCount} hop(s)",
                                    messages = messages.map { if (it.id == pendingId) it.copy(delivered = true, hopCount = state.hopCount) else it },
                                    transcribedText = ""
                                )
                            }
                        }
                        is DeliveryState.Failed -> {
                            update {
                                copy(
                                    communicationState = CommunicationState.IDLE,
                                    lastMessage = "Failed: ${state.reason}",
                                    transcribedText = ""
                                )
                            }
                        }
                    }
                }
        }
    }

    override fun onVadStateChanged(isSpeaking: Boolean, confidence: Float) {
        // Can be used to show visual indicator
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        scope.launch {
            val langCode = getLangCode()
            val sttLang = STTLanguage.fromCode(langCode)
            val sttResult = STTResult(
                text = text,
                language = sttLang,
                confidence = 1.0f,
                processingTimeMs = 10L,
                audioDurationMs = 1000L,
                isFinal = true
            )

            val procResult = textProcessor.process(
                sttResult = sttResult,
                conversationId = "conv_active",
                senderId = "local_node"
            )
            val processed = procResult.message ?: return@launch

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            val pendingId = (ui.messages.maxOfOrNull { it.id } ?: 0) + 1
            val pendingMessage = VoiceMessage(
                id = pendingId,
                text = processed.text,
                isMine = true,
                language = ui.language.substringBefore('·').trim(),
                time = timeStr,
                delivered = true,
                emergency = false
            )
            update { copy(messages = messages + pendingMessage) }

            val dummyRecipient = PeerIdentity(
                alias = ui.connectedDevice,
                publicKey = ByteArray(32) { 0x42.toByte() }
            )
            communicationManager.sendProcessedMessage(dummyRecipient, processed).launchIn(scope)
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
                delivered = true,
                emergency = true
            )
            update {
                copy(
                    messages = messages + sosMessage,
                    lastMessage = "EMERGENCY ALERT BROADCASTED"
                )
            }
            communicationManager.broadcastEmergency(alertText, getLangCode())
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
        update {
            copy(
                connectionType = type,
                linkState = LinkState.SEARCHING,
                lastMessage = "Switched to $type transport"
            )
        }
        scope.launch {
            delay(1200)
            update { copy(linkState = LinkState.DEVICE_FOUND) }
        }
    }

    fun showDevices() {
        update { copy(linkState = LinkState.SEARCHING) }
        scope.launch {
            delay(1000)
            update { copy(linkState = LinkState.DEVICE_FOUND) }
        }
    }

    fun connect(device: NearbyDevice) {
        update {
            copy(
                linkState = LinkState.CONNECTED,
                connectedDevice = device.name,
                lastMessage = "Connected to ${device.name}"
            )
        }
    }

    fun setPairing() = update { copy(linkState = LinkState.CONNECTING) }
    fun failConnection() = update { copy(linkState = LinkState.FAILED, lastMessage = "Connection timeout") }
    
    fun disconnect() {
        update {
            copy(
                linkState = LinkState.SEARCHING,
                connectedDevice = "No device",
                lastMessage = "Disconnected"
            )
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
