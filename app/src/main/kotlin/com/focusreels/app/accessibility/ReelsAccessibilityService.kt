package com.focusreels.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusreels.app.FocusReelsApplication
import com.focusreels.app.data.repository.BlockedAppRepository
import com.focusreels.app.data.repository.HistoryRepository
import com.focusreels.app.domain.SwipeSessionTracker
import com.focusreels.app.util.AppIds
import com.focusreels.app.util.Defaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
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

        /**
         * Garde-fou de sécurité uniquement (protection contre une boucle infinie en cas de bug),
         * PAS une limite fonctionnelle. Avant ce correctif, la chaîne abandonnait après 2
         * tentatives même si le Reels restait affiché ; un Reels qui joue sans interaction ne
         * génère plus aucun événement d'accessibilité (cf. commentaire plus haut), donc rien ne
         * relançait la vérification après l'abandon — un utilisateur cliquant plusieurs fois de
         * suite sur l'onglet Reels finissait par "gagner" et voir son Reels rester affiché
         * (constat empirique, contournement du blocage). Tant que le blocage est actif et que le
         * Reels général reste détecté, la chaîne doit continuer ; seule [blockingEnabledCache]
         * passant à false (déblocage via friction) ou la sortie d'Instagram y met fin. ~160
         * tentatives × 800ms ≈ 2 minutes avant abandon de sécurité, largement suffisant pour ne
         * jamais être atteint en usage normal.
         */
        private const val MAX_VERIFY_RETRIES = 160

        /**
         * Délai minimal entre deux analyses de l'arbre d'accessibilité. `TYPE_WINDOW_CONTENT_CHANGED`
         * peut être émis plusieurs fois par seconde pendant qu'un Reels défile ; sans throttle,
         * chaque événement déclenche plusieurs `findAccessibilityNodeInfosByViewId`/`ByText` sur tout
         * l'arbre Instagram, coûteux en CPU/batterie pour un gain de réactivité imperceptible.
         */
        private const val SCAN_THROTTLE_MS = 30L

        /**
         * Fenêtre pendant laquelle on considère qu'on est encore en contexte DM après avoir vu
         * une conversation, le temps que le lecteur plein écran finisse de s'ouvrir
         * (cf. [isBlockedReelsScreen]).
         */
        /** Passes consécutives où le lecteur doit être visible avant d'agir (cf. [isBlockedReelsScreen]). */
        private const val VIEWER_DEBOUNCE_COUNT = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var blockedAppRepository: BlockedAppRepository
    private lateinit var historyRepository: HistoryRepository
    private var swipeTracker = SwipeSessionTracker(toleratedSwipes = Defaults.TOLERATED_SWIPES_AFTER_DM)
    private var lastBlockUptimeMs = 0L
    private var lastScanUptimeMs = 0L

    /** Compteur anti-rebond du lecteur plein écran (cf. [VIEWER_DEBOUNCE_COUNT]). */
    private var consecutiveViewerDetections = 0

    /** True tant que le lecteur plein écran était ouvert lors de la dernière passe (cf. [isBlockedReelsScreen]). */
    private var wasViewerOpenLastScan = false

    /** Origine DM du lecteur actuellement ouvert, figée à l'ouverture (cf. [isBlockedReelsScreen]). */
    private var currentViewerIsDm = false

    /** Origine feed Accueil du lecteur actuellement ouvert, figée à l'ouverture (cf. [isBlockedReelsScreen]). */
    private var currentViewerIsFromFeed = false

    /** True une fois que l'origine du lecteur a été tranché pour l'ouverture en cours (cf. [isBlockedReelsScreen]). */
    private var viewerOriginDecided = false


    /**
     * Empêche deux chaînes de redirection (clic + vérifications différées) de tourner en
     * parallèle. Constat empirique : une chaîne dure jusqu'à ~1,6 s (plusieurs tentatives
     * espacées de [VERIFY_DELAY_MS]), soit plus que l'ancien [BLOCK_COOLDOWN_MS] (1,5 s). Un
     * nouvel événement légitime pouvait donc démarrer une seconde chaîne avant la fin de la
     * première : les deux cliquaient sur l'onglet Accueil presque simultanément, ce qu'Instagram
     * interprète comme une demande de rafraîchissement du flux (d'où les rafraîchissements en
     * rafale observés). Cette garde rend les chaînes strictement séquentielles.
     */
    @Volatile
    private var redirectChainActive = false

    /**
     * Cache synchrone de l'état d'activation, alimenté par [BlockedAppRepository.observe].
     * Nécessaire car [onAccessibilityEvent] doit décider *immédiatement* s'il clique sur
     * l'onglet Accueil : le nœud d'accessibilité capturé n'est valide qu'à l'instant présent,
     * une lecture asynchrone de la base risquerait d'agir sur un nœud périmé.
     *
     * `@Volatile` : écrit depuis le collecteur de Flow (Dispatchers.Default) et lu depuis le
     * thread principal (callbacks d'accessibilité) — sans cette annotation, rien ne garantit
     * qu'une écriture soit visible depuis l'autre thread.
     */
    @Volatile
    private var blockingEnabledCache = false

    private lateinit var audioManager: AudioManager

    /** Volume STREAM_MUSIC sauvegardé juste avant la coupure, pour restauration exacte. */
    @Volatile private var savedMusicVolume: Int? = null

    /**
     * Coupe le son dès qu'un Reels bloqué est détecté (§ demande utilisateur : éviter que le
     * son du Reels sorte le temps que la redirection s'exécute). N'agit que sur STREAM_MUSIC
     * (musique/vidéo), pas sur la sonnerie ni les notifications.
     *
     * Utilise setStreamVolume(0) plutôt qu'ADJUST_MUTE/ADJUST_UNMUTE : sur certains appareils
     * (OEM Samsung/Xiaomi notamment) le flag "mute" relatif a une latence perceptible ou un
     * comportement moins prévisible qu'un réglage direct du niveau, ce qui accentuait le "blip"
     * audio audible avant la coupure effective.
     */
    private fun muteMediaAudio() {
        try {
            if (savedMusicVolume == null) {
                savedMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de couper le son : ${e.message}")
        }
    }

    /** Restaure le son une fois la redirection confirmée (ou en cas d'abandon, cf. [endRedirectChain]). */
    private fun unmuteMediaAudio() {
        try {
            val saved = savedMusicVolume ?: return
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, saved, 0)
            savedMusicVolume = null
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de restaurer le son : ${e.message}")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = application as FocusReelsApplication
        blockedAppRepository = BlockedAppRepository(app.database)
        historyRepository = HistoryRepository(app.database)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        scope.launch {
            blockedAppRepository.observe(AppIds.INSTAGRAM).collect { entity ->
                blockingEnabledCache = entity?.blockingEnabled == true
                swipeTracker.toleratedSwipes = entity?.toleratedSwipesAfterDm ?: Defaults.TOLERATED_SWIPES_AFTER_DM
            }
        }

        // Scan actif indépendant des événements d'accessibilité. Bug constaté en test terrain :
        // pendant la lecture d'un Reels, tant que rien ne change dans l'arbre (pas de nouveau
        // commentaire, pas de rotation de légende…), Instagram n'émet plus aucun
        // TYPE_WINDOW_STATE_CHANGED / TYPE_WINDOW_CONTENT_CHANGED pendant plusieurs secondes —
        // observé jusqu'à 10 s d'affilée dans les logs. [handleWindowUpdate] étant purement
        // déclenché par ces événements, le blocage restait alors en sommeil tout ce temps sur
        // l'onglet Reels dédié. Cette boucle republie un scan à intervalle fixe, avec le même
        // throttle ([SCAN_THROTTLE_MS]) qui protège déjà le chemin événementiel.
        //
        // Garde-fou (bug YouTube) : n'exécuter le scan que si Instagram a le focus. Sans ce
        // filtrage, une fenêtre Instagram résiduelle en arrière-plan était trouvée par
        // [findInstagramRoot], déclenchant une chaîne de vérification qui s'appliquait à l'app
        // réellement au premier plan (écran noir sur YouTube).
        scope.launch(Dispatchers.Main) {
            while (true) {
                delay(SCAN_THROTTLE_MS)
                try {
                    if (isInstagramForeground()) {
                        handleWindowUpdate()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur pendant le scan périodique : ${e.message}")
                }
            }
        }
        Log.i(TAG, "Service d'accessibilité connecté et prêt")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        // Le service peut être désactivé puis réactivé par l'utilisateur ; sans annulation du
        // scope, chaque cycle laisse un collecteur du Flow Room tourner indéfiniment en tâche
        // de fond (fuite cumulative).
        scope.coroutineContext.cancelChildren()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != AppIds.INSTAGRAM) return

        Log.v(TAG, "Événement AccessibilityEvent reçu : type=${event.eventType}, package=${event.packageName}")

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowUpdate()

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> handleScroll(event)
        }
    }

    /**
     * Détermine si l'écran actuellement affiché doit être traité comme un Reels à bloquer :
     * onglet Reels dédié, lecteur plein écran ouvert autrement (Explorer, profil, hashtag…), ou
     * Reels DM dont la tolérance de swipes est dépassée.
     *
     * Centralisé et réutilisé à la fois par la détection initiale ([handleWindowUpdate]) et par
     * la vérification post-redirection ([performRedirectAndVerify]) : avant ce correctif, la
     * vérification ne testait que [InstagramUiDetector.isGeneralReelsFeed] — un Reels ouvert
     * depuis Explorer qui résistait au premier retour arrière n'était donc plus jamais re-détecté
     * ni retenté, la chaîne abandonnant après un seul essai (bypass constaté en test terrain avec
     * des clics répétés).
     */
    private fun isBlockedReelsScreen(root: AccessibilityNodeInfo): Boolean {
        // Anti-rebond (constat terrain) : sur certains appareils, le flag `isSelected` du bouton
        // d'onglet Reels/Accueil de la barre de navigation scintille pendant la transition
        // d'animation (vrai un instant, faux l'instant suivant), y compris quand l'utilisateur
        // n'est PAS sur l'onglet Reels (ex : simple appui sur le bouton DM depuis Accueil). Un
        // seul événement positif isolé déclenchait alors une redirection à tort, expulsant
        // l'utilisateur d'écrans n'ayant rien à voir avec les Reels. On n'agit donc qu'après deux
        // détections positives consécutives (~200 ms d'écart via le throttle), ce qui filtre le
        // scintillement tout en restant largement en deçà du temps nécessaire pour regarder un
        // Reels.
        // Signal unique : la présence réelle du lecteur Reels plein écran à l'écran.
        //
        // L'ancienne détection principale reposait sur le flag `isSelected` du bouton d'onglet
        // Reels. Les sniffs terrain (Galaxy S24) ont montré que ce flag est structurellement
        // inutilisable : il scintille pendant les transitions, reste collé à `true` après avoir
        // quitté l'onglet, et peut être vrai en même temps que celui de l'onglet Accueil. D'où
        // toute une série de symptômes qui se contredisaient — DM fermés à tort, écrans sans
        // rapport redirigés, et blocages qui retentaient des dizaines de fois sans effet.
        //
        // `clips_viewer_action_bar_title` (confirmé par dump) n'est présent QUE quand le lecteur
        // plein écran est réellement affiché, quelle que soit son origine : onglet Reels dédié,
        // Explorer, profil, hashtag ou DM. Il ne s'affiche jamais sur une carte Reels intégrée au
        // flux Accueil, donc pas de faux positif au défilement normal du feed.
        if (!InstagramUiDetector.isImmersiveReelsViewerOpen(root)) {
            consecutiveViewerDetections = 0
            wasViewerOpenLastScan = false
            viewerOriginDecided = false
            currentViewerIsDm = false
            currentViewerIsFromFeed = false
            return false
        }
        wasViewerOpenLastScan = true

        // Anti-rebond : pendant le défilement du flux Accueil, le lecteur peut apparaître visible
        // le temps d'une unique passe (transition/préchargement) — mesuré en test terrain : une
        // occurrence isolée au milieu du feed, contre six consécutives quand l'onglet Reels est
        // réellement affiché. Exiger deux passes consécutives élimine ce cas sans retarder
        // perceptiblement un vrai blocage.
        consecutiveViewerDetections++
        if (consecutiveViewerDetections < VIEWER_DEBOUNCE_COUNT) return false

        // Origine déterminée UNE SEULE FOIS par ouverture de lecteur, et seulement une fois le
        // debounce ci-dessus atteint — pas dès la toute première passe. Bug constaté en test
        // terrain : les marqueurs de partage DM mettent quelques dizaines de ms de plus à se
        // rendre que le conteneur du lecteur lui-même. Trancher l'origine sur la toute première
        // passe capturait donc parfois un vrai Reels DM avant que ses marqueurs n'existent.
        // Attendre le debounce laisse le temps aux marqueurs de se stabiliser.
        if (!viewerOriginDecided) {
            currentViewerIsDm = InstagramUiDetector.isReelViewerFromDirectMessage(root)
            currentViewerIsFromFeed = InstagramUiDetector.isReelViewerFromGeneralFeed(root)
            viewerOriginDecided = true
            val origin = when {
                currentViewerIsDm -> "DM"
                currentViewerIsFromFeed -> "feed Accueil/Explorer/profil"
                else -> "onglet Reels dédié"
            }
            Log.d(TAG, "Origine du lecteur tranchée : $origin")
            if (currentViewerIsDm || currentViewerIsFromFeed) {
                // DM et feed Accueil utilisent le même système de tolérance de swipes.
                swipeTracker.onDmReelsOpened()
            } else {
                // Onglet Reels dédié : pas de tolérance, blocage immédiat. Purger tout résidu.
                swipeTracker.reset()
            }
        }

        if ((currentViewerIsDm || currentViewerIsFromFeed) && swipeTracker.isWithinDmTolerance()) {
            Log.d(TAG, "Reels ${if (currentViewerIsDm) "DM" else "feed"} dans la tolérance de swipes : pas de blocage")
            return false
        }

        return true
    }

    /**
     * Localise la racine Instagram, y compris en affichage multi-fenêtre (écran partagé).
     *
     * `rootInActiveWindow` ne renvoie que la fenêtre ayant le focus clavier/système : en écran
     * partagé (ex. Instagram + une vidéo YouTube dans l'autre volet), Instagram reste visible et
     * ses événements d'accessibilité continuent d'arriver, mais dès que le focus est sur l'autre
     * appli, `rootInActiveWindow` renvoie la racine de CETTE autre appli — plus moyen de lire
     * l'écran Instagram, donc plus de détection ni de blocage (constat terrain). On parcourt donc
     * la liste de toutes les fenêtres visibles pour retrouver celle d'Instagram explicitement.
     */
    /**
     * Vrai uniquement si Instagram a réellement le focus système à cet instant précis.
     *
     * Distinct de [findInstagramRoot] : ce dernier balaie *toutes* les fenêtres pour retrouver
     * Instagram même sans focus (utile pour la détection/mute en écran partagé), mais
     * `performGlobalAction(GLOBAL_ACTION_BACK)` s'applique toujours à la fenêtre qui a le focus
     * au moment de son exécution — jamais à une fenêtre Instagram choisie explicitement. Bug
     * constaté en test terrain : après être passé sur YouTube, une fenêtre Instagram résiduelle
     * (chaîne de vérification encore active, ou reliquat en arrière-plan) était encore retrouvée
     * par [findInstagramRoot], déclenchant un retour arrière qui s'appliquait en réalité à
     * YouTube au premier plan (écran noir, application à relancer). Tout retour arrière doit donc
     * être gardé par ce contrôle, jamais par [findInstagramRoot] seul.
     */
    private fun isInstagramForeground(): Boolean =
        try {
            rootInActiveWindow?.packageName?.toString() == AppIds.INSTAGRAM
        } catch (_: Exception) {
            false
        }

    private fun findInstagramRoot(): AccessibilityNodeInfo? {
        try {
            val active = rootInActiveWindow
            if (active?.packageName?.toString() == AppIds.INSTAGRAM) return active
        } catch (_: Exception) {
            // Ignoré : on retente via la liste des fenêtres ci-dessous.
        }
        return try {
            windows?.firstNotNullOfOrNull { window ->
                val root = window.root
                if (root?.packageName?.toString() == AppIds.INSTAGRAM) root else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de localiser la fenêtre Instagram (multi-fenêtre) : ${e.message}")
            null
        }
    }

    private fun handleWindowUpdate() {
        // Throttle indépendant du cooldown de blocage (cf. [SCAN_THROTTLE_MS]) : n'analyse pas
        // l'arbre d'accessibilité plus souvent que nécessaire même quand aucun blocage n'est en
        // jeu (déblocage désactivé, écran hors flux Reels…).
        val now = SystemClock.uptimeMillis()
        if (now - lastScanUptimeMs < SCAN_THROTTLE_MS) return
        lastScanUptimeMs = now

        // Fail-open : toute exception lève l'appel sans bloquer (§4.5).
        val root = findInstagramRoot() ?: return

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
                // Dump complet des ids (mot-clé vide = tout) : le lecteur DM ne présente aucun des
                // marqueurs attendus, il faut recalibrer entièrement sur cette version d'Instagram.
                InstagramUiDetector.dumpMatchingIds(root, "")
            }

            // [isBlockedReelsScreen] est la seule fonction qui met à jour l'état stable
            // (wasViewerOpenLastScan / currentViewerIsDm), figé une seule fois à l'ouverture du
            // lecteur : on l'appelle donc en premier, et le mute ci-dessous se contente de LIRE
            // cet état déjà tranché plutôt que de recalculer l'origine DM en direct sur ce même
            // `root`. Recalculer ici aurait réintroduit le bug corrigé dans [isBlockedReelsScreen]
            // : les marqueurs de partage DM s'estompent après quelques secondes (comme des
            // contrôles vidéo), et un recalcul à chaque passe perdait la tolérance en cours de
            // lecture sans qu'aucun swipe n'ait eu lieu.
            val shouldBlock = isBlockedReelsScreen(root)
            val viewerOpen = InstagramUiDetector.isImmersiveReelsViewerOpen(root)

            // Le son est coupé dès la toute première détection du lecteur plein écran, séparément
            // de la décision de blocage : celle-ci exige deux passes consécutives (cf.
            // [isBlockedReelsScreen]) pour ignorer les faux positifs de transition du feed, mais
            // attendre cette confirmation avant de couper le son laissait passer ~200-400 ms
            // audibles à chaque ouverture d'un Reels (constat terrain).
            val shouldStaySilenced = viewerOpen && !(currentViewerIsDm && swipeTracker.isWithinDmTolerance())
            if (blockingEnabledCache && shouldStaySilenced) {
                muteMediaAudio()
            } else if (!redirectChainActive) {
                // Symétrique du mute ci-dessus : si l'unique détection positive était un
                // scintillement isolé (transition du feed) qui ne déclenche jamais de blocage
                // confirmé, rien d'autre ne restaurerait le son. On ne touche à rien pendant
                // qu'une chaîne de redirection est en cours : c'est [endRedirectChain] qui gère
                // la restauration dans ce cas, une fois la sortie confirmée.
                unmuteMediaAudio()
            }

            // Ne déclenche une chaîne de redirection que si Instagram a réellement le focus
            // maintenant (cf. [isInstagramForeground]) : `root` peut provenir du repli
            // multi-fenêtre de [findInstagramRoot] et donc être une fenêtre Instagram résiduelle
            // en arrière-plan, sans quoi `performGlobalAction(GLOBAL_ACTION_BACK)` risquerait de
            // s'appliquer à l'application réellement au premier plan (constat terrain : écran
            // noir sur YouTube).
            if (shouldBlock && isInstagramForeground()) {
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

    private fun handleScroll(event: AccessibilityEvent) {
        // Ne compter que les swipes faits DANS le lecteur plein écran. Bug constaté en test
        // terrain : tout défilement Instagram déclenchait TYPE_VIEW_SCROLLED, y compris faire
        // défiler la conversation DM elle-même pour retrouver le message contenant le Reels —
        // avec une tolérance par défaut d'un seul swipe, ce simple geste épuisait le quota avant
        // même d'ouvrir le Reels, provoquant un blocage immédiat à l'ouverture.
        val root = findInstagramRoot() ?: return
        if (!InstagramUiDetector.isImmersiveReelsViewerOpen(root)) return

        // Restreindre en plus à la source de l'événement : à l'ouverture d'un Reels DM, la mise
        // en page de la barre de réponse / du composeur déclenche elle aussi des
        // TYPE_VIEW_SCROLLED (settling initial, pas un geste de l'utilisateur), ce qui épuisait la
        // tolérance dès l'ouverture (bug constaté en test terrain : blocage immédiat malgré aucun
        // swipe réel). Seul le défilement du pager du lecteur (`clips_viewer_view_pager` /
        // `clips_swipe_refresh_container`) correspond à un vrai swipe entre Reels.
        val sourceId = try {
            val source = event.source
            val id = source?.viewIdResourceName
            @Suppress("DEPRECATION")
            source?.recycle()
            id
        } catch (_: Exception) {
            null
        }
        if (sourceId == "${AppIds.INSTAGRAM}:id/clips_viewer_view_pager" ||
            sourceId == "${AppIds.INSTAGRAM}:id/clips_swipe_refresh_container"
        ) {
            swipeTracker.onSwipeDetected()
        }
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
        // Restaure le son à chaque fin de chaîne, y compris les abandons (erreur, sécurité) :
        // mieux vaut restaurer un peu trop souvent (ADJUST_UNMUTE est sans effet si déjà audible)
        // que laisser le son coupé après une redirection.
        unmuteMediaAudio()
    }

    /**
     * Effectue la redirection puis vérifie son effet réel après un court délai, en retentant
     * si nécessaire (cf. [MAX_VERIFY_RETRIES]). Contourne volontairement le cooldown et le
     * cache d'activation : une vérification n'est pas un nouvel événement de blocage, c'est la
     * confirmation qu'un blocage déjà décidé a bien abouti.
     */
    private fun performRedirectAndVerify(homeTab: AccessibilityNodeInfo?, retryCount: Int) {
        // Garde-fou : un nœud d'accessibilité périmé ou scellé peut faire lever `performAction`
        // (constat empirique). Sans ce try/catch, une exception ici laisserait
        // `redirectChainActive` bloqué à `true` pour toujours, gelant le blocage jusqu'au
        // redémarrage du service — d'où la couverture de l'intégralité du corps de la méthode.
        try {
            // Garde-fou critique : `performGlobalAction(GLOBAL_ACTION_BACK)` s'applique à la
            // fenêtre ayant le focus au moment de son exécution, jamais à Instagram
            // spécifiquement. Sans ce contrôle, une fenêtre Instagram résiduelle en arrière-plan
            // (utilisateur passé sur une autre app pendant qu'une chaîne de vérification tourne
            // encore) provoquait un retour arrière qui s'appliquait à l'app réellement au premier
            // plan — constat terrain : écran noir sur YouTube, obligé de la relancer.
            if (!isInstagramForeground()) {
                Log.w(TAG, "Instagram n'a plus le focus, abandon de la chaîne de redirection (pas de retour arrière envoyé)")
                @Suppress("DEPRECATION")
                homeTab?.recycle()
                endRedirectChain()
                return
            }

            // Retour arrière en PRIORITÉ, clic sur l'onglet Accueil en repli.
            //
            // L'ordre était inverse auparavant (clic d'onglet d'abord), au motif qu'il ne fait
            // jamais quitter Instagram. Mais le seul écran qu'on bloque désormais est le lecteur
            // Reels plein écran (cf. [isBlockedReelsScreen]), et les sniffs terrain montrent que
            // le clic sur l'onglet Accueil y est purement inopérant : `performAction` renvoyait
            // `true` alors que rien ne bougeait, la chaîne enchaînant des dizaines de tentatives
            // sans jamais fermer le lecteur. Le retour arrière, lui, ferme bien ce lecteur.
            performGlobalAction(GLOBAL_ACTION_BACK)
            Log.i(TAG, "Blocage Reels activé : retour arrière (tentative #${retryCount + 1})")

            // Le nœud d'onglet n'est plus cliqué mais doit être libéré : l'appelant l'a capturé
            // pour nous et personne d'autre ne le recyclera.
            @Suppress("DEPRECATION")
            homeTab?.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lors de la redirection : ${e.message}")
            endRedirectChain()
            return
        }

        if (retryCount == 0) {
            // Ne réinitialiser le tracker qu'au tout début d'une NOUVELLE chaîne de blocage.
            // Le réinitialiser à chaque retry (comme avant) effaçait le contexte DM même quand
            // l'utilisateur avait déjà quitté cette chaîne pour un tout autre écran (retries d'une
            // chaîne précédente encore en cours en arrière-plan) — constat terrain : la fenêtre
            // DM se refermait alors que l'utilisateur était dans la fenêtre de tolérance.
            swipeTracker.reset()
            scope.launch {
                historyRepository.recordAttempt(AppIds.INSTAGRAM)
            }
        }

        if (retryCount >= MAX_VERIFY_RETRIES) {
            Log.w(TAG, "Abandon de sécurité après $MAX_VERIFY_RETRIES tentatives : cas anormal, à investiguer")
            endRedirectChain()
            return
        }

        // Dispatchers.Main : rootInActiveWindow / performAction doivent s'exécuter sur le
        // même thread que les événements d'accessibilité (le thread principal du service),
        // pas sur le pool par défaut utilisé pour les écritures en base.
        scope.launch(Dispatchers.Main) {
            delay(VERIFY_DELAY_MS)
            val stillOnReels = findInstagramRoot()
            try {
                // La garde `homeAlreadySelected` a été retirée avec l'abandon de la détection par
                // onglet sélectionné : elle reposait sur le même flag `isSelected` non fiable, et
                // servait à éviter un reclic d'onglet qu'on ne fait plus (on utilise le retour
                // arrière). Le lecteur plein écran, lui, est un signal direct et sans ambiguïté.
                val stillBlocked = stillOnReels != null &&
                        blockingEnabledCache &&
                        isBlockedReelsScreen(stillOnReels)

                if (stillBlocked) {
                    Log.w(TAG, "Toujours sur Reels après redirection, nouvelle tentative")
                    performRedirectAndVerify(homeTab = null, retryCount = retryCount + 1)
                } else {
                    endRedirectChain()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur lors de la vérification post-redirection : ${e.message}")
                endRedirectChain()
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
