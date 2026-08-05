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

    /**
     * Marqueurs d'une conversation privée (§3.2). Identifiants recalibrés via dump terrain
     * (les précédents — direct_thread_reel_viewer, direct_thread_toggle, thread_message_list —
     * n'existent pas dans cette version d'Instagram, la détection DM ne matchait donc jamais).
     * Ces nœuds restent présents dans l'arbre même quand le lecteur plein écran s'ouvre par-
     * dessus (overlay, pas un nouvel écran) : la détection reste donc valable pendant tout le
     * visionnage du Reels reçu en DM, pas seulement avant de l'ouvrir.
     */
    private val DM_VIEW_IDS = listOf(
        "$PACKAGE:id/direct_thread_header",
        "$PACKAGE:id/direct_thread_content_below_action_bar"
    )

    /**
     * Barre de titre du lecteur Reels plein écran (constat empirique, test terrain, dump
     * diagnostic) : présente que le Reels soit ouvert depuis l'onglet Reels dédié (texte
     * "Reels"), depuis la grille Explorer/Recherche (texte "Explorer"), un profil, un hashtag,
     * etc. — le lecteur plein écran est le même composant partout, seul le titre change selon
     * l'origine. Contrairement à l'ancienne approche abandonnée (détection de `clips_viewer_*`
     * en général, cf. [isGeneralReelsFeed]), cette barre de titre n'apparaît que dans le lecteur
     * plein écran dédié, jamais sur une carte Reels intégrée au défilement normal du flux
     * Accueil — elle ne recrée donc pas le faux positif qui avait fait abandonner cette piste.
     */
    private val IMMERSIVE_REELS_VIEWER_IDS = listOf(
        "$PACKAGE:id/clips_viewer_view_pager",
        "$PACKAGE:id/root_clips_layout",
        "$PACKAGE:id/clips_viewer_container",
        "$PACKAGE:id/clips_viewer_action_bar"
    )

    /**
     * Marqueurs présents UNIQUEMENT quand le lecteur plein écran affiche un Reels partagé en DM
     * (constat dump terrain) — absents de l'onglet Reels dédié. Contrairement aux marqueurs de
     * conversation ([DM_VIEW_IDS]), ceux-ci restent lisibles PENDANT que le lecteur est ouvert :
     * pas besoin d'un délai de grâce fondé sur le dernier instant où la conversation a été vue,
     * qui ne permettait pas de distinguer « Reels DM tout juste quitté » de « onglet Reels dédié
     * ouvert juste après » (bug constaté : onglet Reels dédié non bloqué après passage en DM).
     */
    // Recalibré par dump terrain (Galaxy S24) : `reel_share_item_view` /
    // `direct_reel_share_legibility_gradient_footer` sont les marqueurs de la BULLE Reels dans la
    // liste des messages, pas du lecteur plein écran — ils disparaissent dès que celui-ci s'ouvre,
    // ce qui classait à tort tout Reels DM comme onglet dédié (fermé sans swipe). Les identifiants
    // ci-dessous appartiennent au lecteur plein écran lui-même et n'existent QUE quand il a été
    // ouvert depuis une conversation (barre de réponse "Répondre à …", identité de l'expéditeur) ;
    // absents sur l'onglet Reels dédié, Explorer ou le profil.
    private val DM_REEL_VIEWER_MARKER_IDS = listOf(
        "$PACKAGE:id/sender_username_or_fullname",
        "$PACKAGE:id/sender_profile_pic",
        "$PACKAGE:id/reel_viewer_message_composer"
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
     *
     * Garde supplémentaire (constat empirique, test terrain) : le fallback par libellé de
     * [isReelsTabSelected] peut capter un badge « Reels » intégré dans une carte du flux
     * Accueil (Instagram y insère des Reels directement) et le lire à tort comme sélectionné.
     * Résultat observé : après redirection vers Accueil, la détection restait bloquée sur
     * « Reels sélectionné » en boucle, provoquant des reclics incessants et un rafraîchissement
     * continu du feed Accueil. L'onglet Reels et l'onglet Accueil sont mutuellement exclusifs
     * dans l'UI réelle : si l'onglet Accueil est déjà sélectionné, on ne peut pas être
     * simultanément sur le flux Reels général — on considère alors la détection Reels comme un
     * faux positif et on l'ignore.
     */
    fun isGeneralReelsFeed(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            if (isReelsTabSelected(root)) {
                if (isHomeTabSelected(root)) {
                    Log.w(TAG, "Faux positif ignoré : Reels ET Accueil détectés sélectionnés simultanément")
                    return false
                }
                // Constat terrain (Galaxy S24) : une fois l'onglet Reels tapé une première fois,
                // son flag isSelected reste bloqué à true même après avoir quitté l'écran — tout
                // écran suivant (DM, etc.) était alors à tort détecté comme "Reels sélectionné" et
                // redirigé. Les marqueurs DM restent fiables (cf. [DM_VIEW_IDS]) : leur présence
                // écarte ce faux positif exactement comme pour l'onglet Accueil.
                if (isReelsOpenedFromDirectMessage(root)) {
                    Log.w(TAG, "Faux positif ignoré : onglet Reels bloqué sur sélectionné alors qu'on est en DM")
                    return false
                }
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
     * Vérifie que l'onglet Accueil est déjà l'onglet actif (§4.5, correctif rafraîchissement).
     *
     * Utilisé pour éviter de recliquer sur l'onglet Accueil pendant la vérification post-
     * redirection : un second tap sur un onglet **déjà sélectionné** est interprété par
     * Instagram comme une demande de rafraîchissement/retour en haut du flux, ce qui provoquait
     * un refresh visible du feed Accueil après chaque blocage.
     */
    fun isHomeTabSelected(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            val node = findFirstByAnyViewId(root, HOME_TAB_VIEW_IDS) ?: return false
            val selected = isSelectedOrAncestorSelected(node)
            @Suppress("DEPRECATION")
            node.recycle()
            selected
        } catch (e: Exception) {
            Log.w(TAG, "Erreur détection onglet Accueil sélectionné : ${e.message}")
            false
        }
    }

    /**
     * Vérifie que le bouton d'onglet Reels est l'onglet actif.
     * Cherche d'abord par identifiant de vue, puis par libellé, et remonte au parent
     * cliquable si le nœud porteur du texte n'est pas lui-même l'élément sélectionnable.
     *
     * Garde de visibilité (bug constaté en test terrain, Galaxy S24) : quand le lecteur plein
     * écran s'ouvre par-dessus, Instagram masque la barre de navigation inférieure, mais son
     * bouton Reels reste présent dans l'arbre d'accessibilité avec son flag `isSelected` figé à
     * sa dernière valeur connue (dès qu'on a tapé une fois sur l'onglet Reels dédié, ce flag ne
     * redevient jamais `false`). Sans ce filtre, tout Reels ouvert plus tard depuis le feed
     * Accueil héritait à tort de ce flag « collé », classé comme onglet Reels dédié et fermé
     * immédiatement — alors qu'aucun swipe n'avait eu lieu. Un onglet réellement actif est
     * toujours visible à l'écran ; un nœud invisible ne peut porter qu'un état obsolète.
     */
    private fun isReelsTabSelected(root: AccessibilityNodeInfo): Boolean {
        findFirstByAnyViewId(root, REELS_TAB_VIEW_IDS)?.let { node ->
            val visible = node.isVisibleToUser
            val selected = isSelectedOrAncestorSelected(node)
            node.recycle()
            if (visible && selected) return true
        }

        REELS_TAB_LABELS.forEach { label ->
            val matches = try {
                root.findAccessibilityNodeInfosByText(label)
            } catch (_: Exception) {
                emptyList<AccessibilityNodeInfo>()
            }
            // `firstOrNull` équivalent manuel : on doit recycler *tous* les nœuds de `matches`,
            // y compris ceux qui suivent le premier trouvé sélectionné (le `return true` anticipé
            // les laissait fuiter auparavant).
            var found = false
            matches.forEach { node ->
                if (!found) {
                    // Un libellé exact évite de capter « Reels et vidéos », « Voir les Reels », etc.
                    val exactLabel = node.contentDescription?.toString()?.equals(label, ignoreCase = true) == true ||
                            node.text?.toString()?.equals(label, ignoreCase = true) == true
                    if (exactLabel && node.isVisibleToUser && isSelectedOrAncestorSelected(node)) {
                        found = true
                    }
                }
                @Suppress("DEPRECATION")
                node.recycle()
            }
            if (found) return true
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

    /**
     * Détecte le lecteur Reels plein écran peu importe son origine (Explorer, profil, hashtag…) —
     * pas seulement l'onglet Reels dédié. Corrige un contournement constaté (test terrain) : un
     * Reels ouvert depuis la grille Recherche/Explorer n'était pas détecté ([isGeneralReelsFeed]
     * ne teste que l'onglet Reels sélectionné), laissant l'utilisateur défiler indéfiniment.
     *
     * L'appelant doit exclure au préalable le contexte DM ([isReelsOpenedFromDirectMessage]) et
     * sa fenêtre de tolérance ([com.focusreels.app.domain.SwipeSessionTracker.isWithinDmTolerance])
     * pour ne pas casser la tolérance de swipes voulue sur les Reels reçus en DM, qui utilisent
     * le même composant de lecteur.
     */
    fun isImmersiveReelsViewerOpen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            // LA subtilité qui a coûté plusieurs itérations : ces vues existent en permanence
            // dans l'arbre d'accessibilité, y compris sur le flux Accueil et dans les DM, parce
            // qu'Instagram garde le lecteur préchargé en arrière-plan. Mesure terrain sur une
            // session mêlant onglet Reels, DM et défilement du feed : 7 occurrences avec
            // `isVisibleToUser = true` (toutes pendant l'affichage réel du lecteur) contre 62
            // avec `false`. Tester la seule *présence* revenait donc à bloquer le feed Accueil —
            // c'est le faux positif qui avait fait abandonner cette piste par le passé. Seule la
            // visibilité effective distingue « le lecteur est à l'écran » de « il est en cache ».
            val visible = IMMERSIVE_REELS_VIEWER_IDS.any { id -> isAnyNodeVisible(root, id) }
            if (visible) {
                Log.d(TAG, "Lecteur Reels plein écran réellement affiché")
            }
            visible
        } catch (e: Exception) {
            Log.w(TAG, "Erreur détection lecteur plein écran : ${e.message}")
            false
        }
    }

    /**
     * True si le lecteur plein écran actuellement affiché montre un Reels partagé en DM
     * (cf. [DM_REEL_VIEWER_MARKER_IDS]). L'appelant doit avoir déjà vérifié
     * [isImmersiveReelsViewerOpen].
     */
    fun isReelViewerFromDirectMessage(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            DM_REEL_VIEWER_MARKER_IDS.any { id -> isAnyNodeVisible(root, id) }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur détection origine DM du lecteur : ${e.message}")
            false
        }
    }

    /**
     * True si le lecteur plein écran affiche un Reels cliqué depuis le feed Accueil
     * (tolérance de swipe comme DM).
     *
     * Heuristique : lecteur plein écran ouvert, mais pas sur l'onglet Reels dédié,
     * pas en DM, et pas en contexte de Reels général (onglet sélectionné).
     * Cela couvre : feed Accueil, Explorer, profil, hashtag, etc. — tous les contextes
     * où cliquer un Reels unique ouvre le lecteur plein écran.
     */
    fun isReelViewerFromGeneralFeed(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return try {
            // Si on est sur l'onglet Reels dédié, ce n'est pas un Reels du feed.
            if (isGeneralReelsFeed(root)) {
                Log.d(TAG, "Lecteur depuis feed : non, c'est l'onglet Reels sélectionné")
                return false
            }
            // Si c'est un Reels DM, ce n'est pas un Reels du feed Accueil.
            if (isReelViewerFromDirectMessage(root)) {
                Log.d(TAG, "Lecteur depuis feed : non, c'est un Reels DM")
                return false
            }
            // Lecteur plein écran + pas onglet dédié + pas DM = feed/Explorer/profil.
            Log.d(TAG, "Lecteur depuis feed Accueil/Explorer/profil détecté")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Erreur détection feed Accueil du lecteur : ${e.message}")
            false
        }
    }

    /**
     * True si au moins un nœud portant [viewId] est réellement visible à l'écran.
     * Plusieurs nœuds peuvent partager l'identifiant (instances recyclées du lecteur) : il suffit
     * qu'une seule soit visible.
     */
    private fun isAnyNodeVisible(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = try {
            root.findAccessibilityNodeInfosByViewId(viewId)
        } catch (_: Exception) {
            return false
        }
        var visible = false
        nodes?.forEach { node ->
            if (node.isVisibleToUser) visible = true
            @Suppress("DEPRECATION")
            node.recycle()
        }
        return visible
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

    /** Profondeur maximale de [dumpTree], pour éviter tout risque de StackOverflowError sur un arbre anormalement profond. */
    private const val MAX_DUMP_DEPTH = 50

    /**
     * Trace l'arbre d'accessibilité pour calibrer les identifiants ci-dessus sur un appareil
     * réel (§4.5). Activée uniquement par [ReelsAccessibilityService] en mode diagnostic, car
     * le volume de logs est important.
     */
    /**
     * Trace uniquement les identifiants de vue contenant un mot-clé donné, au lieu de l'arbre
     * entier ([dumpTree]) : suffisant pour calibrer un signal de détection, et assez léger pour
     * rester lisible dans un logcat en direct pendant un test terrain.
     */
    fun dumpMatchingIds(root: AccessibilityNodeInfo?, keyword: String, depth: Int = 0) {
        if (root == null || depth >= MAX_DUMP_DEPTH) return
        val id = root.viewIdResourceName
        if (id != null && id.contains(keyword, ignoreCase = true)) {
            Log.d(TAG, "[ids] $id text=${root.text} visible=${root.isVisibleToUser}")
        }
        for (i in 0 until root.childCount) {
            dumpMatchingIds(root.getChild(i) ?: continue, keyword, depth + 1)
        }
    }

    fun dumpTree(root: AccessibilityNodeInfo?, depth: Int = 0) {
        if (root == null) return
        if (depth >= MAX_DUMP_DEPTH) {
            Log.w(TAG, "dumpTree : profondeur maximale atteinte, arrêt de la récursion")
            return
        }
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
