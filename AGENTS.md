# AGENTS.md — instructions for AI agents

## Overview
Tsuyu (露, "dew") is a privileged access service for Android TV / Google TV. It is compatible with the Shizuku API and fully usable with the remote. Tsuyu:
- starts a server with shell (ADB) rights on the TV itself, with no computer and no phone;
- restarts it after every reboot;
- makes it available to Shizuku client apps, including SygixOs.

Owner: Sygix. UI language: French.

Tsuyu is a clean rewrite for TV, not a fork. Shizuku and Shevery are references and a source of code. Take only what the scope needs, and leave out their side layers: modules, WebUI, terminal, AI, Dhizuku, root, rish, Sui, themes.

## Roadmap
- **P0 — Foundation:** delivered.
  - Minimal Gradle project: `app` module with an empty screen.
  - CI: `test`, `release` and `codeql`, plus Dependabot.
  - The module layout (server, AIDL) is decided in the P1 change.
- **P1 — Local start:** adb connection to the TV itself with the app's own key, a one-time "Always allow", server launch, and the state shown in the app.
- **P2 — Shizuku API compatibility:**
  - binder, and its delivery to client apps;
  - D-pad permission screen;
  - authorized apps, with revocation.
  - Validated with the Shizuku-API demo app and with SygixOs.
- **P3 — Persistence:** the access Tsuyu grants itself once started, and the restart when the TV boots.
- **P4 — Distribution:** APK signed by CI from a tag, published as a GitHub release.

Nothing is specified yet. Each phase becomes an OpenSpec change. Undecided topics live in `openspec/backlog.md`.

## Verified facts on the reference TV (TCL Google TV, Android 14)
- **Pairing is impossible from the TV:**
  - the wireless debugging pairing code changes as soon as the code screen loses focus, for example when the notification panel opens;
  - it stops when the user switches apps;
  - so no app screen, notification or focus-taking overlay can carry out the pairing.
- **Port 5555:**
  - the firmware sets `service.adb.tcp.port=5555` at every boot;
  - whenever adbd runs (USB or wireless debugging enabled), it also listens on that TCP port with the legacy adb protocol and RSA authentication.
- **Key prompt:** an unknown key triggers "Allow debugging?" on the TV. "Always allow" can be ticked and confirmed with the remote.
- **After a reboot:**
  - USB debugging (`adb_enabled=1`) stays on; adbd then starts by itself at boot and listens on 5555 again;
  - wireless debugging may be switched off, and its TLS port changes at every boot.
- **AUTO_START (vendor app-op):**
  - It is `ignore` by default for third-party apps. While the app process isn't running, this blocks `BOOT_COMPLETED` and the binding of an accessibility service.
   - `appops set --uid <package> AUTO_START allow` changes only the uid mode; the package mode stays `ignore` until `appops set <package> AUTO_START allow` also runs. Both modes can be set to `allow` and persist.
   - Which mode the vendor boot policy reads is unverified. Prepare and read back both modes, and validate boot delivery with the app process absent.
- **Key expiry:** an authorized adb key expires after 7 days without a connection, unless `adb_allowed_connection_time` is 0. This is a global setting, and writing it requires `WRITE_SECURE_SETTINGS`.
- **Disabling debugging:** init kills adbd's process cgroup with SIGKILL. Shell children remain in that cgroup even after `setsid`; the shell cannot write the migration files. Keep USB debugging enabled while using the service.
- **Legacy launch stream:** an immediate shell return can kill a background child before `setsid` and `app_process` complete. Wait for deterministic server readiness before releasing the shell; a fixed delay is not a readiness check.

Any other assumption about the device must be checked on the TV before it is specified.

## Shizuku API compatibility
- **Interface:** the Shizuku-API AIDL (`moe.shizuku.server.IShizukuService` and related interfaces). Client apps call `rikka.shizuku.Shizuku` unchanged.
- **Binder delivery:**
  - The server runs with the shell uid. It calls the `sendBinder` method of the client's `ShizukuProvider` (authority `<client package>.shizuku`), with the binder in the extra `moe.shizuku.privileged.api.intent.extra.BINDER`.
  - Targets: every app that *requests* `moe.shizuku.manager.permission.API_V23` in its manifest.
  - When: when the server starts, and whenever a client process starts.
- **Permission:** the server manages it; it is not an Android permission. A request opens Tsuyu's permission screen.
- **`moe.shizuku.*` names:**
  - Actions, extras and permissions requested by clients are part of the API, so they are reused as is.
  - Tsuyu *declares* no `moe.shizuku.manager.permission.*` permission (see Licenses).
- **Accepted limit:** an app that checks for the `moe.shizuku.privileged.api` package reports Shizuku as missing.

## Specification workflow (OpenSpec)
- **Before any work:**
  - read `openspec/config.yaml` (`context`, per-document `rules`, `operations`): it holds the context, conventions and writing rules;
  - `openspec instructions` injects them.
- **Skills:** go through the OpenSpec skills (`openspec-propose`, `openspec-apply-change`, `openspec-verify-change`, `openspec-archive-change`…). They apply the repo rules, so never write a change's documents by hand without them.
- **Current requirements:** one file per capability, `openspec/specs/<capability>/spec.md`.
- **New feature or behavior change:**
  1. First create `openspec/changes/<id>/proposal.md`, plus `tasks.md` and the deltas in `specs/`.
  2. Get it approved.
  3. Implement it.
  4. Archive it into `changes/archive/`.
