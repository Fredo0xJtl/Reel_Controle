package com.focusreels.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusreels.app.data.preferences.ThemePreferences
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.util.Defaults
import com.focusreels.app.ui.theme.BottomNav
import com.focusreels.app.ui.theme.BrutalistStepper
import com.focusreels.app.ui.theme.FocusReelsType
import com.focusreels.app.ui.theme.LocalFocusColors
import com.focusreels.app.ui.theme.NavTab
import kotlinx.coroutines.launch

private enum class ThemeChoice(val label: String) {
    LIGHT("CLAIR"), DARK("SOMBRE"), SYSTEM("SYSTÈME")
}

/**
 * Réglages du système de friction progressive, du reverrouillage automatique et des swipes
 * tolérés après un Reels reçu en DM (§3.3, §3.4, §3.2, §3.5). Style brutaliste (handoff Claude
 * Design) : sections en steppers, sélecteur de thème 3 boutons.
 */
@Composable
fun SettingsScreen(
    packageName: String,
    repository: BlockedAppRepository,
    onBack: () -> Unit,
    onOpenHome: () -> Unit = onBack,
    onOpenHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalFocusColors.current
    val scope = rememberCoroutineScope()

    val appFlow = remember(packageName) { repository.observe(packageName) }
    val app by appFlow.collectAsStateWithLifecycle(initialValue = null)

    // null = suivre le thème système ; true/false = préférence explicite de l'utilisateur.
    val darkModePreferenceFlow = remember { ThemePreferences.observeDarkMode(context) }
    val darkModePreference by darkModePreferenceFlow.collectAsStateWithLifecycle(initialValue = null)
    val themeChoice = when (darkModePreference) {
        true -> ThemeChoice.DARK
        false -> ThemeChoice.LIGHT
        null -> ThemeChoice.SYSTEM
    }

    // États locaux éditables, initialisés depuis Room une fois l'entité chargée (§ pattern
    // LaunchedEffect existant : écrire un état pendant la composition est un anti-pattern Compose).
    var baseDelay by remember { mutableStateOf(5) }
    var increment by remember { mutableStateOf(5) }
    var relockMinutes by remember { mutableStateOf(30) }
    var toleratedSwipes by remember { mutableStateOf(1) }
    var initialized by remember { mutableStateOf(false) }
    var resetSignal by remember { mutableStateOf(0) }

    LaunchedEffect(app) {
        val current = app ?: return@LaunchedEffect
        if (!initialized) {
            baseDelay = current.baseDelaySeconds
            increment = current.incrementSeconds
            relockMinutes = current.relockDelayMinutes
            toleratedSwipes = current.toleratedSwipesAfterDm
            initialized = true
        }
    }

    // Persistance immédiate à chaque changement de stepper (pas de bouton "Enregistrer" dans le
    // handoff — chaque +/- doit prendre effet tout de suite).
    fun persist(newBase: Int = baseDelay, newIncrement: Int = increment, newRelock: Int = relockMinutes, newSwipes: Int = toleratedSwipes) {
        val current = app ?: return
        scope.launch {
            repository.save(
                current.copy(
                    baseDelaySeconds = newBase,
                    incrementSeconds = newIncrement,
                    relockDelayMinutes = newRelock,
                    toleratedSwipesAfterDm = newSwipes
                )
            )
        }
    }

    // Filet de sécurité pour une saisie ratée au clavier (suppression accidentelle du chiffre,
    // fausse manip) : un bouton explicite plutôt que de compter sur le comportement silencieux
    // du stepper (un champ vide/invalide ne change rien à la validation, mais rien ne signale
    // à l'utilisateur que rien n'a changé).
    fun resetToDefaults() {
        baseDelay = Defaults.BASE_DELAY_SECONDS
        increment = Defaults.INCREMENT_SECONDS
        relockMinutes = Defaults.RELOCK_DELAY_MINUTES
        toleratedSwipes = Defaults.TOLERATED_SWIPES_AFTER_DM
        persist(newBase = baseDelay, newIncrement = increment, newRelock = relockMinutes, newSwipes = toleratedSwipes)
        resetSignal++
    }

    Scaffold(containerColor = colors.bg) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "RÉGLAGES",
                style = FocusReelsType.screenTitle,
                color = colors.text,
                modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 16.dp)
            )

            Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
                SectionLabel("FRICTION PROGRESSIVE")
                Divider(2.dp)
                BrutalistStepper(
                    label = "Délai de base",
                    value = baseDelay,
                    unit = "s",
                    min = 1,
                    max = 60,
                    onValueChange = { baseDelay = it; persist(newBase = it) },
                    resetSignal = resetSignal
                )
                Divider(1.dp)
                BrutalistStepper(
                    label = "Incrément par tentative",
                    value = increment,
                    unit = "s",
                    min = 0,
                    max = 60,
                    onValueChange = { increment = it; persist(newIncrement = it) },
                    resetSignal = resetSignal
                )
                Divider(2.dp)

                SectionLabel("REVERROUILLAGE")
                Divider(2.dp)
                BrutalistStepper(
                    label = "Reverrouillage automatique",
                    value = relockMinutes,
                    unit = "m",
                    min = 5,
                    max = 240,
                    step = 5,
                    onValueChange = { relockMinutes = it; persist(newRelock = it) },
                    resetSignal = resetSignal
                )
                Divider(2.dp)

                SectionLabel("TOLÉRANCE DM")
                Divider(2.dp)
                BrutalistStepper(
                    label = "Swipes tolérés après DM",
                    value = toleratedSwipes,
                    unit = "",
                    min = 0,
                    max = 20,
                    onValueChange = { toleratedSwipes = it; persist(newSwipes = it) },
                    resetSignal = resetSignal
                )
                Divider(2.dp)

                SectionLabel("APPARENCE")
                Divider(2.dp)
                Row(modifier = Modifier.fillMaxWidth()) {
                    ThemeChoice.entries.forEachIndexed { index, choice ->
                        val selected = choice == themeChoice
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (selected) colors.text else colors.surface)
                                .clickable {
                                    scope.launch {
                                        when (choice) {
                                            ThemeChoice.LIGHT -> ThemePreferences.setDarkMode(context, false)
                                            ThemeChoice.DARK -> ThemePreferences.setDarkMode(context, true)
                                            ThemeChoice.SYSTEM -> ThemePreferences.clearDarkMode(context)
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                choice.label,
                                color = if (selected) colors.surface else colors.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Divider(2.dp)

                // Sans ces raccourcis, l'utilisateur doit retrouver seul les écrans système
                // concernés (Réglages > Accessibilité, Réglages > Applications > batterie) —
                // c'était pourtant prévu dès le cahier des charges (§4.3) et présent sur
                // l'ancien écran d'accueil avant la refonte brutaliste. Restauré ici, dans une
                // section "SYSTÈME" plutôt que perdu.
                SectionLabel("SYSTÈME")
                Divider(2.dp)
                SystemLinkRow(
                    label = "Service d'accessibilité",
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
                Divider(1.dp)
                SystemLinkRow(
                    label = "Optimisation de batterie",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:" + context.packageName)
                            }
                        )
                    }
                )
                Divider(2.dp)

                ResetDefaultsButton(onClick = { resetToDefaults() })
            }

            BottomNav(
                current = NavTab.SETTINGS,
                onSelect = { tab ->
                    when (tab) {
                        NavTab.HOME -> onOpenHome()
                        NavTab.HISTORY -> onOpenHistory()
                        NavTab.SETTINGS -> {}
                    }
                }
            )
        }
    }
}

/**
 * Filet de sécurité demandé après une fausse manip au clavier numérique (suppression accidentelle
 * d'un chiffre dans un stepper) : remet les 4 réglages de friction/reverrouillage/tolérance à leurs
 * valeurs d'usine (§ util.Constants.Defaults) en un tap, sans confirmation supplémentaire — le
 * risque est faible (aucune donnée n'est perdue, juste des préférences) et la friction d'un dialog
 * de confirmation irait à l'encontre de l'objectif "filet de sécurité rapide".
 */
@Composable
private fun ResetDefaultsButton(onClick: () -> Unit) {
    val colors = LocalFocusColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 24.dp)
            .border(2.dp, colors.text)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "RÉINITIALISER AUX VALEURS PAR DÉFAUT",
            color = colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SystemLinkRow(label: String, onClick: () -> Unit) {
    val colors = LocalFocusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = colors.text, fontSize = 14.sp)
        Text("→", color = colors.sub, fontSize = 14.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalFocusColors.current
    Text(
        text,
        style = FocusReelsType.sectionLabel,
        color = colors.sub,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun Divider(thickness: androidx.compose.ui.unit.Dp) {
    val colors = LocalFocusColors.current
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(thickness)
            .background(colors.border)
    )
}
