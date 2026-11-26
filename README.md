<h1 align="center"><img src=".github/assets/readme-logo.png" alt="Tsuyu" width="640"></h1>

[![test](https://github.com/Sygix/Tsuyu/actions/workflows/test.yml/badge.svg)](https://github.com/Sygix/Tsuyu/actions/workflows/test.yml)
[![release](https://img.shields.io/github/v/release/Sygix/Tsuyu?include_prereleases&sort=semver)](https://github.com/Sygix/Tsuyu/releases)
[![license](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)

**Shizuku-compatible privileged access, built for Android TV.** Tsuyu brings the [Shizuku API](https://github.com/RikkaApps/Shizuku-API) to the TV, so compatible apps can use privileged Android capabilities without a phone or computer. Everything is designed for the television screen and its remote.

## Features

- **Made for Android TV** — a clear, remote-friendly flow, readable from the couch and navigable with the D-pad.
- **Five languages** — English, French, German, Spanish and Italian. Follow the TV language automatically or choose a language from Home, even before setup.
- **Start on the TV itself** — uses the TV's local ADB service to start Tsuyu with shell-level access. No phone, computer or wireless pairing flow required.
- **Shizuku API compatibility** — delivers the binder to compatible apps and supports their existing Shizuku API calls.
- **Consent you control** — review each app's request, allow or refuse it, revoke access later, or let a refused app ask again, from one « Accès des applications » list. A refused app cannot reopen the request until you allow it to ask again.
- **Designed to survive reboots and updates** — prepare automatic startup so Tsuyu resumes after the TV restarts or after one of its own updates.
- **Updates from the TV** — check the published releases, install a verified update in one press, or turn on silent automatic updates (off by default).
- **No account or telemetry** — Tsuyu runs locally on your TV. Its only network request is the update check against this repository's public releases.

## Getting started

Tsuyu's installation minimum is Android 9 (API 28). A usable local legacy ADB endpoint and firmware that delivers startup events are still required; the Android version alone does not establish device compatibility. Tsuyu is tested on a TCL Google TV running Android 14. If it fails, or works, on another TV, please [open an issue](https://github.com/Sygix/Tsuyu/issues/new/choose) with the brand, model and Android version. Below Android 14, the on-device debugging authorization prompt has not been exercised even on an emulator, and boot restart only on an Android 9 emulator through a proxied ADB endpoint; the service and client contracts were verified on Android 9 to 12 emulators (see `openspec/changes/android9-compatibility/verification.md`, later archived under `openspec/changes/archive/`).

1. Download Tsuyu from [Releases](https://github.com/Sygix/Tsuyu/releases).
2. Enable developer options and USB debugging when Tsuyu guides you through setup.
3. Start Tsuyu and approve the Android debugging prompt, selecting **Always allow**.

Keep USB debugging enabled while using Tsuyu. Disabling it can stop the service; enable it again and start Tsuyu to recover.

While USB debugging is on, the TV accepts ADB connection attempts from any device on your network, and any of them can make the authorization prompt appear. Approve the prompt only right after starting Tsuyu or renewing its authorization, refuse any prompt you did not trigger, and do not use Tsuyu on shared or untrusted networks.

With automatic startup enabled, Tsuyu periodically reconnects with its own ADB key to refresh that key's authorization. It does not change the global expiry policy or other keys, run shell commands during refresh, or open a new authorization prompt in the background. Android schedules the refresh roughly every twelve hours; prolonged power-off or background-work restrictions can still let the authorization expire. In that case, open « Démarrage au redémarrage » and choose « Renouveler l'autorisation » to approve the system prompt without restarting the service. An automatic startup waiting for authorization can be cancelled from Home or the startup repair screen, then retried without restarting the app.

« Désactiver le démarrage automatique » on the same screen stops the restart after reboots and the authorization refresh without stopping the running service.

Home distinguishes configured automatic startup from an automatic startup that actually succeeded during the current TV boot. A successful manual recovery clears an earlier startup failure without claiming that automatic startup succeeded.

## Language

Tsuyu follows the TV's system languages by default, using the first supported language or English if none is supported. Open **Language** on Home to choose **System language** or a specific language. Changes apply immediately to Tsuyu's screens, including app access requests, without changing the TV's language or the running service. On Android 9–12 Tsuyu saves the choice privately; on Android 13 and later it uses Android's app-language setting, migrating an older Tsuyu choice once without replacing a newer platform choice. The choice survives app restarts and updates. Selecting **System language** restores automatic language selection.

## Opening Tsuyu from another app

An app can open Tsuyu to help the user set it up. Launch `fr.sygix.tsuyu.MainActivity` explicitly, without `FLAG_ACTIVITY_NEW_TASK`. On Android 14 and later, pass `ActivityOptions.makeBasic().setShareIdentityEnabled(true)`: once the service runs and the restart preparation has a result, Tsuyu shows « Revenir à <app> » to return to it. Without a shared identity, or on Android 9–13, the user returns with normal Back navigation; Tsuyu does not infer a caller name from extras or the referrer.

## Updates

Tsuyu reads the public releases of this repository, verifies the downloaded APK (size, SHA-256, package, version and signing certificate) and installs it through its own service, without any dialog. Updates therefore need the service running and « Démarrage au redémarrage » prepared: the service restarts by itself afterwards, and apps reconnect with their access preserved. Prereleases are only offered when « Inclure les préversions » is on.

## Core capabilities

- **Local startup** — start a shell-uid service through the TV's own ADB daemon, with a one-time system authorization.
- **Shizuku API** — binder delivery, app permission requests, access management, revocation and refusal reset. A shared-UID group is authorized, revoked or reset as a whole, and revoking access stops its privileged processes immediately.
- **Remote-first interface** — service status, setup guidance and permission screens designed for TV navigation.
- **Boot persistence** — opt-in setup for automatic startup after a reboot, with status and recovery guidance.

## Credits

Tsuyu is an independent rewrite for Android TV, inspired by [Shizuku](https://github.com/RikkaApps/Shizuku), [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) and [Shevery](https://github.com/HmnDev-Tech/shevery). See [NOTICE](NOTICE) for source and license details.

> Tsuyu is not affiliated with Shizuku, Google or TCL. Android TV and Google TV are trademarks of Google LLC.

## License

Tsuyu is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE). Files derived from Shizuku or Shevery keep their Apache-2.0 license, and code derived from Shizuku-API keeps its MIT license, as stated in each file header and in [NOTICE](NOTICE).
