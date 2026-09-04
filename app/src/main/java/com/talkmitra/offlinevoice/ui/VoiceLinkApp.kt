package com.talkmitra.offlinevoice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.talkmitra.offlinevoice.ui.mock.MockVoiceLinkController
import com.talkmitra.offlinevoice.security.SecurityController
import com.talkmitra.offlinevoice.ui.screens.AboutScreen
import com.talkmitra.offlinevoice.ui.screens.ConnectionScreen
import com.talkmitra.offlinevoice.ui.screens.ConversationScreen
import com.talkmitra.offlinevoice.ui.screens.EmergencyAlertScreen
import com.talkmitra.offlinevoice.ui.screens.EmergencyScreen
import com.talkmitra.offlinevoice.ui.screens.HomeScreen
import com.talkmitra.offlinevoice.ui.screens.LanguageScreen
import com.talkmitra.offlinevoice.ui.screens.OnboardingScreen
import com.talkmitra.offlinevoice.ui.screens.PairingScreen
import com.talkmitra.offlinevoice.ui.screens.SecurityDebugScreen
import com.talkmitra.offlinevoice.ui.screens.SecurityStatusScreen
import com.talkmitra.offlinevoice.ui.screens.SettingsScreen
import com.talkmitra.offlinevoice.ui.screens.SplashScreen
import com.talkmitra.offlinevoice.ui.screens.SttDiagnosticsScreen
import com.talkmitra.offlinevoice.ui.screens.SystemStatusScreen
import com.talkmitra.offlinevoice.ui.screens.TextProcessingDebugScreen
import com.talkmitra.offlinevoice.ui.screens.TranslationDebugScreen
import com.talkmitra.offlinevoice.ui.screens.TTSDebugScreen
import com.talkmitra.offlinevoice.ui.screens.TrustedDevicesScreen

object Route {
    const val Splash = "splash"
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Conversation = "conversation"
    const val Language = "language"
    const val Connection = "connection"
    const val Pairing = "pairing"
    const val Emergency = "emergency"
    const val IncomingEmergency = "incoming_emergency"
    const val Settings = "settings"
    const val SystemStatus = "system_status"
    const val About = "about"
    const val TextProcessingDebug = "text_processing_debug"
    const val SecurityStatus = "security_status"
    const val TrustedDevices = "trusted_devices"
    const val SecurityDebug = "security_debug"
    const val TTSDebug = "tts_debug"
    const val SttDiagnostics = "stt_diagnostics"
    const val TranslationDebug = "translation_debug"
}

@Composable
fun VoiceLinkApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val controller = remember { MockVoiceLinkController(context) }
    val securityController = remember { controller.securityController }
    NavHost(navController = navController, startDestination = Route.Splash) {
        composable(Route.Splash) { SplashScreen { navController.navigate(Route.Onboarding) { popUpTo(Route.Splash) { inclusive = true } } } }
        composable(Route.Onboarding) { OnboardingScreen { navController.navigate(Route.Home) { popUpTo(Route.Onboarding) { inclusive = true } } } }
        composable(Route.Home) { HomeScreen(controller) { navController.navigate(it) } }
        composable(Route.Conversation) { ConversationScreen(controller) { navController.popBackStack() } }
        composable(Route.Language) { LanguageScreen(controller.ui.language, controller::chooseLanguage) { navController.popBackStack() } }
        composable(Route.Connection) { ConnectionScreen(controller, { navController.navigate(it) }) { navController.popBackStack() } }
        composable(Route.Pairing) { PairingScreen(controller) { navController.popBackStack() } }
        composable(Route.Emergency) { EmergencyScreen(controller, { navController.navigate(it) }) { navController.popBackStack() } }
        composable(Route.IncomingEmergency) { EmergencyAlertScreen(controller) { navController.popBackStack() } }
        composable(Route.Settings) { SettingsScreen(controller, { navController.navigate(it) }) { navController.popBackStack() } }
        composable(Route.SystemStatus) { SystemStatusScreen(controller) { navController.popBackStack() } }
        composable(Route.About) { AboutScreen { navController.popBackStack() } }
        composable(Route.TextProcessingDebug) { TextProcessingDebugScreen { navController.popBackStack() } }
        composable(Route.SecurityStatus) { SecurityStatusScreen(securityController, { navController.navigate(it) }) { navController.popBackStack() } }
        composable(Route.TrustedDevices) { TrustedDevicesScreen(securityController) { navController.popBackStack() } }
        composable(Route.SecurityDebug) { SecurityDebugScreen(securityController) { navController.popBackStack() } }
        composable(Route.TTSDebug) { TTSDebugScreen { navController.popBackStack() } }
        composable(Route.SttDiagnostics) { SttDiagnosticsScreen(controller) { navController.popBackStack() } }
        composable(Route.TranslationDebug) { TranslationDebugScreen(controller) { navController.popBackStack() } }
    }
}
