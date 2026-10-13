# Proposal

## Why

Tsuyu currently has only an empty TV screen. A first local shell server start must work from the remote without wireless pairing, a phone or a computer.

## What Changes

- Guide the user through developer options, USB debugging and the system's one-time “Always allow” key confirmation.
- Explain that debugging leaves a local-network port open, accessible only to authorized devices; provide no close-port option.
- Detect a usable local legacy ADB endpoint, authenticate with Tsuyu's private RSA key and start a shell-uid server.
- Show verified running, stopped, setup, waiting and actionable error states.
- Confirm server readiness before releasing the launch shell so it survives ADB disconnection; keep debugging enabled during use and explain that disabling it can terminate the server. Exit on Tsuyu package replacement or removal.
- Detect unsupported TVs without a legacy TCP endpoint and explain the v1 limitation.

## Capabilities

### New Capabilities

- `local-start`: local ADB discovery, authentication, server launch and verified state.
- `tv-navigation`: common remote navigation and accessible TV presentation for setup, home and help.

### Modified Capabilities

None. There are no current capability specs.

## Dependencies

P0 foundation only. P2 (`p2-shizuku-compatibility`) depends on this change; P3 (`p3-boot-persistence`) depends on P2. Implementation PRs and later archives follow P1 → P2 → P3.

## Non-goals

- Client API, client permission requests, authorized-app management and UserServices: P2.
- Self-granted persistence access and automatic restart after boot: P3.
- Signed release distribution: P4.
- TLS pairing, external-device setup and TVs without a legacy TCP endpoint: outside v1.
- Root, Sui, rish, terminal, modules, WebUI, AI and Dhizuku: outside scope.

## Impact

Add focused server and internal IPC modules alongside `app`; add StateFlow/ViewModel, TV Compose components and test dependencies through the version catalog. Add the network permission and an authenticated manager provider, without declaring upstream manager permissions. Adapt only the needed legacy ADB implementation with Apache-2.0 provenance headers and NOTICE entries.

## Open questions

None for the accepted P1 behavior. Device assumptions and their validation obligations are documented in design.md, not treated as verified facts.
