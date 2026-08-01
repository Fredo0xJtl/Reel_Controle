package com.focusreels.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusreels.app.data.repository.BlockedAppRepository
import kotlinx.coroutines.launch

/**
 * Réglages du système de friction progressive, du reverrouillage automatique et des swipes
 * tolérés après un Reels reçu en DM (§3.3, §3.4, §3.2, §3.5).
 */
@Composable
fun SettingsScreen(
    packageName: String,
    repository: BlockedAppRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val app by repository.observe(packageName).collectAsStateWithLifecycle(initialValue = null)

    var baseDelay by remember { mutableStateOf("") }
    var increment by remember { mutableStateOf("") }
    var relockMinutes by remember { mutableStateOf("") }
    var toleratedSwipes by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    if (app != null && !initialized) {
        baseDelay = app!!.baseDelaySeconds.toString()
        increment = app!!.incrementSeconds.toString()
        relockMinutes = app!!.relockDelayMinutes.toString()
        toleratedSwipes = app!!.toleratedSwipesAfterDm.toString()
        initialized = true
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Réglages — friction et reverrouillage")

            OutlinedTextField(
                value = baseDelay,
                onValueChange = { baseDelay = it },
                label = { Text("Délai de base (secondes)") }
            )
            OutlinedTextField(
                value = increment,
                onValueChange = { increment = it },
                label = { Text("Incrément par déblocage (secondes)") }
            )
            OutlinedTextField(
                value = relockMinutes,
                onValueChange = { relockMinutes = it },
                label = { Text("Reverrouillage automatique (minutes)") }
            )
            OutlinedTextField(
                value = toleratedSwipes,
                onValueChange = { toleratedSwipes = it },
                label = { Text("Swipes tolérés après Reels reçu en DM") }
            )

            Button(onClick = {
                val current = app ?: return@Button
                scope.launch {
                    repository.save(
                        current.copy(
                            baseDelaySeconds = baseDelay.toIntOrNull() ?: current.baseDelaySeconds,
                            incrementSeconds = increment.toIntOrNull() ?: current.incrementSeconds,
                            relockDelayMinutes = relockMinutes.toIntOrNull() ?: current.relockDelayMinutes,
                            toleratedSwipesAfterDm = toleratedSwipes.toIntOrNull() ?: current.toleratedSwipesAfterDm
                        )
                    )
                    onBack()
                }
            }) { Text("Enregistrer") }

            Button(onClick = onBack) { Text("Retour") }
        }
    }
}
