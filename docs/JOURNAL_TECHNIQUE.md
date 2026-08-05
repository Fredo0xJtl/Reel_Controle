# Journal technique — Focus Reels

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

## Bilan des versions

V1.0 → V1.0-beta → v1.3 → v1.5 → v1.6 → v1.7 → v1.8 → v1.8.1–v1.8.4 → v1.9 (refonte détection) → v2.0 → v2.0.1 → v2.1 → v2.2 → **v2.3** (stable, dernière version validée terrain avant la phase design UI).

## Compétences illustrées par ce projet

- Diagnostic par instrumentation terrain (ADB, dump d'arbre d'accessibilité, logcat en direct) plutôt que par hypothèse — plusieurs bugs n'ont été réellement compris qu'après mesure, la première hypothèse était incorrecte à plusieurs reprises (marqueurs DM notamment).
- Gestion de la concurrence sur Android (races entre threads UI/coroutines, `@Volatile`, verrouillage de chaînes d'action asynchrones).
- Conception d'une architecture testable en isolant la logique métier pure (`FrictionCalculator`, `SwipeSessionTracker`) du framework Android.
- Développement contraint par la privacy dès la conception (zéro permission réseau), vérifié et audité en fin de projet plutôt que supposé.
- Adaptation à la fragmentation Android réelle (comportements différents entre surcouches constructeur MIUI/OneUI, gestion agressive du cycle de vie en arrière-plan).
