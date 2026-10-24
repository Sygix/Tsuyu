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
- **Consent you control** — review each app's request, allow or refuse it, revoke access later, or let a refused app ask again, from one « Accès des applications » list.
- **Designed to survive reboots and updates** — prepare automatic startup so Tsuyu resumes after the TV restarts or after one of its own updates.
- **Updates from the TV** — check the published releases, install a verified update in one press, or turn on silent automatic updates (off by default).
- **No account or telemetry** — Tsuyu runs locally on your TV.

## Getting started

1. Download Tsuyu from [Releases](https://github.com/Sygix/Tsuyu/releases).
2. Enable developer options and USB debugging when Tsuyu guides you through setup.
3. Start Tsuyu and approve the Android debugging prompt, selecting **Always allow**.

Keep USB debugging enabled while using Tsuyu. Disabling it can stop the service; enable it again and start Tsuyu to recover. ADB is available on the local network and protected by the TV's authorization prompt.

## Language

Tsuyu follows the TV's system languages by default, using the first supported language or English if none is supported. Open **Language** on Home to choose **System language** or a specific language. Changes apply immediately to Tsuyu's screens, including app access requests, without changing the TV's language or the running service. Android remembers the choice across app restarts and updates. Selecting **System language** restores automatic language selection.

## Opening Tsuyu from another app

An app can open Tsuyu to help the user set it up. Launch `fr.sygix.tsuyu.MainActivity` explicitly, without `FLAG_ACTIVITY_NEW_TASK`, and pass `ActivityOptions.makeBasic().setShareIdentityEnabled(true)`. Once the service runs and the restart preparation has a result, Tsuyu shows « Revenir à <app> » to return to it. Without a shared identity, the user returns with Back.

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
