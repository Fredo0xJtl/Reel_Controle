# Journal technique — Réel Contrôle

Journal de développement rédigé a posteriori (2026-08-05) à partir de l'historique complet des sessions de travail, pour usage portfolio. Documente les décisions d'architecture, les problèmes rencontrés en test terrain et les solutions retenues, dans l'ordre chronologique.

**Projet** : APK Android de blocage progressif des Reels Instagram. 100% offline, privacy-first (zéro permission INTERNET, zéro dépendance réseau/analytics). Détection via `AccessibilityService`, friction progressive au déblocage, reverrouillage automatique, distinction Reels onglet dédié vs Reels reçus en DM.

**Stack** : Kotlin, Jetpack Compose / Material3, Room (DB locale), WorkManager, DataStore. Cibles testées : Xiaomi Redmi Note 17 Pro (HyperOS), Samsung Galaxy S24 (OneUI).

---

## 1. Genèse — 2026-08-01

**Contexte** : besoin d'un bloqueur de Reels réellement fiable, sans dépendance cloud. Cahier des charges rédigé en amont, exigence non négociable §4.4 : aucune permission INTERNET.

**Décisions d'architecture** :
- `AccessibilityService` plutôt qu'un VPN local ou un overlay : seule approche capable de lire l'état réel de l'UI Instagram (quel écran est affiché) sans permission réseau.
- Room pour la persistance locale (réglages, historique des tentatives bloquées) — aucune donnée ne sort jamais de l'appareil.
- `FrictionCalculator` isolé en Kotlin pur (aucune dépendance Android) pour être testable unitairement dès le départ.
- Émulateur jugé insuffisant pour valider le comportement réel : décision de valider systématiquement sur téléphone physique (le comportement des services d'accessibilité et de gestion batterie varie fortement selon les surcouches constructeur MIUI/OneUI, non reproductible en émulateur).

**Résultat** : structure de projet (33 fichiers), premier build réussi, 4 tests unitaires `FrictionCalculatorTest` verts, dépôt git initialisé. Ajout du support Samsung S24 en complément du Xiaomi dès la version bêta (détection par cascade : ID → description → activité, pour résister aux différences entre surcouches).

---

## 2. Le service ne détectait rien du tout — 2026-08-04

**Symptôme** : "les réels ne sont pas bloqués" ; le service semblait actif mais n'avait aucun effet, sans qu'aucun log ne remonte — pas même le log de connexion du service.

**Diagnostic** : session de connexion ADB WiFi pour sniffer les logs en direct sur l'appareil réel. Constat : le service ne loggait littéralement rien, y compris au démarrage.

**Cause trouvée** : le flag `flagReportViewIds` était absent de `accessibility_service_config.xml`. Sans ce flag, `findAccessibilityNodeInfosByViewId()` renvoie toujours une liste vide — toute détection basée sur les identifiants de vue Instagram était morte depuis le premier commit, sans erreur visible.

**Solution** : ajout du flag. En parallèle, deux autres bugs racines corrigés dans la même passe :
- Faux positif généralisé sur "Reels" : Instagram n'a qu'une seule Activity Android (`MainTabActivity`) pour tous ses onglets ; chercher la simple présence du texte "Reels" matchait en permanence, quel que soit l'écran affiché. Correction : vérifier que l'onglet est réellement sélectionné.
- Redirection vers Instagram depuis le gestionnaire de tâches système : le service déclenchait un retour arrière dès qu'un événement Instagram était capté, même en arrière-plan, ramenant l'app au premier plan malgré l'utilisateur. Correction : garde sur le package actif + anti-rebond.

**Résultat** : premier blocage effectif observé sur appareil réel.

---

## 3. GLOBAL_ACTION_BACK fermait complètement Instagram

**Symptôme** : "ça en vient aussi à fermer Insta" — sur l'onglet Reels en tant que page racine (sans pile de navigation à dépiler), l'action "retour" système faisait sortir entièrement de l'application.

**Diagnostic** : dump de l'arbre d'accessibilité pour identifier des identifiants stables plutôt que de déduire un comportement.

**Solution** : remplacement de `GLOBAL_ACTION_BACK` par un clic direct sur l'identifiant de l'onglet Accueil (`feed_tab`), indépendant de l'état de la pile de navigation. Retrait d'identifiants génériques trop larges (`clips_video_container`) qui risquaient de confondre une vidéo du feed normal avec le lecteur Reels dédié.

---

## 4. Audit exhaustif de bugs et failles — 2026-08-04

**Contexte** : demande explicite d'un audit complet de l'application ("fais le tour de toute l'apk"), avant de poursuivre les itérations terrain.

**21 problèmes identifiés**, dont 5 critiques :

| # | Problème | Cause | Correction |
|---|---|---|---|
| 1 | Réglage "swipes tolérés après DM" fantôme | Valeur codée en dur, jamais lue depuis Room | Branché sur la vraie valeur en base |
| 2 | Tolérance DM ne se déclenchait jamais | Compteur remis à zéro à chaque `TYPE_WINDOW_CONTENT_CHANGED` (dizaines/seconde) | Logique de comptage revue |
| 3 | Blocage figé définitivement sur exception | `performAction()` hors try/finally, un nœud périmé laissait l'état "verrouillé" à vie | try/catch autour de la chaîne de redirection |
| 4 | Friction contournable en 3 taps | Aucune validation sur les réglages (délai à 0 = déblocage instantané) | Bornes minimales (`coerceAtLeast`) |
| 5 | Double déblocage par double-tap | Activité sans `launchMode="singleTop"` | Ajout du flag |

Autres corrections notables : `@Volatile` sur les caches partagés entre threads, `onUnbind()` pour éviter les fuites de scope Room, throttle sur l'analyse de l'arbre, alerte visuelle si le service d'accessibilité est coupé alors qu'un blocage est censé être actif.

**Non corrigé volontairement** : ProGuard laissé désactivé (risque de casser Room/WorkManager par réflexion sans validation terrain complète) ; la limitation "Forcer l'arrêt" côté OS (annule WorkManager) est une contrainte Android inhérente, non contournable.

**Résultat** : 20/21 points corrigés en une session.

---

## 5. Refonte de la détection — cycle intensif 2026-08-04 → 2026-08-05

Série de régressions croisées découvertes en test terrain sur le Galaxy S24, avec sniff logcat en direct :

- **Rafraîchissement en boucle du feed** : un badge "Reels" intégré au feed Accueil était lu à tort comme "onglet Reels sélectionné". → Mutuelle exclusivité Accueil/Reels dans la détection.
- **Bypass en cliquant plusieurs fois sur l'onglet Reels** : le service abandonnait après 2 tentatives de vérification même si le blocage n'avait pas pris effet.
- **Bypass via l'onglet Recherche/Explorer** : un Reel ouvert depuis la grille de recherche n'émettait aucun événement d'accessibilité détecté par les filtres existants. Le filtre d'événements a été élargi, et un signal fiable trouvé par dump d'arbre (`clips_viewer_action_bar_title`, dont le texte varie selon l'origine : "Explorer" vs "Reels") a permis de détecter le lecteur plein écran quelle que soit sa provenance.
- **Écran partagé (split-screen)** : quand Instagram n'a pas le focus (ex. YouTube en split-screen), `rootInActiveWindow` renvoyait la racine de l'autre application. Correction : recherche explicite de la fenêtre Instagram parmi toutes les fenêtres ouvertes du système.
- **Accumulation de régressions** (flag `isSelected` incohérent selon les écrans, chevauchement DM/Explorer) : décision de refonte du signal de détection. Abandon du flag `isSelected`, jugé structurellement non fiable, au profit du signal unique `clips_viewer_action_bar_title`.

