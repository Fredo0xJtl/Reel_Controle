package com.focusreels.app.ui.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.data.repository.HistoryRepository
import com.focusreels.app.domain.FrictionCalculator
import com.focusreels.app.ui.theme.FocusReelsType
import com.focusreels.app.ui.theme.FrictionColors
import kotlinx.coroutines.delay

// Phrases dissuasives affichées sous "Reels bloqué." — une différente à chaque tentative pour
// éviter l'effet "bannière ignorée" d'un texte statique répété à l'identique à chaque écran.
private val DISSUASIVE_MESSAGES = listOf(
    "La friction augmente à chaque nouvelle tentative. Réinitialisation à minuit.",
    "Ce que tu cherchais sur Reels sera encore là dans 5 minutes.",
    "Chaque déblocage rend le prochain plus long.",
    "Respire. Ce n'est pas urgent.",
    "Tu avais prévu de faire autre chose, non ?",
    "Reels n'ira nulle part. Toi, si tu veux.",
    "Ce compte à rebours, c'est toi qui l'as réglé — pour de bonnes raisons.",
    "Encore une tentative, encore quelques secondes de vie ailleurs.",
    "Le scroll infini attendra. Ton temps, non.",
    "Une petite pause dans le défilement ne fait pas de mal.",
    "Tu peux fermer l'appli à la place.",
    "Ce blocage existe parce qu'un jour, tu l'as voulu."
)

/**
 * Écran de friction/déblocage (§3.3) — le plus brutaliste du handoff : toujours en contraste dur
 * (fond quasi-noir), quel que soit le thème clair/sombre choisi ailleurs dans l'app.
 */
@Composable
fun UnlockFrictionScreen(
    packageName: String,
    repository: BlockedAppRepository,
    historyRepository: HistoryRepository,
    onCancel: () -> Unit,
    onUnlocked: (relockDelayMinutes: Int) -> Unit
) {
    val colors = FrictionColors

    var totalDelaySeconds by remember { mutableIntStateOf(-1) }
    var remainingSeconds by remember { mutableIntStateOf(-1) }
    var attemptNumber by remember { mutableIntStateOf(1) }
    var relockDelayMinutes by remember { mutableStateOf(30) }
    var loaded by remember { mutableStateOf(false) }
    // Série en jours sans déblocage : rappelée ici, pas seulement sur l'accueil, car c'est
    // précisément au moment de la tentative de déblocage qu'elle a le plus de poids ("qu'est-ce
    // que je m'apprête à casser ?"), pas quand on ne consulte jamais l'app.
    var streakDays by remember { mutableIntStateOf(0) }

    // Nombre de fois où Reels a été bloqué aujourd'hui avant ce déblocage — donne une mesure
    // concrète de la sollicitation du jour, pas seulement "tentative n° X" (qui ne compte que
    // les déblocages, pas les blocages).
    val blocksTodayCount by historyRepository.observeCountToday(packageName).collectAsStateWithLifecycle(initialValue = 0)

    LaunchedEffect(packageName) {
        val entity = repository.get(packageName) ?: run { onCancel(); return@LaunchedEffect }

        val today = FrictionCalculator.todayString()
        val attemptsToday = FrictionCalculator.attemptsForToday(entity.unlockAttemptsToday, entity.unlockAttemptsDate, today)
        val delaySeconds = FrictionCalculator.delayForAttempt(entity.baseDelaySeconds, entity.incrementSeconds, attemptsToday)

        totalDelaySeconds = delaySeconds
        remainingSeconds = delaySeconds
        attemptNumber = attemptsToday + 1
        relockDelayMinutes = entity.relockDelayMinutes
        streakDays = entity.lastUnlockAtMillis?.let { lastUnlock ->
            ((System.currentTimeMillis() - lastUnlock) / (24 * 3600 * 1000L)).toInt().coerceAtLeast(0)
        } ?: 0
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

    val progress = if (totalDelaySeconds > 0) {
        (totalDelaySeconds - remainingSeconds).toFloat() / totalDelaySeconds
    } else 0f

    Scaffold(containerColor = colors.bg) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(colors.bg)) {
            if (loaded) {
                // Barre pleine plutôt que rayée : le motif oblique, pensé pour une bande large,
                // se réduisait à un petit triangle tronqué en tout début de compte à rebours
                // (barre encore très étroite) — un artefact visuel plutôt qu'un effet voulu.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(7.dp)
                        .background(colors.accent)
                )
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!loaded) {
                    Text("CHARGEMENT…", style = FocusReelsType.monoLabel, color = colors.sub)
                } else {
                    Text(
                        "TENTATIVE $attemptNumber AUJOURD'HUI",
                        style = FocusReelsType.monoLabel,
                        color = colors.sub
                    )
                    // Un seul Text plutôt qu'un Row nombre + "s" : un Row centré comme bloc reste
                    // décalé visuellement à gauche, car le "s" n'ajoute de la largeur qu'à droite
                    // (le centre géométrique du bloc n'est pas le centre visuel des chiffres). Un
                    // texte unique avec textAlign = Center sur toute la largeur d'écran verrouille
                    // le centrage quel que soit le nombre de chiffres affichés (1, 2 ou 3).
                    Text(
                        text = androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(FocusReelsType.countdown.toSpanStyle()) {
                                append(remainingSeconds.toString())
                            }
                            withStyle(
                                androidx.compose.ui.text.SpanStyle(
                                    fontSize = 36.sp,
                                    baselineShift = androidx.compose.ui.text.style.BaselineShift(-0.15f)
                                )
                            ) {
                                append("s")
                            }
                        },
                        color = colors.text,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 26.dp, bottom = 20.dp)
                            .width(64.dp)
                            .height(4.dp)
                            .background(colors.accent)
                    )
                    Text(
                        "Reels bloqué.",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.text
                    )
                    Text(
                        // Modulo sur le n° de tentative plutôt qu'un tirage aléatoire : la phrase
                        // reste stable pendant tout le compte à rebours (pas de recomposition
                        // avec un nouveau texte à chaque seconde), mais change d'une tentative à
                        // l'autre.
                        DISSUASIVE_MESSAGES[(attemptNumber - 1).mod(DISSUASIVE_MESSAGES.size)],
                        fontSize = 13.sp,
                        color = colors.sub,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    if (streakDays > 0) {
                        Text(
                            "Série actuelle : $streakDays jour${if (streakDays > 1) "s" else ""} sans déblocage.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                    if (blocksTodayCount > 0) {
                        Text(
                            "Reels a été bloqué $blocksTodayCount fois aujourd'hui.",
                            fontSize = 12.sp,
                            color = colors.sub,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            if (loaded) {
                // ANNULER abandonne la tentative de déblocage et garde le blocage actif — c'est
                // le geste qu'on veut encourager, donc il reste immédiatement cliquable (aucune
                // raison d'y ajouter de la friction). Le déblocage, lui, n'a pas de bouton : il
                // survient automatiquement à la fin du compte à rebours, qui est déjà la friction.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp, 24.dp, 32.dp, 40.dp)
                        .border(2.dp, colors.text)
                        .clickable(onClick = onCancel)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ANNULER",
                        color = colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
