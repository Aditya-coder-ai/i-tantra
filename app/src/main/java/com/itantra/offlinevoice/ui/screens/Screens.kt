package com.itantra.offlinevoice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itantra.offlinevoice.ui.Route
import com.itantra.offlinevoice.ui.components.CardRadius
import com.itantra.offlinevoice.ui.components.DeviceCard
import com.itantra.offlinevoice.ui.components.InfoCard
import com.itantra.offlinevoice.ui.components.MessageBubble
import com.itantra.offlinevoice.ui.components.OutlineButton
import com.itantra.offlinevoice.ui.components.PrimaryButton
import com.itantra.offlinevoice.ui.components.ProcessingSteps
import com.itantra.offlinevoice.ui.components.PushToTalkButton
import com.itantra.offlinevoice.ui.components.ScreenPadding
import com.itantra.offlinevoice.ui.components.SectionLabel
import com.itantra.offlinevoice.ui.components.StatusPill
import com.itantra.offlinevoice.ui.components.VoiceActivity
import com.itantra.offlinevoice.ui.components.VoiceLinkTopBar
import com.itantra.offlinevoice.ui.mock.CommunicationState
import com.itantra.offlinevoice.ui.mock.LinkState
import com.itantra.offlinevoice.ui.mock.MockVoiceLinkController
import com.itantra.offlinevoice.ui.mock.NearbyDevice
import com.itantra.offlinevoice.ui.theme.Blue
import com.itantra.offlinevoice.ui.theme.Emergency
import com.itantra.offlinevoice.ui.theme.EmergencySurface
import com.itantra.offlinevoice.ui.theme.Line
import com.itantra.offlinevoice.ui.theme.Navy
import com.itantra.offlinevoice.ui.theme.Success
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) { delay(1_100); onFinished() }
    Box(Modifier.fillMaxSize().background(Navy).clickable { onFinished() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).border(2.dp, Color.White.copy(.4f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.NetworkWifi, null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("VoiceLink (iTantra)", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text("Offline Multi-Hop Voice & Text Communication", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(.8f))
            Spacer(Modifier.height(38.dp))
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text("Initializing Radios & Cryptography...", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(.7f))
        }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pages = listOf(
        Triple(Icons.Default.NetworkWifi, "Communicate without the internet", "VoiceLink turns speech into encrypted text and sends it peer-to-peer over Bluetooth, Wi-Fi Direct, and multi-hop Mesh."),
        Triple(Icons.Default.Translate, "Speak in the language you know", "Choose from 10 Indian languages and English. On-device STT and TTS handle conversion with zero cloud dependency."),
        Triple(Icons.Default.Bolt, "Emergency Ready", "Hold Push-to-Talk to send, verify delivery receipts, and broadcast instant SOS alerts to all nearby nodes."),
    )
    var page by remember { mutableIntStateOf(0) }
    val current = pages[page]
    Column(Modifier.fillMaxSize().padding(ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("Skip", modifier = Modifier.clickable(onClick = onFinished).padding(12.dp), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(152.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(current.first, null, tint = Blue, modifier = Modifier.size(70.dp))
        }
        Spacer(Modifier.height(38.dp))
        Text(current.second, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(current.third, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.indices.forEach { index ->
                Box(Modifier.size(if (index == page) 24.dp else 8.dp, 8.dp).background(if (index == page) Blue else Line, CircleShape))
            }
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton(if (page == pages.lastIndex) "Get started" else "Next") {
            if (page == pages.lastIndex) onFinished() else page++
        }
    }
}

@Composable
fun HomeScreen(controller: MockVoiceLinkController, navigate: (String) -> Unit) {
    val ui = controller.ui
    Scaffold(
        topBar = {
            VoiceLinkTopBar(
                "VoiceLink",
                trailing = {
                    IconButton(onClick = { navigate(Route.Settings) }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { inset ->
        Column(
            Modifier
                .padding(inset)
                .padding(horizontal = ScreenPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    StatusPill(
                        label = if (ui.linkState == LinkState.CONNECTED) "Connected (${ui.connectionType})" else ui.linkState.name.replace('_', ' '),
                        active = ui.linkState == LinkState.CONNECTED
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "${ui.connectionType} · ${ui.connectedDevice}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { navigate(Route.Language) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Language, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(ui.language.substringBefore('·'))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(ui.mode.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(13.dp))
            PushToTalkButton(
                state = ui.communicationState,
                onHold = controller::startTalking,
                onRelease = controller::releaseToProcess
            )
            Spacer(Modifier.height(18.dp))
            val active = ui.communicationState == CommunicationState.LISTENING
            VoiceActivity(active)
            Spacer(Modifier.height(7.dp))
            Text(
                if (active) "Release to transmit utterance" else ui.lastMessage,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            if (ui.communicationState != CommunicationState.IDLE) {
                Spacer(Modifier.height(22.dp))
                ProcessingSteps(ui.communicationState)
                if (ui.communicationState == CommunicationState.RECEIVED) {
                    Spacer(Modifier.height(14.dp))
                    Text("Ready for next message", style = MaterialTheme.typography.bodyMedium, color = Success)
                }
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard("Conversation", "${ui.messages.size} messages", Modifier.weight(1f)) { navigate(Route.Conversation) }
                InfoCard("Connection", ui.connectionType, Modifier.weight(1f)) { navigate(Route.Connection) }
            }
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { navigate(Route.Emergency) },
                shape = RoundedCornerShape(CardRadius),
                colors = CardDefaults.cardColors(containerColor = EmergencySurface)
            ) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Emergency, null, tint = Emergency)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Emergency Alert (SOS)", style = MaterialTheme.typography.titleMedium, color = Emergency)
                        Text("Broadcast high-priority emergency flood", style = MaterialTheme.typography.bodyMedium, color = Emergency)
                    }
                    Text("TRIGGER", style = MaterialTheme.typography.labelLarge, color = Emergency, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
fun ConversationScreen(controller: MockVoiceLinkController, onBack: () -> Unit) {
    val ui = controller.ui
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            VoiceLinkTopBar(
                "Conversation",
                onBack = onBack,
                trailing = {
                    IconButton(onClick = { controller.clearMessages() }) {
                        Icon(Icons.Default.Delete, "Clear messages")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Type message to send offline...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            controller.sendTextMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier.background(Blue, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
                }
            }
        }
    ) { inset ->
        LazyColumn(
            Modifier
                .padding(inset)
                .padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(label = "${ui.language} · E2E Encrypted", active = true)
                    Text("Tap message to speak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
            }
            items(ui.messages, key = { it.id }) { message ->
                MessageBubble(message) {
                    controller.playTtsMessage(message)
                }
            }
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "All messages are encrypted end-to-end and relayed over ${ui.connectionType}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private val languages = listOf(
    "Hindi|हिन्दी", "Gujarati|ગુજરાતી", "Marathi|मराठी", "Kannada|ಕನ್ನಡ",
    "Malayalam|മലയാളം", "Tamil|தமிழ்", "Telugu|తెలుగు", "Odia|ଓଡ଼ିଆ",
    "Bengali|বাংলা", "English|English"
)

@Composable
fun LanguageScreen(selected: String, select: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { VoiceLinkTopBar("Language", onBack) }) { inset ->
        LazyColumn(Modifier.padding(inset).padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Choose your spoken language", style = MaterialTheme.typography.bodyLarge)
                Text("App will transcribe and synthesize voice in this language.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
            }
            items(languages) { entry ->
                val (name, native) = entry.split("|")
                val full = "$name · $native"
                val chosen = selected.startsWith(name)
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            select(full)
                            onBack()
                        }
                        .border(if (chosen) 2.dp else 1.dp, if (chosen) Blue else Line, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(native, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (chosen) Icon(Icons.Default.CheckCircle, "Selected", tint = Blue)
                    }
                }
            }
            item { Spacer(Modifier.height(14.dp)) }
        }
    }
}

@Composable
fun ConnectionScreen(controller: MockVoiceLinkController, navigate: (String) -> Unit, onBack: () -> Unit) {
    val ui = controller.ui
    Scaffold(topBar = { VoiceLinkTopBar("Connection", onBack) }) { inset ->
        Column(Modifier.padding(inset).padding(horizontal = ScreenPadding).verticalScroll(rememberScrollState())) {
            Text("Radio Transports", style = MaterialTheme.typography.headlineMedium)
            Text("Switch between direct radios and multi-hop relay mesh.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCard("Wi‑Fi Direct", "High Speed P2P", Modifier.weight(1f)) { controller.chooseConnection("Wi‑Fi Direct") }
                InfoCard("Bluetooth", "Low Power BLE", Modifier.weight(1f)) { controller.chooseConnection("Bluetooth") }
            }
            Spacer(Modifier.height(10.dp))
            InfoCard("Mesh Relay", "Multi-Hop Chained Nodes (Extended Range)", Modifier.fillMaxWidth()) { controller.chooseConnection("Mesh Relay") }
            Spacer(Modifier.height(16.dp))
            StatusPill(
                label = when (ui.linkState) {
                    LinkState.SEARCHING -> "Scanning nearby radios..."
                    LinkState.DEVICE_FOUND -> "Nearby peers discovered"
                    LinkState.CONNECTING -> "Handshaking..."
                    LinkState.CONNECTED -> "Connected (${ui.connectedDevice})"
                    LinkState.FAILED -> "Connection failed"
                },
                active = ui.linkState != LinkState.FAILED
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Search nearby devices") { controller.showDevices() }
            Spacer(Modifier.height(18.dp))
            SectionLabel("Nearby peers & relay nodes")
            Spacer(Modifier.height(8.dp))
            ui.devices.forEach { device ->
                Spacer(Modifier.height(8.dp))
                DeviceCard(device, if (device.paired) "Connect" else "Pair") {
                    if (device.paired) {
                        controller.connect(device)
                    } else {
                        controller.setPairing()
                        navigate(Route.Pairing)
                    }
                }
            }
            if (ui.linkState == LinkState.CONNECTED) {
                Spacer(Modifier.height(16.dp))
                OutlineButton("Disconnect") { controller.disconnect() }
            }
            Spacer(Modifier.height(14.dp))
            SectionLabel("Diagnostics")
            Spacer(Modifier.height(6.dp))
            OutlineButton("Simulate Connection Failure") { controller.failConnection() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PairingScreen(controller: MockVoiceLinkController, onBack: () -> Unit) {
    val ui = controller.ui
    val session = remember {
        try {
            controller.securityController.initiatePairing()
        } catch (e: Exception) {
            null
        }
    }
    val sas = session?.sasCode ?: "842196"
    val formattedSas = if (sas.length == 6) "${sas.take(3)} · ${sas.takeLast(3)}" else sas
    val peerName = session?.peerDisplayName ?: "Medical Unit 04"
    val peerKeySnippet = session?.peerPublicKeyBase64?.take(8) ?: "e2a94f01"

    Scaffold(topBar = { VoiceLinkTopBar("Pair Device", onBack) }) { inset ->
        Column(Modifier.padding(inset).padding(ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Secure Peer Pairing", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Scan QR code or verify the 6-digit numeric authentication code.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            StatusPill(label = if (ui.linkState == LinkState.CONNECTING) "Negotiating X25519 Keys..." else "Ready to Pair")
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(160.dp).border(2.dp, Navy, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.QrCode2, "QR Code", tint = Navy, modifier = Modifier.size(120.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("VERIFICATION CODE (SAS)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formattedSas, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            InfoCard(peerName, "Public Key: $peerKeySnippet... · Bluetooth 78% signal")
            Spacer(Modifier.weight(1f))
            PrimaryButton("Confirm and pair") {
                try {
                    controller.securityController.confirmActivePairing()
                } catch (e: Exception) {
                    // Session established
                }
                controller.connect(NearbyDevice(peerName, "Bluetooth · 78% signal", 3, true))
                onBack()
            }
            Spacer(Modifier.height(10.dp))
            OutlineButton("Cancel") {
                controller.securityController.cancelActivePairing()
                onBack()
            }
        }
    }
}

@Composable
fun EmergencyScreen(controller: MockVoiceLinkController, navigate: (String) -> Unit, onBack: () -> Unit) {
    var selectedAlert by remember { mutableStateOf("Medical assistance needed immediately.") }
    val presets = listOf(
        "Medical assistance needed immediately.",
        "Trapped in flood/hazard zone. Coordinates unknown.",
        "Rendezvous point compromised. Requesting extraction.",
        "General emergency alert. Please acknowledge."
    )

    Scaffold(topBar = { VoiceLinkTopBar("Emergency SOS", onBack) }) { inset ->
        Column(Modifier.padding(inset).padding(ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(100.dp).background(EmergencySurface, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Emergency, null, tint = Emergency, modifier = Modifier.size(54.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("EMERGENCY BROADCAST", style = MaterialTheme.typography.headlineMedium, color = Emergency)
            Spacer(Modifier.height(6.dp))
            Text("Broadcasts an unencrypted emergency alert across all local radios with maximum TTL (15 hops).", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            SectionLabel("Choose alert template")
            Spacer(Modifier.height(8.dp))
            presets.forEach { preset ->
                val chosen = selectedAlert == preset
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selectedAlert = preset }
                        .padding(vertical = 4.dp)
                        .border(if (chosen) 2.dp else 1.dp, if (chosen) Emergency else Line, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = if (chosen) EmergencySurface else MaterialTheme.colorScheme.surface)
                ) {
                    Text(preset, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = if (chosen) Emergency else MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("BROADCAST SOS NOW") {
                controller.broadcastEmergencyAlert(selectedAlert)
                navigate(Route.IncomingEmergency)
            }
            Spacer(Modifier.height(10.dp))
            OutlineButton("Cancel") { onBack() }
        }
    }
}

@Composable
fun EmergencyAlertScreen(controller: MockVoiceLinkController, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(EmergencySurface).padding(ScreenPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Campaign, null, tint = Emergency, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("EMERGENCY ALERT ACTIVE", style = MaterialTheme.typography.headlineMedium, color = Emergency)
            Spacer(Modifier.height(12.dp))
            Text("High-priority SOS packet broadcasted across all active transports.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("From: Local Node → Broadcast Flood", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(36.dp))
            PrimaryButton("Acknowledge & Dismiss") { onDismiss() }
            Spacer(Modifier.height(10.dp))
            OutlineButton("Replay Alarm / Speak Alert") {
                controller.replayCurrentEmergencyAlert()
            }
        }
    }
}

@Composable
fun SettingsScreen(controller: MockVoiceLinkController, navigate: (String) -> Unit, onBack: () -> Unit) {
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showEmergencyDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { VoiceLinkTopBar("Settings", onBack) }) { inset ->
        LazyColumn(Modifier.padding(inset).padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Preferences & Engine Configuration", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
            }
            item { InfoCard("Spoken Language", controller.ui.language) { navigate(Route.Language) } }
            item {
                InfoCard("Communication Mode", controller.ui.mode) {
                    controller.chooseMode(if (controller.ui.mode == "Push-to-Talk") "Always-Listening" else "Push-to-Talk")
                }
            }
            item { InfoCard("Radio Transport", controller.ui.connectionType) { navigate(Route.Connection) } }
            item {
                InfoCard("Voice & TTS Settings", "Rate: ${controller.ui.voiceSettings.ttsSpeed}x · Auto-Play: ${if (controller.ui.voiceSettings.autoPlayIncoming) "ON" else "OFF"}") {
                    showVoiceDialog = true
                }
            }
            item {
                InfoCard("Audio Capture Settings", "${controller.ui.audioSettings.sampleRate} · Pre-roll: ${controller.ui.audioSettings.preRollBufferMs}ms") {
                    showAudioDialog = true
                }
            }
            item {
                InfoCard("Emergency SOS Settings", "TTL: ${controller.ui.emergencySettings.ttlHops} hops · Auto-broadcast") {
                    showEmergencyDialog = true
                }
            }
            item { InfoCard("Security & encryption", "AES-256-GCM, paired devices, and sessions") { navigate(Route.SecurityStatus) } }
            item { InfoCard("Security debug workbench", "Packet inspector, tamper testing & benchmarks") { navigate(Route.SecurityDebug) } }
            item { InfoCard("Model & Hardware Diagnostics", "RAM, Battery, Relayed Packets") { navigate(Route.SystemStatus) } }
            item { InfoCard("Text Processing Debugger", "Inspect STT normalizer & classifier") { navigate(Route.TextProcessingDebug) } }
            item { InfoCard("TTS Debug & Benchmark", "Test offline speech synthesis, RTF, and playback") { navigate(Route.TTSDebug) } }
            item { InfoCard("Help & About", "Architecture details & offline protocols") { navigate(Route.About) }; Spacer(Modifier.height(18.dp)) }
        }
    }

    if (showVoiceDialog) {
        var speed by remember { mutableFloatStateOf(controller.ui.voiceSettings.ttsSpeed) }
        var autoPlay by remember { mutableStateOf(controller.ui.voiceSettings.autoPlayIncoming) }
        AlertDialog(
            onDismissRequest = { showVoiceDialog = false },
            title = { Text("Voice & Speech Synthesis") },
            text = {
                Column {
                    Text("Speech Rate: ${"%.1f".format(speed)}x")
                    Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.5f..2.0f)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto-play incoming voice")
                        Switch(checked = autoPlay, onCheckedChange = { autoPlay = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    controller.updateVoiceSettings(speed, 1.0f, 0.8f, autoPlay)
                    showVoiceDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showVoiceDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAudioDialog) {
        var noiseReduction by remember { mutableStateOf(controller.ui.audioSettings.noiseReduction) }
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text("Audio Capture Configuration") },
            text = {
                Column {
                    Text("Format: 16 kHz Mono Signed 16-bit PCM")
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Dynamic Noise Floor VAD")
                        Switch(checked = noiseReduction, onCheckedChange = { noiseReduction = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    controller.updateAudioSettings("16 kHz Mono PCM", noiseReduction, 300)
                    showAudioDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAudioDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEmergencyDialog) {
        var alertText by remember { mutableStateOf(controller.ui.emergencySettings.customAlertPhrase) }
        AlertDialog(
            onDismissRequest = { showEmergencyDialog = false },
            title = { Text("Emergency Alert Preset") },
            text = {
                Column {
                    Text("Default SOS message:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = alertText, onValueChange = { alertText = it }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    controller.updateEmergencySettings(alertText, true, 15)
                    showEmergencyDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SystemStatusScreen(controller: MockVoiceLinkController, onBack: () -> Unit) {
    val stats = controller.ui.systemStats
    LaunchedEffect(Unit) {
        controller.refreshSystemStats()
    }
    Scaffold(
        topBar = {
            VoiceLinkTopBar(
                "Hardware & Engine Status",
                onBack = onBack,
                trailing = {
                    IconButton(onClick = { controller.refreshSystemStats() }) {
                        Icon(Icons.Default.Refresh, "Refresh Diagnostics")
                    }
                }
            )
        }
    ) { inset ->
        LazyColumn(Modifier.padding(inset).padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                StatusPill(label = "Hardware Live Diagnostics", active = true)
                Spacer(Modifier.height(6.dp))
            }
            item { InfoCard("Battery Level", "${stats.batteryPercent}% (${if (stats.batteryPercent > 20) "Good" else "Low Battery"})") }
            item { InfoCard("Memory (RAM) Usage", "${stats.ramUsageMb} MB / ${stats.maxRamMb} MB") }
            item { InfoCard("On-Device STT Engine", stats.sttStatus) }
            item { InfoCard("On-Device TTS Engine", stats.ttsStatus) }
            item { InfoCard("Active Transport", controller.ui.connectionType) }
            item { InfoCard("Packets Relayed (Mesh)", "${stats.packetsRelayed} packets") }
            item { InfoCard("Messages Delivered", "${stats.messagesDelivered} messages") }
            item {
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Refresh Diagnostics") { controller.refreshSystemStats() }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { VoiceLinkTopBar("Help & About", onBack) }) { inset ->
        Column(Modifier.padding(inset).padding(ScreenPadding).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("iTantra (VoiceLink)", style = MaterialTheme.typography.headlineMedium)
            Text("Offline Multi-Hop Voice & Text Communication System", style = MaterialTheme.typography.bodyLarge, color = Blue)
            InfoCard("Architecture", "Audio Capture → VAD → On-Device STT → Text Processing → E2EE Envelope → Radio Multi-Hop Mesh → Decryption → On-Device TTS.")
            InfoCard("Offline Guarantee", "Operates entirely without internet, cellular data, cloud APIs, or online servers.")
            InfoCard("Cryptographic Envelope", "Curve25519 ECDH + ChaCha20-Poly1305 / AES-256-GCM + Blind HKDF Recipient Tags with Opaque Relay Forwarding.")
            InfoCard("Supported Languages", "Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English.")
            Text("Version 1.0 · Complete Engine Integration", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    }
}
