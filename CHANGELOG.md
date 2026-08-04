# Changelog

Historique des évolutions significatives de Focus Reels, avec le contexte de diagnostic pour
chaque correction (utile en revue de code / portfolio : montre la démarche, pas seulement le
résultat).

## V1.1 — Diagnostic terrain sur Galaxy S24 (2026-08-04)

Première validation sur appareil réel (Samsung Galaxy S24, OneUI). Trois bugs bloquants
découverts par analyse de logs `adb logcat` et dump d'arbre d'accessibilité
(`uiautomator dump`), aucun visible en lecture de code seule.

### Bug 1 — Détection par ID totalement inopérante

**Symptôme** : le service ne loggait rien, comme s'il ne tournait pas, alors que le service
d'accessibilité était bien activé.

**Cause** : `accessibility_service_config.xml` ne déclarait pas le flag
`flagReportViewIds`. Sans lui, Android ne remonte jamais les identifiants de vue aux services
d'accessibilité : `findAccessibilityNodeInfosByViewId()` renvoyait systématiquement une liste
vide, quelle que soit la justesse des identifiants ciblés.

**Correction** : ajout de `flagReportViewIds` (et `flagIncludeNotImportantViews` pour la
robustesse) dans `accessibility_service_config.xml`.

**Leçon** : un flag de configuration XML peut invalider silencieusement toute la logique
Kotlin qui en dépend. Un test d'intégration sur device réel aurait dû être fait avant toute
mise en production — les tests JVM ne peuvent pas détecter ce genre de problème car
`AccessibilityService` n'existe que dans le framework Android réel.

### Bug 2 — Faux positif systématique sur tous les écrans Instagram

**Symptôme** : une fois le bug 1 corrigé, Instagram se faisait bloquer/rediriger en
permanence, y compris hors de l'onglet Reels (accueil, profil, DM, gestionnaire de tâches).

**Cause** : Instagram utilise une seule `Activity` (`MainTabActivity`, confirmé via
`dumpsys window`) pour tous ses onglets. Le bouton « Reels » de la barre de navigation
inférieure est donc présent dans l'arbre d'accessibilité en permanence. L'ancienne détection
cherchait la simple présence du texte « Reels » → correspondance quasi permanente.

**Correction** : dump réel de l'arbre UI (`uiautomator dump`) pour identifier que le bouton
Reels porte un id stable `com.instagram.android:id/clips_tab` avec l'attribut
`selected="true"` uniquement quand l'onglet est actif. La détection vérifie désormais l'état
`isSelected` du bouton (ou de ses ancêtres proches), pas sa simple présence.

**Leçon** : sur une UI à onglet unique (single-Activity), la présence d'un élément ne dit
rien de l'écran affiché ; seul son état (sélectionné, visible, focus) est significatif.

### Bug 3 — Fermeture complète d'Instagram au lieu d'un changement d'onglet

**Symptôme** : le blocage fonctionnait, mais expulsait parfois l'utilisateur d'Instagram
entièrement vers l'écran d'accueil du téléphone, au lieu de revenir sur l'onglet Accueil
d'Instagram.

**Cause** : le service utilisait `performGlobalAction(GLOBAL_ACTION_BACK)`. Quand l'onglet
Reels est la page racine (aucun écran à dépiler dans la pile interne d'Instagram à cet
instant), un retour arrière système sort intégralement de l'application. Confirmé par les
logs : la fenêtre active passait de `com.instagram.android` à
`com.sec.android.app.launcher` juste après l'action.

**Correction** : dump de l'arbre UI pour localiser le bouton d'onglet Accueil
(`com.instagram.android:id/feed_tab`, `content-desc="Home"`). Le service effectue maintenant
un clic direct (`AccessibilityNodeInfo.ACTION_CLICK`) sur ce bouton, ce qui change d'onglet
sans jamais dépendre de la pile de navigation. `GLOBAL_ACTION_BACK` reste un repli si le
bouton n'est pas trouvé.

**Leçon** : une action globale (back, home, recents) est toujours risquée dans un service
d'accessibilité car son effet dépend d'un état externe (la pile de navigation de l'app
ciblée) hors du contrôle du service. Cibler directement l'élément d'UI voulu est plus
prévisible.

