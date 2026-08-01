# Déploiement et Test — Focus Reels V1.0-beta

## Statut du projet

✅ **Prêt pour test sur appareil réel**

- Code compilé, testé sur PC (JVM)
- APK debug fonctionnelle (25 MB)
- Compatible Xiaomi (MIUI/HyperOS) et Samsung (OneUI)
- Logs de diagnostic complets
- Documentation d'installation fournie

## Checklist de déploiement

### Avant installation

- [ ] Télécharger l'APK : `app/build/outputs/apk/debug/app-debug.apk`
- [ ] Vérifier l'appareil (Redmi Note 17 Pro ou S24)
- [ ] Activer mode développeur + débogage USB
- [ ] Installer ADB sur ta machine

### Installation

- [ ] `adb devices` — vérifier la connexion
- [ ] `adb install app-debug.apk` — installer l'app
- [ ] Ouvrir **Focus Reels** sur l'appareil
- [ ] Accepter l'alerte d'accessibilité → aller à la config

### Configuration

- [ ] Activer service d'accessibilité (paramètres système)
- [ ] Désactiver optimisation batterie :
  - **Xiaomi** : Paramètres → Apps → Focus Reels → Optimisation batterie → OFF
  - **Samsung** : Paramètres → Batterie → Gestion d'alimentation → Ajouter à liste blanche
- [ ] Redémarrer l'app

### Test de blocage

1. Ouvrir **Instagram**
2. Aller sur onglet **Reels**
3. **Attendu** : redirection immédiate vers l'onglet **Accueil**
4. Vérifier dans Focus Reels → **Historique** → une tentative enregistrée

### Diagnostic (logs)

Si le blocage ne fonctionne pas :

```bash
adb logcat -s "ReelsAccessibilityService|InstagramUiDetector"
```

**Logs à chercher** :
- ✅ `Service d'accessibilité connecté et prêt` → service actif
- ✅ `Événement AccessibilityEvent reçu` → Instagram détecté
- ✅ `Reels général détecté` → détection réussie
- ✅ `Blocage Reels activé : redirection` → blocage appliqué
- ❌ `Erreur lors de la détection` → Instagram a changé d'UI (à affiner)

## Résultats attendus

### Fonctionnalités validées en V1.0

| Fonctionnalité | Redmi Note 17 Pro | Galaxy S24 | Status |
|---|---|---|---|
| Blocage Reels | À tester | À tester | Implémenté |
| Alerte accessibilité | À tester | À tester | ✅ Implémenté |
| Friction progressive | À tester | À tester | ✅ Implanté (5→10→15s) |
| Reverrouillage auto | À tester | À tester | ✅ Implémenté (30 min) |
| Historique | À tester | À tester | ✅ Implémenté |
| Zéro réseau | ✅ Vérifiée | ✅ Vérifiée | ✅ Pas de permission INTERNET |

## Prochaines étapes après validation

1. **Affiner détection Instagram** (si logique `InstagramUiDetector` ne détecte pas)
   - Extraire les vrais IDs/descriptions depuis les logs
   - Mettre à jour `InstagramUiDetector.kt`
   - Régénérer l'APK et retester

2. **Tests robustesse**
   - Redémarrer appareil → vérifier reverrouillage auto
   - Tuer l'app → vérifier que service persist
   - Fermer accessibilité → vérifier alerte au relancement

3. **Optimisations**
   - Réduire empreinte mémoire (Reels are générés en 10+ threads)
   - Améliorer détection par machine learning (futur)
   - Support d'autres apps (TikTok, YouTube Shorts)

## Support et contact

Pour signaler un bug ou une amélioration : consulter `README.md` (roadmap) et les logs détaillés.
