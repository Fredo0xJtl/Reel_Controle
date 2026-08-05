package com.focusreels.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusreels.app.data.repository.HistoryRepository
import com.focusreels.app.ui.theme.BottomNav
import com.focusreels.app.ui.theme.FocusReelsType
import com.focusreels.app.ui.theme.LocalFocusColors
import com.focusreels.app.ui.theme.NavTab
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private enum class DateFilter(val label: String) {
    ALL("Tout"),
    TODAY("Aujourd'hui"),
    WEEK("7 derniers jours")
}

/**
 * Historique illimité des tentatives bloquées, avec horodatage (§3.6). Groupé par jour, style
 * brutaliste (handoff Claude Design).
 *
 * Chaque entrée correspond à une tentative d'ouverture de Reels effectivement interceptée par
 * le service : la table `block_attempts` ne connaît qu'un seul type d'événement (un blocage),
 * il n'existe donc pas de statut "débloquée" distinct à filtrer ici. Un filtre par période est
 * proposé à la place, utile dès que la liste s'allonge.
 */
@Composable
fun HistoryScreen(
    packageName: String,
    repository: HistoryRepository,
    onBack: () -> Unit,
    onOpenHome: () -> Unit = onBack,
    onOpenSettings: () -> Unit = {}
) {
    val colors = LocalFocusColors.current

    // `remember(packageName)` : sans clé, `observeHistory()` construirait un nouveau Flow (donc
    // une nouvelle souscription Room) à chaque recomposition de l'écran.
    val historyFlow = remember(packageName) { repository.observeHistory(packageName) }
    val fullHistory by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }

    var selectedFilter by remember { mutableStateOf(DateFilter.ALL) }

    val filtered = remember(fullHistory, selectedFilter) {
        when (selectedFilter) {
            DateFilter.ALL -> fullHistory
            DateFilter.TODAY -> {
                val todayStart = LocalDate.now(ZoneId.systemDefault())
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                fullHistory.filter { it.timestampMillis >= todayStart }
            }
            DateFilter.WEEK -> {
                val weekStart = Instant.now().minusSeconds(7 * 24 * 3600).toEpochMilli()
                fullHistory.filter { it.timestampMillis >= weekStart }
            }
        }
    }

    // Groupement par jour ("AUJOURD'HUI" / "HIER" / date), comme dans le handoff.
    val groups = remember(filtered) {
        val today = LocalDate.now(ZoneId.systemDefault())
        filtered
            .groupBy {
                Instant.ofEpochMilli(it.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            .toSortedMap(compareByDescending { it })
            .map { (date, attempts) ->
                val label = when (date) {
                    today -> "AUJOURD'HUI"
                    today.minusDays(1) -> "HIER"
                    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                }
                label to attempts.sortedByDescending { it.timestampMillis }
            }
    }

    Scaffold(containerColor = colors.bg) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "HISTORIQUE",
                style = FocusReelsType.screenTitle,
                color = colors.text,
                modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateFilter.entries.forEach { filter ->
                    FilterPill(
                        label = filter.label,
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            Box(modifier = Modifier.padding(top = 12.dp))

            if (groups.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (fullHistory.isEmpty()) "Aucune tentative bloquée pour l'instant." else "Rien sur cette période.",
                        color = colors.sub,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    groups.forEach { (dayLabel, attempts) ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.text)
                                    .padding(20.dp, 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(dayLabel, color = colors.surface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(attempts.size.toString(), style = FocusReelsType.mono, color = colors.surface)
                            }
                        }
                        items(attempts) { attempt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp, 13.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    timeFormatter.format(Date(attempt.timestampMillis)),
                                    style = FocusReelsType.mono,
                                    color = colors.sub
                                )
                                Text("Tentative bloquée", color = colors.text, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            BottomNav(
                current = NavTab.HISTORY,
                onSelect = { tab ->
                    when (tab) {
                        NavTab.HOME -> onOpenHome()
                        NavTab.HISTORY -> {}
                        NavTab.SETTINGS -> onOpenSettings()
                    }
                }
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalFocusColors.current
    Box(
        modifier = Modifier
            .border(2.dp, colors.border)
            .background(if (selected) colors.text else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) colors.surface else colors.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
