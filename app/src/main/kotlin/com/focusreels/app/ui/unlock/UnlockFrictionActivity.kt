package com.focusreels.app.ui.unlock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.focusreels.app.FocusReelsApplication
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.data.repository.HistoryRepository
import com.focusreels.app.domain.RelockScheduler
import com.focusreels.app.ui.theme.FocusReelsTheme
import kotlinx.coroutines.launch

/**
 * Écran neutre d'attente avant déblocage effectif (§3.3).
 * Ne contient volontairement aucun contenu additionnel imposé (pas de respiration guidée,
 * pas de question) — un simple minuteur visuel, conformément au cahier des charges (§8).
 */
class UnlockFrictionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nommé différemment de `Context.packageName` (celui de l'app elle-même) : il s'agit ici
        // du package de l'application *gérée* (ex. Instagram), pas de Focus Reels.
        val targetPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (targetPackageName == null) {
            finish()
            return
        }

        val app = application as FocusReelsApplication
        val repository = BlockedAppRepository(app.database)
        val historyRepository = HistoryRepository(app.database)

        setContent {
            FocusReelsTheme {
                UnlockFrictionScreen(
                    packageName = targetPackageName,
                    repository = repository,
                    historyRepository = historyRepository,
                    onCancel = { finish() },
                    onUnlocked = { relockDelayMinutes ->
                        RelockScheduler.scheduleRelock(this, targetPackageName, relockDelayMinutes)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "package_name"

        fun start(context: Context, packageName: String) {
            val intent = Intent(context, UnlockFrictionActivity::class.java)
                .putExtra(EXTRA_PACKAGE_NAME, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
