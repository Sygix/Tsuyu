# Tasks

## 1. Validation boundary and API contracts

- [ ] 1.1 Record pending Sygix real-TV checks for provider/observer signatures, background process starts, permission-screen launch and demo native UserService loading; verify the PR separates these assumptions from emulator evidence. TV execution is deferred to Sygix by the explicit task authorization.
- [ ] 1.2 Add all four upstream AIDL interfaces, BinderContainer/constants and API module wiring; verify generated descriptors/transaction codes against the pinned upstream contract and `./gradlew assembleRelease`.
- [ ] 1.3 Retain upstream MIT/Apache-2.0 copyrights and provenance, add license texts and NOTICE origins; verify all source headers and dependency licenses and exclude rish/Sui/root layers.

## 2. Binder service and delivery

- [ ] 2.1 Implement authenticated attachment, metadata and permission checks; verify JUnit coverage for spoofed packages, unattached/denied callers, uid reuse, death and grant lookup failure.
- [ ] 2.2 Implement startup enumeration and process/UID-driven provider delivery with finite retries; verify requesting-but-not-granted clients receive a binder, unrelated manifests do not and one blocked provider does not block others.
- [ ] 2.3 Implement every API-matrix endpoint and remote transaction forwarding; verify allowed and denied cases, malformed parcels, flags, actual shell identity and failure propagation with contract tests.
- [ ] 2.4 Update README with upstream API version, binder delivery and package-presence-check limitation; verify wording matches observed client results rather than claiming root capabilities.
- [ ] 2.5 Preserve authenticated non-Sui `dispatchPackageChanged` no-op and `isHidden=false`; verify contract tests cover legitimate calls, unauthorized administrative dispatch and unchanged package/access state.

## 3. Consent and revocation

- [ ] 3.1 Implement single-authority persistent access state and pending-request validation; verify JUnit cases for authorize/refuse, rationale, Back, replay, concurrent requests, timeout, signer changes and decision persistence.
- [ ] 3.2 Implement manager flag/result methods and immediate revocation with child/client cleanup; verify forged manager calls fail, stolen binders cannot administer access and an already-bound service stops after revoke.
- [ ] 3.3 Implement request and authorized-app ViewModels/screens with French strings, icons and testTags; verify empty/loading/error/expired/partial-cleanup states and update README with consent and revocation behavior.
- [ ] 3.4 Add Compose tests at `@Config(qualifiers = "w960dp-h540dp-xhdpi")`; verify refusal initial focus, D-pad edges, center activation, Back denial/caller return, list entry restoration and focused-row disappearance.
- [ ] 3.5 Implement shared-UID group identity, disclosure and consent/revocation; verify JUnit cases for group-wide callbacks/access, child cleanup, unknown identity, lookup timeout and invalidation on membership/signature change, and Compose assertions for visible related-app names and group-wide wording.

## 4. Processes and UserServices

- [ ] 4.1 Implement owned IRemoteProcess descriptors, pumping, waits, exit and destruction; verify stdout/stderr/stdin, finite timeout, owner death/revoke and identity checks, including `id -u` output on the emulator.
- [ ] 4.2 Implement owned-component child startup and token attachment with Context/no-arg and native-library loading; verify hostile inputs/token replay cannot attach or execute another client's code.
- [ ] 4.3 Implement tag/version/daemon/no-create/ABI/debug options, unbind/remove and failure cleanup; verify lifecycle tests cover absent/existing peek, version replacement, dead callbacks, launch timeout, revoke and server death.

## 5. Integration and handoff

- [ ] 5.1 Build/install the pinned upstream demo outside Tsuyu; on the TV emulator with simulated legacy TCP debugging after each boot, show binder received, refusal then explicit permission grant, actual shell-uid command output and native UserService bind/peek/unbind/remove. Deliver screenshots and sanitized assertions, then stop all started processes.
- [ ] 5.2 Validate startup/later foreground and background delivery, revoke while a service is bound and service restart with persisted decisions; verify actual upstream clients, including SygixOs when available, and list any unexecuted client scenario explicitly.
- [ ] 5.3 Run `./gradlew test`, `./gradlew assembleRelease`, `openspec validate --all --strict` and openspec-verify-change; verify a method/scenario-to-test report has no unacknowledged API gap.
- [ ] 5.4 Open a signed P2 PR based on P1 with independent review, privacy/license scans, test evidence, confirmed non-Sui/shared-UID decisions and pending TV/demo/SygixOs checklist; verify base branch and URL.

## Workflow follow-up

- Sygix validates the release build on the TCL Google TV running Android 14: requesting client delivery before/after server startup, D-pad request/return, demo shell command and UserService/native library, SygixOs, refusal/revocation and decisions after reboot.
- Merge/archive P1 before P2; archive P2 only after Sygix merges and validates it on the TV. Run strict validation after archiving.
- P3 stacks on this phase. The agent creates no tag and performs no merge.
