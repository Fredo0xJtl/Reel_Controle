package com.focusreels.app.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Onboarding : activation du service d'accessibilité + désactivation de l'optimisation
 * batterie MIUI/HyperOS (§4.3, §5). Renvoie vers les écrans système correspondants ;
 * aucune action automatique n'est possible sans intervention manuelle de l'utilisateur.
 */
@Composable
fun OnboardingScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("1. Activer le service d'accessibilité \"Blocage Reels Instagram\".")
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("Ouvrir les réglages d'accessibilité") }

            Text("2. Désactiver l'optimisation de batterie pour Focus Reels (indispensable sur MIUI/HyperOS).")
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:" + context.packageName)
                })
            }) { Text("Ouvrir les infos de l'application") }

            Button(onClick = onBack) { Text("Retour") }
        }
    }
}
