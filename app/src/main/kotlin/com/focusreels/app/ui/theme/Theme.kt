package com.focusreels.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tokens de design brutaliste (handoff Claude Design, `.docs/design_handoff`).
 *
 * Note offline-first : le handoff spécifie les polices "IBM Plex Sans" / "Space Mono" (Google
 * Fonts). Zéro accès réseau étant une exigence non négociable du projet (§4.4), ces polices ne
 * sont pas téléchargées ; on les remplace par leurs équivalents système avec les mêmes poids,
 * tailles et espacements — [FontFamily.SansSerif] pour le texte, [FontFamily.Monospace] pour la
 * police "signature" (chiffres, horodatages, compte à rebours).
 */
data class FocusReelsColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val sub: Color,
    val border: Color,
    val accent: Color,
    /** Couleur sombre alternée des rayures d'alerte, indépendante du thème (toujours quasi-noir). */
    val stripeDark: Color = Color(0xFF0A0A0A)
)

val LightFocusColors = FocusReelsColors(
    bg = Color(0xFFEEF0E6),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF173028),
    sub = Color(0xFF4D6459),
    border = Color(0xFF173028),
    accent = Color(0xFFE2451F)
)

val DarkFocusColors = FocusReelsColors(
    bg = Color(0xFF141A17),
    surface = Color(0xFF1C2622),
    text = Color(0xFFEEF2EE),
    sub = Color(0xFF8EA69C),
    border = Color(0xFF2F3F38),
    accent = Color(0xFFE2451F)
)

/** Écran de friction/déblocage : toujours en contraste dur, indépendant du thème clair/sombre. */
val FrictionColors = FocusReelsColors(
    bg = Color(0xFF0C1613),
    surface = Color(0xFF0C1613),
    text = Color(0xFFEEF2EE),
    sub = Color(0xFF7FA396),
    border = Color(0xFFEEF2EE),
    accent = Color(0xFFE2451F)
)

val LocalFocusColors = staticCompositionLocalOf { LightFocusColors }

/** Accès pratique depuis n'importe quel composable, à l'image de `MaterialTheme.colorScheme`. */
object FocusReelsTheme2 {
    val colors: FocusReelsColors
        @Composable get() = LocalFocusColors.current
}

/** Styles de texte "signature" utilisés à travers l'app, en plus de `MaterialTheme.typography`. */
object FocusReelsType {
    val screenTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = (-0.02).sp
    )
    val statNumber = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 44.sp
    )
    val countdown = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 112.sp,
        lineHeight = 112.sp
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    )
    val monoLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.05.sp
    )
    val sectionLabel = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.08.sp
    )
    val bodyTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        letterSpacing = (-0.02).sp
    )
}

fun focusReelsTypography(): Typography = Typography(
    displayLarge = FocusReelsType.countdown,
    displayMedium = FocusReelsType.bodyTitle,
    headlineLarge = FocusReelsType.screenTitle,
    titleLarge = FocusReelsType.statNumber,
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = FocusReelsType.monoLabel
)

@Composable
fun FocusReelsTheme(
    forceNightMode: Boolean? = null,
    content: @Composable () -> Unit
) {
    val isDarkMode = forceNightMode ?: isSystemInDarkTheme()
    val focusColors = if (isDarkMode) DarkFocusColors else LightFocusColors

    // MaterialTheme.colorScheme reste utilisé par les composants Material bruts (OutlinedTextField,
    // etc.) qui n'ont pas encore de variante brutaliste dédiée ; mappé sur les mêmes tokens pour
    // rester cohérent visuellement.
    val materialColors = if (isDarkMode) {
        darkColorScheme(
            primary = focusColors.accent,
            background = focusColors.bg,
            surface = focusColors.surface,
            onBackground = focusColors.text,
            onSurface = focusColors.text,
            onSurfaceVariant = focusColors.sub,
            outline = focusColors.border
        )
    } else {
        lightColorScheme(
            primary = focusColors.accent,
            background = focusColors.bg,
            surface = focusColors.surface,
            onBackground = focusColors.text,
            onSurface = focusColors.text,
            onSurfaceVariant = focusColors.sub,
            outline = focusColors.border
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalFocusColors provides focusColors) {
        MaterialTheme(colorScheme = materialColors, typography = focusReelsTypography(), content = content)
    }
}
