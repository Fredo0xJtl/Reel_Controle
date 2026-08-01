package com.focusreels.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.focusreels.app.FocusReelsApplication
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.data.repository.HistoryRepository
import com.focusreels.app.domain.SwipeSessionTracker
import com.focusreels.app.util.AppIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service d'accessibilité central (cahier des charges §4.2).
 * Fonctionne uniquement en local : aucune donnée observée n'est transmise à un tiers.
 *
 * La reconnaissance fine de l'UI Instagram est déléguée à [InstagramUiDetector] (module isolé,
 * §4.5) pour faciliter les mises à jour en cas de changement d'interface d'Instagram.
 */
class ReelsAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var blockedAppRepository: BlockedAppRepository
    private lateinit var historyRepository: HistoryRepository
    private var swipeTracker = SwipeSessionTracker(toleratedSwipes = 1)

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as FocusReelsApplication
        blockedAppRepository = BlockedAppRepository(app.database)
        historyRepository = HistoryRepository(app.database)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != AppIds.INSTAGRAM) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowUpdate()

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScroll()
        }
    }

    private fun handleWindowUpdate() {
        // Fail-open : toute exception lève l'appel sans bloquer (§4.5).
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return

        try {
            if (InstagramUiDetector.isReelsOpenedFromDirectMessage(root)) {
                swipeTracker.onDmReelsOpened()
                return
            }

            val isGeneralFeed = InstagramUiDetector.isGeneralReelsFeed(root)
            val treatAsGeneralFeed = isGeneralFeed || swipeTracker.shouldTreatAsGeneralFeed()

            if (treatAsGeneralFeed) {
                blockIfEnabled()
            }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun handleScroll() {
        swipeTracker.onSwipeDetected()
    }

    private fun blockIfEnabled() {
        scope.launch {
            val entity = blockedAppRepository.get(AppIds.INSTAGRAM) ?: return@launch
            if (!entity.blockingEnabled) return@launch

            historyRepository.recordAttempt(AppIds.INSTAGRAM)
            performGlobalAction(GLOBAL_ACTION_BACK)
            swipeTracker.reset()
        }
    }

    override fun onInterrupt() {
        // Rien à nettoyer : aucun état persistant hors base locale.
    }
}
