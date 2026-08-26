package com.itantra.offlinevoice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.offlinevoice.audio.stt.STTLanguage
import com.itantra.offlinevoice.audio.stt.STTResult
import com.itantra.offlinevoice.text.ConfidenceStatus
import com.itantra.offlinevoice.text.MessageType
import com.itantra.offlinevoice.text.ProcessedMessage
import com.itantra.offlinevoice.text.TextProcessingResult
import com.itantra.offlinevoice.text.TextProcessingStatus
import com.itantra.offlinevoice.text.TextProcessor
import com.itantra.offlinevoice.ui.components.VoiceLinkTopBar
import com.itantra.offlinevoice.ui.theme.Emergency
import com.itantra.offlinevoice.ui.theme.EmergencySurface

/**
 * Developer/debug screen that wires the text-processing pipeline into VoiceLink's UI.
 *
 * Shows every field specified in Section 22 of the original spec:
 * raw STT, processed text, language, type, priority, confidence %, message ID,
 * timestamp, sequence number, UTF-8 size, processing time.
 *
 * Includes an optional user-facing edit/confirm step before a message is considered
 * ready for the (future) encryption/network handoff. Emergency-confirmed messages
 * can skip the edit step.
 */
@Composable
fun TextProcessingDebugScreen(onBack: () -> Unit) {
    val processor = remember { TextProcessor() }

    var rawSttInput by remember { mutableStateOf("I need help there is a fire") }
    var selectedLanguage by remember { mutableStateOf(STTLanguage.ENGLISH) }
    var confidenceInput by remember { mutableFloatStateOf(0.92f) }
    var userConfirmedEmergency by remember { mutableStateOf(false) }

    var lastResult by remember { mutableStateOf<TextProcessingResult?>(null) }

    // Edit/confirm state
    var editMode by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }

    Scaffold(topBar = { VoiceLinkTopBar("Text Processing Debug", onBack) }) { inset ->
        LazyColumn(
            modifier = Modifier
                .padding(inset)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- Input Section ---
            item {
                SectionHeader("Raw STT Input")
                OutlinedTextField(
                    value = rawSttInput,
                    onValueChange = { rawSttInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Simulated STT text") },
                    minLines = 2
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Language", style = MaterialTheme.typography.labelMedium)
                        Text(selectedLanguage.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Confidence", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = "%.2f".format(confidenceInput),
                            onValueChange = { confidenceInput = it.toFloatOrNull() ?: 0.92f },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { userConfirmedEmergency = !userConfirmedEmergency },
                        modifier = Modifier.weight(1f),
                        colors = if (userConfirmedEmergency) ButtonDefaults.outlinedButtonColors(containerColor = EmergencySurface) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(Icons.Default.Emergency, null, modifier = Modifier.size(16.dp), tint = if (userConfirmedEmergency) Emergency else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(4.dp))
                        Text(if (userConfirmedEmergency) "Emergency: ON" else "Emergency: OFF", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            confirmed = false
                            editMode = false
                            val sttResult = STTResult(
                                text = rawSttInput,
                                language = selectedLanguage,
                                confidence = confidenceInput,
                                processingTimeMs = 0,
                                audioDurationMs = 2000,
                                isFinal = true
                            )
                            lastResult = processor.process(sttResult, "debug-conv", "debug-user", userConfirmedEmergency)
                            lastResult?.message?.let { editedText = it.text }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.BugReport, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Process", fontSize = 12.sp)
                    }
                }
            }

            // --- Language Selector ---
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    STTLanguage.values().forEach { lang ->
                        OutlinedButton(
                            onClick = { selectedLanguage = lang },
                            modifier = Modifier.weight(1f),
                            contentPadding = ButtonDefaults.ContentPadding.let {
                                androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                            },
                            colors = if (selectedLanguage == lang)
                                ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text(lang.code, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- Result Section ---
            lastResult?.let { result ->
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("Pipeline Result")
                    StatusBadge(result.status)
                }

                result.message?.let { msg ->
                    // --- All fields from Section 22 ---
                    item { DebugField("Raw STT Input", rawSttInput) }
                    item { DebugField("Processed Text", msg.text) }
                    item { DebugField("Language", msg.language) }
                    item { DebugField("Message Type", msg.messageType.name) }
                    item { DebugField("Priority", msg.priority.name) }
                    item { DebugField("Confidence", "%.1f%%".format(msg.confidence * 100)) }
                    item { DebugField("Confidence Status", msg.confidenceStatus.name) }
                    item { DebugField("Message ID", msg.messageId) }
                    item { DebugField("Timestamp", msg.timestamp) }
                    item { DebugField("Sequence Number", msg.sequenceNumber.toString()) }
                    item { DebugField("UTF-8 Byte Size", "${msg.utf8ByteSize} bytes") }
                    item { DebugField("Processing Time", "${msg.processingTimeMs} ms") }

                    // --- Edit/Confirm Step ---
                    item {
                        Spacer(Modifier.height(12.dp))
                        SectionHeader("Edit & Confirm")

                        if (msg.messageType == MessageType.EMERGENCY) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = EmergencySurface)
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Emergency, null, tint = Emergency)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Emergency confirmed — edit step bypassed.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Emergency
                                    )
                                }
                            }
                        } else if (editMode) {
                            OutlinedTextField(
                                value = editedText,
                                onValueChange = { editedText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Edit message text") }
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { editMode = false; editedText = msg.text }) {
                                    Text("Cancel")
                                }
                                Button(onClick = { editMode = false; confirmed = true }) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Confirm")
                                }
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { editMode = true; editedText = msg.text },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit")
                                }
                                Button(
                                    onClick = { confirmed = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Confirm & Send")
                                }
                            }
                        }

                        if (confirmed) {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Ready for encryption/network handoff.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }

                // Partial preview
                result.partialPreview?.let { preview ->
                    item { DebugField("Partial Preview", preview) }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun StatusBadge(status: TextProcessingStatus) {
    val (color, label) = when (status) {
        TextProcessingStatus.SUCCESS -> Color(0xFF2E7D32) to "SUCCESS"
        TextProcessingStatus.EMPTY_MESSAGE -> Color(0xFF9E9E9E) to "EMPTY MESSAGE"
        TextProcessingStatus.LOW_CONFIDENCE_PENDING_REVIEW -> Color(0xFFF57C00) to "LOW CONFIDENCE — PENDING REVIEW"
        TextProcessingStatus.DUPLICATE -> Color(0xFF9E9E9E) to "DUPLICATE"
        TextProcessingStatus.PARTIAL_IN_PROGRESS -> Color(0xFF1565C0) to "PARTIAL (IN PROGRESS)"
        TextProcessingStatus.UNSUPPORTED_LANGUAGE -> Color(0xFFF57C00) to "UNSUPPORTED LANGUAGE"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DebugField(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
    }
}
