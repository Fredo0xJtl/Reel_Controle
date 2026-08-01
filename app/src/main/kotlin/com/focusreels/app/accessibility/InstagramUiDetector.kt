package com.focusreels.app.accessibility

import android.util.Log
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
 *
 * Compatible : Xiaomi MIUI/HyperOS, Samsung OneUI, AOSP standard.
 */
object InstagramUiDetector {

    private const val TAG = "InstagramUiDetector"

    // Identifiants observés empiriquement ; à ajuster à chaque évolution de l'UI Instagram.
    // Concentrer ces constantes ici est ce qui permet une correction rapide et localisée.
    // Versions testées : Instagram 300.0+ (2024+)
    private val REELS_TAB_VIEW_IDS = listOf(
        "com.instagram.android:id/clips_tab",
        "com.instagram.android:id/reels_tab_icon",
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/reels_tab",
        "com.instagram.android:id/clips_icon"
    )

    private val REELS_TAB_CONTENT_DESCRIPTIONS = listOf(
        "Reels",
        "Reels Tab",
        "Clips",
        "Clips Tab"
    )

    private val REELS_ACTIVITY_NAMES = listOf(
        "ReelsActivity",
        "ClipsActivity",
        "ReelsViewerActivity"
    )

    /**
     * Retourne true si l'écran actuel correspond à l'onglet Reels dédié (flux général),
     * et non à un Reels ouvert individuellement depuis un DM.
     *
     * Multi-heuristiques (fallback cascade) :
     * 1. Cherche par ID de vue
     * 2. Cherche par description textuelle
     * 3. Cherche par classe d'activité
     *
     * Fail-open : toute exception ou incertitude renvoie false (pas de blocage).
     */
    fun isGeneralReelsFeed(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            val matchedById = matchesById(root)
            if (matchedById) {
                Log.d(TAG, "Reels général détecté : ID de vue trouvé")
                return true
            }

            val matchedByDesc = matchesByContentDescription(root)
            if (matchedByDesc) {
                Log.d(TAG, "Reels général détecté : description textuelle trouvée")
                return true
            }

            val matchedByActivity = matchesByActivityName(root)
            if (matchedByActivity) {
                Log.d(TAG, "Reels général détecté : classe d'activité trouvée")
                return true
            }

            Log.v(TAG, "Reels général non détecté (probablement DM ou autre écran)")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors de la détection : ${e.message}")
            false
        }
    }

    private fun matchesById(root: AccessibilityNodeInfo): Boolean =
        REELS_TAB_VIEW_IDS.any { id ->
            try {
                root.findAccessibilityNodeInfosByViewId(id).isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }

    private fun matchesByContentDescription(root: AccessibilityNodeInfo): Boolean =
        REELS_TAB_CONTENT_DESCRIPTIONS.any { desc ->
            try {
                root.findAccessibilityNodeInfosByText(desc).isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }

    private fun matchesByActivityName(root: AccessibilityNodeInfo): Boolean {
        val className = root.className?.toString() ?: return false
        return REELS_ACTIVITY_NAMES.any { className.contains(it) }
    }

    /**
     * Heuristique pour détecter un Reels ouvert depuis une conversation privée (DM) plutôt
     * que depuis le flux Reels général. À affiner selon l'UI réelle observée (§4.5).
     *
     * Cherche des marqueurs spécifiques au contexte DM/groupe.
     */
    fun isReelsOpenedFromDirectMessage(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            val isDmContext = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/direct_thread_reel_viewer").isNotEmpty()
                    || root.className?.toString()?.contains("DirectThreadActivity") == true
                    || root.findAccessibilityNodeInfosByText("Message").isNotEmpty()

            if (isDmContext) {
                Log.d(TAG, "Reels depuis DM détecté")
            }
            isDmContext
        } catch (e: Exception) {
            Log.w(TAG, "Erreur détection DM : ${e.message}")
            false
        }
    }
}
