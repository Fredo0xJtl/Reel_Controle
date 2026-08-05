package com.focusreels.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.data.repository.HistoryRepository
import com.focusreels.app.domain.RelockScheduler
import com.focusreels.app.ui.theme.BottomNav
import com.focusreels.app.ui.theme.BrutalistCard
import com.focusreels.app.ui.theme.BrutalistSwitch
import com.focusreels.app.ui.theme.FocusReelsType
import com.focusreels.app.ui.theme.LocalFocusColors
import com.focusreels.app.ui.theme.NavTab
import com.focusreels.app.ui.theme.StripedBar
import com.focusreels.app.ui.unlock.UnlockFrictionActivity
import com.focusreels.app.util.AccessibilityChecker
import com.focusreels.app.util.AppIds
import kotlinx.coroutines.launch

/**
 * Écran principal (§5), style brutaliste (handoff Claude Design, `.docs/design_handoff`).
 * V1 : Instagram uniquement, mais la liste est déjà pilotée par [BlockedAppRepository.observeAll]
 * pour permettre l'ajout futur d'autres applications sans refonte (§3.5, §7).
 *
 * Décocher déclenche l'écran de friction progressive (§3.3) plutôt qu'une désactivation immédiate.
 * Recocher (réactivation manuelle du blocage) reste immédiat.
 */
@Composable
fun HomeScreen(
    repository: BlockedAppRepository,
    historyRepository: HistoryRepository,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalFocusColors.current
    val scope = rememberCoroutineScope()

    // remember : sans clé, `observeAll()` recrée un Flow (nouvelle souscription Room) à chaque
    // recomposition de l'écran.
    val appsFlow = remember { repository.observeAll() }
    val apps by appsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val countTodayFlow = remember { historyRepository.observeCountToday(AppIds.INSTAGRAM) }
    val countToday by countTodayFlow.collectAsStateWithLifecycle(initialValue = 0)

    // Un switch "activé" en base ne garantit rien si le service d'accessibilité a été coupé
    // par l'utilisateur ou tué par le système (MIUI/OneUI) : sans ce contrôle, l'app afficherait
    // un blocage actif qui ne bloque plus rien. Revérifié à chaque retour au premier plan.
    var accessibilityServiceEnabled by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        accessibilityServiceEnabled = AccessibilityChecker.isServiceEnabled(context)
        onPauseOrDispose { }
    }

    val versionLabel = remember {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "v${info.versionName}"
    }

    val instagram = apps.firstOrNull { it.packageName == AppIds.INSTAGRAM }
    val blockingActive = instagram?.blockingEnabled == true
    val blockingIneffective = blockingActive && !accessibilityServiceEnabled

    // "Jours sans déblocage" : calculé depuis lastUnlockAtMillis (jamais débloqué = pas de valeur
    // à afficher, on montre 0 plutôt qu'un chiffre inventé).
    val streakDays = instagram?.lastUnlockAtMillis?.let { lastUnlock ->
        val elapsedMillis = System.currentTimeMillis() - lastUnlock
        (elapsedMillis / (24 * 3600 * 1000L)).toInt().coerceAtLeast(0)
    } ?: 0

    Scaffold(containerColor = colors.bg) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp, 20.dp, 20.dp, 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("Réel Contrôle", style = FocusReelsType.screenTitle, color = colors.text)
                Text(versionLabel, style = FocusReelsType.mono, color = colors.sub)
            }

            // Bannière d'alerte : visible seulement si le blocage est censé être actif mais ne
            // l'est plus réellement (service d'accessibilité coupé par le système ou l'utilisateur).
            if (blockingIneffective) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp, 20.dp, 20.dp, 0.dp)) {
                    StripedBar(modifier = Modifier.height(5.dp), accent = colors.accent, animated = false)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0A0A))
                            .clickable(onClick = onOpenOnboarding)
                            .padding(14.dp, 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠", color = colors.accent, modifier = Modifier.padding(end = 10.dp))
                        Text(
                            "ALERTE — REELS ACCESSIBLE, BLOCAGE INACTIF",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Carte Statut : inversée (fond = texte, texte = surface) quand le blocage est ACTIF.
            BrutalistCard(
                modifier = Modifier.fillMaxWidth().padding(20.dp, 22.dp, 20.dp, 0.dp),
                backgroundColor = if (blockingActive) colors.text else colors.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusTextColor = if (blockingActive) colors.surface else colors.text
                    Column {
                        Text(
                            "STATUT",
                            style = FocusReelsType.sectionLabel,
                            color = statusTextColor.copy(alpha = 0.7f)
                        )
                        Text(
                            if (blockingActive) "BLOCAGE ACTIF" else "BLOCAGE INACTIF",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusTextColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    BrutalistSwitch(
                        checked = blockingActive,
                        onCheckedChange = { enabled ->
                            val app = instagram ?: return@BrutalistSwitch
                            if (enabled) {
                                scope.launch {
                                    repository.save(app.copy(blockingEnabled = true))
                                    RelockScheduler.cancelRelock(context, app.packageName)
                                }
                            } else {
                                UnlockFrictionActivity.start(context, app.packageName)
                            }
                        },
                        trackColor = if (blockingActive) colors.surface else colors.text,
                        knobColor = if (blockingActive) colors.text else colors.surface,
                        borderColor = statusTextColor
                    )
                }
            }

            // Carte Stats : 2 colonnes séparées par un filet vertical.
            BrutalistCard(modifier = Modifier.fillMaxWidth().padding(20.dp, 22.dp, 20.dp, 0.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    StatColumn(
                        modifier = Modifier.weight(1f),
                        value = countToday.toString(),
                        label = "tentatives bloquées\naujourd'hui"
                    )
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(56.dp)
                            .background(colors.border)
                    )
                    StatColumn(
                        modifier = Modifier.weight(1f).padding(start = 24.dp),
                        value = streakDays.toString(),
                        label = "jours sans\ndéblocage"
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(2.dp)
                    .background(colors.border)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp, 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Instagram — Reels", color = colors.text, fontSize = 15.sp)
                Text(
                    if (blockingActive) "bloqué" else "débloqué",
                    style = FocusReelsType.mono,
                    color = colors.sub
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.border)
            )

            Box(modifier = Modifier.weight(1f))

            BottomNav(
                current = NavTab.HOME,
                onSelect = { tab ->
                    when (tab) {
                        NavTab.HOME -> {}
                        NavTab.HISTORY -> onOpenHistory()
                        NavTab.SETTINGS -> onOpenSettings()
                    }
                }
            )
        }
    }
}

@Composable
private fun StatColumn(modifier: Modifier = Modifier, value: String, label: String) {
    val colors = LocalFocusColors.current
    Column(modifier = modifier) {
        Text(value, style = FocusReelsType.statNumber, color = colors.text)
        Text(
            label,
            fontSize = 12.sp,
            color = colors.sub,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