**Découverte clé par mesure (dump complet, pas hypothèse)** : les identifiants du lecteur Reels existent en permanence dans l'arbre d'accessibilité Instagram, préchargés même quand rien n'est affiché à l'écran (62 occurrences invisibles mesurées contre 7 réellement visibles). La simple présence d'un identifiant ne suffit donc jamais à conclure qu'un écran est affiché.

**Solution retenue** : discrimination par `isVisibleToUser` (visibilité effective) plutôt que présence brute dans l'arbre, sur 4 identifiants combinés (au lieu d'1 seul) pour résister aux changements de nommage Instagram, avec anti-rebond sur 2 passes consécutives. 9 tests unitaires ajoutés pour verrouiller la logique DM / tolérance de swipes contre les régressions futures.

---

## 6. Distinction Reels onglet dédié vs Reels en DM — 2026-08-05

**Besoin exprimé** : "l'onglet réel doit être mute et couper automatiquement (...) dans les DM ça fait juste tourner la première vidéo avec le son puis coupe après swipe" — deux comportements de blocage différents selon le contexte d'ouverture du Reels.

### Bug des marqueurs DM mal identifiés

**Symptôme** : en DM, le Reels se fermait alors qu'aucun swipe n'avait été fait par l'utilisateur.

**Diagnostic en plusieurs passes** (le premier correctif n'a pas suffi, chaque itération affinait la cause) :
1. Première tentative : marqueurs `reel_share_item_view` / `direct_reel_share_legibility_gradient_footer`. Insuffisant.
2. Hypothèse d'une race condition : la classification DM/non-DM était réévaluée à chaque passe de scan, alors que les marqueurs DM mettent quelques millisecondes de plus à s'afficher que le conteneur du lecteur. Fix intermédiaire : figer la décision une seule fois à la transition fermé→ouvert du lecteur, après un debounce de 2 passes.
3. **Cause réelle trouvée par dump complet** : `reel_share_item_view` et `direct_reel_share_legibility_gradient_footer` sont les marqueurs de la **bulle Reels dans la liste des messages**, pas du lecteur plein écran lui-même — ils disparaissent dès que le lecteur s'ouvre, ce qui faisait classer à tort tout Reels DM comme "non-DM" après quelques millisecondes.

**Solution finale** : recalibrage vers les vrais marqueurs du lecteur DM plein écran, trouvés par dump terrain : `sender_username_or_fullname`, `sender_profile_pic`, `reel_viewer_message_composer`.

### Bug des faux positifs de swipe

**Symptôme** : le Reels DM était bloqué immédiatement à l'ouverture, sans geste réel de l'utilisateur — tolérance de swipe épuisée sans swipe.

**Cause** : tout événement de scroll Instagram comptait dans le tracker, y compris le scroll de mise en page déclenché par l'apparition du composeur de message DM (barre de réponse qui s'installe à l'ouverture), et le défilement pour retrouver le message dans la conversation.

**Solution** : filtrage des événements de scroll par leur source (`viewIdResourceName`) — seul un scroll provenant du pager du lecteur Reels lui-même est compté comme un vrai swipe.

---

## 7. Latence de blocage variable sur l'onglet Reels dédié — 2026-08-05

**Symptôme** : "ça coupe bien, mais pas tout le temps au même moment" — le temps entre l'ouverture de l'onglet Reels et la redirection automatique variait de 1 à 10 secondes.

**Diagnostic** : le service ne réagissait qu'aux événements d'accessibilité émis par Instagram. Or pendant la lecture d'une vidéo statique, Instagram n'émet aucun événement d'accessibilité pendant plusieurs secondes (jusqu'à 10s observées dans les logs) — aucun scan ne se déclenchait donc pendant ce temps.

