package com.focusreels.app.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.focusreels.app.FocusReelsApplication
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

        setContent {
            FocusReelsTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            repository = blockedAppRepository,
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenHistory = { navController.navigate("history") },
                            onOpenOnboarding = { navController.navigate("onboarding") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            packageName = AppIds.INSTAGRAM,
                            repository = blockedAppRepository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            packageName = AppIds.INSTAGRAM,
                            repository = historyRepository,
                            onBack = { navController.popBackStack() }
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
