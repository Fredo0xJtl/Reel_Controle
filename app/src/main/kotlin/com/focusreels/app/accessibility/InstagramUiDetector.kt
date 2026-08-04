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
 * Comportement fail-open : en cas de doute ou d'erreur, une méthode renvoie `false`
 * plutôt que de risquer un faux positif qui bloquerait Instagram à tort.
 *
 * IMPORTANT (leçon empirique, appareil Galaxy S24 / OneUI) :
 * Instagram utilise une seule activité `MainTabActivity` pour tous les onglets, et la barre
 * de navigation inférieure contient en permanence un bouton « Reels ». Détecter la simple
 * *présence* du mot « Reels » provoque donc un faux positif sur chaque écran d'Instagram.
 * La détection doit vérifier que l'onglet est **sélectionné**, ou qu'un lecteur de Reels
 * plein écran est réellement affiché.
 *
 * Compatible : Xiaomi MIUI/HyperOS, Samsung OneUI, AOSP standard.
 */
object InstagramUiDetector {

    private const val TAG = "InstagramUiDetector"
    private const val PACKAGE = "com.instagram.android"

    /** Bouton « Reels » de la barre de navigation inférieure : présent partout, à tester via isSelected. */
    private val REELS_TAB_VIEW_IDS = listOf(
        "$PACKAGE:id/clips_tab",
        "$PACKAGE:id/reels_tab",
        "$PACKAGE:id/clips_icon",
        "$PACKAGE:id/reels_tab_icon"
    )

    /**
     * Bouton « Accueil » de la barre de navigation inférieure (confirmé empiriquement,
     * content-desc="Home", Galaxy S24 / OneUI, Instagram 2026). Cliquer dessus fait changer
     * d'onglet sans jamais quitter l'application, contrairement à une action Retour globale
     * qui sort d'Instagram lorsque l'onglet Reels est la page racine (pas de pile à dépiler).
     */
    private val HOME_TAB_VIEW_IDS = listOf(
        "$PACKAGE:id/feed_tab"
    )

    /** Libellés du bouton d'onglet Reels, selon la langue de l'appareil. */
    private val REELS_TAB_LABELS = listOf("Reels", "Clips")

    /** Marqueurs d'un Reels ouvert depuis une conversation privée (§3.2). */
    private val DM_VIEW_IDS = listOf(
        "$PACKAGE:id/direct_thread_reel_viewer",
        "$PACKAGE:id/direct_thread_toggle",
        "$PACKAGE:id/thread_message_list"
    )

