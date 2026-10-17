# Tasks

All boxes are implementation work, not work completed by the specification PR. No new platform behavior is assumed: this is presentation over the existing P1–P3 adapters. Device rendering remains an explicit acceptance check.

## 1. Shared Material 3 TV foundation

- [ ] 1.1 Introduce the shared dark theme, semantic color roles, typography and spacing from design.md, replacing per-screen theme literals; verify home, access and persistence use the same definitions and remain readable at the reference viewport.
- [ ] 1.2 Extract action, header, status, supporting-panel and progress components with stable testTags; verify Compose tests distinguish focused/resting/unavailable actions and no unavailable action can receive focus or activate.
- [ ] 1.3 Update DESIGN.md to name Material 3 as the visual reference and retain its remote/accessibility contract; verify its palette, text minimum and motion guidance agree with the implementation and this change. Check AGPL headers on new files and update NOTICE only if a new code origin is actually introduced.

## 2. State-screen layout and feedback

- [ ] 2.1 Migrate StateScreen/HomeScreen and PersistenceScreen to the left-information/right-actions shell with safe insets and room for focused scale; verify measured bounds and non-overlap at `@Config(qualifiers = "w960dp-h540dp-xhdpi")` for H, S, P, E, D and R board families.
- [ ] 2.2 Expose operation progress and supporting/status text from existing presentation state without duplicating domain state; extend JUnit presentation tests for checking, startup, unknown setup, failed boot, unsupported vendor access, optional-policy loading/error and compatibility failure while the service remains live.
- [ ] 2.3 Split French body/supporting strings, retain required warnings and label technical mode/policy details; update help to describe prepared automatic restart and manual recovery accurately. Verify resource assertions and screenshots preserve the network/debugging/global-policy explanations and do not promise an unverified reboot.
- [ ] 2.4 Extend NavigationTest and PersistenceNavigationTest for both edges, center activation, Back, settings return, initiating-control restoration, disappearing/disabled controls and keep-policy initial focus; verify all pass at the reference TV qualifiers without changing the existing operations or deadlines.

## 3. Permission and authorized-app compositions

- [ ] 3.1 Migrate permission requests to the centered surface with real verified names/icons and horizontal decision actions; verify AccessNavigationTest covers refusal initial focus, directional edges, Back denial and request expiry with no late approval.
- [ ] 3.2 Migrate authorized apps to identity/revoke row surfaces with all shared-group members visible, textual empty/loading/error states and pending-cleanup actions; extend AccessViewModelTest/AccessNavigationTest for next/previous/return focus fallback and incomplete revoke retry, including per-client delivery failures.
- [ ] 3.3 Verify long names, multiple group members and enlarged text using Compose bounds/scrolling checks: identities and effects remain reachable, text never drops below the specified minimum and focus stays in the visible safe area. Keep stable row keys and testTags; document the shared-group and immediate-removal behavior in README alongside the new layout.

## 4. Integration acceptance

- [ ] 4.1 Run `openspec validate --all --strict`, the change verification workflow and `./gradlew test`; record actual results, confirm permission/start/persistence logic regressions remain covered and verify the diff introduces no server/AIDL/settings mutation or data migration.
- [ ] 4.2 Build with `./gradlew assembleRelease` and validate on a TCL Google TV running Android 14: home, guided setup, system-prompt return, consent/refusal, app/group revocation, persistence/expiry recovery, failed boot and current state after reboot. Compare captures to each Penpot board family and record legibility at 3 m, overscan, focus restoration and reduced motion; publish sanitized evidence and remaining gaps only.
- [ ] 4.3 With the upstream Shizuku-API demo on the TV, verify `requestPermission` callbacks for allow/refuse, `checkSelfPermission()` after revoke/reattachment and preserved decisions after restart. Confirm the changed presentation leaves the P2 API outcomes intact and no UI success is claimed on a timed-out/expired request.

## Workflow follow-up

- Obtain approval of this specification before implementation.
- After implementation merge and real-TV validation, archive P2 then P3 then `material3-tv-alignment` with `openspec archive`; re-run `openspec validate --all --strict` and verify the resulting current specs retain all pre-existing scenarios.