**Solution architecturale** : ajout d'un scan périodique actif, indépendant des événements système, tournant en boucle sur le thread principal (`while(true) { delay(SCAN_THROTTLE_MS); handleWindowUpdate() }`). Le throttle a ensuite été réduit progressivement (200ms → 80ms → 30ms) pour stabiliser le temps de réaction perçu, jusqu'à validation terrain par Spout ("ça a l'air de bien fonctionner").

---

## 8. Audit privacy final — 2026-08-05

Vérification systématique demandée avant la phase de design visuel : aucune permission INTERNET, `usesCleartextTraffic=false`, aucune dépendance réseau/analytics/tracking dans le code, `allowBackup=false`, service d'accessibilité `exported=false`, seul point de log de contenu textuel désactivé par défaut (flag `DIAGNOSTIC_DUMP`), export de schéma Room désactivé. Verdict : conforme à l'exigence privacy-first du cahier des charges.

---

## 9. Écran noir sur YouTube et détection cassée par cycles activer/désactiver — 2026-08-05

**Symptôme 1** : "ça me fait toujours cet écran noir quand je veux aller sur YouTube pendant que l'app est activée." Le scan périodique (§7) retrouvait une fenêtre Instagram résiduelle en arrière-plan via le balayage multi-fenêtre et déclenchait un retour arrière qui s'appliquait en réalité à YouTube au premier plan.

