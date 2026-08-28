package com.itantra.offlinevoice.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itantra.offlinevoice.ui.mock.CommunicationState
import com.itantra.offlinevoice.ui.mock.NearbyDevice
import com.itantra.offlinevoice.ui.mock.VoiceMessage
import com.itantra.offlinevoice.ui.theme.Blue
import com.itantra.offlinevoice.ui.theme.Emergency
import com.itantra.offlinevoice.ui.theme.EmergencySurface
import com.itantra.offlinevoice.ui.theme.Line
import com.itantra.offlinevoice.ui.theme.Success

val ScreenPadding = 20.dp
val CardRadius = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceLinkTopBar(title: String, onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                }
            }
        },
        actions = { trailing?.invoke() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
fun StatusPill(label: String, modifier: Modifier = Modifier, active: Boolean = true) {
    val color = if (active) Success else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.clip(RoundedCornerShape(100.dp)).background(color.copy(alpha = .11f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    ElevatedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OutlineButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun InfoCard(title: String, body: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(5.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun VoiceActivity(active: Boolean, modifier: Modifier = Modifier) {
    val bars = if (active) listOf(.35f, .65f, .95f, .55f, .8f, .42f, .72f, .5f, .9f) else listOf(.18f, .23f, .15f, .2f, .14f, .2f, .16f, .22f, .17f)
    Canvas(modifier = modifier.height(45.dp).fillMaxWidth()) {
        val gap = size.width / (bars.size * 2f)
        bars.forEachIndexed { index, amount ->
            val barHeight = size.height * amount
            drawRoundRect(
                color = if (active) Blue else Line,
                topLeft = androidx.compose.ui.geometry.Offset(((index * gap * 2) + (gap / 2)), (size.height - barHeight) / 2),
                size = androidx.compose.ui.geometry.Size(gap, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(gap / 2)
            )
        }
    }
}

@Composable
fun PushToTalkButton(state: CommunicationState, onHold: () -> Unit, onRelease: () -> Unit) {
    val isListening = state == CommunicationState.LISTENING
    val color = if (isListening) Emergency else Blue
    val message = when (state) {
        CommunicationState.LISTENING -> "RELEASE\nTO SEND"
        CommunicationState.PROCESSING -> "PROCESSING"
        CommunicationState.SENDING -> "SENDING"
        CommunicationState.RECEIVED -> "DELIVERED"
        CommunicationState.IDLE -> "HOLD\nTO TALK"
    }

    val currentOnHold by rememberUpdatedState(onHold)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentState by rememberUpdatedState(state)

    // Pulsating animation for the outer ring while listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.13f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val outerScale = if (isListening) pulseScale else 1f
    val outerAlpha = if (isListening) pulseAlpha else 0.13f

    Box(
        modifier = Modifier
            .size(238.dp)
            .graphicsLayer(scaleX = outerScale, scaleY = outerScale)
            .clip(CircleShape)
            .background(color.copy(alpha = outerAlpha))
            .padding(12.dp)
            .border(2.dp, color.copy(alpha = .22f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(color)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        if (currentState == CommunicationState.IDLE || currentState == CommunicationState.RECEIVED) {
                            currentOnHold()
                            try {
                                tryAwaitRelease()
                            } finally {
                                currentOnRelease()
                            }
                        }
                    })
                }
                .semantics { contentDescription = "Hold to talk. Current state: ${state.name.lowercase()}" },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (isListening) Icons.Default.Mic else Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(9.dp))
                Text(message, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun ProcessingSteps(state: CommunicationState) {
    val steps = listOf("Listening", "Converting speech", "Sending", "Delivered")
    val activeIndex = when (state) {
        CommunicationState.LISTENING -> 0
        CommunicationState.PROCESSING -> 1
        CommunicationState.SENDING -> 2
        CommunicationState.RECEIVED -> 3
        CommunicationState.IDLE -> -1
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        steps.forEachIndexed { index, step ->
            val reached = index <= activeIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                Box(Modifier.size(26.dp).background(if (reached) Blue else Line, CircleShape), contentAlignment = Alignment.Center) {
                    if (reached) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    else Text(text = (index + 1).toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(step, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: VoiceMessage,
    onPlayAudio: (() -> Unit)? = null,
    onPlayOriginal: (() -> Unit)? = null
) {
    var showOriginalText by remember { mutableStateOf(false) }
    val surface = when {
        message.emergency -> EmergencySurface
        message.isMine -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
    ) {
        if (message.emergency) Text("🚨 EMERGENCY ALERT", color = Emergency, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = surface),
            modifier = Modifier
                .fillMaxWidth(.92f)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val langLabel = when {
                        message.isMine -> "YOU · ${message.language}"
                        message.isTranslated -> "${message.originalLanguage.uppercase()} ➔ ${(message.targetLanguage ?: message.language).uppercase()}"
                        else -> "REMOTE · ${message.language.uppercase()}"
                    }
                    Text(
                        langLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (message.isTranslated) Blue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (message.isTranslated && message.translationLatencyMs > 0) {
                        Text(
                            "⚡ ${message.translationLatencyMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = Success,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (message.hopCount > 0) {
                        Text(
                            "${message.hopCount} hop(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Blue
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                // Prominently display primary (translated or original) message text
                Text(
                    "“${message.text}”",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                // If translated, show original text box
                if (message.isTranslated && message.originalText.isNotBlank() && message.originalText != message.text) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                "Original (${message.originalLanguage}):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "“${message.originalText}”",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Play primary TTS
                        Box(
                            modifier = Modifier
                                .clickable { onPlayAudio?.invoke() }
                                .background(Blue.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, "Play voice", modifier = Modifier.size(16.dp), tint = Blue)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (message.isTranslated) "Play ${(message.targetLanguage ?: message.language).substringBefore(' ')}" else "Play voice",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Blue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Play original TTS if translated
                        if (message.isTranslated && onPlayOriginal != null) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clickable { onPlayOriginal.invoke() }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Original (${message.originalLanguage.take(3)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        "${message.time}${if (message.delivered && message.isMine) "  ✓✓" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: NearbyDevice, actionLabel: String, onAction: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.GraphicEq, null, tint = Blue)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text("${"▂▅▇█".take(device.signal)}  ${device.detail}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(actionLabel) }
        }
    }
}
