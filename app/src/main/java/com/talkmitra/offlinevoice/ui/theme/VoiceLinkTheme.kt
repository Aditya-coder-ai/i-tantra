package com.talkmitra.offlinevoice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF102A43)
val Blue = Color(0xFF1267E8)
val Sky = Color(0xFFEAF3FF)
val Ink = Color(0xFF102033)
val Slate = Color(0xFF62738A)
val Line = Color(0xFFDDE5EF)
val Success = Color(0xFF0B7A53)
val Warning = Color(0xFFAA6400)
val Emergency = Color(0xFFB42318)
val EmergencySurface = Color(0xFFFFE9E7)

private val LightColors = lightColorScheme(
    primary = Blue, onPrimary = Color.White, primaryContainer = Sky, onPrimaryContainer = Navy,
    secondary = Navy, background = Color(0xFFF8FAFD), onBackground = Ink,
    surface = Color.White, onSurface = Ink, outline = Line, error = Emergency, onError = Color.White
)

@Composable
fun VoiceLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = VoiceLinkTypography, content = content)
}
