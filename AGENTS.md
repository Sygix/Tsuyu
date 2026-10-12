# AGENTS.md — instructions pour agents IA

## Vue d'ensemble
Tsuyu (露, « rosée ») : service d'accès privilégié pour Android TV / Google TV, compatible avec l'API Shizuku, utilisable entièrement à la télécommande. Il démarre un serveur avec les droits shell (ADB) sur la TV elle-même, sans ordinateur ni téléphone, le relance après chaque redémarrage et le met à disposition des apps clientes de Shizuku (dont SygixOs). Propriétaire : Sygix. Langue UI : français.

Tsuyu est une réécriture propre pour la TV, pas un fork. Shizuku et Shevery servent de référence et de source de code. On ne reprend que ce que le périmètre exige, sans les couches annexes : modules, WebUI, terminal, IA, Dhizuku, root, rish, Sui, thèmes.

## Feuille de route
- P0 — Socle : projet Gradle, CI `test`, en-têtes de licence, `NOTICE` à jour.
- P1 — Démarrage local : connexion adb à la TV elle-même avec la clé de l'app, « Toujours autoriser » au premier usage, lancement du serveur, état visible dans l'app.
- P2 — Compatibilité API Shizuku : binder, livraison aux apps clientes, écran d'autorisation au D-pad, apps autorisées et révocation. Validé avec l'app de démo de Shizuku-API et avec SygixOs.
- P3 — Persistance : accès que Tsuyu s'accorde à lui-même une fois démarré, relance au démarrage de la TV.
- P4 — Distribution : APK signé par la CI depuis un tag, releases GitHub.

Rien n'est spécifié à ce stade. Chaque phase devient un change OpenSpec, et les sujets non tranchés sont dans `openspec/backlog.md`.

## Faits vérifiés sur la TV de référence (TCL Google TV, Android 14)
- **Appairage :** le code d'appairage du débogage sans fil change dès que l'écran de code perd le focus (ouverture du panneau de notifications) et s'arrête quand on change d'app. Aucun appairage n'est donc possible depuis la TV elle-même : ni écran de l'app, ni notification, ni fenêtre superposée qui prend le focus.
- **Port 5555 :** le firmware fixe `service.adb.tcp.port=5555` à chaque démarrage. Dès qu'adbd tourne (débogage USB ou sans fil activé), il écoute aussi en TCP sur ce port, en protocole adb classique avec authentification RSA.
- **Autorisation d'une clé :** une clé inconnue déclenche « Autoriser le débogage ? » sur la TV. « Toujours autoriser » se coche et se valide à la télécommande.
- **Après un redémarrage :**
  - le débogage USB (`adb_enabled=1`) reste actif, et adbd démarre seul au boot puis réécoute sur 5555 ;
  - le débogage sans fil, lui, peut être désactivé, et son port TLS change à chaque démarrage.
- **AUTO_START (app-op constructeur) :**
  - **Par défaut :** `ignore` pour les apps tierces. Tant que le processus de l'app ne tourne pas, cela bloque `BOOT_COMPLETED` et la liaison d'un service d'accessibilité.
  - **Accord par le shell :** `appops set --uid <package> AUTO_START allow` fonctionne et la valeur persiste.
- **Expiration :** une clé adb autorisée expire après 7 jours sans connexion, sauf si `adb_allowed_connection_time` vaut 0 (réglage global, qui exige `WRITE_SECURE_SETTINGS`).

Toute autre hypothèse sur l'appareil se vérifie sur la TV avant d'être spécifiée.

## Compatibilité API Shizuku
- **Interface :** l'AIDL de Shizuku-API (`moe.shizuku.server.IShizukuService` et les interfaces associées). Les apps clientes appellent `rikka.shizuku.Shizuku` sans modification.
- **Livraison du binder :**
  - le serveur, avec l'uid shell, appelle la méthode `sendBinder` du `ShizukuProvider` (autorité `<package client>.shizuku`) ; le binder est passé dans l'extra `moe.shizuku.privileged.api.intent.extra.BINDER` ;
  - il le fait pour chaque app qui *demande* `moe.shizuku.manager.permission.API_V23` dans son manifeste, au démarrage du serveur puis à chaque démarrage d'un processus client.
- **Autorisation :** gérée par le serveur, ce n'est pas une permission Android. Une demande ouvre l'écran d'autorisation de Tsuyu.
- **Noms `moe.shizuku.*` :** les actions, extras et permissions demandées par les clients font partie de l'API et se réutilisent tels quels. Tsuyu ne *déclare* aucune permission `moe.shizuku.manager.permission.*` (voir Licences).
- **Limite assumée :** une app qui vérifie la présence du package `moe.shizuku.privileged.api` indique Shizuku comme absent.

## Workflow de spécification (OpenSpec)
- **À lire avant tout travail :** contexte, conventions et règles de rédaction sont dans `openspec/config.yaml` (`context`, `rules` par document, `operations`). `openspec instructions` les injecte.
- **Skills OpenSpec :** passer par eux (`openspec-propose`, `openspec-apply-change`, `openspec-verify-change`, `openspec-archive-change`…), car ils appliquent les règles du repo. Ne pas rédiger les documents d'un change à la main sans eux.
- **Exigences courantes :** une spec par capability, dans `openspec/specs/<capability>/spec.md`.
- **Feature ou changement de comportement :**
  1. créer d'abord `openspec/changes/<id>/proposal.md`, avec `tasks.md` et les deltas dans `specs/` ;
  2. le faire valider, puis implémenter ;
  3. archiver dans `changes/archive/`.
