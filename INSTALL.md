# Installation de Focus Reels

APK debug générée : `app/build/outputs/apk/debug/app-debug.apk` (25 MB)

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

Depuis le téléphone : ouvrir `Focus Reels` depuis le menu des apps.

## Configuration initiale

À la première ouverture, tu verras une **alerte** (accessible trop tôt, avant onboarding) :

> ⚠️ Service d'accessibilité inactif  
> Le blocage Reels ne fonctionne pas sans le service d'accessibilité.

**Action requise** :

1. Cliquer sur **« Aller à la config »** (ou manuelle : Paramètres → Accessibilité).
2. Chercher **« Blocage Reels Instagram »** dans la liste.
3. Activer le service.
4. Accepter les permissions (pas d'accès réseau, juste accessibilité).

### 4. Désactiver l'optimisation batterie (MIUI/HyperOS)

Aller à Paramètres → Apps → Focus Reels → Optimisation batterie → Désactiver.

**Pourquoi ?** Sur Xiaomi, le système tue les services background agressivement pour économiser la batterie. Sans cette étape, l'AccessibilityService peut se couper sans avertissement.

## Tester le blocage

1. Ouvrir **Instagram**.
2. Aller sur l'onglet **Reels** (icône vidéo).
3. Instantanément, tu dois être redirigé vers l'onglet **Accueil**.
4. Revenir à Focus Reels → tu verras une tentative bloquée dans l'**Historique**.

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
adb logcat -s "ReelsAccessibilityService"
```

Cela affichera les événements du service d'accessibilité (détection Reels, blocages, etc.).

## Génération de release (futur)

Pour générer une APK signée prête pour Google Play (ou distribution manuelle sécurisée) :

```bash
./gradlew assembleRelease
```

(Nécessite une clé de signature configurée dans `keystore.properties`.)