**Solution** : garde `isInstagramForeground()` (fenêtre ayant réellement le focus système) avant tout scan périodique et avant tout retour arrière, distincte de `findInstagramRoot()` (balayage multi-fenêtre, utile pour la détection/le mute mais jamais pour une action).

**Symptôme 2** : "j'ai essayé de faire un stress test en activant/désactivant plusieurs fois le blocage et ça a fini par faire buguer le truc." Après plusieurs cycles, le blocage restait activé en apparence mais n'avait plus aucun effet.

**Cause** : les flags d'état (`redirectChainActive`, `viewerOriginDecided`, `swipeTracker`, horodatages…) n'étaient jamais réinitialisés à la reconnexion du service, seulement à la création de l'instance.

**Solution** : reset complet de tous les flags dans `onServiceConnected()`.

---

## 10. Distinguer Reels du feed et onglet Reels dédié — 2026-08-05

**Besoin exprimé** : "quand je clique sur un réel dans le feed, que ça le met en plein écran, tu me laisses le lire, mais si je scroll, là tu bloques" — un Reel ouvert depuis le feed Accueil doit bénéficier de la même tolérance de swipe qu'un Reels DM ; l'onglet Reels dédié doit rester bloqué immédiatement, son coupé.

Cinq bugs successifs découverts par tests répétés avec capture logcat en direct, chacun corrigeant une hypothèse insuffisante de la précédente :

