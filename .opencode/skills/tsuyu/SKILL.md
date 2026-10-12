---
name: tsuyu
description: "Use for any work on the Tsuyu repo (Shizuku-API-compatible privileged service for Android TV): where the reference code lives, protocol facts and pitfalls the specs rely on."
---

# Tsuyu

Source of truth for rules: `AGENTS.md`, `DESIGN.md` and `openspec/config.yaml` on main. Re-read them; never contradict them. This skill adds facts about the reference code and the platform.

## Reference code
- **Shizuku** (Apache-2.0, github.com/RikkaApps/Shizuku, latest release v13.6.0):
  - the server (`server/`), started with `app_process` by the starter (`starter/`);
  - the manager's adb client (`manager/.../adb/`).
- **Shizuku-API** (MIT, github.com/RikkaApps/Shizuku-API, API version 13.1.5):
  - the AIDL (`aidl/`) and the client library (`api/`, `provider/`);
  - the demo app (`demo/`) is the reference client for compatibility tests.
- **Shevery** (Apache-2.0, github.com/HmnDev-Tech/shevery): a Shizuku fork with TV attempts. Reviewed state: commit `95ea592`. It bumps the API version to 14.3, which upstream Shizuku-API does not know. Report the upstream API version unless a spec decides otherwise.
- **What to take:** take only what a spec needs, and rewrite it to the repo's conventions. A copied file follows the header rule of AGENTS.md.

## Protocol facts
- **Legacy adb over TCP:**
  - `CNXN`, then `AUTH`: sign the token with the app's RSA key (`ADB_AUTH_SIGNATURE`);
  - if the key is unknown, send the public key in adb format (`ADB_AUTH_RSAPUBLICKEY`); adbd then shows the "Allow debugging" prompt and the connection waits for it.
  - Shizuku's `AdbClient`/`AdbKey` already implement this alongside the TLS path. The TLS path (`STLS`) needs a pairing that is impossible on the reference TV.
- **The key** must live in the Android Keystore or in app-private storage, never in a file other apps can read. If the key changes, the user must accept a new prompt.
- **Client binder delivery:** see AGENTS.md, Shizuku API compatibility. In Shizuku it lives in `ShizukuService.sendBinderToUserApp` and `BinderSender` (process observer).
- **Permission prompt:** Shizuku's server starts the manager's request activity by component name. Tsuyu's server must target Tsuyu's own activity, which must work with the D-pad.

## Pitfalls seen in Shizuku and Shevery
- **TV branch of Shevery's start worker:** it requires `service.adb.tcp.port`, a property that `adb tcpip` sets and that does not survive a reboot. On a TV whose firmware does not set it, the branch fails at every boot. Discover the listening port; do not assume it.
- **Shevery's pairing:** it relies on split screen or on a reply in a notification. Neither exists on TV.
- **Boot receivers:** a vendor app-op (AUTO_START on TCL) can block them until the shell allows it. Plan the first start so the app grants itself what later boots need.
- **Phone-only material:** Material 3 bottom sheets, pull-to-refresh and long-press menus are unusable with a remote.
