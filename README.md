# Focus Reels

Application Android 100 % locale de blocage sélectif de l'onglet Reels d'Instagram.

**Compatible** : Xiaomi Redmi Note 17 Pro (MIUI/HyperOS), Samsung Galaxy S24 (OneUI), Android AOSP standard.

Voir [.docs/Cahier_des_charges_Focus_Reels_V1.1.md](.docs/Cahier_des_charges_Focus_Reels_V1.1.md) pour les spécifications complètes.

## Stack technique

- **Langage** : Kotlin (compilé JVM/Android)
- **UI** : Jetpack Compose (moderne, déclaratif, sans XML verbose)
- **Base de données** : Room (SQLite abstraite, type-safe, exécutable sur PC/JVM)
- **Services** : AccessibilityService (détection UI), WorkManager (reverrouillage automatique)
- **Build** : Gradle 8.7, Android Gradle Plugin 8.5, KSP (code generation pour Room)
- **Tests** : JUnit (unitaires JVM, indépendants d'Android)

**Architecture** : Clean Architecture (Separation of Concerns)

```
accessibility/   AccessibilityService (interface système) + InstagramUiDetector (module isolé, §4.5)
data/            Couche données (Room/SQLite, local uniquement)
domain/          Logique métier pure (testable sur PC sans émulateur)
ui/              Couche présentation (Jetpack Compose, navigation)
```

**Confidentialité** : Aucune permission `INTERNET` dans le manifeste — exigence non négociable (§4.4).

## Environnement de développement

### Installation initiale (première fois)

**Option 1 : Android Studio (recommandé)**
1. Installer [Android Studio 2026.1.3 ou plus récent](https://developer.android.com/studio)
2. Ouvrir le dossier `Bloqueur_Reel` dans Android Studio
3. Laisser le setup wizard télécharger le SDK Android (platform 35, build-tools 35.0.0)
4. Laisser Gradle se synchroniser (wrapper généré automatiquement)

**Option 2 : CLI (développé & testé)**
```bash
# JDK 17 minimum (testé avec Eclipse Temurin 17.0.20)
export JAVA_HOME=/path/to/jdk-17

# SDK Android (si pas déjà installé)
curl -L -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip
unzip cmdline-tools.zip && mv cmdline-tools ~/.android/sdk/cmdline-tools/latest
yes | ~/.android/sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0"

# Génération du wrapper Gradle
gradle wrapper --gradle-version 8.7

# Build & tests
./gradlew build
```

### Validation du projet

Tests unitaires **exécutables sur PC** (JVM, sans émulateur) :
```bash
./gradlew test
```
✅ **Status** : Build successful (52 tasks), tests unitaires du calcul de friction passent.

## Tester le projet

### Logique métier (sur PC, pas d'émulateur requis)

```bash
./gradlew test
```

Tests JUnit purs sur la friction progressive : 
- Première tentative = délai de base (5 s)  
- Chaque nouvelle tentative = + incrément (5 s)  
- Réinitialisation quotidienne (00h00)  

Voir `app/src/test/kotlin/com/focusreels/app/FrictionCalculatorTest.kt`.

### Interface & logique d'accessibilité (nécessite Android)

1. **Émulateur Android Studio ou WSA** (Windows Subsystem for Android)  
   Teste les écrans Compose, la navigation, la persistance Room.
   
2. **Appareil réel** (Redmi Note 17 Pro cible)  
   Valide le service d'accessibilité, la détection Instagram réelle, les contraintes MIUI.

### Installation sur l'appareil

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Puis dans l'app : **Configuration système** → activer accessibilité + désactiver optimisation batterie.

## État actuel

### Implémentation complète des exigences V1

| Exigence | Module | Status |
|----------|--------|--------|
| Blocage Reels + redirection | `ReelsAccessibilityService` | ✅ Implémenté, fail-open (§4.5) |
| Friction progressive (5→10→15s…) | `FrictionCalculator` + `UnlockFrictionScreen` | ✅ Testé JVM, configurable |
| Reverrouillage auto (30 min) | `RelockWorker` (WorkManager) | ✅ Implémenté, configurable |
| DM vs flux général (swipes) | `SwipeSessionTracker` | ✅ Implémenté |
| Historique illimité local | Room `BlockAttemptEntity` | ✅ Implémenté (SQLite) |
| Interface de configuration | `SettingsScreen` | ✅ Compose UI |
| Onboarding MIUI/batterie | `OnboardingScreen` | ✅ Lien vers réglages système |
| Zéro réseau | ✅ Aucune permission `INTERNET` | ✅ Vérifié manifeste |

### Choix architecturaux justifiés

1. **Module `InstagramUiDetector` isolé** (§4.5, maintenabilité)  
   Instagram change son UI ~chaque trimestre → concentrer la fragilité dans un seul fichier pour correction rapide.

2. **Logique métier en Kotlin pur** (`FrictionCalculator`, `SwipeSessionTracker`)  
   Tests sur PC sans émulateur ni dépendances Android → feedback immédiat, réutilisable hors app.

3. **Room + DataStore** vs SharedPreferences  
   Room offre type-safety, migrations, et testabilité ; DataStore pour les préférences simples.

4. **WorkManager** vs AlarmManager  
   WorkManager persiste sur redémarrage du téléphone et respecte l'optimisation batterie.

5. **Jetpack Compose** vs XML classique  
   Moins de code (déclaratif), meilleure testabilité, et c'est l'avenir officiel d'Android.

### À valider avant usage réel

- **Détection Instagram sur MIUI et OneUI** : 
  - Redmi Note 17 Pro + MIUI/HyperOS version cible
  - Samsung Galaxy S24 + OneUI
  - Vérifier les logs (`adb logcat`) pour confirmer détection correcte
- **Comportement batterie** : vérifier que le reverrouillage automatique fonctionne après redémarrage
- **Logs disponibles** : lancés via `adb logcat -s "ReelsAccessibilityService|InstagramUiDetector"` pour diagnostic

## Structure des fichiers clés

```
app/src/main/kotlin/com/focusreels/app/
├── FocusReelsApplication.kt         # Point d'entrée, initialisation DB
├── accessibility/
│   ├── ReelsAccessibilityService    # Service Android d'accessibilité (écoute UI)
│   └── InstagramUiDetector          # Reconnaissance de l'interface Instagram (module isolé)
├── data/db/
│   ├── BlockedAppEntity             # Entité : app à bloquer + paramètres
│   ├── BlockAttemptEntity           # Entité : historique des tentatives
│   └── AppDatabase                  # Database Room (SQLite local)
├── domain/
│   ├── FrictionCalculator           # Logique pure : délai(tentative, jour)
│   ├── SwipeSessionTracker          # Contexte DM vs flux général
│   ├── RelockScheduler              # Planification reverrouillage auto
│   └── RelockWorker                 # Tâche WorkManager
├── ui/
│   ├── main/                        # Accueil : liste apps + interrupteur
│   ├── settings/                    # Réglages : friction, reverrouillage
│   ├── history/                     # Historique des tentatives
│   ├── unlock/                      # Écran de friction (minuteur neutre)
│   ├── onboarding/                  # Configuration système
│   └── theme/                       # Thème Compose

app/src/test/kotlin/
└── FrictionCalculatorTest           # 4 tests JVM (exécutables sur PC)
```

## Prochaines étapes (roadmap V1 → V2)

1. ✅ **V1.0 (actuel)** : Instagram, friction progressive, base de données locale
2. **V1.1** : Tests d'intégration ; validation empirique sur Redmi Note 17 Pro
3. **V1.2** : Détection améliorée (patterns image ou ML léger si possible)
4. **V2.0** : Support d'autres apps (TikTok, YouTube Shorts) — architecture déjà modulaire