    /**
     * Retourne true uniquement si le flux Reels général est **réellement affiché**.
     *
     * Signal unique et sans ambiguïté : le bouton d'onglet Reels de la barre de navigation
     * est marqué `isSelected`. C'est la seule condition qui distingue de façon fiable « je
     * suis dans la section Reels dédiée » de « un Reels s'affiche ailleurs ».
     *
     * Ancienne approche abandonnée (constat empirique, §4.5) : détecter la présence d'un
     * conteneur de lecteur plein écran (`clips_viewer_*`) semblait plus robuste, mais
     * Instagram insère désormais des Reels directement dans le flux Accueil, avec
     * vraisemblablement le même rendu de lecteur. Cette détection bloquait donc le simple
     * défilement du feed normal. Se fier uniquement à l'onglet sélectionné élimine ce faux
     * positif, au prix de ne pas intercepter un Reels isolé ouvert autrement (profil, feed) —
     * acceptable au regard du cahier des charges, qui vise l'onglet Reels dédié (§3.1).
     *
     * La simple présence du libellé « Reels » n'est **jamais** suffisante : le bouton de
     * navigation est affiché sur tous les écrans d'Instagram.
     *
     * Fail-open : toute exception ou incertitude renvoie false (pas de blocage).
     */
    fun isGeneralReelsFeed(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            if (isReelsTabSelected(root)) {
                Log.d(TAG, "Reels général détecté : onglet Reels sélectionné")
                return true
            }

            Log.v(TAG, "Reels général non détecté (autre écran Instagram)")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors de la détection : ${e.message}")
            false
        }
    }

    /**
     * Vérifie que le bouton d'onglet Reels est l'onglet actif.
     * Cherche d'abord par identifiant de vue, puis par libellé, et remonte au parent
     * cliquable si le nœud porteur du texte n'est pas lui-même l'élément sélectionnable.
     */
    private fun isReelsTabSelected(root: AccessibilityNodeInfo): Boolean {
        findFirstByAnyViewId(root, REELS_TAB_VIEW_IDS)?.let { node ->
            val selected = isSelectedOrAncestorSelected(node)
            node.recycle()
            if (selected) return true
        }

        REELS_TAB_LABELS.forEach { label ->
            val matches = try {
                root.findAccessibilityNodeInfosByText(label)
            } catch (_: Exception) {
                emptyList<AccessibilityNodeInfo>()
            }
            matches.forEach { node ->
                // Un libellé exact évite de capter « Reels et vidéos », « Voir les Reels », etc.
                val exactLabel = node.contentDescription?.toString()?.equals(label, ignoreCase = true) == true ||
                        node.text?.toString()?.equals(label, ignoreCase = true) == true
                val selected = exactLabel && isSelectedOrAncestorSelected(node)
                @Suppress("DEPRECATION")
                node.recycle()
                if (selected) return true
            }
        }
        return false
    }

    /**
     * L'état sélectionné est porté tantôt par le nœud lui-même, tantôt par son conteneur
     * cliquable. On remonte donc quelques niveaux, sans parcourir tout l'arbre.
     */
    private fun isSelectedOrAncestorSelected(node: AccessibilityNodeInfo, maxDepth: Int = 3): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        var ownsCurrent = false
        while (current != null && depth <= maxDepth) {
            if (current.isSelected) {
                if (ownsCurrent) {
                    @Suppress("DEPRECATION")
                    current.recycle()
                }
                return true
            }
            val parent = current.parent
            if (ownsCurrent) {
                @Suppress("DEPRECATION")
                current.recycle()
            }
            current = parent
            ownsCurrent = true
            depth++
        }
        if (ownsCurrent && current != null) {
            @Suppress("DEPRECATION")
            current.recycle()
        }
        return false
    }

    /**
     * Retourne le nœud cliquable de l'onglet Accueil, s'il est présent à l'écran.
     * L'appelant est responsable de son cycle de vie (clic puis `recycle()`).
     */
    fun findHomeTabNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        return try {
            findFirstByAnyViewId(root, HOME_TAB_VIEW_IDS)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur recherche de l'onglet Accueil : ${e.message}")
            null
        }
    }

    /**
     * Heuristique pour détecter un Reels ouvert depuis une conversation privée (DM) plutôt
     * que depuis le flux Reels général (§3.2).
     *
     * N'utilise que des identifiants propres au contexte DM : rechercher le texte
     * « Message » captait auparavant presque tous les écrans d'Instagram.
     */
    fun isReelsOpenedFromDirectMessage(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            val node = findFirstByAnyViewId(root, DM_VIEW_IDS)
            val isDmContext = node != null
            @Suppress("DEPRECATION")
            node?.recycle()

            if (isDmContext) {
                Log.d(TAG, "Reels depuis DM détecté")
            }
            isDmContext
        } catch (e: Exception) {
            Log.w(TAG, "Erreur détection DM : ${e.message}")
            false
        }
    }

    private fun findFirstByAnyViewId(
        root: AccessibilityNodeInfo,
        viewIds: List<String>
    ): AccessibilityNodeInfo? {
        viewIds.forEach { id ->
            val nodes = try {
                root.findAccessibilityNodeInfosByViewId(id)
            } catch (_: Exception) {
                emptyList<AccessibilityNodeInfo>()
            }
            if (nodes.isNotEmpty()) {
                // On conserve le premier et libère les suivants.
                nodes.drop(1).forEach {
                    @Suppress("DEPRECATION")
                    it.recycle()
                }
                return nodes.first()
            }
        }
        return null
    }

    /**
     * Trace l'arbre d'accessibilité pour calibrer les identifiants ci-dessus sur un appareil
     * réel (§4.5). Activée uniquement par [ReelsAccessibilityService] en mode diagnostic, car
     * le volume de logs est important.
     */
    fun dumpTree(root: AccessibilityNodeInfo?, depth: Int = 0) {
        if (root == null) return
        val indent = " ".repeat(depth * 2)
        Log.d(
            TAG,
            "$indent id=${root.viewIdResourceName} class=${root.className} " +
                    "text=${root.text} desc=${root.contentDescription} " +
                    "selected=${root.isSelected} visible=${root.isVisibleToUser}"
        )
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            dumpTree(child, depth + 1)
            @Suppress("DEPRECATION")
            child.recycle()
        }
    }
}
