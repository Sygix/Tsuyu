# Tasks

## 1. Validation boundary and preparation

- [ ] 1.1 Record Sygix's pending real-TV checks for AUTO_START read-back, boot receiver delivery from an absent app process, worker scheduling and delayed daemon startup; verify the PR distinguishes these from emulator-supported behavior. TV execution is deferred to Sygix by the explicit task authorization.
- [ ] 1.2 Implement manager-only typed preparation/status adapters and read-back verification; verify JUnit fakes cover supported allow/ignore, denial, unknown operation, query failure, timeout and spoofed manager calls.
- [ ] 1.3 Add the effect-oriented French disclosure before preparation and read-only status afterwards; verify Compose tests prove Back before enablement causes no mutation and status viewing cannot change settings.
- [ ] 1.4 Update README with persistence prerequisites, actual access effects and recovery limitations; verify its claims match the implemented setting/grant set and the proposal's still-open questions.

## 2. Boot orchestration

- [ ] 2.1 Add boot permission/receiver and unique bounded work using the existing startup coordinator; verify receiver/worker tests cover disabled setup, normal boot, repeated events, concurrent manual start and app-process death.
- [ ] 2.2 Implement persistent attempt/deadline accounting and typed last outcome; verify virtual-time JUnit tests cover delayed endpoint, finite exhaustion, permanent auth/setup failure, late completion and successful retry without duplicate servers.
- [ ] 2.3 Verify keys and P2 decisions remain unchanged across boot startup; verify persisted-key/consent tests and manager handshake reject stale binder/request state.
- [ ] 2.4 Add required headers and any actual new provenance to NOTICE/design; verify the diff has no unapproved setting mutations, upstream permission declarations, secrets or local environment details.

## 3. Readiness and navigation

- [ ] 3.1 Integrate persistence results into immutable home state and recovery/details screens with French resources/testTags; verify running with unverified persistence, ready, missing access and boot-failure states independently.
- [ ] 3.2 Add Compose D-pad tests at `@Config(qualifiers = "w960dp-h540dp-xhdpi")`; verify initial recovery focus, edges without wrapping, Back/return restoration and disappearing-action fallback.

## 4. Integration and handoff

- [ ] 4.1 Run release-build boot E2E on the Android TV emulator, running `adb tcpip 5555` after every boot as the explicit emulator fixture; verify Tsuyu is not manually opened, the receiver schedules work, one shell server returns and the demo obtains its new binder, retained permission and shell-uid command result.
- [ ] 4.2 Exercise delayed endpoint, authorization loss, unsupported vendor operation, process-dead boot delivery, manual/boot overlap and exhausted retries; verify recovery evidence separately from vendor AUTO_START, deliver screenshots and stop all started processes.
- [ ] 4.3 Run `./gradlew test`, `./gradlew assembleRelease`, `openspec validate --all --strict` and openspec-verify-change; verify P1/P2 regression evidence and a complete persistence scenario-to-test map.
- [ ] 4.4 Open a signed P3 PR based on P2 with independent review, verification evidence, real-TV checklist and both Open questions including long-offline key-expiry consequences; verify its base branch and URL.

## Workflow follow-up

- Sygix validates `./gradlew assembleRelease` on the TCL Google TV running Android 14: first enablement/disclosure, auto-start read-back, reboot without opening Tsuyu, shell uid/binder return, retained consent, repeated reboot, removed access and key-confirmation recovery.
- Archive P3 only after P1/P2 archival, Sygix's merge and real-TV validation. Run `openspec validate --all --strict` after archive.
- The agent performs no merge and creates no tag. Product decisions in Open questions require propagation to all affected artifacts and the PR when confirmed.
