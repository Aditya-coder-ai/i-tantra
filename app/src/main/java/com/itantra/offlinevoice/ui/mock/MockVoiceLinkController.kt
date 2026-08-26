package com.itantra.offlinevoice.ui.mock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Preview-only state. Replace this controller with real feature adapters in later milestones. */
enum class CommunicationState { IDLE, LISTENING, PROCESSING, SENDING, RECEIVED }
enum class LinkState { SEARCHING, DEVICE_FOUND, CONNECTING, CONNECTED, FAILED }

data class VoiceMessage(
    val id: Int,
    val text: String,
    val isMine: Boolean,
    val language: String,
    val time: String,
    val delivered: Boolean = true,
    val emergency: Boolean = false
)

data class NearbyDevice(val name: String, val detail: String, val signal: Int, val paired: Boolean = false)

data class VoiceLinkUiState(
    val language: String = "Hindi · हिन्दी",
    val communicationState: CommunicationState = CommunicationState.IDLE,
    val linkState: LinkState = LinkState.CONNECTED,
    val connectionType: String = "Wi‑Fi Direct",
    val connectedDevice: String = "Aarav’s VoiceLink",
    val mode: String = "Push-to-Talk",
    val lastMessage: String = "Ready to communicate",
    val messages: List<VoiceMessage> = demoMessages,
    val devices: List<NearbyDevice> = demoDevices
)

private val demoMessages = listOf(
    VoiceMessage(1, "मुझे मदद चाहिए", true, "Hindi", "10:42 PM"),
    VoiceMessage(2, "मैं आपकी मदद कर रहा हूँ", false, "Hindi", "10:42 PM"),
    VoiceMessage(3, "Meeting point shared. Please stay safe.", false, "English", "10:44 PM")
)

private val demoDevices = listOf(
    NearbyDevice("Aarav’s VoiceLink", "Wi‑Fi Direct · 92% signal", 4, true),
    NearbyDevice("Rescue Team 04", "Bluetooth · 74% signal", 3),
    NearbyDevice("Field Unit 12", "Wi‑Fi Direct · 58% signal", 2)
)

class MockVoiceLinkController {
    var ui by mutableStateOf(VoiceLinkUiState())
        private set

    fun startTalking() = update { copy(communicationState = CommunicationState.LISTENING, lastMessage = "Listening locally…") }
    fun releaseToProcess() = update { copy(communicationState = CommunicationState.PROCESSING, lastMessage = "Converting speech…") }
    fun setSending() = update { copy(communicationState = CommunicationState.SENDING, lastMessage = "Sending lightweight text…") }
    fun markDelivered() = update {
        copy(
            communicationState = CommunicationState.RECEIVED,
            lastMessage = "Delivered to ${connectedDevice}",
            messages = messages + VoiceMessage(messages.size + 1, "स्थिति सुरक्षित है", true, "Hindi", "Now")
        )
    }
    fun resetTalk() = update { copy(communicationState = CommunicationState.IDLE) }
    fun chooseLanguage(language: String) = update { copy(language = language) }
    fun chooseMode(mode: String) = update { copy(mode = mode) }
    fun chooseConnection(type: String) = update { copy(connectionType = type, linkState = LinkState.SEARCHING) }
    fun showDevices() = update { copy(linkState = LinkState.DEVICE_FOUND) }
    fun connect(device: NearbyDevice) = update { copy(linkState = LinkState.CONNECTED, connectedDevice = device.name) }
    fun setPairing() = update { copy(linkState = LinkState.CONNECTING) }
    fun failConnection() = update { copy(linkState = LinkState.FAILED) }
    fun disconnect() = update { copy(linkState = LinkState.SEARCHING, connectedDevice = "No device") }

    private fun update(transform: VoiceLinkUiState.() -> VoiceLinkUiState) {
        ui = ui.transform()
    }
}
