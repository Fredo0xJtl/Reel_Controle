# CAHIER DES CHARGES

## Application Android de blocage sélectif de contenus
### "Focus Reels" — Blocage local des Reels Instagram

*Document rédigé pour transmission à un développeur*
*Version 1.0 — 01/08/2026*

> **Principe fondamental du projet**
> - Application 100 % personnelle, à usage strictement individuel.
> - Aucune donnée ne doit sortir du téléphone : pas de serveur, pas de cloud, pas d'analytics tiers, pas de connexion réseau.
> - Objectif : bloquer l'accès à l'onglet Reels d'Instagram, tout en conservant la possibilité de voir les Reels envoyés par des amis en message privé.

---

## Sommaire

1. [Contexte et objectifs](#1-contexte-et-objectifs)
2. [Périmètre du projet](#2-périmètre-du-projet)
3. [Spécifications fonctionnelles](#3-spécifications-fonctionnelles)
   - 3.1 [Blocage de l'onglet Reels](#31-blocage-de-longlet-reels)
   - 3.2 [Comportement avec les Reels reçus en message privé](#32-comportement-avec-les-reels-reçus-en-message-privé)
   - 3.3 [Système de déblocage à friction progressive](#33-système-de-déblocage-à-friction-progressive)
   - 3.4 [Reverrouillage automatique](#34-reverrouillage-automatique)
   - 3.5 [Écran de configuration](#35-écran-de-configuration)
   - 3.6 [Statistiques et historique](#36-statistiques-et-historique)
4. [Spécifications techniques](#4-spécifications-techniques)
   - 4.1 [Environnement cible](#41-environnement-cible)
   - 4.2 [Mécanisme de détection et de blocage](#42-mécanisme-de-détection-et-de-blocage)
   - 4.3 [Contraintes spécifiques MIUI / HyperOS (Xiaomi)](#43-contraintes-spécifiques-miui--hyperos-xiaomi)
   - 4.4 [Confidentialité et gestion des données](#44-confidentialité-et-gestion-des-données)
   - 4.5 [Résilience aux mises à jour d'Instagram](#45-résilience-aux-mises-à-jour-dinstagram)
5. [Interface utilisateur](#5-interface-utilisateur)
6. [Installation et distribution](#6-installation-et-distribution)
7. [Maintenance et évolutivité](#7-maintenance-et-évolutivité)
8. [Hors périmètre (version 1)](#8-hors-périmètre-version-1)
9. [Tableau récapitulatif des paramètres par défaut](#9-tableau-récapitulatif-des-paramètres-par-défaut)

---

## 1. Contexte et objectifs

Le porteur de projet utilise Instagram quotidiennement et constate que l'onglet Reels (contenu vidéo court, défilement infini) représente la principale source de temps passé de façon non maîtrisée sur l'application. Des solutions existent sur le marché (Wall Habit, One Sec, etc.), mais elles imposent de faire confiance à un tiers pour la collecte et le traitement de données comportementales sensibles (habitudes de navigation, usage des applications).

Le présent projet vise donc à faire développer une application Android strictement personnelle, installée en local, qui reproduit un mécanisme de blocage sélectif et de friction comportementale, sans aucune transmission de données vers l'extérieur.

### Objectifs principaux

- Empêcher l'accès direct à l'onglet Reels d'Instagram (flux dédié).
- Permettre malgré tout la lecture des Reels envoyés individuellement par des amis, en message privé ou dans un groupe.
- Introduire une friction volontaire et croissante pour décourager le déblocage impulsif, sans reposer sur la seule volonté.
- Garantir que toutes les données (statistiques, historique, réglages) restent stockées uniquement sur l'appareil.

---

## 2. Périmètre du projet

- **Application ciblée :** uniquement Instagram (application native Android), en version 1. Pas de navigateur, pas d'autres réseaux sociaux à ce stade.
- **Utilisateur :** usage strictement individuel et durable ; pas de gestion multi-utilisateurs, pas de partage prévu.
- **Format de livraison :** fichier APK installable par sideload (hors Google Play Store).
- **Budget / délai :** aucune contrainte de budget ni de deadline fixée par le porteur de projet.

---

## 3. Spécifications fonctionnelles

### 3.1 Blocage de l'onglet Reels

Lorsque l'utilisateur navigue dans Instagram et tente d'accéder à l'onglet Reels dédié (icône Reels du menu principal, ou tout accès direct à ce flux), l'application doit :

1. Détecter l'ouverture de cet onglet en temps réel.
2. Rediriger automatiquement et immédiatement l'utilisateur vers l'onglet sur lequel il se trouvait juste avant (dans la majorité des cas, l'onglet Accueil).

*Le blocage porte uniquement sur l'onglet Reels dédié. Les Reels qui apparaissent naturellement dans le fil d'actualité (Accueil) ne sont pas bloqués, car ce flux est perçu comme lié au contenu des amis suivis.*

### 3.2 Comportement avec les Reels reçus en message privé

- Un Reels envoyé par un ami en message privé (conversation 1-to-1) doit s'ouvrir et se lire normalement, exactement comme le comportement standard actuel d'Instagram.
- Un Reels partagé dans une conversation de groupe doit également rester accessible et lisible normalement.
- En revanche, dès que l'utilisateur swipe (défilement vertical) vers un contenu suggéré après la lecture de ce Reels envoyé par DM, cela doit être considéré comme une entrée dans le flux Reels général, et déclencher la redirection décrite en 3.1.
- Le nombre de swipes tolérés avant redirection (par défaut : **1 seul swipe**) doit être paramétrable dans l'écran de configuration (voir 3.5).

### 3.3 Système de déblocage à friction progressive

Le blocage n'est pas permanent : l'utilisateur peut le désactiver à tout moment en revenant dans l'application de configuration et en décochant le blocage des Reels. Ce déblocage est volontairement soumis à une friction croissante au fil de la journée, afin de limiter les déblocages impulsifs sans les rendre impossibles.

**Fonctionnement par défaut**

- 1ère tentative de déblocage de la journée : un écran neutre d'attente de **5 secondes** s'affiche avant que le déblocage ne prenne effet.
- 2ème tentative : **10 secondes** d'attente.
- 3ème tentative : **15 secondes** d'attente.
- Et ainsi de suite, incrément de **+5 secondes** à chaque nouvelle tentative de déblocage, sans plafond.
- Le compteur de tentatives (et donc le délai) se réinitialise automatiquement chaque jour à **00h00**.

**Paramétrage libre de la progression**

Ce système de progression doit être entièrement configurable par l'utilisateur depuis l'application, sous forme de règle libre définie par trois valeurs modifiables :

- Délai de base (valeur par défaut : 5 secondes).
- Incrément ajouté à chaque déblocage (valeur par défaut : +5 secondes).
- Fréquence/déclencheur de l'incrément — par exemple « à chaque déblocage », ou « après X minutes d'utilisation cumulée » (l'utilisateur doit pouvoir saisir ses propres valeurs plutôt que choisir parmi des préréglages fixes).

*L'écran neutre affiché pendant le délai ne doit contenir aucun contenu additionnel imposé (pas d'exercice de respiration obligatoire, pas de question) — un simple minuteur visuel suffit, sauf évolution future souhaitée par l'utilisateur.*

### 3.4 Reverrouillage automatique

- Une fois le blocage des Reels désactivé (après le délai de friction), il reste désactivé pendant **30 minutes**.
- Passé ce délai de 30 minutes, le blocage se réactive automatiquement, sans action nécessaire de l'utilisateur.
- Ce délai de reverrouillage (30 minutes par défaut) doit être consultable et modifiable dans l'écran de configuration.

### 3.5 Écran de configuration

L'application doit proposer un écran principal de configuration permettant de :

- Activer / désactiver le blocage des Reels pour Instagram (case à cocher).
- Consulter et modifier les paramètres du système de friction progressive (délai de base, incrément, déclencheur — voir 3.3).
- Consulter et modifier la durée de reverrouillage automatique (voir 3.4).
- Consulter et modifier le nombre de swipes tolérés après un Reels reçu en DM avant redirection (voir 3.2).
- Consulter les statistiques et l'historique des tentatives bloquées (voir 3.6).

*L'architecture de cet écran doit être conçue de façon modulaire (une entrée par application bloquée), même si seule Instagram est implémentée en version 1, pour permettre l'ajout ultérieur d'autres applications sans refonte complète.*

### 3.6 Statistiques et historique

- Historique détaillé de chaque tentative d'accès bloquée à l'onglet Reels, avec horodatage précis (date et heure).
- Conservation de cet historique de façon illimitée dans le temps (pas de purge automatique).
- Ces données sont stockées exclusivement en local sur l'appareil (voir 4.4) et ne sont jamais transmises.

---

## 4. Spécifications techniques

### 4.1 Environnement cible

- Système d'exploitation : Android (testé en priorité sur Xiaomi Redmi Note 17 Pro, HyperOS / MIUI).
- Format de livraison : fichier APK, installation manuelle (sideload).
- Aucune publication prévue sur le Google Play Store en version 1.

### 4.2 Mécanisme de détection et de blocage

Le développeur utilisera un **service d'accessibilité Android** (AccessibilityService) pour :

1. Détecter en temps réel l'affichage de l'onglet Reels au sein de l'application Instagram.
2. Déclencher automatiquement une action de retour vers l'écran précédent lorsque ce blocage est actif.
3. Détecter le contexte « Reels reçu en DM » versus « flux Reels général », notamment via le comptage des swipes après lecture d'un Reels partagé en message privé.

**Ce service doit fonctionner uniquement en local : aucune donnée lue par le service d'accessibilité ne doit être stockée en dehors de l'appareil, ni transmise à un tiers, ni utilisée à d'autres fins que le blocage décrit dans ce document.**

### 4.3 Contraintes spécifiques MIUI / HyperOS (Xiaomi)

Les surcouches Xiaomi (MIUI / HyperOS) sont connues pour tuer agressivement les processus et services en arrière-plan afin d'économiser la batterie, ce qui peut interrompre le service d'accessibilité sans prévenir. Le développeur doit donc prévoir :

- Un écran d'onboarding qui guide explicitement l'utilisateur pour désactiver l'optimisation de batterie sur l'application (accès à l'écran système correspondant).
- Une vérification périodique ou un indicateur visible dans l'application permettant de savoir si le service d'accessibilité est toujours actif.
- Idéalement, une notification discrète ou un rappel si le service venait à être coupé par le système, pour que l'utilisateur puisse le relancer manuellement.

### 4.4 Confidentialité et gestion des données

> **Exigences non négociables**
> - Aucune connexion réseau sortante de l'application (pas d'appel API, pas de SDK publicitaire ou analytics tiers).
> - Toutes les données (réglages, historique, statistiques) sont stockées uniquement en local (base de données locale de type SQLite/Room ou équivalent).
> - Aucun compte utilisateur, aucune authentification externe, aucune télémétrie.
> - Le style d'écriture du code n'est pas un critère de qualité imposé (l'application est à usage strictement personnel), mais l'absence de toute dépendance réseau doit être vérifiable simplement (ex. permission INTERNET absente du manifeste Android, sauf si strictement nécessaire techniquement, à justifier le cas échéant).

### 4.5 Résilience aux mises à jour d'Instagram

Instagram modifie régulièrement son interface, ce qui peut casser la détection des éléments d'écran (identifiants, hiérarchie de vues) utilisés par le service d'accessibilité.

- En cas d'échec de détection (l'application ne reconnaît plus l'onglet Reels), le comportement par défaut doit être le mode **« fail-open »** : l'application laisse passer sans bloquer, plutôt que de bloquer Instagram dans son intégralité.
- Le code doit être structuré de façon à isoler clairement la logique de reconnaissance des éléments d'interface Instagram (idéalement dans un module ou fichier dédié), afin de faciliter une mise à jour rapide par le développeur ou une autre personne en cas de changement d'interface.
- Une clause de maintenance (ponctuelle, à la demande, sans engagement formel de durée) sera à discuter directement avec le développeur retenu, en fonction de sa disponibilité.

---

## 5. Interface utilisateur

L'interface doit rester volontairement minimaliste, sans recherche esthétique particulière. Elle doit néanmoins comporter clairement :

- Un écran principal listant les applications gérées (Instagram uniquement en V1) avec un interrupteur d'activation du blocage.
- Un écran de réglages détaillés pour le système de friction progressive (délai de base, incrément, déclencheur) et le délai de reverrouillage automatique.
- Un écran d'historique listant les tentatives bloquées avec horodatage.
- Un écran (ou une étape d'onboarding) dédié à l'activation du service d'accessibilité et à la désactivation de l'optimisation de batterie (spécifique MIUI).

---

## 6. Installation et distribution

- Livraison sous forme de fichier APK signé, installable manuellement sur le téléphone du porteur de projet (activation de l'installation d'applications hors Play Store requise côté utilisateur).
- Le développeur devra fournir une notice d'installation simple (étapes à suivre pour installer l'APK et activer les permissions nécessaires : accessibilité, désactivation de l'optimisation batterie).

---

## 7. Maintenance et évolutivité

- Le code doit permettre d'ajouter facilement une nouvelle application à bloquer à l'avenir (architecture modulaire évoquée en 3.5), même si cela ne fait pas partie du périmètre de la version 1.
- Le mécanisme de détection Instagram doit pouvoir être mis à jour indépendamment du reste de l'application, en anticipation des changements d'interface réguliers d'Instagram.
- Aucun engagement de maintenance long terme n'est demandé au développeur dans le cadre de ce cahier des charges ; les modalités d'un éventuel suivi seront à négocier séparément.

---

## 8. Hors périmètre (version 1)

- Blocage d'autres applications que Instagram (TikTok, YouTube Shorts, Snapchat, etc.).
- Blocage via navigateur web.
- Fonctionnalités sociales, partage, ou synchronisation entre plusieurs appareils.
- Publication sur le Google Play Store.
- Contenu additionnel imposé pendant l'écran de friction (respiration guidée, question de motivation, etc.).

---

## 9. Tableau récapitulatif des paramètres par défaut

| Paramètre | Valeur par défaut |
|---|---|
| Délai de base (1er déblocage du jour) | 5 secondes |
| Incrément par déblocage | +5 secondes, illimité, valeurs modifiables librement |
| Réinitialisation du compteur | Chaque jour à 00h00 |
| Durée avant reverrouillage automatique | 30 minutes, modifiable |
| Swipes tolérés après Reels reçu en DM | 1 swipe, modifiable |
| Comportement en cas d'échec de détection | Fail-open (aucun blocage tant que non corrigé) |
| Conservation de l'historique | Illimitée, aucune purge automatique |
| Connexion réseau | Aucune — fonctionnement 100 % local |

---

*Document destiné à être transmis tel quel au développeur en charge de la réalisation de l'application.*


# ANNEXE V1.1
Ajouts décidés...