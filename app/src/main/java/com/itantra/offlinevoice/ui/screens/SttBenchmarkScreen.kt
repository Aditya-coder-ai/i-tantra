package com.itantra.offlinevoice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itantra.offlinevoice.audio.stt.STTLanguage
import com.itantra.offlinevoice.audio.stt.STTResult
import com.itantra.offlinevoice.ui.components.InfoCard
import com.itantra.offlinevoice.ui.components.ScreenPadding
import com.itantra.offlinevoice.ui.components.StatusPill
import com.itantra.offlinevoice.ui.components.VoiceLinkTopBar
import java.util.Locale

@Composable
fun SttBenchmarkScreen(onBack: () -> Unit) {
    var selectedLanguage by remember { mutableStateOf(STTLanguage.HINDI) }
    val results = remember { mutableStateListOf<STTResult>() }

    Scaffold(
        topBar = { VoiceLinkTopBar("STT Benchmark", onBack) }
    ) { inset ->
        LazyColumn(
            modifier = Modifier.padding(inset).padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Model Performance & Accuracy", style = MaterialTheme.typography.titleMedium)
                Text("Measure Real-Time Factor (RTF) and Latency", style = MaterialTheme.typography.bodySmall)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Selected Language: ${selectedLanguage.displayName}", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            STTLanguage.values().take(3).forEach { lang ->
                                FilterChip(
                                    selected = selectedLanguage == lang,
                                    onClick = { selectedLanguage = lang },
                                    label = { Text(lang.code.uppercase()) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Benchmarks", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { results.clear() }) { Text("Clear") }
                }
            }

            if (results.isEmpty()) {
                item {
                    InfoCard("No data", "Record or select audio to start benchmarking.")
                }
            } else {
                items(results.reversed()) { res ->
                    BenchmarkResultCard(res)
                }
            }
            
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun BenchmarkResultCard(result: STTResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Result", style = MaterialTheme.typography.labelMedium)
                StatusPill(label = "RTF: ${String.format(Locale.US, "%.2f", result.realTimeFactor)}", active = result.realTimeFactor < 0.3f)
            }
            Spacer(Modifier.height(4.dp))
            Text("“${result.text}”", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("Audio", "${result.audioDurationMs}ms")
                MetricItem("Proc", "${result.processingTimeMs}ms")
                MetricItem("Lang", result.language.code.uppercase())
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
