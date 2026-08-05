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
    val REELS_TAB_VIEW_IDS = listOf(
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

    /**
     * Barre de titre du lecteur Reels plein écran (constat empirique, test terrain, dump
     * diagnostic) : présente que le Reels soit ouvert depuis l'onglet Reels dédié (texte
     * "Reels"), depuis la grille Explorer/Recherche (texte "Explorer"), un profil, un hashtag,
     * etc. — le lecteur plein écran est le même composant partout, seul le titre change selon
     * l'origine. Contrairement à l'ancienne approche abandonnée (détection de `clips_viewer_*`
     * en général, testant uniquement l'onglet Reels sélectionné), cette barre de titre n'apparaît que dans le lecteur
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
     * Détecte le lecteur Reels plein écran peu importe son origine (Explorer, profil, hashtag…) —
     * pas seulement l'onglet Reels dédié. Corrige un contournement constaté (test terrain) : un
     * Reels ouvert depuis la grille Recherche/Explorer n'était pas détecté, laissant l'utilisateur
     * défiler indéfiniment.
     *
     * L'appelant doit exclure au préalable le contexte DM ([isReelViewerFromDirectMessage]) et
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
            // Contrairement à [dumpTree], les enfants n'étaient pas recyclés ici — fuite d'un
            // AccessibilityNodeInfo par nœud de l'arbre à chaque appel. Sans conséquence tant que
            // DIAGNOSTIC_DUMP reste désactivé (seul appelant), mais piégeux pour une future session
            // de diagnostic terrain où cette fonction tournerait à la cadence du scan (jusqu'à 33
            // fois/seconde) sur l'arbre entier.
            val child = root.getChild(i) ?: continue
            dumpMatchingIds(child, keyword, depth + 1)
            @Suppress("DEPRECATION")
            child.recycle()
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
