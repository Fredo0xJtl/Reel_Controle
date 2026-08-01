package com.focusreels.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.domain.RelockScheduler
import com.focusreels.app.ui.unlock.UnlockFrictionActivity
import com.focusreels.app.util.AccessibilityChecker
import kotlinx.coroutines.launch

/**
 * Écran principal (§5) : liste des applications gérées avec interrupteur de blocage.
 * V1 : Instagram uniquement, mais la liste est déjà pilotée par [BlockedAppRepository.observeAll]
 * pour permettre l'ajout futur d'autres applications sans refonte (§3.5, §7).
 *
 * Décocher déclenche l'écran de friction progressive (§3.3) plutôt qu'une désactivation immédiate.
 * Recocher (réactivation manuelle du blocage) reste immédiat.
 */
@Composable
fun HomeScreen(
    repository: BlockedAppRepository,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by repository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var showAccessibilityWarning by remember { mutableStateOf(!AccessibilityChecker.isServiceEnabled(context)) }

    if (showAccessibilityWarning) {
        AlertDialog(
            onDismissRequest = { showAccessibilityWarning = false },
            title = { Text("Service d'accessibilité inactif") },
            text = { Text("Le blocage Reels ne fonctionne pas sans le service d'accessibilité. Activez-le dans Configuration système.") },
            confirmButton = {
                Button(onClick = {
                    showAccessibilityWarning = false
                    onOpenOnboarding()
                }) { Text("Aller à la config") }
            },
            dismissButton = {
                Button(onClick = { showAccessibilityWarning = false }) { Text("Plus tard") }
            }
        )
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Focus Reels")

            apps.forEach { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(app.displayName)
                    Switch(
                        checked = app.blockingEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                scope.launch {
                                    repository.save(app.copy(blockingEnabled = true))
                                    RelockScheduler.cancelRelock(context, app.packageName)
                                }
                            } else {
                                UnlockFrictionActivity.start(context, app.packageName)
                            }
                        }
                    )
                }
            }

            Button(onClick = onOpenSettings) { Text("Réglages") }
            Button(onClick = onOpenHistory) { Text("Historique") }
            Button(onClick = onOpenOnboarding) { Text("Configuration système (accessibilité / batterie)") }
        }
    }
}
