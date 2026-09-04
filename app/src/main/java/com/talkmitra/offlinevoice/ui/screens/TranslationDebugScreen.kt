package com.talkmitra.offlinevoice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talkmitra.offlinevoice.translation.OfflineTranslationEngine
import com.talkmitra.offlinevoice.translation.SupportedLanguage
import com.talkmitra.offlinevoice.translation.TranslationPath
import com.talkmitra.offlinevoice.translation.TranslationResult
import com.talkmitra.offlinevoice.ui.components.ScreenPadding
import com.talkmitra.offlinevoice.ui.components.VoiceLinkTopBar
import com.talkmitra.offlinevoice.ui.mock.MockVoiceLinkController
import com.talkmitra.offlinevoice.ui.theme.Blue
import com.talkmitra.offlinevoice.ui.theme.Line
import com.talkmitra.offlinevoice.ui.theme.Success
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslationDebugScreen(controller: MockVoiceLinkController, onBack: () -> Unit) {
    val engine = controller.translationEngine
    val tts = controller.ttsEngine
    val scope = rememberCoroutineScope()

    var sourceLang by remember { mutableStateOf(SupportedLanguage.ENGLISH) }
    var targetLang by remember { mutableStateOf(SupportedLanguage.HINDI) }
    var inputText by remember { mutableStateOf("I need help. There is a fire.") }
    var result by remember { mutableStateOf<TranslationResult?>(null) }
    var isTranslating by remember { mutableStateOf(false) }

    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var targetMenuExpanded by remember { mutableStateOf(false) }

    val presets = when (sourceLang) {
        SupportedLanguage.HINDI -> listOf(
            "मुझे मदद चाहिए। वहाँ आग लगी है।",
            "आग लगी है! मुझे मदद चाहिए!",
            "मैं सुरक्षित हूँ।",
            "हमें चिकित्सा सहायता की आवश्यकता है।",
            "आप कहाँ हैं?",
            "तुरंत मदद भेजें।",
            "पानी और भोजन की आवश्यकता है।",
            "नमस्ते",
            "आप कैसे हैं?",
            "सब ठीक है।"
        )
        SupportedLanguage.GUJARATI -> listOf(
            "મને મદદની જરૂર છે. ત્યાં આગ લાગી છે.",
            "હું સુરક્ષિત છું.",
            "અમને તબીબી સહાયની જરૂર છે.",
            "તમે ક્યાં છો?",
            "નમસ્તે"
        )
        SupportedLanguage.MARATHI -> listOf(
            "मला मदतीची गरज आहे. तिथे आग लागली आहे.",
            "मी सुरक्षित आहे.",
            "आम्हाला वैद्यकीय मदतीची गरज आहे.",
            "तुम्ही कुठे आहात?",
            "नमस्ते"
        )
        SupportedLanguage.TAMIL -> listOf(
            "எனக்கு உதவி தேவை. அங்கு தீ பிடித்துள்ளது.",
            "நான் பாதுகாப்பாக இருக்கிறேன்.",
            "எங்களுக்கு மருத்துவ உதவி தேவை.",
            "நீங்கள் எங்கே இருக்கிறீர்கள்?",
            "வணக்கம்"
        )
        else -> listOf(
            "I need help. There is a fire.",
            "Fire! I need help!",
            "I am safe.",
            "We need medical assistance.",
            "Where are you?",
            "Send help immediately.",
            "Water and food required.",
            "Hello",
            "How are you?",
            "All clear."
        )
    }

    Scaffold(
        topBar = { VoiceLinkTopBar("Offline Translation Workbench", onBack) }
    ) { inset ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "On-Device Multilingual Translation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Blue
                )
                Text(
                    "Each selected language model downloads once, then translates on your device. Built-in emergency phrases remain available offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Language Selector Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("LANGUAGE PAIR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Source Language Button
                            Box {
                                OutlinedButton(
                                    onClick = { sourceMenuExpanded = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("${sourceLang.displayName} (${sourceLang.code})")
                                }
                                DropdownMenu(
                                    expanded = sourceMenuExpanded,
                                    onDismissRequest = { sourceMenuExpanded = false }
                                ) {
                                    SupportedLanguage.entries.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text("${lang.displayName} · ${lang.nativeName}") },
                                            onClick = {
                                                sourceLang = lang
                                                sourceMenuExpanded = false
                                                result = null
                                            }
                                        )
                                    }
                                }
                            }

                            // Swap Languages Button
                            IconButton(onClick = {
                                val prevTarget = targetLang
                                val prevSource = sourceLang
                                sourceLang = prevTarget
                                targetLang = prevSource
                                if (result != null && result?.isTranslationRequired == true) {
                                    inputText = result!!.translatedText
                                }
                                result = null
                            }) {
                                Icon(Icons.Default.SwapHoriz, "Swap", tint = Blue)
                            }

                            // Target Language Button
                            Box {
                                OutlinedButton(
                                    onClick = { targetMenuExpanded = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("${targetLang.displayName} (${targetLang.code})")
                                }
                                DropdownMenu(
                                    expanded = targetMenuExpanded,
                                    onDismissRequest = { targetMenuExpanded = false }
                                ) {
                                    SupportedLanguage.entries.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text("${lang.displayName} · ${lang.nativeName}") },
                                            onClick = {
                                                targetLang = lang
                                                targetMenuExpanded = false
                                                result = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Input Text Field
            item {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Input Text (${sourceLang.displayName} · ${sourceLang.nativeName})") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2
                )
            }

            // Quick Preset Chips
            item {
                Column {
                    Text("Quick Presets (${sourceLang.displayName}):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .clickable { inputText = preset }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Line, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(preset, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Translate Action Button
            item {
                Button(
                    onClick = {
                        scope.launch {
                            isTranslating = true
                            result = engine.translate(
                                text = inputText,
                                sourceLanguage = sourceLang,
                                targetLanguage = targetLang
                            )
                            isTranslating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) {
                    Icon(Icons.Default.Translate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTranslating) "TRANSLATING..." else "TRANSLATE WITH GOOGLE", fontWeight = FontWeight.Bold)
                }
            }

            // Translation Output Card
            result?.let { res ->
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Translate, null, tint = Blue, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${res.originalLanguage.code.uppercase()} ➔ ${res.targetLanguage.code.uppercase()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Blue
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .background(Success.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                    Text(
                                        "⚡ ${res.translationTimeMs} ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Success,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                "“${res.translatedText}”",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Translation powered by Google Translate",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (res.isTranslationRequired && res.originalText != res.translatedText) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Original: “${res.originalText}”",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (res.intermediateText != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Pivot (English): “${res.intermediateText}”",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Path: ${res.translationPath.displayName} · ${res.modelName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = { tts.speak(res.translatedText, res.targetLanguage.code) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Hear ${res.targetLanguage.displayName}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // Translation Telemetry Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, null, tint = Blue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("TRANSLATION ENGINE TELEMETRY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Total Inferences", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${engine.metrics.totalTranslations}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Average Latency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${engine.metrics.averageLatencyMs} ms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Success)
                            }
                            Column {
                                Text("Models in RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${engine.modelManager.getLoadedModelsList().size} active", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Memory Footprint: ~${engine.modelManager.getTotalEstimatedMemoryBytes() / (1024 * 1024)} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