- **Specs courantes :** ne jamais éditer `openspec/specs/` directement. Elles évoluent uniquement par des changes implémentés.
- **Avant d'ouvrir ou de mettre à jour une PR de feature :** lancer `openspec-verify-change` sur le change et `./gradlew test`. Ne cocher une tâche, ou déclarer un point de review traité, qu'après vérification.
- **Après merge et validation sur la TV :** `openspec archive <id>`.

## Conventions de code (non négociables)
- SOLID, KISS, DRY : petites classes à responsabilité unique, interfaces aux frontières (connexion adb, serveur, réglages système), aucune logique dans les composables.
- State uniflow : ViewModel → StateFlow → Compose, sans état dupliqué.
- Aucun commentaire dans le code ni dans les tests, sauf l'en-tête de licence de chaque fichier.
- Toute opération système ou réseau : états chargement/vide/erreur en UI, timeout borné, jamais d'erreur avalée ni de crash silencieux. Une erreur dit à l'utilisateur quoi faire.
- Le serveur privilégié n'expose que l'API Shizuku et ce que l'app utilise. Aucune commande shell construite à partir d'une saisie non validée.
- Tests JUnit sur la logique (protocole adb, état du démarrage, autorisations), UI validée sur la TV réelle.

## Licences
- Licence du dépôt : AGPL-3.0-or-later. Tout fichier `.kt` / `.kts` / `.java` / `.aidl` écrit pour Tsuyu commence par cet en-tête, suivi d'une ligne vide (tests compris) :
  ```
  /*
   * Copyright (C) <année> Sygix
   * SPDX-License-Identifier: AGPL-3.0-or-later
   */
  ```
- **Fichier repris ou adapté de Shizuku ou de Shevery :**
  - il reste en Apache-2.0 et garde le copyright d'origine ;
  - il ajoute la ligne Sygix et indique sa provenance :
  ```
  /*
   * Copyright (C) <année d'origine> <auteur d'origine>
   * Copyright (C) <année> Sygix
   * SPDX-License-Identifier: Apache-2.0
   * Modified by Sygix from <projet>/<chemin d'origine>.
   */
  ```
- **Code repris de Shizuku-API :**
  - il reste en MIT ;
  - en-tête avec `Copyright (c) 2021 RikkaW` et `SPDX-License-Identifier: MIT`.
- **Interdits de Shizuku (section 6 de sa licence) :**
  - le nom « Shizuku » comme nom d'app ;
  - ses icônes ;
  - l'application id `moe.shizuku.privileged.api` ;
  - toute déclaration de permission `moe.shizuku.manager.permission.*`.
  - « Shizuku » ne peut apparaître que pour décrire la compatibilité.
- **Ne rien reprendre d'autre :** pas de code d'autres projets crédités par Shevery (Dhizuku, aShellYou…), puisque ce code est hors périmètre.
- **Mise à jour de `NOTICE` :** à chaque nouveau projet d'origine.
- **Vérification avant de pousser :** `git ls-files '*.kt' '*.kts' '*.java' '*.aidl' | xargs grep -L 'SPDX-License-Identifier'` doit être vide.
- **Dépendances externes :** uniquement des libs éprouvées et maintenues, sous licence compatible AGPL-3.0 (Apache-2.0, MIT, BSD, LGPL, GPL-3.0…).

## Git & packaging
- **Commits et branches :** Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`…) ; branches `feat/`, `fix/`, `chore/`. Aucun commit direct sur main après le commit initial.
- **Signature :** commits signés, avec un auteur cohérent avec le compte GitHub.
- **Package :** `fr.sygix.tsuyu`.
- **Cible :** Android TV 14+ (minSdk 34, compileSdk 37).

## Repo privé, à traiter comme public
Le repo passera public. Tout ce qui est poussé reste lisible dans l'historique : code, specs, messages de commit, descriptions et commentaires de PR, issues, logs de CI.
- **Jamais de secret :** clés, tokens, mots de passe, keystore, URL avec identifiants.
- **Jamais de détail de l'environnement local :**
  - chemins absolus (`/home/…`) ;
  - noms d'utilisateur, de machine ou de VM ;
  - adresses IP et ports d'appareils ;
  - noms de réseau, numéro de série ou modèle exact d'un appareil ;
  - comptes.
  - Dans les specs, les PR et les commits, l'appareil de test se désigne de façon générique : « TV Google TV TCL sous Android 14 ».
- **Données d'appareil :** des sorties de `adb shell`, `logcat` ou `dumpsys`, ne publier que la structure (propriétés, états, packages), jamais de données personnelles.
- **Jamais de sortie de build ni de cache :** `build/`, `.gradle/`, `.kotlin/`, APK.
- **Avant de pousser :** `git diff origin/main... | grep -nE '/home/|/Users/|([0-9]{1,3}\.){3}[0-9]{1,3}'` doit être vide, hors versions de dépendances et adresse de bouclage.

## Environnement
- Messages de commit en anglais, à l'impératif, courts.
- Repo : github.com/Sygix/Tsuyu.
- TV de référence : TCL Google TV, Android 14. Tests sur la TV réelle avec `adb connect <IP-TV>` en `assembleRelease`.

## Outils
- OpenSpec CLI (`openspec`) :
  - valider avec `openspec validate --all --strict` ;
  - archiver un change avec `openspec archive <id>`.
- pnpm obligatoire à la place de npm pour tout paquet Node.
- Format strict des specs : `### Requirement:` (SHALL/MUST) et `#### Scenario:` avec `- **WHEN** / **THEN**`.
