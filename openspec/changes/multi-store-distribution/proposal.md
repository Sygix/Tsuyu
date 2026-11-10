# Proposal

## Why

Tsuyu needs store-managed distribution alongside its existing GitHub updater. A build that cannot update itself, reproducible developer-signed APKs and explicit device-validation evidence make publication and cross-channel upgrades supportable without confusing installation eligibility with tested TV compatibility.

## What Changes

- Introduce `store` and `standalone` distribution variants; exclude the internal updater and its privileged installer from store builds.
- Publish the existing `tsuyu.apk` standalone asset, a `tsuyu-store.apk` and a Play `tsuyu-store.aab`, using coherent tag-derived version codes.
- Preserve app data across compatible-signature upgrades and prevent a previously scheduled standalone updater from running after migration to store.
- Prepare Google Play, F-Droid and Amazon publication materials and runbooks; use reproducible developer-signed delivery for F-Droid and configure Play signing for compatible upgrades.
- Treat Amazon's documented re-signing as a migration exception, with Fire TV and delivered-artifact validation required before claiming support.
- Add a community test protocol, structured reports and a README compatibility matrix backed by reviewed, release-specific evidence.

## Capabilities

### New Capabilities

- `release-distribution`: build variants, artifacts, signing, migration, store-delivery readiness and release publication contracts.
- `device-validation`: repeatable compatibility reports, evidence review and public support-status maintenance.

### Modified Capabilities

- `self-update`: scope the existing updater requirements to standalone builds and remove their UI/work/installation capability from store builds through the distribution contract.

The existing `tv-navigation`, `local-start`, `boot-persistence`, `client-access` and `shizuku-api` contracts are preserved and referenced, not duplicated.

## Impact

App/server source sets, composition root, home navigation, manager IPC implementation, updater scheduling/storage, manifest, Gradle packaging and release/test CI. Public metadata, privacy documentation, community issue template, README and store runbooks are added during implementation. Actual account operations and store approvals are separate publication steps.

## Dependencies and order

Depends on `android9-compatibility`, which must be implemented and archived first. This change does not modify its baseline or caller-return requirements. The two proposals can be reviewed together; their implementations remain ordered.

## Non-goals

Automatic submission to store consoles, a new privileged-start mechanism, data export between incompatible signatures and guarantees of store approval are outside this change. Future store automation or additional device-start methods require separate scope. Current same-signature restart and Shizuku consent semantics remain owned by their existing capabilities.

## Open questions

- Publication inputs: Play account eligibility and any existing signing enrollment, public privacy-page location and listing contact details. These block the corresponding publication step, not variant implementation.
- Amazon console DRM choice: disabling DRM is the conservative proposal, **to confirm** before submission. Its documented wrapper still requires license/privacy review and a delivered-build test regardless of that choice.
- Amazon coexistence: the conservative proposal is the existing package identity with uninstall/reconfiguration when signatures differ, **to confirm** before Amazon submission; simultaneous standard/Amazon installations would need a separate identity decision.
