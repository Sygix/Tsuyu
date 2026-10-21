<h1 align="center"><img src=".github/assets/readme-logo.png" alt="Tsuyu" width="640"></h1>

[![test](https://github.com/Sygix/Tsuyu/actions/workflows/test.yml/badge.svg)](https://github.com/Sygix/Tsuyu/actions/workflows/test.yml)
[![release](https://img.shields.io/github/v/release/Sygix/Tsuyu?include_prereleases&sort=semver)](https://github.com/Sygix/Tsuyu/releases)
[![license](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)

**Shizuku-compatible privileged access, built for Android TV.** Tsuyu brings the [Shizuku API](https://github.com/RikkaApps/Shizuku-API) to the TV, so compatible apps can use privileged Android capabilities without a phone or computer. Everything is designed for the television screen and its remote.

> **Work in progress:** alpha builds (`tsuyu.apk`) are available on the [Releases](https://github.com/Sygix/Tsuyu/releases) page. Core features are implemented; validation on a real TV is still in progress.

## Features

- **Made for Android TV** — a clear, remote-friendly flow, readable from the couch and navigable with the D-pad.
- **Start on the TV itself** — uses the TV's local ADB service to start Tsuyu with shell-level access. No phone, computer or wireless pairing flow required.
- **Shizuku API compatibility** — delivers the binder to compatible apps and supports their existing Shizuku API calls.
- **Consent you control** — review each app's request, allow or refuse it, and revoke access later from Tsuyu.
- **Designed to survive reboots** — prepare automatic startup so Tsuyu can resume after the TV restarts.
- **No account or telemetry** — Tsuyu runs locally on your TV.

## Getting started

1. Install an alpha build from [Releases](https://github.com/Sygix/Tsuyu/releases).
2. Enable developer options and USB debugging when Tsuyu guides you through setup.
3. Start Tsuyu and approve the Android debugging prompt, selecting **Always allow**.

Keep USB debugging enabled while using Tsuyu. Disabling it can stop the service; enable it again and start Tsuyu to recover. ADB is available on the local network and protected by the TV's authorization prompt.

## Roadmap

### Implemented — validation in progress

- **Local startup** — start a shell-uid service through the TV's own ADB daemon, with a one-time system authorization.
- **Shizuku API** — binder delivery, app permission requests, authorized-app management and revocation.
- **Remote-first interface** — service status, setup guidance and permission screens designed for TV navigation.
- **Boot persistence** — opt-in setup for automatic startup after a reboot, with status and recovery guidance.

The implementation is covered by automated tests and emulator checks. Real-TV validation of startup, API compatibility and boot behavior remains in progress.

### Coming next

- **Release-ready distribution** — stable, signed releases.

## Credits

Tsuyu is an independent rewrite for Android TV, inspired by [Shizuku](https://github.com/RikkaApps/Shizuku), [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) and [Shevery](https://github.com/HmnDev-Tech/shevery). See [NOTICE](NOTICE) for source and license details.

> Tsuyu is not affiliated with Shizuku, Google or TCL. Android TV and Google TV are trademarks of Google LLC.

## License

Tsuyu is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE). Files derived from Shizuku or Shevery keep their Apache-2.0 license, and code derived from Shizuku-API keeps its MIT license, as stated in each file header and in [NOTICE](NOTICE).
