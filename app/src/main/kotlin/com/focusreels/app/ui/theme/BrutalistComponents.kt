package com.focusreels.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composants brutalistes réutilisables (bordures franches, ombre dure, coins droits), fidèles
 * au handoff Claude Design. Regroupés ici plutôt que dupliqués sur chaque écran.
 */

@Composable
fun BrutalistCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalFocusColors.current.surface,
    content: @Composable () -> Unit
) {
    val colors = LocalFocusColors.current
    Box(
        modifier = modifier
            .padding(end = 6.dp, bottom = 6.dp) // laisse la place à l'ombre dure
            .drawBehind {
                // Ombre dure brutaliste : rectangle plein décalé (pas de flou), dessiné derrière la carte.
                val offsetPx = 6.dp.toPx()
                drawRect(
                    color = colors.border,
                    topLeft = Offset(offsetPx, offsetPx),
                    size = size
                )
            }
            .background(backgroundColor)
            .border(2.dp, colors.border)
    ) {
        content()
    }
}

@Composable
fun BrutalistSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trackColor: Color,
    knobColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .background(trackColor)
            .border(2.dp, borderColor)
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .offset(x = if (checked) 22.dp else 0.dp)
                .size(22.dp)
                .background(knobColor)
        )
    }
}

/**
 * Stepper +/- dont la valeur au centre est aussi éditable au clavier numérique : un tap dessus
 * bascule en mode saisie avec le texte pré-sélectionné (taper remplace directement, pas besoin de
 * supprimer les chiffres à la main). La valeur est bornée à [min, max] à la validation, comme les
 * boutons +/-.
 */
@Composable
fun BrutalistStepper(
    label: String,
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
    modifier: Modifier = Modifier,
    resetSignal: Int = 0
) {
    val colors = LocalFocusColors.current
    var isEditing by remember { mutableStateOf(false) }
    // Génération de reset au moment où l'édition a commencé. Si le parent incrémente
    // `resetSignal` (bouton "réinitialiser") pendant que ce champ est ouvert, la perte de focus
    // qui suit (le tap sur le bouton reset lui-même) déclenche quand même `onFocusChanged` avec
    // le texte encore tapé — sans ce garde-fou, ce `commit()` tardif réécrasait la valeur tout
    // juste remise à zéro avec l'ancienne saisie. On compare donc la génération capturée à
    // l'ouverture avec la génération courante plutôt que de dépendre de l'ordre des événements
    // (non garanti entre onClick du bouton et onFocusChanged du champ).
    var editGeneration by remember { mutableStateOf(resetSignal) }
    var editText by remember(value, isEditing) {
        mutableStateOf(
            TextFieldValue(
                text = value.toString(),
                selection = TextRange(0, value.toString().length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // `onFocusChanged` reçoit un premier événement "non focus" dès la composition du champ,
    // avant même que `focusRequester.requestFocus()` n'ait pris effet — sans ce garde-fou, ce
    // faux événement initial déclenchait `commit()` immédiatement et refermait le champ avant que
    // l'utilisateur ait pu taper quoi que ce soit (constat terrain : "flash bleuté" puis
    // fermeture instantanée). On n'accepte donc une perte de focus comme validation qu'après
    // avoir observé un vrai gain de focus.
    var hasGainedFocus by remember(isEditing) { mutableStateOf(false) }

    // Un reset externe pendant l'édition doit fermer le champ tout de suite, sans attendre la
    // perte de focus (et sans laisser `commit()` s'exécuter avec le texte encore tapé).
    LaunchedEffect(resetSignal) {
        if (isEditing) isEditing = false
    }

    fun commit() {
        if (editGeneration != resetSignal) {
            // Un reset a eu lieu depuis l'ouverture de ce champ : la saisie en cours est
            // abandonnée, la valeur réinitialisée fait foi.
            isEditing = false
            keyboardController?.hide()
            return
        }
        val parsed = editText.text.toIntOrNull()
        if (parsed != null) onValueChange(parsed.coerceIn(min, max))
        isEditing = false
        keyboardController?.hide()
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = colors.text)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepperButton("−", colors) { onValueChange((value - step).coerceIn(min, max)) }

            if (isEditing) {
                BasicTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier
                        .width(44.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                hasGainedFocus = true
                            } else if (hasGainedFocus && isEditing) {
                                commit()
                            }
                        },
                    textStyle = FocusReelsType.mono.copy(color = colors.text, textAlign = TextAlign.Center),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() })
                )
                androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                Text(
                    "$value$unit",
                    style = FocusReelsType.mono,
                    color = colors.text,
                    modifier = Modifier
                        .width(44.dp)
                        .clickable {
                            editGeneration = resetSignal
                            isEditing = true
                        },
                    textAlign = TextAlign.Center
                )
            }

            StepperButton("+", colors) { onValueChange((value + step).coerceIn(min, max)) }
        }
    }
}

@Composable
private fun StepperButton(symbol: String, colors: FocusReelsColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .border(2.dp, colors.text)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Bande rayée oblique accent/noir (alerte inactive, progression de l'écran de friction).
 * `animated` reproduit le pulse du handoff (opacity 1↔0.45).
 */
@Composable
fun StripedBar(
    modifier: Modifier = Modifier,
    accent: Color,
    dark: Color = Color(0xFF0A0A0A),
    stripeWidth: Dp = 10.dp,
    animated: Boolean = false
) {
    val alpha = if (animated) {
        val transition = rememberInfiniteTransition(label = "stripe-pulse")
        val a by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "stripe-alpha"
        )
        a
    } else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .drawBehind {
                val stripePx = stripeWidth.toPx()
                val period = stripePx * 2
                rotate(degrees = -45f, pivot = Offset(0f, 0f)) {
                    var offsetX = -size.width
                    while (offsetX < size.width * 2) {
                        drawRect(
                            color = dark.copy(alpha = alpha),
                            topLeft = Offset(offsetX, -size.height),
                            size = Size(stripePx, size.height * 3)
                        )
                        drawRect(
                            color = accent.copy(alpha = alpha),
                            topLeft = Offset(offsetX + stripePx, -size.height),
                            size = Size(stripePx, size.height * 3)
                        )
                        offsetX += period
                    }
                }
            }
    )
}

enum class NavTab(val label: String) {
    HOME("ACCUEIL"),
    HISTORY("HISTORIQUE"),
    SETTINGS("RÉGLAGES")
}

@Composable
fun BottomNav(
    current: NavTab,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFocusColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind {
                // Filet supérieur épais (border-top: 2px), dessiné manuellement pour matcher le handoff.
                drawRect(color = colors.text, size = Size(size.width, 2.dp.toPx()))
            }
    ) {
        NavTab.entries.forEach { tab ->
            val selected = tab == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (selected) colors.text else colors.surface)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tab.label,
                    color = if (selected) colors.surface else colors.text,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
