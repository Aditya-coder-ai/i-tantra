package com.itantra.offlinevoice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itantra.offlinevoice.ui.mock.MockVoiceLinkController
import com.itantra.offlinevoice.ui.screens.AboutScreen
import com.itantra.offlinevoice.ui.screens.ConnectionScreen
import com.itantra.offlinevoice.ui.screens.ConversationScreen
import com.itantra.offlinevoice.ui.screens.EmergencyAlertScreen
import com.itantra.offlinevoice.ui.screens.EmergencyScreen
import com.itantra.offlinevoice.ui.screens.HomeScreen
import com.itantra.offlinevoice.ui.screens.LanguageScreen
import com.itantra.offlinevoice.ui.screens.OnboardingScreen
import com.itantra.offlinevoice.ui.screens.PairingScreen
import com.itantra.offlinevoice.ui.screens.SettingsScreen
import com.itantra.offlinevoice.ui.screens.SplashScreen
import com.itantra.offlinevoice.ui.screens.SystemStatusScreen

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
}

@Composable
fun VoiceLinkApp() {
    val navController = rememberNavController()
    val controller = remember { MockVoiceLinkController() }
    NavHost(navController = navController, startDestination = Route.Splash) {
        composable(Route.Splash) { SplashScreen { navController.navigate(Route.Onboarding) { popUpTo(Route.Splash) { inclusive = true } } } }
        composable(Route.Onboarding) { OnboardingScreen { navController.navigate(Route.Home) { popUpTo(Route.Onboarding) { inclusive = true } } } }
        composable(Route.Home) { HomeScreen(controller) { navController.navigate(it) } }
        composable(Route.Conversation) { ConversationScreen(controller.ui) { navController.popBackStack() } }
        composable(Route.Language) { LanguageScreen(controller.ui.language, controller::chooseLanguage) { navController.popBackStack() } }
        composable(Route.Connection) { ConnectionScreen(controller, { navController.navigate(it) }) { navController.popBackStack() } }
        composable(Route.Pairing) { PairingScreen(controller.ui, controller::connect) { navController.popBackStack() } }
        composable(Route.Emergency) { EmergencyScreen({ navController.navigate(it) }) { navController.popBackStack() } }
        composable(Route.IncomingEmergency) { EmergencyAlertScreen { navController.popBackStack() } }
        composable(Route.Settings) { SettingsScreen(controller, { navController.navigate(it) }) { navController.popBackStack() } }
        composable(Route.SystemStatus) { SystemStatusScreen { navController.popBackStack() } }
        composable(Route.About) { AboutScreen { navController.popBackStack() } }
    }
}