### Bug 4 — Blocage du défilement du feed Accueil (rafraîchissements en boucle)

**Symptôme** : impossible de faire défiler le feed Accueil normalement ; l'écran se
rafraîchissait sans arrêt pendant le scroll.

**Cause** : la restriction défensive du bug précédent (ids de lecteur limités à
`root_clips_layout` / `clips_viewer_*`) n'était pas suffisante. Instagram insère désormais
des Reels directement **dans** le flux Accueil (contenu suggéré), et ces Reels intégrés
semblent réutiliser exactement le même rendu de lecteur que l'onglet Reels dédié. Résultat :
faire défiler le feed jusqu'à un Reels suggéré déclenchait un clic sur l'onglet Accueil —
qui, cliqué alors qu'on y est déjà, fait remonter/rafraîchir le feed (comportement natif
d'Instagram), d'où la boucle.

**Correction** : abandon complet de la détection par conteneur de lecteur. Seul signal
retenu : l'état `isSelected` du bouton d'onglet Reels (`clips_tab`) lui-même. C'est le seul
signal qui distingue sans ambiguïté « je suis dans la section Reels dédiée » de « un Reels
s'affiche ailleurs (feed, DM, profil) ». Compromis assumé : un Reels isolé ouvert autrement
que via l'onglet dédié n'est plus intercepté par cette heuristique — acceptable au regard du
cahier des charges, centré sur l'onglet Reels général (§3.1).

**Leçon** : sur une application dont le contenu est de plus en plus mélangé entre sections
(Reels intégrés au feed, feed intégré aux Reels), la structure de rendu interne (quels
composants sont affichés) est un signal de moins en moins fiable. L'état de navigation
explicite (quel onglet est sélectionné) reste le seul signal stable dans le temps.

### Bug 5 — Le Reels reste bloqué ouvert après des clics répétés

**Symptôme** : à force de cliquer rapidement sur des Reels successifs, l'utilisateur finissait
par rester coincé sur l'onglet Reels malgré le service actif.

**Cause** : `AccessibilityEvent` n'est déclenché que par un *changement* d'arbre UI. Une vidéo
Reels qui joue simplement sans changement de vue ne génère plus aucun événement. Si le clic du
service sur l'onglet Accueil entre en concurrence avec un vrai tap de l'utilisateur et que ce
dernier « gagne » (Reels reste affiché), aucun futur événement n'arrivera pour permettre une
nouvelle tentative : le blocage restait silencieusement en échec.

**Correction** : après chaque clic de redirection, vérification différée (`VERIFY_DELAY_MS`,
initialement 400ms) de l'état réel de l'écran, avec jusqu'à `MAX_VERIFY_RETRIES` nouvelles
tentatives si l'onglet Reels est toujours sélectionné. Exécutée explicitement sur
`Dispatchers.Main`, car `rootInActiveWindow`/`performAction` doivent rester sur le thread
principal du service (contrairement aux écritures Room, faites sur le dispatcher par défaut).

**Leçon** : un service piloté par événements ne doit jamais supposer qu'une action a réussi
simplement parce qu'aucune erreur n'a été levée ; sans vérification active du résultat, un échec
silencieux causé par une course avec l'utilisateur devient impossible à rattraper.

### Bug 6 — « Flash » visible entre deux pages après la redirection

**Symptôme** : une fois la vérification+retry du bug 5 en place, l'écran faisait un aller-retour
visible entre Reels et Accueil au lieu d'un changement d'onglet net.

**Cause** : le délai de vérification (400ms) était trop court et lisait l'arbre d'accessibilité
en pleine animation de transition d'onglet d'Instagram, l'interprétant à tort comme « toujours
sur Reels » et déclenchant un second clic sur Accueil pendant que le premier venait tout juste
d'aboutir.

**Correction** : délai porté à 800ms (`VERIFY_DELAY_MS`) et nombre de tentatives réduit à 2
(`MAX_VERIFY_RETRIES`), laissant l'animation native d'Instagram se terminer avant toute lecture
de l'état.

**Leçon** : une vérification post-action doit laisser le temps aux animations natives de l'app
ciblée de se terminer, sous peine de lire un état transitoire et de déclencher des actions
correctives inutiles.

