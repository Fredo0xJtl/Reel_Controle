# Handoff: Focus Reels — UI brutaliste (Jetpack Compose / Material 3)

## Aperçu
Design d'écrans pour une app Android 100% locale de blocage sélectif de l'onglet Reels d'Instagram (accessibilité + Room + WorkManager, aucune permission INTERNET). Ce handoff couvre 4 écrans : Accueil, Historique, Réglages, Écran de friction/déblocage. Thème clair + sombre.

## À propos des fichiers de design
Le fichier `Focus Reels.dc.html` est une **référence visuelle créée en HTML**, un prototype montrant le look et le comportement voulus — ce n'est **pas du code à copier tel quel**. La tâche est de **recréer ce design dans le code Compose existant** (`app/src/main/kotlin/com/focusreels/app/ui/`), en réutilisant les composables Material 3 déjà en place (`HomeScreen.kt`, `HistoryScreen.kt`, `SettingsScreen.kt`, `UnlockFrictionScreen.kt`, `Theme.kt`) plutôt qu'en générant du HTML/CSS.

## Fidélité
**Haute fidélité (hifi)** : couleurs, typographie, espacements et interactions finaux. Recréer l'UI au pixel près avec Compose (Modifier, Box/Column/Row, Canvas si besoin pour les formes).

## Design tokens

### Couleurs — mode clair
- Fond (`bg`) : `#eef0e6`
- Surface / cartes (`surface`) : `#ffffff`
- Texte / encre (`text`) : `#173028` (vert forêt profond, remplace le noir pur)
- Texte secondaire (`sub`) : `#4d6459`
- Bordures (`border`) : `#173028` (2px, même couleur que le texte — bordures franches)
- Accent alerte (`accent`) : `#e2451f` (rouge-orange)

### Couleurs — mode sombre
- Fond : `#141a17`
- Surface : `#1c2622`
- Texte : `#eef2ee`
- Texte secondaire : `#8ea69c`
- Bordures : `#2f3f38`
- Accent alerte : `#e2451f` (identique)

### Règle stricte sur l'accent
`#e2451f` est réservé **strictement aux alertes actives** :
- Bannière "blocage inactif" sur Accueil
- Bandeau de progression rayé sur l'écran de friction
Ne jamais l'utiliser sur un bouton neutre ou de façon décorative.

### Écran de friction (cas spécial)
Toujours en contraste dur, indépendant du thème clair/sombre choisi :
- Fond : `#0c1613` (vert quasi-noir)
- Texte : `#eef2ee`
- Texte secondaire : `#7fa396`

### Typographie
- Police principale : IBM Plex Sans (400/600/700/800)
- Police signature (chiffres, stats, horodatages, compte à rebours) : Space Mono (400/700) — **détail signature répété sur tous les écrans**
- Titres écran : 20px / 800 / letter-spacing -0.02em, tout en majuscules
- Chiffres de stats (Accueil) : Space Mono 44px / 700
- Compte à rebours (Friction) : Space Mono 112px / 700

### Bordures, ombres, formes
- Bordures : 2px solid, couleur = texte (pas de gris clair)
- Ombres portées "dures" (brutalistes) : `box-shadow: 6px 6px 0 <border>` (offset dur, pas de flou) sur les cartes de l'Accueil
- Aucun border-radius nulle part (formes brutes, coins droits) sauf le cercle du logo
- Séparateurs : filets 1-2px, jamais de card avec radius

## Écrans

### 1. Accueil
- Header : "RÉELS CONTRÔLE" (titre app) + version en Space Mono à droite
- Bannière d'alerte (visible seulement si blocage désactivé) : fond quasi-noir, rayures obliques rouge-orange/noir en haut (5px, `repeating-linear-gradient(-45deg, accent 0 10px, #0a0a0a 10px 20px)`), texte "⚠ ALERTE — REELS ACCESSIBLE, BLOCAGE INACTIF"
- Carte Statut : inversée (fond = couleur texte, texte = couleur surface) quand le blocage est ACTIF ; normale sinon. Contient le libellé "BLOCAGE ACTIF"/"BLOCAGE INACTIF" + un switch custom (rectangle 52×30, pas de radius, knob carré qui glisse)
- Carte Stats : 2 colonnes séparées par un filet vertical — "tentatives bloquées aujourd'hui" (grand nombre mono) et "jours sans déblocage" (grand nombre mono)
- Ligne "Instagram — Reels" avec statut court à droite, encadrée de filets épais
- Nav bas : 3 onglets (ACCUEIL/HISTORIQUE/RÉGLAGES), onglet actif = fond plein couleur texte + texte couleur surface, séparés par filets 1px

