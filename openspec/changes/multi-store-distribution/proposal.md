# Proposal

## Why

Tsuyu needs store-managed distribution alongside its existing GitHub updater. A build that cannot update itself, reproducible developer-signed APKs and explicit device-validation evidence make publication and cross-channel upgrades supportable without confusing installation eligibility with tested TV compatibility.

## What Changes

- Introduce `store` and `standalone` distribution variants; exclude the internal updater and its privileged installer from store builds.
- Publish the existing `tsuyu.apk` standalone asset, a `tsuyu-store.apk` and a Play `tsuyu-store.aab`, all carrying the version of one committed version file.
- Replace the tag-supplied version variable with a committed version file (versionName and versionCode) read by the build. A bump commit precedes each tag, the release job refuses a tag that does not match the file, and F-Droid detects new versions from it.
- Make release builds without signing inputs truly unsigned instead of debug-signed. Sign the two APKs with the existing release key and the Play bundle with a separate upload key; publication fails when either identity is missing or swapped.
- Preserve app data across compatible-signature upgrades and prevent a previously scheduled standalone updater from running after migration to store. Store-side cleanup is silent, retried at every process start and logged on failure.
- Reset automatic updating to off when a standalone build finds it was replaced outside its own updater, which covers a return from a store build and a manual installation.
- Accept that Google Play may replace an installation made from the GitHub APK with its store build at a later version; document it in the README, with no in-app notice.
- Prepare Google Play and F-Droid publication materials and runbooks; use reproducible developer-signed delivery for F-Droid and configure Play signing for compatible upgrades.
- Add a community test protocol, structured reports, a README compatibility matrix backed by reviewed, release-specific evidence and a README « Availability » table giving each channel's status.

## Capabilities

### New Capabilities

- `release-distribution`: build variants, artifacts, version source, signing, migration, store-delivery readiness and release publication contracts.
- `device-validation`: repeatable compatibility reports, evidence review and public support-status maintenance.

### Modified Capabilities

- `self-update`: scope the existing updater requirements to standalone builds and remove their UI/work/installation capability from store builds through the distribution contract.

The existing `tv-navigation`, `local-start`, `boot-persistence`, `client-access` and `shizuku-api` contracts are preserved and referenced, not duplicated. Store builds keep every persistence control, including those added by `consent-and-persistence-controls`.

## Impact

App/server source sets, composition root, home navigation, manager IPC implementation, updater scheduling/storage, manifest, Gradle packaging and version source, release/test CI and its production secrets. Public metadata, privacy documentation, community issue template, README and store runbooks are added during implementation. This change adds no user-visible string. Actual account operations and store approvals are separate publication steps.

## Dependencies and order

Depends on `android9-compatibility`, which must be implemented and archived first. This change does not modify its baseline or caller-return requirements. It refers to the persistence controls of the open `consent-and-persistence-controls` change without modifying its requirements. Archive order: `consent-and-persistence-controls`, then `android9-compatibility`, then `multi-store-distribution`. The proposals can be reviewed together; their implementations remain ordered.

## Non-goals

Automatic submission to store consoles, a new privileged-start mechanism, data export between incompatible signatures and guarantees of store approval are outside this change. Future store automation or additional device-start methods require separate scope. Current same-signature restart and Shizuku consent semantics remain owned by their existing capabilities.

Amazon Appstore publication is postponed: it is no part of this change, and `openspec/backlog.md` records the evidence and what would reopen it. Returning package-visibility data through the shell server, the planned fallback if Play refuses QUERY_ALL_PACKAGES, is described in the design and is not implemented here.

## Open questions

- Publication inputs: Play account eligibility and any existing signing enrollment, public privacy-page location and listing contact details. These block the corresponding publication step, not variant implementation.
