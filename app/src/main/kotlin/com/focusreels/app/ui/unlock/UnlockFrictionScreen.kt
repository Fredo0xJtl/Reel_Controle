package com.focusreels.app.ui.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.domain.FrictionCalculator
import kotlinx.coroutines.delay

/**
 * Minuteur visuel simple avant déblocage effectif (§3.3). Aucun contenu additionnel imposé.
 */
@Composable
fun UnlockFrictionScreen(
    packageName: String,
    repository: BlockedAppRepository,
    onCancel: () -> Unit,
    onUnlocked: (relockDelayMinutes: Int) -> Unit
) {
    var totalDelaySeconds by remember { mutableIntStateOf(-1) }
    var remainingSeconds by remember { mutableIntStateOf(-1) }
    var relockDelayMinutes by remember { mutableStateOf(30) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(packageName) {
        val entity = repository.get(packageName) ?: run { onCancel(); return@LaunchedEffect }

        val today = FrictionCalculator.todayString()
        val attemptsToday = FrictionCalculator.attemptsForToday(entity.unlockAttemptsToday, entity.unlockAttemptsDate, today)
        val delaySeconds = FrictionCalculator.delayForAttempt(entity.baseDelaySeconds, entity.incrementSeconds, attemptsToday)

        totalDelaySeconds = delaySeconds
        remainingSeconds = delaySeconds
        relockDelayMinutes = entity.relockDelayMinutes
        loaded = true

        // Persiste immédiatement l'incrément de tentative, pour que la friction progresse
        // même si l'utilisateur ferme l'app avant la fin du compte à rebours.
        repository.save(
            entity.copy(
                unlockAttemptsToday = attemptsToday + 1,
                unlockAttemptsDate = today
            )
        )

        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
        }

        val current = repository.get(packageName) ?: return@LaunchedEffect
        repository.save(
            current.copy(
                blockingEnabled = false,
                lastUnlockAtMillis = System.currentTimeMillis()
            )
        )
        onUnlocked(relockDelayMinutes)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!loaded) {
                Text("Chargement…")
            } else {
                Text("Déblocage dans $remainingSeconds s")
                Button(onClick = onCancel) { Text("Annuler") }
            }
        }
    }
}
