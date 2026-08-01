package com.focusreels.app.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Module isolé de reconnaissance de l'interface Instagram (cahier des charges §4.5).
 *
 * Instagram change régulièrement ses identifiants de vues. Toute la logique fragile et
 * dépendante de la version d'Instagram est concentrée ICI, pour permettre une mise à jour
 * rapide sans toucher au reste de l'application (ReelsAccessibilityService, friction, etc.).
 *
 * Comportement fail-open : en cas de doute ou d'erreur, une méthode renvoie `false`/`UNKNOWN`
 * plutôt que de risquer un faux positif qui bloquerait Instagram à tort.
 */
object InstagramUiDetector {

    // Identifiants observés empiriquement ; à ajuster à chaque évolution de l'UI Instagram.
    // Concentrer ces constantes ici est ce qui permet une correction rapide et localisée.
    private val REELS_TAB_VIEW_IDS = listOf(
        "com.instagram.android:id/clips_tab",
        "com.instagram.android:id/reels_tab_icon",
        "com.instagram.android:id/clips_viewer_view_pager"
    )

    private val REELS_TAB_CONTENT_DESCRIPTIONS = listOf(
        "Reels",
        "Reels Tab"
    )

    /**
     * Retourne true si l'écran actuel correspond à l'onglet Reels dédié (flux général),
     * et non à un Reels ouvert individuellement depuis un DM.
     *
     * Fail-open : toute exception ou incertitude renvoie false (pas de blocage).
     */
    fun isGeneralReelsFeed(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            matchesById(root) || matchesByContentDescription(root)
        } catch (_: Exception) {
            false
        }
    }

    private fun matchesById(root: AccessibilityNodeInfo): Boolean =
        REELS_TAB_VIEW_IDS.any { id -> root.findAccessibilityNodeInfosByViewId(id).isNotEmpty() }

    private fun matchesByContentDescription(root: AccessibilityNodeInfo): Boolean =
        REELS_TAB_CONTENT_DESCRIPTIONS.any { desc -> root.findAccessibilityNodeInfosByText(desc).isNotEmpty() }

    /**
     * Heuristique pour détecter un Reels ouvert depuis une conversation privée (DM) plutôt
     * que depuis le flux Reels général. À affiner selon l'UI réelle observée (§4.5).
     */
    fun isReelsOpenedFromDirectMessage(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/direct_thread_reel_viewer").isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