### Bug 7 — Rafraîchissements non-stop du feed Accueil

**Symptôme** : après une redirection vers Accueil, le feed se rafraîchissait en boucle continue
au lieu de se stabiliser.

**Cause** : plusieurs chaînes de redirection (clic + vérifications différées) pouvaient tourner
en parallèle. Une chaîne complète dure jusqu'à ~1,6s (plusieurs tentatives espacées de
`VERIFY_DELAY_MS`), soit plus que l'ancien `BLOCK_COOLDOWN_MS` (1,5s) — mesuré depuis le
*début* de la chaîne précédente. Un nouvel événement légitime pouvait donc démarrer une
seconde chaîne avant la fin de la première : les deux cliquaient sur l'onglet Accueil presque
simultanément, ce qu'Instagram interprète comme une demande de rafraîchissement du flux.
Diagnostiqué via des logs montrant des numéros de tentative incohérents dans le temps
(« tentative #3 » suivie peu après de « tentative #1 »), preuve de deux chaînes actives en
même temps.

**Correction** : ajout d'un verrou explicite `redirectChainActive`, rendant les chaînes de
redirection strictement séquentielles (toute nouvelle tentative de blocage est ignorée tant
qu'une chaîne est en cours). Le cooldown redémarre désormais à la *fin* de la chaîne
(`endRedirectChain()`), pas à son début.

**Leçon** : un cooldown basé sur un timestamp de départ n'est fiable que si la durée de
l'opération qu'il protège est constante. Dès qu'une opération peut avoir une durée variable
(ici, un nombre de retries non déterministe), un verrou d'état explicite est la seule garantie
réelle d'exclusivité mutuelle.

### Durcissement additionnel (anticipation, pas de bug constaté)

- **Anti-rebond** (`BLOCK_COOLDOWN_MS = 1500`) : un écran Reels émet plusieurs événements de
  contenu par seconde ; sans garde-fou, chaque événement déclenchait une nouvelle action de
  blocage.
- **Garde de premier plan** : un événement `AccessibilityEvent` d'Instagram peut arriver
  alors qu'une autre application (gestionnaire de tâches, écran d'accueil) occupe l'écran.
  Le service vérifie désormais que `rootInActiveWindow.packageName` est bien Instagram avant
  d'agir, pour ne pas ramener Instagram au premier plan par erreur.
- **Restriction défensive des ids de lecteur Reels** : certains ids observés dans l'arbre
  (`clips_video_container`, `clips_media_component`) sont assez génériques pour risquer
  d'exister aussi sur une vidéo de *feed* ouverte en plein écran. Seuls les conteneurs de
  plus haut niveau, sans ambiguïté propres au lecteur Reels (`root_clips_layout`,
  `clips_viewer_container`, `clips_viewer_view_pager`), sont conservés — fail-open (§4.5) :
  en cas de doute, ne pas bloquer plutôt que bloquer à tort sur un contenu normal.
- **Cache synchrone de l'état d'activation** (`blockingEnabledCache`, alimenté par un
  `Flow` Room) : la décision de cliquer sur un nœud d'accessibilité doit être prise
  immédiatement, avant que ce nœud ne devienne périmé. Une lecture asynchrone de la base à
  ce moment précis aurait réintroduit un risque de décision basée sur un état obsolète.

### Méthode de diagnostic (reproductible)

```bash
# Connexion (USB ou WiFi après un premier adb tcpip 5555)
adb devices

# Logs ciblés du service en direct
adb logcat -s "ReelsAccessibilityService|InstagramUiDetector" -v threadtime

# Dump de l'arbre d'accessibilité affiché à l'instant T (calibrage des ids)
adb shell uiautomator dump /sdcard/tree.xml
adb pull /sdcard/tree.xml
```

Le flag `DIAGNOSTIC_DUMP` dans `ReelsAccessibilityService` trace l'arbre complet dans les
logs applicatifs (via `InstagramUiDetector.dumpTree`) pour un recalibrage rapide en cas de
changement d'UI Instagram, sans repasser par `uiautomator`.

## V1.0-beta — Version initiale

Voir [DEPLOYMENT.md](DEPLOYMENT.md) et [README.md](README.md) pour l'état des lieux avant
validation terrain.