- **`openspec/specs/`:** never edit it directly. Current specs only change through implemented changes.
- **Before opening or updating a feature PR:**
  - run `openspec-verify-change` on the change and `./gradlew test`;
  - only tick a task, or report a review point as handled, after checking it.
- **After merge and validation on the TV:** `openspec archive <id>`.

## Code conventions (non-negotiable)
- **SOLID, KISS, DRY:**
  - small single-purpose classes;
  - interfaces at the boundaries: adb connection, server, system settings;
  - no logic in composables.
- **Uniflow state:** ViewModel → StateFlow → Compose, with no duplicated state.
- **Comments:** none in code or tests, except each file's license header.
- **System and network operations:**
  - loading, empty and error states in the UI;
  - bounded timeouts;
  - never a swallowed error or a silent crash;
  - every error tells the user what to do.
- **Privileged server:**
  - it exposes only the Shizuku API and what the app uses;
  - no shell command is built from unvalidated input.
- **Tests:**
  - JUnit on the logic: adb protocol, start state, permissions;
  - the UI is validated on the real TV.

## Licenses
- **Repository license:** AGPL-3.0-or-later.
- **Files written for Tsuyu:** every `.kt` / `.kts` / `.java` / `.aidl` file starts with this header, then a blank line (tests included):
  ```
  /*
   * Copyright (C) <year> Sygix
   * SPDX-License-Identifier: AGPL-3.0-or-later
   */
  ```
- **Files taken or adapted from Shizuku or Shevery:**
  - they stay Apache-2.0 and keep the original copyright;
  - they add the Sygix line and state where they come from:
  ```
  /*
   * Copyright (C) <original year> <original author>
   * Copyright (C) <year> Sygix
   * SPDX-License-Identifier: Apache-2.0
   * Modified by Sygix from <project>/<original path>.
   */
  ```
- **Code taken from Shizuku-API:** stays MIT, with a header holding `Copyright (c) 2021 RikkaW` and `SPDX-License-Identifier: MIT`.
- **Forbidden by Shizuku (section 6 of its license):**
  - the name "Shizuku" as an app name;
  - its icons;
  - the application id `moe.shizuku.privileged.api`;
  - any declaration of a `moe.shizuku.manager.permission.*` permission.

  "Shizuku" may only appear to describe compatibility.
- **Other projects credited by Shevery** (Dhizuku, aShellYou…): take nothing from them; that code is out of scope.
- **`NOTICE`:** update it whenever code comes from a new origin project.
- **Check before pushing:** `git ls-files '*.kt' '*.kts' '*.java' '*.aidl' | xargs grep -L 'SPDX-License-Identifier'` must be empty.
- **External dependencies:** only proven, maintained libraries, under a license compatible with AGPL-3.0 (Apache-2.0, MIT, BSD, LGPL, GPL-3.0…).

## Git & packaging
- **Commits:** Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`…), messages in English, short and in the imperative.
- **Branches:** `feat/`, `fix/`, `chore/`. No direct commit on main.
- **Signing:** signed commits, with an author consistent with the GitHub account.
- **Package:** `fr.sygix.tsuyu`.
- **Target:** Android TV 14+ (minSdk 34, compileSdk 37).

## CI and release
- **`test`:** runs `./gradlew test` on every PR and on main. It is the required check for merging.
- **`codeql`:** runs only while the repository is public, because code scanning is unavailable on a private repository.
- **`release`:**
  - a signed tag `vX.Y.Z` or `vX.Y.Z-(alpha|beta|rc).N` builds the APK, signs it with the key of the `production` environment, and publishes the GitHub release;
  - the versionCode is derived from the tag (`app/build.gradle.kts`);
  - tags and releases belong to Sygix, never to an agent.
- **Actions:**
  - pinned by full SHA;
  - minimal permissions per job, with `permissions: {}` at workflow level;
  - the action allowlist is a repository setting: Sygix must add a new third-party action before it can be used.
- **Merging:** rebase only, linear history, signed commits, branch deleted after merge.

## Private repository, treated as public
The repository will become public. Everything pushed stays readable in the history: code, specs, commit messages, PR descriptions and comments, issues, CI logs.
- **Never a secret:** keys, tokens, passwords, keystores, URLs with credentials.
- **Never a detail of the local environment:**
  - absolute paths (`/home/…`);
  - user, machine or VM names;
  - device IP addresses and ports;
  - network names;
  - a device's serial number or exact model;
  - accounts.

  In specs, PRs and commits, name the test device generically: "TCL Google TV running Android 14".
- **Device output** (`adb shell`, `logcat`, `dumpsys`): publish only its structure (properties, states, packages), never personal data.
- **Never build output or caches:** `build/`, `.gradle/`, `.kotlin/`, APKs.
- **Check before pushing:** `git diff origin/main... | grep -nE '/home/|/Users/|([0-9]{1,3}\.){3}[0-9]{1,3}'` must be empty, except for dependency versions and the loopback address.

## Environment
- Repository: github.com/Sygix/Tsuyu.
- Reference TV: TCL Google TV, Android 14. Test on the real TV with `adb connect <TV-IP>`, using `assembleRelease`.

## Tools
- OpenSpec CLI (`openspec`):
  - validate with `openspec validate --all --strict`;
  - archive a change with `openspec archive <id>`.
- Use pnpm, not npm, for any Node package.
- Strict spec format: `### Requirement:` (SHALL/MUST), `#### Scenario:` with `- **WHEN** / **THEN**`.
- Spec artifacts (proposal, design, tasks, specs) are written in English, as set in `openspec/config.yaml`; UI strings stay in French.
