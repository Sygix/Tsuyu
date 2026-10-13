# Tasks

## 1. Validation boundary and module foundation

- [x] 1.1 Record Sygix's pending real-TV checks for settings/property visibility, APK classpath launch and authenticated provider callbacks; distinguish verified AGENTS.md facts from assumptions in the PR. The agent does not run the real-TV checks under this task's explicit authorization.
- [x] 1.2 Add `server` and `ipc` modules, entry-point keep rules and version-catalog dependencies; verify `./gradlew assembleRelease` packages the shell entry point and builds AIDL.

## 2. Local authentication

- [x] 2.1 Implement bounded ADB framing and streams with JUnit coverage for fragmentation, negotiated checksums, invalid magic/length, EOF, mismatched ids, TLS rejection and deadlines; verify the protocol tests fail when validation is removed.
- [x] 2.2 Implement private stable RSA key storage, Android public-key encoding and prehashed token signatures; verify fixed-format/signature vectors, restart reuse and explicit corrupt-storage errors with JUnit.
- [x] 2.3 Implement endpoint discovery and authentication behind interfaces; verify JUnit fakes cover accepted key, unknown key, refusal/disconnect, timeout and missing endpoint without LAN discovery.
- [x] 2.4 Add required Apache-2.0 provenance headers, retained copyrights, license texts and NOTICE; verify every adapted source matches its origin and no secrets or payload logging are introduced.

## 3. Launch and state

- [x] 3.1 Implement validated APK launch and shell-only provider handoff with manager identity enforcement; verify wrong uid/token rejection, shell uid reporting and release-build launch on the emulator.
- [x] 3.2 Implement singleton start coordination, liveness and lifecycle StateFlow; verify JUnit tests cover concurrent requests, late completion, cancellation, server death and reboot reconciliation.
- [x] 3.3 Implement typed settings and operation errors with bounded workers and resource release; verify success, denied/missing APIs and blocked-operation timeout using deterministic fakes.
- [ ] 3.4 Implement session detachment, stream redirection and package replacement/removal exit; verify emulator binder survival after ADB close, old binder death on update/uninstall including a process-preserving replacement, and same-signature private-key survival. Add lifecycle failure tests and release diagnostics; Sygix validates debugging-off termination and recovery with the stored key on the TV. Process-preserving replacement remains unverified.

## 4. TV setup and home

- [x] 4.1 Implement ViewModel-driven home, guided setup and help with shared TV components, French resources and testTags; verify every state has a visible next action and update README with manual-start behavior and v1 limitations.
- [x] 4.2 Add Compose navigation tests at `@Config(qualifiers = "w960dp-h540dp-xhdpi")`; verify initial focus, all D-pad directions, no edge wrapping, Back, settings return, focus restoration and disappearing-control fallback.

## 5. Integration and handoff

- [x] 5.1 Run emulator E2E using a release APK, enabling legacy TCP debugging after each emulator boot to simulate the reference TV; verify first authorization, repeat start, actual shell uid, error recovery and manual restart after reboot. Deliver screenshots to Sygix and stop the emulator and all started processes.
- [x] 5.2 Run `./gradlew test`, `./gradlew assembleRelease`, `openspec validate --all --strict` and openspec-verify-change; verify a scenario-to-test/evidence report separates pending TV checks from completed automated checks.
- [ ] 5.3 Open the P1 PR against `docs/v1-specs` while the specification PR is pending, then rebase and retarget it to main after that PR merges; include signed commits, license/privacy scans, verification evidence, pending real-TV checklist and Open questions, and obtain independent review before proposing a merge.

## Workflow follow-up

- Sygix validates `./gradlew assembleRelease` on the TCL Google TV running Android 14: settings navigation, discovery, “Always allow”, shell server startup, binder survival after launch-stream close, debugging-off termination and recovery, server exit on same-signature update/uninstall, private-key survival across update, UI at couch distance and key reuse after reboot.
- Stack P2 on P1, then P3 on P2, one implementation PR per change. Sygix alone merges; no tag is created by the agent.
- After merge and Sygix's real-TV validation, archive `p1-local-start`, then validate the archived specs. Archive in P1 → P2 → P3 order.

## 6. P1 launch correction

- [x] 6.1 Obtain real-TV diagnosis distinguishing authentication, pre-VM launch failure, server death, Binder loss and UI state. Sygix supplied retained-key authentication logs, six failed immediate-return trials, cgroup membership, init SIGKILL and denied migration observations; approve the debugging-enabled contract before implementation.
- [x] 6.2 Gate shell completion on deterministic server readiness with bounded failure reporting; add regression tests that fail for immediate shell return and launch timeouts misreported as authorization problems.
- [ ] 6.3 Verify repeat launch retains the private key and accepted authorization across debugging changes and manual reboot recovery; separate fake/unit evidence from actual TV evidence.
- [x] 6.4 Run `./gradlew test`, `./gradlew assembleRelease`, `openspec validate --all --strict` and openspec-verify-change for the corrected head, and reconcile the report without checking unrelated pending tasks.
- [ ] 6.5 Validate the corrected release on the real TV: repeat starts without revocation, Binder/process survival after launch-ADB close, debugging-off termination, re-enabled recovery, and manual restart after reboot with USB debugging retained. Record the tested build identity and actual errors/recovery.
- [ ] 6.6 Prepare signed correction commits, independent exact-head review and a correction PR. Archive only after explicit merge authorization, merge and real-TV validation, then validate resulting specs.
