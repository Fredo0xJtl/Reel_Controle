package com.focusreels.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusreels.app.data.db.BlockAttemptEntity
import com.focusreels.app.data.repository.HistoryRepository
import com.focusreels.app.ui.theme.BottomNav
import com.focusreels.app.ui.theme.FocusReelsType
import com.focusreels.app.ui.theme.LocalFocusColors
import com.focusreels.app.ui.theme.NavTab
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.util.Date
import java.util.Locale

/** Granularité de l'axe des abscisses du graphique d'historique. */
private enum class ChartGranularity(val label: String) {
    DAY("Jour"),
    MONTH("Mois")
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

    var chartGranularity by remember { mutableStateOf(ChartGranularity.DAY) }
    // Index de la barre sélectionnée dans [chartBars], ou -1 si aucune. Réinitialisé quand la
    // granularité change (index #13 en vue "Jour" n'a pas de sens en vue "Mois").
    var selectedBarIndex by remember { mutableStateOf(-1) }

    // Points du graphique : sur l'historique complet, sur une fenêtre glissante récente adaptée
    // à la granularité choisie. Chaque barre porte directement ses tentatives (plutôt qu'un
    // simple compte) : le tap sur une barre affiche son détail sans nouvelle requête.
    val chartBars = remember(fullHistory, chartGranularity) {
        when (chartGranularity) {
            ChartGranularity.DAY -> {
                val today = LocalDate.now(ZoneId.systemDefault())
                val byDay = fullHistory.groupBy {
                    Instant.ofEpochMilli(it.timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                }
                val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
                days.map { day ->
                    val dayLabel = when (day) {
                        today -> "Aujourd'hui"
                        today.minusDays(1) -> "Hier"
                        else -> day.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }
                    ChartBar(
                        label = day.format(java.time.format.DateTimeFormatter.ofPattern("d/M")),
                        detailLabel = dayLabel,
                        attempts = (byDay[day] ?: emptyList()).sortedByDescending { it.timestampMillis }
                    )
                }
            }
            ChartGranularity.MONTH -> {
                val currentMonth = YearMonth.now(ZoneId.systemDefault())
                val byMonth = fullHistory.groupBy {
                    YearMonth.from(Instant.ofEpochMilli(it.timestampMillis).atZone(ZoneId.systemDefault()))
                }
                val months = (11 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
                months.map { month ->
                    val monthName = month.month.getDisplayName(JTextStyle.FULL, Locale.FRANCE)
                        .replaceFirstChar { it.uppercase() }
                    ChartBar(
                        label = month.month.getDisplayName(JTextStyle.SHORT, Locale.FRANCE)
                            .replaceFirstChar { it.uppercase() },
                        detailLabel = "$monthName ${month.year}",
                        attempts = (byMonth[month] ?: emptyList()).sortedByDescending { it.timestampMillis }
                    )
                }
            }
        }
    }
    val selectedBar = selectedBarIndex.takeIf { it in chartBars.indices }?.let { chartBars[it] }

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
                ChartGranularity.entries.forEach { granularity ->
                    FilterPill(
                        label = granularity.label,
                        selected = chartGranularity == granularity,
                        onClick = {
                            chartGranularity = granularity
                            selectedBarIndex = -1
                        }
                    )
                }
            }

            HistoryChart(
                bars = chartBars,
                selectedIndex = selectedBarIndex,
                onBarTap = { index ->
                    // Un 2e tap sur la même barre désélectionne (bascule), plutôt que de rester
                    // bloqué en détail sans moyen évident de revenir en arrière.
                    selectedBarIndex = if (selectedBarIndex == index) -1 else index
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp, 12.dp)
                    .height(160.dp)
            )

            Box(modifier = Modifier.padding(top = 4.dp))

            if (selectedBar == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (fullHistory.isEmpty()) "Aucune tentative bloquée pour l'instant." else "Touchez une barre pour voir le détail.",
                        color = colors.sub,
                        fontSize = 14.sp
                    )
                }
            } else if (selectedBar.attempts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Aucun blocage : ${selectedBar.detailLabel}", color = colors.sub, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.text)
                                .padding(20.dp, 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                selectedBar.detailLabel.uppercase(),
                                color = colors.surface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(selectedBar.attempts.size.toString(), style = FocusReelsType.mono, color = colors.surface)
                        }
                    }
                    items(selectedBar.attempts) { attempt ->
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

/**
 * Un point du graphique d'historique : un jour ou un mois. Porte directement ses tentatives (pas
 * juste un compte) pour que le tap sur la barre affiche son détail sans requête supplémentaire.
 */
private data class ChartBar(val label: String, val detailLabel: String, val attempts: List<BlockAttemptEntity>) {
    val count: Int get() = attempts.size
}

/**
 * Graphique en barres brutaliste et tactile : abscisse = jours ou mois (selon [ChartGranularity]
 * choisie par l'utilisateur), ordonnée = nombre de tentatives bloquées. Taper une barre la
 * sélectionne ([onBarTap]) pour afficher son détail ailleurs à l'écran, sans avoir à dérouler tout
 * l'historique. Dessiné en [Canvas] plutôt qu'avec une lib de charts externe, pour rester
 * offline-first et cohérent avec le reste du style (traits nets, pas d'ombres/dégradés).
 */
@Composable
private fun HistoryChart(
    bars: List<ChartBar>,
    selectedIndex: Int,
    onBarTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFocusColors.current
    if (bars.isEmpty() || bars.all { it.count == 0 }) {
        Box(
            modifier = modifier.border(2.dp, colors.border).background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Text("Aucun blocage sur cette période.", color = colors.sub, fontSize = 12.sp)
        }
        return
    }

    val maxCount = (bars.maxOf { it.count }).coerceAtLeast(1)
    // N'affiche pas un label sous chaque barre si elles sont trop nombreuses (mois : 12 c'est
    // encore lisible ; jours : 14, on saute 1 sur 2 pour éviter le chevauchement du texte).
    val labelStride = if (bars.size > 10) 2 else 1

    Canvas(
        modifier = modifier
            .border(2.dp, colors.border)
            .background(colors.surface)
            .pointerInput(bars) {
                detectTapGestures { offset ->
                    val slotWidth = size.width / bars.size.toFloat()
                    val index = (offset.x / slotWidth).toInt().coerceIn(0, bars.size - 1)
                    onBarTap(index)
                }
            }
    ) {
        val axisLabelHeight = 28.dp.toPx()
        val chartHeight = size.height - axisLabelHeight
        val barCount = bars.size
        val slotWidth = size.width / barCount
        val barWidth = slotWidth * 0.55f
        val textPaint = android.graphics.Paint().apply {
            color = colors.sub.toArgb()
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val countPaint = android.graphics.Paint().apply {
            color = colors.text.toArgb()
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        // Ligne de base (axe des abscisses).
        drawLine(
            color = colors.border,
            start = Offset(0f, chartHeight),
            end = Offset(size.width, chartHeight),
            strokeWidth = Stroke.DefaultMiter
        )

        bars.forEachIndexed { index, bar ->
            val slotCenterX = slotWidth * index + slotWidth / 2f
            val isSelected = index == selectedIndex
            val barHeight = if (bar.count == 0) 0f else (bar.count.toFloat() / maxCount) * (chartHeight - 16.dp.toPx())
            val barTop = chartHeight - barHeight

            // Zone de tap complète de la barre (toute la hauteur du graphique), pour donner un
            // retour visuel même quand on touche au-dessus d'une petite barre.
            if (isSelected) {
                drawRect(
                    color = colors.text.copy(alpha = 0.08f),
                    topLeft = Offset(slotCenterX - slotWidth / 2f, 0f),
                    size = androidx.compose.ui.geometry.Size(slotWidth, chartHeight)
                )
            }

            if (bar.count > 0) {
                drawRect(
                    color = if (isSelected) colors.text else colors.accent,
                    topLeft = Offset(slotCenterX - barWidth / 2f, barTop),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    bar.count.toString(),
                    slotCenterX,
                    barTop - 4.dp.toPx(),
                    countPaint
                )
            }

            if (index % labelStride == 0 || isSelected) {
                drawContext.canvas.nativeCanvas.drawText(
                    bar.label,
                    slotCenterX,
                    size.height - 6.dp.toPx(),
                    textPaint
                )
            }
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
