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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.itantra.offlinevoice.ui.mock.VoiceLinkUiState
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
    Box(Modifier.fillMaxSize().background(Navy), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).border(2.dp, Color.White.copy(.4f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.NetworkWifi, null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text("VoiceLink", style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text("Speak. Connect. Anywhere.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(.8f))
            Spacer(Modifier.height(38.dp))
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text("Starting offline workspace", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(.7f))
        }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pages = listOf(
        Triple(Icons.Default.NetworkWifi, "Communicate without the internet", "VoiceLink prepares your message on-device and is designed for local, low-bandwidth links."),
        Triple(Icons.Default.Translate, "Speak in the language you know", "Choose from ten Indian languages and English. Your preferences remain on your device."),
        Triple(Icons.Default.Bolt, "Built for clear action", "Hold to talk, check delivery, and send an emergency alert when every second matters."),
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pages.indices.forEach { index -> Box(Modifier.size(if (index == page) 24.dp else 8.dp, 8.dp).background(if (index == page) Blue else Line, CircleShape)) } }
        Spacer(Modifier.height(28.dp))
        PrimaryButton(if (page == pages.lastIndex) "Get started" else "Next") { if (page == pages.lastIndex) onFinished() else page++ }
    }
}

@Composable
fun HomeScreen(controller: MockVoiceLinkController, navigate: (String) -> Unit) {
    val ui = controller.ui
    Scaffold(topBar = { VoiceLinkTopBar("VoiceLink", trailing = { IconButton(onClick = { navigate(Route.Settings) }) { Icon(Icons.Default.Settings, "Settings") } }) }) { inset ->
        Column(Modifier.padding(inset).padding(horizontal = ScreenPadding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    StatusPill(label = if (ui.linkState == LinkState.CONNECTED) "Connected" else ui.linkState.name.replace('_', ' '), active = ui.linkState == LinkState.CONNECTED)
                    Spacer(Modifier.height(5.dp))
                    Text("${ui.connectionType} · ${ui.connectedDevice}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { navigate(Route.Language) }, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("${ui.language.substringBefore('·')}") }
            }
            Spacer(Modifier.height(24.dp))
            Text(ui.mode.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(13.dp))
            PushToTalkButton(
                state = ui.communicationState, 
                onHold = controller::startTalking, 
                onRelease = {
                    controller.releaseToProcess()
                    // In a real implementation, we'd get the segment from VAD
                    // For now, we simulate the flow
                }
            )
            Spacer(Modifier.height(18.dp))
            val active = ui.communicationState == CommunicationState.LISTENING
            VoiceActivity(active)
            Spacer(Modifier.height(7.dp))
            Text(if (active) "Release to send · 00:04" else ui.lastMessage, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (ui.communicationState != CommunicationState.IDLE) {
                Spacer(Modifier.height(22.dp)); ProcessingSteps(ui.communicationState)
                if (ui.communicationState == CommunicationState.RECEIVED) { Spacer(Modifier.height(14.dp)); Text("Tap hold-to-talk to send another message", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard("Conversation", "View messages", Modifier.weight(1f)) { navigate(Route.Conversation) }
                InfoCard("Connection", "Manage link", Modifier.weight(1f)) { navigate(Route.Connection) }
            }
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth().clickable { navigate(Route.Emergency) }, shape = RoundedCornerShape(CardRadius), colors = CardDefaults.cardColors(containerColor = EmergencySurface)) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Emergency, null, tint = Emergency); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Emergency alert", style = MaterialTheme.typography.titleMedium, color = Emergency); Text("Send a high-priority local alert", style = MaterialTheme.typography.bodyMedium, color = Emergency) }
                    Text("OPEN", style = MaterialTheme.typography.labelLarge, color = Emergency)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
fun ConversationScreen(ui: VoiceLinkUiState, onBack: () -> Unit) {
    Scaffold(topBar = { VoiceLinkTopBar("Conversation", onBack) }) { inset ->
        LazyColumn(Modifier.padding(inset).padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            item { StatusPill(label = "${ui.language} · local text preview"); Spacer(Modifier.height(8.dp)) }
            items(ui.messages, key = { it.id }) { MessageBubble(it) }
            item { Spacer(Modifier.height(20.dp)); Text("Mock messages only — no speech, network, or replay service is active.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)) }
        }
    }
}

private val languages = listOf("Hindi|हिन्दी", "Gujarati|ગુજરાતી", "Marathi|मराठी", "Kannada|ಕನ್ನಡ", "Malayalam|മലയാളം", "Tamil|தமிழ்", "Telugu|తెలుగు", "Odia|ଓଡ଼ିଆ", "Bengali|বাংলা", "English|English")

@Composable
fun LanguageScreen(selected: String, select: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { VoiceLinkTopBar("Language", onBack) }) { inset ->
        LazyColumn(Modifier.padding(inset).padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Choose your speaking language", style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.height(6.dp)) }
            items(languages) { entry ->
                val (name, native) = entry.split("|")
                val full = "$name · $native"
                val chosen = selected.startsWith(name)
                Card(Modifier.fillMaxWidth().clickable { select(full) }.border(if (chosen) 2.dp else 1.dp, if (chosen) Blue else Line, RoundedCornerShape(18.dp)), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.titleMedium); Text(native, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            Text("Local connection", style = MaterialTheme.typography.headlineMedium)
            Text("Choose a nearby transport. This is a visual prototype; no scan occurs.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoCard("Wi‑Fi Direct", "Fast local link", Modifier.weight(1f)) { controller.chooseConnection("Wi‑Fi Direct") }
                InfoCard("Bluetooth", "Low-power fallback", Modifier.weight(1f)) { controller.chooseConnection("Bluetooth") }
            }
            Spacer(Modifier.height(16.dp))
            StatusPill(label = when (ui.linkState) { LinkState.SEARCHING -> "Searching nearby"; LinkState.DEVICE_FOUND -> "Devices found"; LinkState.CONNECTING -> "Connecting"; LinkState.CONNECTED -> "Connected"; LinkState.FAILED -> "Connection failed" }, active = ui.linkState != LinkState.FAILED)
            Spacer(Modifier.height(16.dp))
            OutlineButton("Search nearby devices") { controller.showDevices() }
            Spacer(Modifier.height(18.dp)); SectionLabel("Nearby devices"); Spacer(Modifier.height(8.dp))
            ui.devices.forEach { device -> Spacer(Modifier.height(8.dp)); DeviceCard(device, if (device.paired) "Paired" else "Pair") { if (device.paired) controller.connect(device) else { controller.setPairing(); navigate(Route.Pairing) } } }
            if (ui.linkState == LinkState.CONNECTED) { Spacer(Modifier.height(16.dp)); OutlineButton("Disconnect") { controller.disconnect() } }
            Spacer(Modifier.height(10.dp)); Text("Prototype controls", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp)); OutlineButton("Show connection failure") { controller.failConnection() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PairingScreen(ui: VoiceLinkUiState, connect: (NearbyDevice) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { VoiceLinkTopBar("Pair device", onBack) }) { inset ->
        Column(Modifier.padding(inset).padding(ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Secure local pairing", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp)); Text("Confirm that the code shown on both nearby devices matches.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp)); StatusPill(label = if (ui.linkState == LinkState.CONNECTING) "Connecting to nearby device" else "Ready to pair")
            Spacer(Modifier.height(30.dp))
            Box(Modifier.size(178.dp).border(2.dp, Navy, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.QrCode2, "QR code placeholder", tint = Navy, modifier = Modifier.size(126.dp)) }
            Spacer(Modifier.height(24.dp)); Text("PAIRING CODE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("842  ·  196", style = MaterialTheme.typography.headlineLarge, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
            Spacer(Modifier.height(22.dp)); InfoCard("Rescue Team 04", "Bluetooth · 74% signal · Not connected")
            Spacer(Modifier.weight(1f)); PrimaryButton("Confirm and pair") { connect(NearbyDevice("Rescue Team 04", "Bluetooth · 74% signal", 3, true)); onBack() }; Spacer(Modifier.height(10.dp)); OutlineButton("Cancel") { onBack() }
        }
    }
}

@Composable
fun EmergencyScreen(navigate: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { VoiceLinkTopBar("Emergency", onBack) }) { inset ->
        Column(Modifier.padding(inset).padding(ScreenPadding), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(118.dp).background(EmergencySurface, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Emergency, null, tint = Emergency, modifier = Modifier.size(62.dp)) }
            Spacer(Modifier.height(22.dp)); Text("EMERGENCY ALERT", style = MaterialTheme.typography.headlineMedium, color = Emergency)
            Spacer(Modifier.height(10.dp)); Text("Emergency assistance required.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp)); InfoCard("Alert message", "I need emergency assistance. My location should be checked.")
            Spacer(Modifier.height(14.dp)); StatusPill(label = "High-priority local delivery", active = true)
            Spacer(Modifier.weight(1f)); PrimaryButton("Send alert") { navigate(Route.IncomingEmergency) }; Spacer(Modifier.height(10.dp)); OutlineButton("Cancel") { onBack() }
        }
    }
}

@Composable
fun EmergencyAlertScreen(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(EmergencySurface).padding(ScreenPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Campaign, null, tint = Emergency, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp)); Text("EMERGENCY ALERT", style = MaterialTheme.typography.headlineMedium, color = Emergency)
            Spacer(Modifier.height(12.dp)); Text("Emergency assistance required.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp)); Text("From: Aarav’s VoiceLink", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(36.dp)); PrimaryButton("Acknowledge") { onDismiss() }; Spacer(Modifier.height(10.dp)); OutlineButton("Replay alert") {}
        }
    }
}

@Composable
fun SettingsScreen(controller: MockVoiceLinkController, navigate: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { VoiceLinkTopBar("Settings", onBack) }) { inset ->
        LazyColumn(Modifier.padding(inset).padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Preferences stay on this device", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(6.dp)) }
            item { InfoCard("Language", controller.ui.language) { navigate(Route.Language) } }
            item { InfoCard("Communication mode", controller.ui.mode) { controller.chooseMode(if (controller.ui.mode == "Push-to-Talk") "Conversation" else "Push-to-Talk") } }
            item { InfoCard("Connection type", controller.ui.connectionType) { navigate(Route.Connection) } }
            item { InfoCard("Voice settings", "Input level, playback, and accessibility") }
            item { InfoCard("Emergency settings", "Alert phrase and confirmation") { navigate(Route.Emergency) } }
            item { InfoCard("Audio settings", "16 kHz mono PCM (planned input profile)") }
            item { InfoCard("Model & system status", "Demo readiness and device health") { navigate(Route.SystemStatus) } }
            item { InfoCard("Text processing debug", "Inspect the STT → message pipeline") { navigate(Route.TextProcessingDebug) } }
            item { InfoCard("TTS Debug & Benchmark", "Test offline speech synthesis, RTF, and playback") { navigate(Route.TTSDebug) } }
            item { InfoCard("Help & about", "How VoiceLink works offline") { navigate(Route.About) }; Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
fun SystemStatusScreen(onBack: () -> Unit) {
    val systems = listOf("STT model" to "Ready · mock", "TTS model" to "Ready · mock", "Language pack" to "Hindi, English · mock", "CPU usage" to "18% · preview", "RAM usage" to "142 MB · preview", "Connection" to "Wi‑Fi Direct · demo", "Battery" to "76% · preview")
    Scaffold(topBar = { VoiceLinkTopBar("Model & system status", onBack) }) { inset ->
        LazyColumn(Modifier.padding(inset).padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { StatusPill(label = "Demo data — no models loaded", active = false); Spacer(Modifier.height(6.dp)) }
            items(systems) { (title, status) -> InfoCard(title, status) }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { VoiceLinkTopBar("Help & about", onBack) }) { inset ->
        Column(Modifier.padding(inset).padding(ScreenPadding).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VoiceLink", style = MaterialTheme.typography.headlineMedium)
            Text("Speak. Connect. Anywhere.", style = MaterialTheme.typography.bodyLarge, color = Blue)
            InfoCard("What it does", "VoiceLink is designed to turn speech into lightweight local messages for nearby communication.")
            InfoCard("Offline operation", "The planned system works without cloud services. Language processing and communication adapters will be connected later.")
            InfoCard("How communication works", "Speak → text → local link → text → speech. This UI preview does not run those services.")
            InfoCard("Supported languages", "Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, and English.")
            Text("Version 0.1 · UI prototype", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    }
}
