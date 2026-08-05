package com.focusreels.app.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import com.focusreels.app.FocusReelsApplication
import com.focusreels.app.data.preferences.ThemePreferences
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.data.repository.HistoryRepository
import com.focusreels.app.ui.history.HistoryScreen
import com.focusreels.app.ui.onboarding.OnboardingScreen
import com.focusreels.app.ui.settings.SettingsScreen
import com.focusreels.app.ui.theme.FocusReelsTheme
import com.focusreels.app.util.AppIds
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as FocusReelsApplication
        val blockedAppRepository = BlockedAppRepository(app.database)
        val historyRepository = HistoryRepository(app.database)

        lifecycleScope.launch { blockedAppRepository.ensureSeeded() }
        lifecycleScope.launch { ThemePreferences.ensureDarkDefaultOnFirstLaunch(this@MainActivity) }

        setContent {
            val darkModePreference = ThemePreferences.observeDarkMode(this).collectAsState(initial = null).value
            FocusReelsTheme(forceNightMode = darkModePreference) {
                val navController = rememberNavController()
                // Fondu court plutôt que le glissement par défaut (~300ms) de Navigation Compose :
                // entre les onglets de la barre du bas, un changement d'écran doit être perçu
                // comme immédiat, pas comme une transition de page à part entière.
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    enterTransition = { fadeIn(tween(120)) },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(120)) },
                    popExitTransition = { fadeOut(tween(120)) }
                ) {
                    composable("home") {
                        HomeScreen(
                            repository = blockedAppRepository,
                            historyRepository = historyRepository,
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenHistory = { navController.navigate("history") },
                            onOpenOnboarding = { navController.navigate("onboarding") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            packageName = AppIds.INSTAGRAM,
                            repository = blockedAppRepository,
                            onBack = { navController.popBackStack() },
                            onOpenHome = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
                            onOpenHistory = { navController.navigate("history") { popUpTo("home") } }
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            packageName = AppIds.INSTAGRAM,
                            repository = historyRepository,
                            onBack = { navController.popBackStack() },
                            onOpenHome = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
                            onOpenSettings = { navController.navigate("settings") { popUpTo("home") } }
                        )
                    }
                    composable("onboarding") {
                        OnboardingScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