1. **Reel du feed fermé sans swipe** : le compteur de swipes n'était remis à zéro qu'à la détection de l'onglet dédié, jamais à la fermeture normale d'un lecteur — carry-over du compteur entre deux ouvertures successives.
2. **Toujours fermé** : le flag `isSelected` du bouton Reels restait collé à `true` même une fois la barre de navigation masquée par le lecteur plein écran (`isVisibleToUser` ne détecte pas l'occlusion en z-order).
3. **Toujours fermé** : un snapshot de `isSelected` pris juste avant l'ouverture du lecteur arrivait parfois trop tard (le lecteur pouvait s'ouvrir avant le scan suivant).
4. **Décision racine** : abandon complet du flag `isSelected`, jugé structurellement non fiable (scintille, reste collé après avoir quitté l'onglet, contredit lui-même selon le moment de lecture). Remplacé par la détection du **clic explicite** (`TYPE_VIEW_CLICKED`) sur le bouton d'onglet Reels, seul signal représentant une intention utilisateur réelle. Par défaut sans clic récent détecté : classement "feed" (le choix le plus sûr — une tolérance accordée à tort est bien moins gênante qu'un Reel du feed fermé sans swipe).
5. **Faux positifs de clic synthétique** : Instagram émet parfois un `TYPE_VIEW_CLICKED` avec l'ID exact du bouton Reels **sans tap utilisateur réel** (mise à jour interne de sélection, observée ~56 ms après l'ouverture d'un Reel du feed). Filtre ajouté : un tap réel ne peut provenir que de la zone de la barre de navigation inférieure (~15 % bas de l'écran).

**Bug de settling résiduel** : même après ces corrections, un Reel du feed ouvert sans le moindre swipe se fermait parfois quand même — l'animation d'ouverture du pager Instagram émettait à elle seule un `TYPE_VIEW_SCROLLED` (~100–200 ms après ouverture), consommant à tort le quota de tolérance (fixé à 1 swipe). **Solution** : fenêtre de grâce de 700 ms après la première détection du lecteur, pendant laquelle les scrolls sont ignorés.

**Bug écran fractionné (split-screen)** : "j'avais YouTube ouvert en fenêtré, ça désordonnait l'analyse d'Insta." Le filtre de position du clic (point 5 ci-dessus) comparait la position du tap à `resources.displayMetrics.heightPixels` — la hauteur de l'écran physique entier, pas celle de la fenêtre Instagram. En écran fractionné, Instagram n'occupe qu'une partie de l'écran : sa vraie barre de nav ne tombait donc jamais dans la zone calculée sur l'appareil entier, cassant la détection de clic. **Solution** : mesure de la hauteur réelle de la fenêtre Instagram (`findInstagramRoot().getBoundsInScreen()`), avec repli sur l'écran entier si indisponible.

---

## 11. Historique visuel et optimisation de la latence de blocage — 2026-08-05

**Demande** : remplacer la liste texte de l'historique par un graphique en barres (jours ou mois au choix, nombre de blocages en ordonnée), cliquable pour afficher le détail d'une période sans dérouler tout l'historique.

**Implémentation** : graphique dessiné en `Canvas` Compose (pas de librairie externe, cohérence offline-first), avec `detectTapGestures` pour la sélection de barre. Chaque barre porte directement ses tentatives plutôt qu'un simple compteur, évitant une requête supplémentaire au clic.

**Latence de blocage jugée trop longue et irrégulière** : "je voudrais vraiment que tu diminues ça le plus possible, et que tous les clics restent ce même temps." Cause : `BLOCK_COOLDOWN_MS` (1500 ms) empêchait toute nouvelle redirection dans les 1,5 s suivant la précédente, même pour un clic volontaire de test — le Reel restait visible jusqu'à expiration du cooldown. Réduit à 400 ms. `VERIFY_DELAY_MS` (délai entre chaque tentative de retour arrière en cas d'échec) réduit de 800 à 300 ms.

---

## 12. Audit pré-release général et stress test — 2026-08-05

**Demande** : "fais-moi un stress test général avec une recherche de bugs et de failles et corrige tout ce que tu peux avant release."

**Protocole** : 10 cycles activer/désactiver du service d'accessibilité via ADB, mesures CPU/mémoire/threads sur appareil réel avant/après, recherche systématique de fuites dans le code de gestion des `AccessibilityNodeInfo` (API nécessitant un recyclage manuel explicite).

**9 problèmes trouvés et corrigés**, aucune régression fonctionnelle détectée :

