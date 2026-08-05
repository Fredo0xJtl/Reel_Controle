# Installation de Réel Contrôle

APK debug générée : `app/build/outputs/apk/debug/app-debug.apk` (25 MB)

**Compatible** : Xiaomi Redmi Note 17 Pro (MIUI/HyperOS), Samsung Galaxy S24 (OneUI), Android standard

## Prérequis

- **ADB** (Android Debug Bridge) sur ta machine  
  Lancer `adb --version` pour vérifier l'installation. Sinon, télécharger depuis [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools).
  
- **Xiaomi Redmi Note 17 Pro** (ou autre Android) :
  - Activer mode développeur : Paramètres → À propos du téléphone → Appuyer 7 fois sur « Version de compilation »
  - Activer débogage USB : Paramètres → Options de développeur → Débogage USB

## Installation

### 1. Connecter le téléphone en USB

```bash
adb devices
```

Tu devrais voir ton appareil listé (ex: `ZY2234XXX device`).

### 2. Installer l'APK

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Sortie attendue :
```
Success
```

### 3. Lancer l'app

Depuis le téléphone : ouvrir `Réel Contrôle` depuis le menu des apps.

## Configuration initiale

À la première ouverture, tu verras une **alerte** (accessible trop tôt, avant onboarding) :

> ⚠️ Service d'accessibilité inactif  
> Le blocage Reels ne fonctionne pas sans le service d'accessibilité.

**Action requise** :

1. Cliquer sur **« Aller à la config »** (ou manuelle : Paramètres → Accessibilité).
2. Chercher **« Blocage Reels Instagram »** dans la liste.
3. Activer le service.
4. Accepter les permissions (pas d'accès réseau, juste accessibilité).

### Xiaomi (HyperOS) : « Ce service ne fonctionne pas »

Sur Xiaomi/HyperOS (constaté sur 15T Pro, Android 16), l'activation peut échouer avec le message
générique **« Ce service ne fonctionne pas »**, même après avoir accepté l'avertissement. Ce
n'est pas un bug de l'app mais une protection Android (renforcée par HyperOS) contre les apps
installées hors Play Store (« Restricted settings », depuis Android 13). Procédure :

1. **Paramètres → Applications → Réel Contrôle → (⋮) → Autoriser les restrictions**, ou
   **Paramètres → Applications → Gérer les applications → Réel Contrôle**, puis chercher l'option
   *« Ce paramètre est actuellement restreint »* et l'autoriser explicitement (le libellé exact
   varie selon la version HyperOS).
2. Si l'option n'apparaît pas directement dans le service d'accessibilité : désinstaller
   entièrement l'app, la réinstaller, ouvrir l'app une fois, puis réessayer l'activation — le
   verrou de restriction se lève parfois après un premier lancement complet.
3. Activer **Autostart** : Paramètres → Applications → Réel Contrôle → Autostart → Activer
   (distinct de l'optimisation batterie ci-dessous, spécifique à MIUI/HyperOS).
4. Si le service se désactive tout seul après quelques minutes/heures : vérifier les logs
   (`adb logcat -s ReelsAccessibilityService`) — un `Échec critique de l'initialisation` dans les
   logs indique un crash à la connexion (base de données, permissions) plutôt qu'une restriction
   OS ; dans ce cas, remonter le rapport avec le stacktrace complet.

### 4. Désactiver l'optimisation batterie

#### Xiaomi (MIUI/HyperOS)
Aller à Paramètres → Apps → Réel Contrôle → Optimisation batterie → Désactiver.

#### Samsung (OneUI / Galaxy S24)
Aller à Paramètres → Apps → Réel Contrôle → Batterie → Mode de batterie adaptative → Désactiver  
(ou : Paramètres → Batterie → Gestion d'alimentation → Ajouter Réel Contrôle à la liste blanche)

**Pourquoi ?** Les systèmes d'exploitation tuent agressivement les services background pour économiser la batterie. Sans cette étape, l'AccessibilityService peut se couper sans avertissement.

## Tester le blocage

1. Ouvrir **Instagram**.
2. Aller sur l'onglet **Reels** (icône vidéo).
3. Instantanément, tu dois être redirigé vers l'onglet **Accueil**.
4. Revenir à Réel Contrôle → tu verras une tentative bloquée dans l'**Historique**.

## Dépannage

### L'app ne bloque pas

- ✅ Vérifier que le service d'accessibilité est activé (alerte au lancement)
- ✅ Vérifier que l'optimisation batterie est désactivée
- ✅ Redémarrer l'app ou le téléphone

### ADB n'est pas reconnu

- Vérifier `adb devices` — si tu vois `unauthorized`, autoriser le débogage sur le téléphone
- Installer les platform-tools officiels depuis [developer.android.com](https://developer.android.com/tools/releases/platform-tools)

### L'APK ne s'installe pas

- Vérifier que le téléphone est connecté : `adb devices`
- Vérifier que tu acceptes l'installation sur le téléphone (une popup peut s'afficher)

## Logs (debug)

Pour voir les logs en temps réel :

```bash
adb logcat -s "ReelsAccessibilityService|InstagramUiDetector"
```

Cela affichera les événements du service d'accessibilité (détection Reels, blocages, tentatives, etc.).

**Lecture des logs** :
- `Service d'accessibilité connecté` → service prêt
- `Événement AccessibilityEvent reçu` → Instagram détecte un changement
- `Reels général détecté` → le blocage a été appliqué
- `Blocage Reels activé : redirection` → Reels bloqué avec succès
- `Erreur lors de la détection` → problème de reconnaissance (Instagram a changé d'UI)

## Génération de release (futur)

Pour générer une APK signée prête pour Google Play (ou distribution manuelle sécurisée) :

```bash
./gradlew assembleRelease
```

(Nécessite une clé de signature configurée dans `keystore.properties`.)
