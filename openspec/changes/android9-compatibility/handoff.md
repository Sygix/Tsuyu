# Implementation handoff: Android 9 compatibility

## Entry point

Change: `android9-compatibility`.
Planning branch: `docs/spec-android9-stores`.
Base examined: `40669b6731ea3c93c9f8f4a795f574f065f9a634`.

This is a specification-only handoff. No compatibility code or runtime result is delivered by this planning PR. Read proposal, design, both delta specs and tasks before starting `/opsx-apply android9-compatibility` after scope approval.

## Confirmed behavior

- Android 9 / API 28 minimum across all packaged modules; existing compile/target SDK retained.
- Existing Shizuku wire/consent/lifecycle contracts preserved, not a reduced API subset.
- Five selectable languages and system fallback on every supported version.
- Android 9–13 uses normal Back navigation without a named caller-return action; Android 14+ retains authenticated launch identity.
- API 28 emulator evidence and physical Android 14 TV evidence are distinct. No physical Android 9 TV has been validated by this work.

## Implementation sequence

1. Record the reference-device baseline and fixture availability before edits.
2. Lower module minimums and complete dependency/API checks.
3. Adapt provider signatures and typed parcelables while retaining identity, deadlines and cleanup.
4. Implement cross-version language lifecycle and the guarded caller-return path.
5. Verify baseline client/process/UserService, persistence and signed-update behavior.
6. Run integrated checks and real Android 14 regression, record gaps, then request review/merge.

The checked provider signature reference is in design section 2. Do not defer that discovery into a generic exploration task. Additional hidden/vendor discrepancies must be recorded as observed failures with a proposed correction.

## Verification record to deliver

For each case record the source head/artifact, OS fixture, operation, expected/actual result and sanitized evidence. At minimum cover:

| Area | Evidence |
| --- | --- |
| Build/API | Four module minimums, transitive compatibility, lint result |
| Providers | API 28, 29, 30 and 31+ branch results; missing fixture explicitly noted |
| Languages | Cold start, five choices, system fallback, failure, recreation, reboot and API 33 migration |
| Caller return | No named action on API 28–33; no identity from extras/referrer; existing API 34 behavior |
| Client | Binder callback, shell uid, grant/deny, process/UserService, revoke and later client start |
| Lifecycle | Manager absent at boot, key reuse, debugging off/on, signed replacement and new binder |
| Physical regression | TCL Google TV running Android 14, including SygixOs where supported |

Use `./gradlew test`, the applicable lint tasks and `./gradlew assembleRelease` for this pre-flavor change; reuse passing CI for the exact tested head where it covers the checks. Run the OpenSpec verify workflow and strict validation before proposing the implementation PR as complete. Do not claim the Android 9 physical-device layer from emulator results.

## Order and stop conditions

Implement/merge/validate/archive this change before `multi-store-distribution`. Current specs are updated by archive, not direct edits. If API 28 cannot satisfy the preserved service contract, stop for an explicit scope decision rather than silently changing the minimum or weakening access checks.

Store signing/account preparation is not a prerequisite for this change. Continue in `../multi-store-distribution/handoff.md` after its dependency is satisfied.
