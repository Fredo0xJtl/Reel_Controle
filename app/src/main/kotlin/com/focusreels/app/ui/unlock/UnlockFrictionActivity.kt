package com.focusreels.app.ui.unlock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.focusreels.app.FocusReelsApplication
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.domain.FrictionCalculator
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

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageName == null) {
            finish()
            return
        }

        val app = application as FocusReelsApplication
        val repository = BlockedAppRepository(app.database)

        setContent {
            FocusReelsTheme {
                UnlockFrictionScreen(
                    packageName = packageName,
                    repository = repository,
                    onCancel = { finish() },
                    onUnlocked = { relockDelayMinutes ->
                        RelockScheduler.scheduleRelock(this, packageName, relockDelayMinutes)
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
