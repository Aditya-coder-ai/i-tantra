package com.itantra.offlinevoice.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.offlinevoice.tts.TTSConfig
import com.itantra.offlinevoice.tts.TTSLanguage
import com.itantra.offlinevoice.tts.TTSResult
import com.itantra.offlinevoice.tts.benchmark.TTSBenchmark
import com.itantra.offlinevoice.tts.benchmark.TTSBenchmarkResult
import com.itantra.offlinevoice.tts.engine.OfflineTTSEngine
import com.itantra.offlinevoice.tts.engine.TTSModelInfo
import com.itantra.offlinevoice.ui.components.VoiceLinkTopBar
import com.itantra.offlinevoice.ui.theme.Emergency
import com.itantra.offlinevoice.ui.theme.EmergencySurface
import com.itantra.offlinevoice.ui.theme.Success
import com.itantra.offlinevoice.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Developer debug/benchmark screen for the TTS engine.
 *
 * Shows real-time metrics: model status, processing time, audio duration,
 * RTF, RAM usage. Allows testing all 10 languages with Unicode test strings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TTSDebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Engine (initialised once)
    val engine = remember { OfflineTTSEngine(context) }
    val benchmark = remember { TTSBenchmark(context, engine) }

    DisposableEffect(Unit) {
        engine.initialize()
        onDispose { engine.release() }
    }

    // State
    var selectedLanguage by remember { mutableStateOf(TTSLanguage.ENGLISH) }
    var inputText by remember { mutableStateOf("I need help.") }
    var isEmergencyMode by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<TTSResult?>(null) }
    var lastBenchmark by remember { mutableStateOf<TTSBenchmarkResult?>(null) }
    var modelInfoList by remember { mutableStateOf<List<TTSModelInfo>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load model info
    val refreshModelInfo = {
        modelInfoList = engine.getModelInfo()
    }

    // Update default text when language changes
    val testSentences = TTSBenchmark.TEST_SENTENCES[selectedLanguage]
    if (testSentences != null && inputText == "I need help." || inputText.isEmpty()) {
        inputText = testSentences?.firstOrNull() ?: ""
    }

    Scaffold(topBar = { VoiceLinkTopBar("TTS Debug / Benchmark", onBack) }) { inset ->
        LazyColumn(
            modifier = Modifier
                .padding(inset)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Language Selector ─────────────────────────────────
            item {
                TTSSectionHeader("Language", Icons.Default.Language)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TTSLanguage.values().forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = {
                                selectedLanguage = lang
                                val sentences = TTSBenchmark.TEST_SENTENCES[lang]
                                inputText = sentences?.firstOrNull() ?: ""
                                refreshModelInfo()
                            },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(lang.code.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(lang.nativeName, fontSize = 9.sp)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // ── Text Input ───────────────────────────────────────
            item {
                TTSSectionHeader("Input Text", Icons.Default.Campaign)
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Text to synthesise") },
                    minLines = 2,
                    maxLines = 5
                )
                Spacer(Modifier.height(4.dp))

                // Quick-fill buttons
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TTSBenchmark.TEST_SENTENCES[selectedLanguage]?.forEachIndexed { i, sentence ->
                        OutlinedButton(
                            onClick = { inputText = sentence },
                            modifier = Modifier.weight(1f),
                            contentPadding = ButtonDefaults.ContentPadding.let {
                                androidx.compose.foundation.layout.PaddingValues(4.dp)
                            }
                        ) {
                            Text("S${i + 1}", fontSize = 10.sp)
                        }
                    }
                }
            }

            // ── Controls ─────────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Emergency toggle
                    OutlinedButton(
                        onClick = { isEmergencyMode = !isEmergencyMode },
                        modifier = Modifier.weight(1f),
                        colors = if (isEmergencyMode) {
                            ButtonDefaults.outlinedButtonColors(containerColor = EmergencySurface)
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        }
                    ) {
                        Icon(
                            Icons.Default.Emergency, null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isEmergencyMode) Emergency else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isEmergencyMode) "🚨 ON" else "Emergency", fontSize = 11.sp)
                    }

                    // Synthesise + Play button
                    Button(
                        onClick = {
                            if (inputText.isBlank()) return@Button
                            errorMessage = null
                            isProcessing = true
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.Default) {
                                        benchmark.measureTTS(inputText, selectedLanguage)
                                    }
                                    lastBenchmark = result
                                    lastResult = TTSResult(
                                        audioData = ShortArray(0), // not stored in benchmark
                                        sampleRate = result.sampleRate,
                                        audioDurationMs = result.audioDurationMs,
                                        processingTimeMs = result.processingTimeMs,
                                        language = selectedLanguage,
                                        textLength = result.textLength
                                    )

                                    // Play via engine
                                    isPlaying = true
                                    withContext(Dispatchers.Default) {
                                        engine.speak(inputText, selectedLanguage)
                                    }
                                    isPlaying = false
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                } finally {
                                    isProcessing = false
                                    isPlaying = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing && inputText.isNotBlank()
                    ) {
                        if (isProcessing) {
                            PulsingDot()
                            Spacer(Modifier.width(6.dp))
                            Text(if (isPlaying) "Playing…" else "Synthesising…", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Speak", fontSize = 11.sp)
                        }
                    }
                }

                // Playback controls
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { engine.pause() }) {
                        Icon(Icons.Default.Pause, "Pause")
                    }
                    IconButton(onClick = { engine.resume() }) {
                        Icon(Icons.Default.PlayArrow, "Resume")
                    }
                    IconButton(onClick = { engine.stop(); isPlaying = false; isProcessing = false }) {
                        Icon(Icons.Default.Stop, "Stop")
                    }
                    IconButton(onClick = {
                        if (lastResult != null) {
                            scope.launch {
                                isPlaying = true
                                withContext(Dispatchers.Default) {
                                    engine.speak(inputText, selectedLanguage)
                                }
                                isPlaying = false
                            }
                        }
                    }) {
                        Icon(Icons.Default.Replay, "Replay")
                    }
                }
            }

            // ── Error Display ────────────────────────────────────
            errorMessage?.let { err ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = EmergencySurface)
                    ) {
                        Text(
                            err,
                            modifier = Modifier.padding(12.dp),
                            color = Emergency,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // ── Benchmark Results ────────────────────────────────
            lastBenchmark?.let { bench ->
                item {
                    TTSSectionHeader("Benchmark Results", Icons.Default.Speed)
                }
                item { TTSMetricCard("Language", "${bench.language.displayName} (${bench.language.code})") }
                item { TTSMetricCard("Model", bench.modelName) }
                item { TTSMetricCard("Model Size", bench.modelSizeDisplay) }
                item { TTSMetricCard("Text Length", "${bench.textLength} chars, ${bench.sentenceCount} sentence(s)") }
                item {
                    TTSMetricCard(
                        "Processing Time",
                        "${bench.processingTimeMs} ms",
                        valueColor = if (bench.processingTimeMs < 2000) Success else Warning
                    )
                }
                item {
                    TTSMetricCard(
                        "Audio Duration",
                        "${bench.audioDurationMs} ms (${bench.totalSamples} samples)"
                    )
                }
                item {
                    val rtfColor = when {
                        bench.realTimeFactor < 0.5f -> Success
                        bench.realTimeFactor < 1.0f -> Warning
                        else -> Emergency
                    }
                    TTSMetricCard(
                        "Real-Time Factor (RTF)",
                        "%.3f".format(bench.realTimeFactor),
                        valueColor = rtfColor
                    )
                }
                item { TTSMetricCard("Sample Rate", "${bench.sampleRate} Hz") }
                item { TTSMetricCard("Quantization", bench.quantization) }
                if (bench.ramDeltaMB >= 0) {
                    item { TTSMetricCard("RAM Δ", "%.1f MB".format(bench.ramDeltaMB)) }
                }
                item {
                    TTSMetricCard(
                        "Playback Status",
                        when {
                            isPlaying -> "🔊 Playing"
                            isProcessing -> "⏳ Processing"
                            else -> "✓ Ready"
                        }
                    )
                }
            }

            // ── Model Status ─────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                TTSSectionHeader("Model Status", Icons.Default.Storage)

                Button(
                    onClick = { refreshModelInfo() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh Model Info")
                }
            }

            if (modelInfoList.isNotEmpty()) {
                modelInfoList.forEach { info ->
                    item {
                        TTSModelStatusCard(info)
                    }
                }
            } else {
                item {
                    Text(
                        "Tap 'Refresh' to scan for installed models.\n" +
                                "Models should be placed in:\n" +
                                "  app_data/models/tts/{language_code}/",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Composable Helpers ───────────────────────────────────────────────

@Composable
private fun TTSSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TTSMetricCard(label: String, value: String, valueColor: Color? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TTSModelStatusCard(info: TTSModelInfo) {
    val statusColor by animateColorAsState(
        when {
            info.isLoaded -> Success
            info.isAvailable -> Warning
            else -> Emergency
        },
        label = "status_color"
    )

    val statusText = when {
        info.isLoaded -> "Loaded ✓"
        info.isAvailable -> "Available (not loaded)"
        else -> "Not installed"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.05f)
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language badge
            Box(
                Modifier
                    .size(36.dp)
                    .background(statusColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    info.language.code.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info.language.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    statusText,
                    fontSize = 11.sp,
                    color = statusColor
                )
                if (info.isAvailable) {
                    Text(
                        "Size: ${info.modelSizeDisplay} · Voice: ${info.voiceName}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )
    Box(
        Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.onPrimary, CircleShape)
    )
}
