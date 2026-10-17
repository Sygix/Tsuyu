# Proposal

## Why

The existing P1–P3 interface implements the application journeys but mixes duplicated theme values with vertically stacked content and actions. The Tsuyu Penpot mockup provides a consistent Material 3 TV presentation; this change specifies the observable differences needed to adopt it at equal functionality.

## What Changes

- Centralize the existing dark cyan/navy Material 3 TV styling into shared foundations and components, including consistent focused, resting, disabled and attention states.
- Replace the bottom-aligned action stack on state screens with the mockup's information-left/actions-right composition.
- Present permission requests in a centered surface and authorized applications in grouped identity/action rows.
- Add operation-bound progress and clearly separated textual service/persistence statuses, using existing state rather than new privileged operations.
- Split dense explanatory copy into readable supporting panels while retaining required warnings and truthful recovery actions.
- Document mockup-to-code differences and implementation acceptance by board family, including corrections where static artwork conflicts with established behavior or minimum text size.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `tv-navigation`: specify the Material 3 visual foundation, responsive TV compositions and component-state presentation across existing journeys. Retain the existing remote focus contract.

## Dependencies

- Builds on archived `p1-local-start` and the implementations of `p2-shizuku-compatibility` and `p3-boot-persistence` already on main.
- P2 and P3 remain unarchived. Their `client-access`, `shizuku-api` and `boot-persistence` requirements continue to own permission, revocation, restart and global-expiry semantics; this change references them without modifying their deltas.
- Archive in the order P2 → P3 → this change after each change's implementation and real-TV validation. This change only writes a delta for the existing `tv-navigation` capability.

## Non-goals

- New application functions, navigation destinations, settings, permissions, startup algorithms or Shizuku API changes: outside this visual-alignment change; any future proposal requires separate approval.
- Release signing, tags and distribution: remain P4.
- Reproducing system-owned settings or debugging dialogs: remain Android-owned surfaces.
- Turning Penpot into an executable remote-navigation prototype: outside this implementation scope; navigation is verified in Compose and on the TV.

## Impact

Affected code: `HomeScreen.kt` / `StateScreen`, `AccessScreen.kt`, `PersistenceScreen.kt`, their presentation models only where needed for existing state, French resources and UI navigation tests. Introduce a shared UI theme/component layer and update `DESIGN.md` and README to describe the approved Material 3 direction. Keep the recently added Tsuyu brand assets. No server, AIDL, storage migration or new dependency is required.

Reference: [Tsuyu · Material 3 TV](https://design.penpot.app/#/workspace?team-id=52a5041f-08a1-80f1-8008-c3c379c88195&file-id=cd444aaf-0d8c-801b-8008-ce19b8b9f465&page-id=cd444aaf-0d8c-801b-8008-ce19b8b9f466). Board families and reconciliation rules are recorded in `design.md`; the prose requirements take precedence over illustrative static states.