- **3 fuites de nœuds d'accessibilité** : `handleScroll` ne recyclait la racine sur aucune de ses deux sorties anticipées (une fuite par scroll, plusieurs par seconde) ; `isInstagramForeground` et `findInstagramRoot` allouaient des nœuds jamais libérés à chaque appel, l'un dans la boucle de scan, l'autre à chaque retour arrière.
- **Boucles cumulatives** : `onServiceConnected` peut être rappelé par le système sans passage préalable par `onUnbind` (reconnexion du service, changement de configuration) — chaque reconnexion empilait une boucle de scan et un collecteur de Flow supplémentaires, doublant la charge à chaque cycle. C'est très probablement la cause racine du bug de stress test du §9. Correction : annulation explicite des coroutines en tout début de méthode.
- **Son bloqué à 0 de façon permanente** si le service s'arrêtait pendant une coupure (désactivation manuelle, kill système) : aucun code ne restaurait le volume dans ce cas. Restauration ajoutée dans `onUnbind`/`onDestroy`, plus un bug d'ordre d'appel où `unmuteMediaAudio()` était invoqué après la purge de sa propre variable de sauvegarde (donc sans effet).
- **`swipeTracker` non `@Volatile`** malgré une réassignation sur un thread et une lecture depuis un autre.
- **Scan périodique à cadence fixe (~33 sondages/seconde) en permanence**, y compris blocage désactivé ou Instagram fermé — chaque sondage appelle `rootInActiveWindow`, un IPC système non gratuit. Passage à une cadence adaptative : 30 ms uniquement quand le blocage est actif ET Instagram au premier plan, 500 ms sinon. Mesuré sur appareil : 109 ticks CPU/5 s en usage contre 9 au repos (−92 %).
- **Garde-fou anti-boucle infinie recalibré** : la limite de tentatives (160) avait été dimensionnée pour l'ancien délai de vérification de 800 ms (~2 min de marge) ; la réduction à 300 ms (§11) l'avait silencieusement ramenée à ~48 s sans que ce soit voulu. Remontée à 400 pour rétablir la marge d'origine.

**Résultats mesurés** : 10 cycles → 10 connexions propres, 0 crash, 0 exception, mémoire stable (42 → 45 MB), aucune boucle empilée.

---

## 13. Suppression du code mort — 2026-08-05

**Demande** : "suppr tous le code mort si tu es sûr qu'il est inutile."

Un cluster de ~160 lignes dans `InstagramUiDetector` — `isGeneralReelsFeed`, `isHomeTabSelected`, `isReelsTabSelected`, `isReelsOpenedFromDirectMessage`, `isReelViewerFromGeneralFeed`, `isSelectedOrAncestorSelected`, et les constantes `DM_VIEW_IDS`/`REELS_TAB_LABELS` qui n'étaient plus lues que par ce cluster — reposait entièrement sur l'ancienne approche par flag `isSelected`, abandonnée au §10 (point 4) au profit de la détection par clic. Ces fonctions n'avaient plus aucun appelant en dehors d'elles-mêmes depuis ce changement, mais étaient restées dans le fichier. Vérifié par recherche exhaustive des usages avant suppression ; compilation et suite de tests toujours vertes après coup, zéro warning résiduel.

---

## 14. Tap résiduel sur l'onglet Reels dédié — 2026-08-05

**Symptôme constaté après la release v2.4.1** : après plusieurs taps rapprochés sur l'onglet Reels dédié (chacun renvoyant à Accueil), un Reel ouvert depuis le feed juste après était parfois fermé instantanément, sans la tolérance de swipe normalement accordée au feed — un second tap, une fois un certain délai passé, fonctionnait normalement, d'où l'impression qu'il fallait "re-cliquer" pour que ça reste ouvert.