### 2. Historique
- Header "HISTORIQUE"
- Groupé par jour : bandeau jour inversé (fond = texte, texte = surface) avec libellé jour ("AUJOURD'HUI", "HIER") + compteur en Space Mono à droite
- Chaque entrée : heure en Space Mono à gauche (couleur secondaire) + libellé à droite ("Tentative bloquée" / "Débloqué — Ns de friction"), séparées par filet 1px

### 3. Réglages
- Header "RÉGLAGES"
- Sections avec label majuscule + letter-spacing (FRICTION PROGRESSIVE / REVERROUILLAGE / TOLÉRANCE DM / APPARENCE), séparées par filets 2px
- Chaque valeur numérique : stepper (bouton "−" carré 30×30 bordure 2px / valeur Space Mono centrée / bouton "+"), hover = inversion couleurs
  - Délai de base (s), Incrément par tentative (s), Reverrouillage automatique (min), Swipes tolérés après DM
- Sélecteur de thème : 3 boutons plein largeur CLAIR/SOMBRE/SYSTÈME, actif = fond plein couleur texte

### 4. Écran de friction/déblocage (le plus brutaliste)
- Toujours fond quasi-noir (#0c1613), toujours contraste dur, quel que soit le thème
- Bande de progression rayée en haut (accent+noir), largeur = % de progression du compte à rebours, animation pulse (opacity 1↔0.45, 1.6s)
- Libellé "TENTATIVE N AUJOURD'HUI" en Space Mono petit, letter-spacing large
- Compte à rebours géant en Space Mono 112px + "s", légère animation pop (scale 1.08→1 à chaque tick)
- Trait horizontal accent 64×4px
- Titre "Reels bloqué." 30px/800 — ton sec et direct (jamais "on fait une pause ?")
- Sous-texte explicatif court, couleur secondaire
- Bouton "ANNULER" pleine largeur, contour blanc, hover = inversion

## Interactions & comportement
- Toggle switch sur Accueil : si passage ACTIF→INACTIF, navigue vers l'écran de friction et démarre un compte à rebours réel de `baseDelay + increment × attemptsToday` secondes (formule identique à `FrictionCalculator`) ; à la fin, retourne à l'Accueil avec blocage désactivé et ajoute une entrée à l'historique du jour
- "ANNULER" sur l'écran de friction : retour immédiat à l'Accueil, blocage reste actif
- Toggle INACTIF→ACTIF : réactivation immédiate, pas de friction
- Nav bas : simple changement d'écran, pas d'animation de transition définie (à définir par le dev si souhaité)
- Steppers Réglages : incrément/décrément avec bornes (ex. délai de base min 1s, incrément min 0s, reverrouillage min 5 par pas de 5, swipes min 0)
- Thème : suit le préférence choisi (clair/sombre/système) — persistant, à câbler sur DataStore existant

## Logo / icône app
Mark carré (sans radius, hors masque adaptatif Android géré séparément à l'installation) : fond couleur texte (`#173028` clair / adapter en sombre), cercle blanc contour 10px (96×96) barré d'une barre blanche 130×14px pivotée à 45° — symbole universel de blocage. Pas d'accent sur le logo.

## Fichiers
- `Focus Reels.dc.html` (bundlé dans ce dossier) : design de référence complet, interactif, tous les écrans + logo
- Code source actuel de l'app (non inclus ici, déjà dans le repo Kotlin) : `app/src/main/kotlin/com/focusreels/app/ui/` — `main/HomeScreen.kt`, `history/HistoryScreen.kt`, `settings/SettingsScreen.kt`, `unlock/UnlockFrictionScreen.kt`, `theme/Theme.kt`
