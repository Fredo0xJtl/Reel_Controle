package com.focusreels.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusreels.app.FocusReelsApplication
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.data.repository.HistoryRepository
import com.focusreels.app.domain.SwipeSessionTracker
import com.focusreels.app.util.AppIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Service d'accessibilité central (cahier des charges §4.2).
 * Fonctionne uniquement en local : aucune donnée observée n'est transmise à un tiers.
 *
 * La reconnaissance fine de l'UI Instagram est déléguée à [InstagramUiDetector] (module isolé,
 * §4.5) pour faciliter les mises à jour en cas de changement d'interface d'Instagram.
 */
class ReelsAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ReelsAccessibilityService"

        /**
         * Délai minimal entre deux redirections. Sans ce garde-fou, un écran Reels émet
         * plusieurs événements de contenu par seconde et le service enchaîne les retours
         * arrière, ce qui sort l'utilisateur d'Instagram voire d'autres applications.
         */
        private const val BLOCK_COOLDOWN_MS = 1_500L

        /**
         * Passer à true pour tracer l'arbre d'accessibilité complet et recalibrer les
         * identifiants d'[InstagramUiDetector] après une mise à jour d'Instagram (§4.5).
         */
        private const val DIAGNOSTIC_DUMP = false

        /**
         * `AccessibilityEvent` est déclenché par un *changement* d'arbre UI, pas en continu :
         * une vidéo Reels qui joue simplement ne génère plus aucun événement. Si notre clic sur
         * l'onglet Accueil arrive juste au moment d'un vrai tap physique de l'utilisateur et que
         * ce dernier « gagne la course » (Reels reste affiché), aucun nouvel événement n'arrivera
         * jamais pour nous permettre de réessayer : le Reels resterait bloqué ouvert
         * indéfiniment. On vérifie donc explicitement le résultat après un court délai, plutôt
         * que de dépendre uniquement de l'arrivée d'un futur événement (constat empirique §4.5).
         */
        // Suffisamment long pour laisser l'animation de changement d'onglet d'Instagram se
        // terminer (constat empirique : une vérification trop rapide, ~400ms, pouvait lire
        // l'onglet Reels comme encore « sélectionné » en pleine transition et déclencher un
        // second clic sur Accueil alors que le premier avait déjà réussi — d'où un aller-retour
        // visible entre les deux onglets, Instagram traitant un second tap sur Accueil comme une
        // demande de rafraîchissement/retour en haut du flux).
        private const val VERIFY_DELAY_MS = 800L
        private const val MAX_VERIFY_RETRIES = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var blockedAppRepository: BlockedAppRepository
    private lateinit var historyRepository: HistoryRepository
    private var swipeTracker = SwipeSessionTracker(toleratedSwipes = 1)
    private var lastBlockUptimeMs = 0L

    /**
     * Empêche deux chaînes de redirection (clic + vérifications différées) de tourner en
     * parallèle. Constat empirique : une chaîne dure jusqu'à ~1,6 s (plusieurs tentatives
     * espacées de [VERIFY_DELAY_MS]), soit plus que l'ancien [BLOCK_COOLDOWN_MS] (1,5 s). Un
     * nouvel événement légitime pouvait donc démarrer une seconde chaîne avant la fin de la
     * première : les deux cliquaient sur l'onglet Accueil presque simultanément, ce qu'Instagram
     * interprète comme une demande de rafraîchissement du flux (d'où les rafraîchissements en
     * rafale observés). Cette garde rend les chaînes strictement séquentielles.
     */
    private var redirectChainActive = false

    /**
     * Cache synchrone de l'état d'activation, alimenté par [BlockedAppRepository.observe].
     * Nécessaire car [onAccessibilityEvent] doit décider *immédiatement* s'il clique sur
     * l'onglet Accueil : le nœud d'accessibilité capturé n'est valide qu'à l'instant présent,
     * une lecture asynchrone de la base risquerait d'agir sur un nœud périmé.
     */
    private var blockingEnabledCache = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as FocusReelsApplication
        blockedAppRepository = BlockedAppRepository(app.database)
        historyRepository = HistoryRepository(app.database)
        scope.launch {
            blockedAppRepository.observe(AppIds.INSTAGRAM).collect { entity ->
                blockingEnabledCache = entity?.blockingEnabled == true
            }
        }
        Log.i(TAG, "Service d'accessibilité connecté et prêt")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != AppIds.INSTAGRAM) return

        Log.v(TAG, "Événement AccessibilityEvent reçu : type=${event.eventType}, package=${event.packageName}")

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
            // Un événement d'Instagram peut arriver alors qu'une autre application occupe
            // l'écran (liste des applications récentes, écran d'accueil, notification…).
            // Rediriger dans ce cas ramènerait Instagram au premier plan : exactement
            // l'inverse du comportement attendu.
            if (root.packageName?.toString() != AppIds.INSTAGRAM) {
                Log.v(TAG, "Instagram n'est pas au premier plan (${root.packageName}), pas d'action")
                return
            }

            if (DIAGNOSTIC_DUMP) {
                Log.d(TAG, "--- Début du dump de l'arbre d'accessibilité ---")
                InstagramUiDetector.dumpTree(root)
                Log.d(TAG, "--- Fin du dump ---")
            }

            if (InstagramUiDetector.isReelsOpenedFromDirectMessage(root)) {
                swipeTracker.onDmReelsOpened()
                return
            }

            val isGeneralFeed = InstagramUiDetector.isGeneralReelsFeed(root)
            val treatAsGeneralFeed = isGeneralFeed || swipeTracker.shouldTreatAsGeneralFeed()

            if (treatAsGeneralFeed) {
                // Le nœud de l'onglet Accueil doit être récupéré ici, pendant que `root` est
                // encore valide, et transmis tel quel : c'est le seul moment où il est garanti
                // à jour (§ constat empirique, cf. commentaire sur blockingEnabledCache).
                val homeTab = InstagramUiDetector.findHomeTabNode(root)
                blockIfEnabled(homeTab)
            }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun handleScroll() {
        swipeTracker.onSwipeDetected()
    }

    private fun blockIfEnabled(homeTab: AccessibilityNodeInfo?) {
        if (!blockingEnabledCache) {
            @Suppress("DEPRECATION")
            homeTab?.recycle()
            Log.v(TAG, "Blocage désactivé actuellement, pas d'action")
            return
        }

        if (redirectChainActive) {
            @Suppress("DEPRECATION")
            homeTab?.recycle()
            Log.v(TAG, "Une chaîne de redirection est déjà en cours, on laisse le temps à l'UI de réagir")
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastBlockUptimeMs < BLOCK_COOLDOWN_MS) {
            @Suppress("DEPRECATION")
            homeTab?.recycle()
            Log.v(TAG, "Redirection terminée trop récemment, on laisse le temps à l'UI de réagir")
            return
        }

        redirectChainActive = true
        performRedirectAndVerify(homeTab, retryCount = 0)
    }

    /** Marque la fin d'une chaîne de redirection : à appeler sur chaque sortie de la chaîne. */
    private fun endRedirectChain() {
        lastBlockUptimeMs = SystemClock.uptimeMillis()
        redirectChainActive = false
    }

    /**
     * Effectue la redirection puis vérifie son effet réel après un court délai, en retentant
     * si nécessaire (cf. [MAX_VERIFY_RETRIES]). Contourne volontairement le cooldown et le
     * cache d'activation : une vérification n'est pas un nouvel événement de blocage, c'est la
     * confirmation qu'un blocage déjà décidé a bien abouti.
     */
    private fun performRedirectAndVerify(homeTab: AccessibilityNodeInfo?, retryCount: Int) {
        // Cliquer sur l'onglet Accueil change d'onglet sans jamais quitter Instagram.
        // GLOBAL_ACTION_BACK est un repli : selon la pile interne d'Instagram au moment de
        // l'événement, il peut soit ne rien faire, soit faire sortir complètement de l'app
        // (constaté empiriquement quand l'onglet Reels est la page racine).
        val navigatedViaTab = homeTab?.let { node ->
            val performed = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            @Suppress("DEPRECATION")
            node.recycle()
            performed
        } ?: false

        if (navigatedViaTab) {
            Log.i(TAG, "Blocage Reels activé : clic sur l'onglet Accueil (tentative #${retryCount + 1})")
        } else {
            Log.w(TAG, "Onglet Accueil introuvable ou clic refusé, repli sur l'action Retour globale")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        swipeTracker.reset()

        if (retryCount == 0) {
            scope.launch {
                historyRepository.recordAttempt(AppIds.INSTAGRAM)
            }
        }

        if (retryCount >= MAX_VERIFY_RETRIES) {
            endRedirectChain()
            return
        }

        // Dispatchers.Main : rootInActiveWindow / performAction doivent s'exécuter sur le
        // même thread que les événements d'accessibilité (le thread principal du service),
        // pas sur le pool par défaut utilisé pour les écritures en base.
        scope.launch(Dispatchers.Main) {
            delay(VERIFY_DELAY_MS)
            val stillOnReels = try {
                rootInActiveWindow
            } catch (_: Exception) {
                null
            }
            try {
                val stillBlocked = stillOnReels != null &&
                        stillOnReels.packageName?.toString() == AppIds.INSTAGRAM &&
                        blockingEnabledCache &&
                        InstagramUiDetector.isGeneralReelsFeed(stillOnReels)

                if (stillBlocked) {
                    Log.w(TAG, "Toujours sur Reels après redirection, nouvelle tentative")
                    val retryHomeTab = InstagramUiDetector.findHomeTabNode(stillOnReels)
                    performRedirectAndVerify(retryHomeTab, retryCount + 1)
                } else {
                    endRedirectChain()
                }
            } finally {
                @Suppress("DEPRECATION")
                stillOnReels?.recycle()
            }
        }
    }

    override fun onInterrupt() {
        // Rien à nettoyer : aucun état persistant hors base locale.
    }
}
