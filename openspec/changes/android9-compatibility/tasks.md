# Tasks

The owner deferred the pre-edit physical-TV baseline on 2026-11-11 and authorized implementation and emulator verification to proceed. Task 1.1 and the later physical-TV validation remain unchecked until actually performed.

## 1. Establish evidence before compatibility edits

- [ ] 1.1 Capture the current local-start, client and reboot baseline on the TCL Google TV running Android 14 before code changes; deliver a sanitized result identifying the tested release and observed states, without resetting an existing setup.
- [x] 1.2 Record the available API 28–34 runtime fixtures and their ADB provisioning, checking the provider signatures against the selected platform/reference APIs; deliver a matrix distinguishing available fixtures, missing images and unverified physical firmware assumptions.
- [x] 1.3 Verify the resolved dependency minimums and version-sensitive call sites, including MainActivity, language, provider acquisition/call, parcelables, observers and user-service construction; deliver a file/API/action inventory with no unexplained baseline blocker.

## 2. Lower the packaged baseline

- [x] 2.1 Set minSdk 28 consistently in app, server, api and ipc while retaining compile/target levels; verify merged manifests and dependency resolution without manifest minimum overrides.
- [x] 2.2 Run the applicable app/library Android lint checks and classify every guarded or incompatible API; verify no unexplained NewApi/InlinedApi issue remains and preserve the inventory with the compatibility verification evidence.
- [x] 2.3 Update implementation-time minimum references in README, AGENTS.md and OpenSpec context to match the implemented baseline; verify that Android 9 install eligibility is not presented as physical-TV validation and that current specs still change only through archive.

## 3. Adapt authenticated server boundaries

- [x] 3.1 Implement the API-selected provider acquisition/call boundary with the existing throwing compatibility helpers and guaranteed release; verify selection, missing-provider, invocation-failure and release behavior with focused tests.
- [x] 3.2 Guard typed parcelable access and preserve UserService component ownership/type validation; verify valid requests and wrong-type, spoofed-package and malformed-component rejection tests.
- [x] 3.3 Adapt any remaining inventoried observer, package-watch, user-service construction or process API incompatibility; verify no weakening of caller identity, consent signatures, cleanup or timeout semantics in the corresponding tests.
- [x] 3.4 Run provider/binder smoke checks on API 28, 29, 30 and 31 fixtures; record each signature path's observed result, failure isolation and any unavailable fixture without calling it verified.
- [x] 3.5 Exercise the full baseline-compatible Shizuku demo contract on API 28: attach, grant/deny, remote process, UserService, revoke and client-process restart; verify actual callbacks, shell identity and effective revocation in a sanitized report.

## 4. Preserve languages and caller navigation

- [x] 4.1 Add the legacy locale store and API-specific gateway with bounded load/save outcomes and one authoritative state; verify system fallback, five explicit languages, persistence failure and restart behavior with focused tests.
- [x] 4.2 Apply the language boundary to every activity entry and resource context before interactive content, preserving recreation/focus; verify home, language, permission, persistence and update screens on API 28 and API 33+.
- [x] 4.3 Add one-time legacy-to-platform language migration without overriding later system choices; verify explicit existing platform choice, initial migration, repeated startup and subsequent return-to-system cases.
- [x] 4.4 Guard caller identity at API 34 and omit named return on API 28–33; verify that forged extras/referrers cannot create a label, Back follows the task stack and API 34 keeps its existing shared-identity return behavior.
- [x] 4.5 Add meaningful Compose D-pad tests using the repository's TV qualifiers and testTags for selection, loading/error recovery, Back and focus restoration; verify all new user-visible strings in English, French, German, Spanish and Italian and document the older-platform return behavior.

## 5. Verify lifecycle and signed updates

- [x] 5.1 Verify unsupported vendor AUTO_START and absent expiry-setting outcomes on the baseline fixture; add regression coverage if an adaptation is needed and confirm they remain distinct from verified readiness/disabled expiry.
- [x] 5.2 Exercise API 28 signed installation, signature/version rejection and installer timeout/reconciliation with increasing test versions; verify private key, language and matching client decisions survive a successful replacement.
- [ ] 5.3 Test reboot with the manager process absent, delayed/unavailable ADB, refused renewed authorization and debugging disabled/re-enabled; verify the existing bounded restart/recovery contracts and record the fixture-specific results.
- [ ] 5.4 Build the release variant and run the full Android 14 physical-TV regression with the demo and SygixOs where supported, including caller return and signed upgrade/reboot; deliver observed results, not inferred emulator equivalence.

## 6. Integrated acceptance

- [x] 6.1 Run the repository-required unit suite and relevant lint/build checks, including `./gradlew test` and `./gradlew assembleRelease`; reuse passing exact-head CI where applicable and record all remaining runtime gaps explicitly.
- [x] 6.2 Verify license headers and source provenance/NOTICE for any adapted reference code, review the public diff for environment information, run OpenSpec verification and `openspec validate --all --strict`; accept only checked requirements and report deviations before implementation approval.

## Workflow follow-up

- Obtain explicit merge authorization after review and validation; do not infer it from specification approval.
- After merge and the required real-TV validation, archive `android9-compatibility` and validate the resulting specs.
- Continue with `multi-store-distribution` only after this prerequisite is implemented and archived; preserve unverified physical Android 9 coverage as a documented limitation.