**Cause** : `reelsTabTappedAtUptimeMs` (horodatage du dernier clic détecté sur l'onglet dédié, utilisé pour classer le prochain lecteur ouvert) restait "récent" pendant toute la durée de `REELS_TAB_TAP_VALIDITY_MS` (2 s) même quand le tap n'avait jamais mené à l'ouverture d'un lecteur (déjà bloqué et refermé, tap ignoré par Instagram…). Un Reel du feed ouvert dans cette fenêtre héritait donc à tort du tap résiduel.

**Solution** : nouvelle constante `REELS_TAB_TAP_STALE_AT_HOME_MS` (400 ms, largement suffisante puisqu'un lecteur s'ouvre en pratique quelques dizaines de ms après le tap) — purge du tap résiduel dès le retour sur un écran sans lecteur ouvert, indépendamment du verrou `redirectChainActive`. Validé sur le terrain.

---

## 15. Debounce de fermeture du lecteur — 2026-08-05

**Symptôme constaté en stress test après la v2.4.2** : clics rapprochés sur l'onglet Reels dédié puis sur un Reel du feed, répétés plusieurs fois de suite — le Reel du feed était encore fermé instantanément comme s'il venait de l'onglet dédié, malgré le correctif du §14.

**Cause** : dans `isBlockedReelsScreen`, la réinitialisation de l'origine déjà tranchée (`viewerOriginDecided`, `currentViewerIsFromFeed`, etc.) n'avait lieu que si `redirectChainActive` était retombé à `false` — garde ajoutée pour tolérer un flicker de fermeture/réouverture furtive du même lecteur pendant notre propre retour arrière (cf. §10). Or `redirectChainActive` reste vrai pendant toute la vérification différée (`VERIFY_DELAY_MS`, jusqu'à ~300 ms), bien plus longtemps qu'un flicker réel (une seule passe, ~30 ms). Si le lecteur "onglet dédié" se fermait pour de bon et qu'un lecteur ENTIÈREMENT DIFFÉRENT s'ouvrait depuis le feed pendant cette fenêtre encore active, l'ancien classement restait figé et le nouveau lecteur en héritait à tort.

**Solution** : remplacement du gate sur `redirectChainActive` par un debounce de fermeture symétrique à celui de l'ouverture — `VIEWER_CLOSE_DEBOUNCE_COUNT` (2 passes consécutives sans lecteur détecté) déclenche désormais la réinitialisation, indépendamment de l'état de la chaîne de redirection. Un flicker d'une seule passe ne fait jamais gagner 2 détections "fermé" consécutives (origine préservée comme avant) ; une fermeture réelle sur ~60 ms est traitée comme définitive. Validé sur le terrain (stress test).

---

## 16. Renommage et titre stylisé — 2026-08-05

**Demande** : renommer l'app ("Réel Contrôle"), le nom du lanceur tronqué visuellement a ensuite été raccourci en "Réel Ctrl" (`app_name`), puis stylisation du titre de l'écran d'accueil (une première itération avec accent orange sur une partie du texte a été jugée trop tranchante avec le reste de la palette et retirée).

**Résultat retenu** : titre en police monospace "signature" (au lieu du sans-serif générique), taille et espacement de lettres augmentés, filet décoratif sous le titre dimensionné exactement sur sa largeur via `Modifier.width(IntrinsicSize.Min)` plutôt qu'une largeur fixe arbitraire.

---

## 17. Passe d'optimisation — 2026-08-05

**Demande** : "vois si tu peux faire des opti" (taille APK, performance/batterie, qualité du code).

**Taille de l'APK** : `isMinifyEnabled`/`isShrinkResources` désactivés depuis le début (§4) par prudence — Room et WorkManager reposent en partie sur la réflexion (Room via ses classes générées, WorkManager pour instancier un `Worker` par son nom de classe stocké en base), et R8 peut renommer/supprimer du code qui n'est référencé que par réflexion sans règle de conservation explicite. Activé cette fois avec règles ProGuard ciblées (`RelockWorker`, entités Room) et validation manuelle complète sur appareil réel avant de considérer le changement sûr : build signé localement (keystore debug, `zipalign` + `apksigner` manuels puisqu'aucune config de signature release n'existe dans le projet), installé, testé (écran d'accueil, lecture Room au démarrage, écriture Room via le switch de blocage, aucun crash en logcat). Résultat : 19,3 MB → 2,3 MB.

**Performance/batterie** : relecture de `ReelsAccessibilityService`/`InstagramUiDetector` — l'essentiel avait déjà été traité à l'audit v2.4.1 (§12, cadence adaptative, recyclage systématique). Rien de significatif trouvé au-delà.

**Qualité du code** : une fuite mineure trouvée dans `dumpMatchingIds` (fonction de diagnostic terrain, désactivée par défaut) — ne recyclait pas les nœuds enfants contrairement à `dumpTree`. Corrigée par prudence pour une future session de calibrage terrain.

---

## 18. Compteur de déblocages sur l'accueil — 2026-08-05

**Demande** : "Il me faudrait aussi un compteur...par jour pour l'instant, où j'ai désactivait le blocage. Et peut-être voir pr add un graphique aussi pr ça."

**Contexte** : L'écran d'accueil affichait déjà « tentatives bloquées aujourd'hui » (chaque fois que Reels était détecté) et « jours sans déblocage » (série de jours sans aucun déblocage volontaire). L'utilisateur veut aussi visualiser l'inverse : combien de fois *par jour* il a *activement désactivé* le blocage (après la friction).

**Design de la feature** :

1. **UnlockEventEntity** : nouvelle entité Room (table `unlock_events`), symétrique à `BlockAttemptEntity`. Enregistre une entrée quand le compte à rebours de friction s'écoule et que le blocage passe à `false`.

2. **Timing du record** : dans `UnlockFrictionScreen`, l'événement est inscrit une fois le compte à rebours terminé (ligne 125, `historyRepository.recordUnlock(packageName, unlockedAtMillis)`), pas au moment où l'utilisateur décoche le switch. Décocher lance juste l'écran de friction ; le déblocage n'est acquis qu'à son terme.

3. **UI — HomeScreen** : ajout d'un troisième compteur sur la carte stats. Layout réorganisé en 3 colonnes (au lieu de 2) :
   - Colonne 1 : "tentatives bloquées aujourd'hui"
   - Colonne 2 : "jours sans déblocage"
   - Colonne 3 : "déblocages aujourd'hui"
   
   Trois colonnes séparées par des filets verticaux (`.width(2.dp).background(colors.border)`).

4. **Schéma de BD** : version bumped v1 → v2, migration gérée par `fallbackToDestructiveMigration()` (perte d'historique acceptable, cf. exigence 4.4 : aucune sync réseau, contenu entièrement local).

**Graphique** : pas implémenté dans cette itération (optionnel, peut être ajouté plus tard sur `HistoryScreen` si nécessaire).

---

## Bilan des versions

V1.0 → V1.0-beta → v1.3 → v1.5 → v1.6 → v1.7 → v1.8 → v1.8.1–v1.8.4 → v1.9 (refonte détection) → v2.0 → v2.0.1 → v2.1 → v2.2 → v2.3 (stable, dernière version validée terrain avant la phase design UI) → v2.3.3–v2.3.16 (correctifs feed/DM/onglet dédié, écran fractionné, latence, historique graphique) → v2.4.1 (audit pré-release, corrections de fuites mémoire, cadence adaptative, suppression du code mort) → v2.4.2 (fix tap résiduel onglet Reels dédié, validé terrain) → v2.4.3 (fix debounce de fermeture du lecteur en stress test, validé terrain) → v2.4.4 (renommage "Réel Ctrl", titre d'accueil restylé) → v2.4.5 (APK release minifiée -88 %, fuite mineure corrigée) → **v2.4.6** (compteur de déblocages sur l'accueil, nouvelle table unlock_events).

## Compétences illustrées par ce projet

- Diagnostic par instrumentation terrain (ADB, dump d'arbre d'accessibilité, logcat en direct) plutôt que par hypothèse — plusieurs bugs n'ont été réellement compris qu'après mesure, la première hypothèse était incorrecte à plusieurs reprises (marqueurs DM notamment).
- Gestion de la concurrence sur Android (races entre threads UI/coroutines, `@Volatile`, verrouillage de chaînes d'action asynchrones).
- Conception d'une architecture testable en isolant la logique métier pure (`FrictionCalculator`, `SwipeSessionTracker`) du framework Android.
- Développement contraint par la privacy dès la conception (zéro permission réseau), vérifié et audité en fin de projet plutôt que supposé.
- Adaptation à la fragmentation Android réelle (comportements différents entre surcouches constructeur MIUI/OneUI, gestion agressive du cycle de vie en arrière-plan).
