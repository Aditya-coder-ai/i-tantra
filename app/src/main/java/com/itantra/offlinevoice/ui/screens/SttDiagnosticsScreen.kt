package com.itantra.offlinevoice.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.offlinevoice.audio.RecordingState
import com.itantra.offlinevoice.audio.stt.STTLanguage
import com.itantra.offlinevoice.audio.stt.STTResult
import com.itantra.offlinevoice.ui.mock.CommunicationState
import com.itantra.offlinevoice.ui.mock.MockVoiceLinkController
import com.itantra.offlinevoice.ui.theme.DarkSlate
import com.itantra.offlinevoice.ui.theme.ElectricCyan
import com.itantra.offlinevoice.ui.theme.Gunmetal
import com.itantra.offlinevoice.ui.theme.MutedBlue
import com.itantra.offlinevoice.ui.theme.NeonGreen
import com.itantra.offlinevoice.ui.theme.PureWhite
import com.itantra.offlinevoice.ui.theme.SafetyOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SttDiagnosticsScreen(
    controller: MockVoiceLinkController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var benchmarkTranscription by remember { mutableStateOf<STTResult?>(null) }
    var selectedLanguage by remember { mutableStateOf(STTLanguage.ENGLISH) }
    var testPhrase by remember { mutableStateOf("I need help.") }
    var isProcessingStt by remember { mutableStateOf(false) }

    val diagnostics = controller.lastDiagnostics
    val isRecording = controller.ui.communicationState == CommunicationState.LISTENING
    val hasCapturedAudio = controller.lastCapturedRawAudio.isNotEmpty() || controller.lastCapturedVadAudio.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "STT & Audio Pipeline Inspector",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                        Text(
                            text = "Acoustic Diagnostics & Verification Workbench",
                            fontSize = 12.sp,
                            color = MutedBlue
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PureWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSlate)
            )
        },
        containerColor = DarkSlate
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Microphone Hardware Configuration Card (Step 1 & 3)
            Card(
                colors = CardDefaults.cardColors(containerColor = Gunmetal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("1. AUDIO HARDWARE & CAPTURE CONFIG", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    DiagnosticSpecRow("Sample Rate", "16,000 Hz (Standard Speech)")
                    DiagnosticSpecRow("Channels", "1 (MONO)")
                    DiagnosticSpecRow("PCM Encoding", "16-Bit Signed Integer")
                    DiagnosticSpecRow("Byte Order", "LITTLE_ENDIAN")
                    DiagnosticSpecRow("Audio Source", "MediaRecorder.AudioSource.MIC")
                    DiagnosticSpecRow("VAD Pre-Roll", "500 ms (25 frames)")
                    DiagnosticSpecRow("VAD Hangover", "600 ms (30 frames)")
                }
            }

            // 2. Real-Time Recording & Audio Quality Metrics (Step 1, 4 & 5)
            Card(
                colors = CardDefaults.cardColors(containerColor = Gunmetal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2. CAPTURED AUDIO METRICS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    DiagnosticSpecRow("Duration", "${diagnostics.durationMs} ms (${diagnostics.sampleCount} samples)")
                    DiagnosticSpecRow("Bytes Recorded", "${diagnostics.byteCount} bytes")
                    DiagnosticSpecRow("RMS Volume", "${"%.1f".format(diagnostics.rmsDbfs)} dBFS (${"%.3f".format(diagnostics.rmsLinear)})")
                    DiagnosticSpecRow("Peak Amplitude", "${diagnostics.peakAmplitude} / 32768 (${"%.1f".format(diagnostics.peakDbfs)} dBFS)")
                    DiagnosticSpecRow("Clipping Samples", "${diagnostics.clippingSampleCount} samples (${if (diagnostics.isClipping) "SEVERELY CLIPPING" else "OK"})")

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("RMS Level Meter:", fontSize = 11.sp, color = MutedBlue)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { diagnostics.rmsLinear.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (diagnostics.isClipping) SafetyOrange else NeonGreen,
                        trackColor = DarkSlate
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSlate, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = diagnostics.speechQualityAssessment,
                            fontSize = 12.sp,
                            color = if (diagnostics.isClipping) SafetyOrange else if (diagnostics.isSilent) Color.Yellow else NeonGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 3. Audio Recording & Immediate Playback Controls (Step 2)
            Card(
                colors = CardDefaults.cardColors(containerColor = Gunmetal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("3. MIC RECORDING & PLAYBACK VERIFICATION", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                    Text("Record your voice, then click PLAY to hear the exact raw PCM audio captured through phone speaker.", fontSize = 11.sp, color = MutedBlue)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isRecording) {
                                    controller.releaseToProcess()
                                } else {
                                    controller.startTalking()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) SafetyOrange else ElectricCyan
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRecording) "Stop Capture" else "Record Mic", fontSize = 12.sp, color = DarkSlate, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (controller.isPlayingDebugAudio) {
                                    controller.stopDebugAudioPlayback()
                                } else {
                                    controller.playLastRecordedAudio()
                                }
                            },
                            enabled = hasCapturedAudio,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (controller.isPlayingDebugAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = DarkSlate,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (controller.isPlayingDebugAudio) "Playing..." else "Play Audio",
                                fontSize = 12.sp,
                                color = DarkSlate,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val savedPath = controller.saveLastRecordingToWav()
                            if (savedPath != null) {
                                Toast.makeText(context, "Saved WAV to: $savedPath", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "No audio to save. Please record first.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = hasCapturedAudio,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Debug WAV File to Storage", fontSize = 12.sp, color = ElectricCyan)
                    }
                }
            }

            // 4. VAD vs. Raw Pipeline Isolation (Step 6 & 10)
            Card(
                colors = CardDefaults.cardColors(containerColor = Gunmetal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("4. VAD SEGMENTATION vs. RAW STREAM COMPARISON", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                    Text("Compare STT results between complete contiguous audio and VAD-segmented audio.", fontSize = 11.sp, color = MutedBlue)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Bypass VAD in Pipeline", fontSize = 13.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
                            Text("Feed 100% unsegmented raw audio to STT", fontSize = 11.sp, color = MutedBlue)
                        }
                        Switch(
                            checked = controller.bypassVadInStt,
                            onCheckedChange = { controller.bypassVadInStt = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    DiagnosticSpecRow("Raw Audio Duration", "${(controller.lastCapturedRawAudio.size * 1000L) / 16000} ms (${controller.lastCapturedRawAudio.size} samples)")
                    DiagnosticSpecRow("VAD Segment Duration", "${controller.vadEngine.lastSegmentDurationMs} ms (${controller.lastCapturedVadAudio.size} samples)")
                    DiagnosticSpecRow("Adaptive Noise Floor", "${"%.1f".format(controller.vadEngine.noiseFloorDb)} dBFS")

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessingStt = true
                                    val res = withContext(Dispatchers.Default) {
                                        controller.runSttOnRawAudio(selectedLanguage)
                                    }
                                    benchmarkTranscription = res
                                    isProcessingStt = false
                                }
                            },
                            enabled = controller.lastCapturedRawAudio.isNotEmpty() && !isProcessingStt,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Run STT (Raw)", fontSize = 11.sp, color = DarkSlate, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessingStt = true
                                    val res = withContext(Dispatchers.Default) {
                                        controller.runSttOnVadSegment(selectedLanguage)
                                    }
                                    benchmarkTranscription = res
                                    isProcessingStt = false
                                }
                            },
                            enabled = controller.lastCapturedVadAudio.isNotEmpty() && !isProcessingStt,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Run STT (VAD)", fontSize = 11.sp, color = DarkSlate, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5. Controlled Test Sentences Benchmark (Step 7 & 12)
            Card(
                colors = CardDefaults.cardColors(containerColor = Gunmetal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("5. CONTROLLED BENCHMARK & REFERENCE AUDIO", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                    Text("Test STT with clean synthesized reference audio to isolate STT model from room noise.", fontSize = 11.sp, color = MutedBlue)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Selected Language:", fontSize = 12.sp, color = MutedBlue)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(STTLanguage.ENGLISH, STTLanguage.HINDI, STTLanguage.TAMIL).forEach { lang ->
                            val isSel = selectedLanguage == lang
                            OutlinedButton(
                                onClick = { selectedLanguage = lang },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) ElectricCyan.copy(alpha = 0.15f) else Color.Transparent
                                )
                            ) {
                                Text(
                                    lang.displayName,
                                    fontSize = 11.sp,
                                    color = if (isSel) ElectricCyan else PureWhite,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Test Sentence:", fontSize = 12.sp, color = MutedBlue)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val phrases = listOf("I need help.", "There is a fire.", "मुझे मदद चाहिए।")
                        phrases.forEach { p ->
                            val isSel = testPhrase == p
                            OutlinedButton(
                                onClick = { testPhrase = p },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) NeonGreen.copy(alpha = 0.15f) else Color.Transparent
                                )
                            ) {
                                Text(
                                    p,
                                    fontSize = 10.sp,
                                    color = if (isSel) NeonGreen else PureWhite,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isProcessingStt = true
                                val res = withContext(Dispatchers.Default) {
                                    controller.runSttOnReferencePhrase(testPhrase, selectedLanguage)
                                }
                                benchmarkTranscription = res
                                isProcessingStt = false
                            }
                        },
                        enabled = !isProcessingStt,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = DarkSlate, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Clean Synthesized Reference Audio", fontSize = 12.sp, color = DarkSlate, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 6. STT Execution & Transcription Output Card (Step 8, 9 & 11)
            Card(
                colors = CardDefaults.cardColors(containerColor = Gunmetal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("6. STT TRANSCRIPTION RESULT", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PureWhite)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isProcessingStt) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ElectricCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Transcribing audio...", fontSize = 12.sp, color = MutedBlue)
                    } else if (benchmarkTranscription != null) {
                        val result = benchmarkTranscription!!
                        DiagnosticSpecRow("Language Tag", result.language.localeTag)
                        DiagnosticSpecRow("Processing Time", "${result.processingTimeMs} ms")
                        DiagnosticSpecRow("Audio Duration", "${result.audioDurationMs} ms")
                        val rtf = if (result.audioDurationMs > 0) result.processingTimeMs.toFloat() / result.audioDurationMs else 0f
                        DiagnosticSpecRow("Real-Time Factor (RTF)", "${"%.2f".format(rtf)}x")
                        DiagnosticSpecRow("Confidence", "${"%.2f".format(result.confidence)}")

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Output Text:", fontSize = 11.sp, color = MutedBlue)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSlate, RoundedCornerShape(8.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = if (result.text.isNotBlank()) result.text else "(No speech recognized)",
                                fontSize = 15.sp,
                                color = if (result.text.isNotBlank()) PureWhite else Color.Yellow,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSlate, RoundedCornerShape(8.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "Run STT using buttons above or hold 'Record Mic' to view live transcription diagnostics.",
                                fontSize = 12.sp,
                                color = MutedBlue
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DiagnosticSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = MutedBlue)
        Text(text = value, fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.Medium)
    }
}
