# P1 verification

## Correction verification (current)

Using change: `p1-local-start`, schema `spec-driven`. This section supersedes
the original assessment below; historical emulator checks are not TV proof.

| Dimension | Result |
| --- | --- |
| Completeness | 18/19 active tracked tasks complete; 3.4 withdrawn and 5.3 superseded by Sygix without marking them complete; only the archive/validation follow-up remains |
| Correctness | Logic, host-shell readiness, release compilation and the three corrected-release TV recovery scenarios passed; package-lifecycle and some UI/runtime coverage remain partial |
| Coherence | Approved debugging-enabled contract propagated through proposal, design, delta, tasks, README and French resources; P3 remains unimplemented |

### Real-TV diagnosis supplied by Sygix

The tested device was a TCL Google TV running Android 14, with release
`0.1.0-alpha.1`. These observations were collected outside the agent VM and
reported by Sygix; they establish the old build's failure causes, not success
of the correction.

- Previously accepted Tsuyu authentication completed immediately, with no new
  prompt or authorization revocation. The launch log was recreated but empty;
  no Tsuyu VM/process or server log appeared. Manager publication timed out
  after 30 seconds.
- An isolated detached command survived zero of six immediate-return legacy
  launches, three of three delayed-return launches and three of three shell-v2
  launches. The corrected command waits for an actual readiness marker;
  delayed return is not treated as a solution.
- Disabling debugging caused init to SIGKILL adbd's entire process cgroup.
  Detached shell children retained that cgroup; shell writes to migration
  files were denied. This establishes process death, rather than merely a lost
  Binder or an incorrect UI. Sygix explicitly approved keeping debugging
  enabled during use.
- USB debugging and both existing authorizations were left enabled. Test
  processes and temporary diagnostic files were cleaned up by the external
  tester. No destructive TV operation was performed by this agent.

### Commands and regression evidence for the correction

- `./gradlew test assembleRelease --no-daemon`, under the VM heavy-work lock:
  passed, 41 app tests and 3 server tests, zero failures/errors/skips.
- `openspec validate --all --strict`: 3 passed, 0 failed.
- `LaunchCommandTest.launchShellWaitsForServerReadinessBeforeCompleting` runs
  the generated shell with a FIFO-gated host fixture. It asserts that the
  shell cannot finish before readiness and that the detached child outlives
  shell completion. It failed against the old immediate-return command and
  passed with the correction. This is host-process evidence, not TV evidence.
- `LocalAdbLauncherTest.authenticatedLaunchDeadlineReportsLaunchFailureAndKeepsTheKeyForRetry`
  failed against the original generic timeout mapping and passed with the
  correction. Retry uses the identical persisted public key and closes both
  connections; authentication is simulated, not vendor-tested.
- `LocalAdbLauncherTest.shellFailureAndSocketDeadlineAfterAuthenticationAreLaunchErrors`
  covers explicit launch failure/deadline markers, socket timeout and rejection
  of a malformed success marker. Existing auth, cancellation, Binder death,
  reboot reconciliation, private-key corruption and TV-size navigation tests
  passed.
- Independent review found cross-attempt contamination through the shared
  diagnostic log. Handled by matching only the current launch generation,
  without writing the persistent authentication token or key. Regression
  `LaunchCommandTest.previousAttemptMarkersCannotCompleteOrFailTheCurrentLaunch`
  failed before this correction and passed afterward: old failure/readiness
  markers cannot fail or complete the new attempt.
- The locally built minified release APK is 1,435,653 bytes, SHA-256
  `7d9c9e792a733e0b5ecb306c9cd21805845b958f9e66b47278331bf0a8d15cfa`.
  It uses the local development signing fallback. It must not replace the
  installed production-signed APK with a different certificate; real-TV
  validation needs a same-signature build with an acceptable version code.

### Correction emulator evidence (first candidate)

The runtime tester exercised commit
`8d8973120e4b076a7b3aeb64b29fb94665203a89` with the previous unscoped-marker
APK (SHA-256 `1b309dc8495551bf6517341ba6ede98b774fbfcbb678f59eb225f33640770113`).
It reported initial Always-allow authorization, shell-uid process and ready
markers, Running after launch ADB closure, settings-return survival, and
server-only termination followed by manual restart without a new prompt.
The running/details screenshots confirm the new debugging-enabled text and
were delivered directly to Sygix. The parent agent restored the emulator TCP
fixture and stopped the emulator after the tester returned. Manual reboot
and the launch-error screen were not exercised by that runtime pass. These
results neither validate the later scoped-marker build nor vendor behavior.

### Scoped-marker runtime and independent review

The runtime tester subsequently checked code commit
`4ac96b1dd1e1d4c80d5af8a78963062e1eda87d6` with the current APK checksum above,
updating the emulator in place while preserving app data and the accepted key.
The scoped readiness marker matched the actual launch generation, the process
reported shell uid, home showed Running, and the only remaining debugging
socket was the daemon listener, not an established launch connection. The
final Running screenshot was delivered directly. The TCP fixture was restored
and the emulator stopped; the parent independently confirmed no connected
device and a stopped emulator. This focused pass did not exercise reboot,
production signing or real late-attempt contamination; the latter is covered
by the host regression, not claimed as Android runtime evidence.

Independent read-only review approved code commit
`4ac96b1dd1e1d4c80d5af8a78963062e1eda87d6` after the marker-isolation fix, with
no remaining code findings. The reviewer independently reran the two host
launch regressions and strict OpenSpec validation. Code approval is distinct
from corrected-release TV validation and merge authorization.

Sygix chose delivery through the existing signed CI release workflow rather
than a locally signed production build. After explicit authorization, PR #6
was merged into main as `2b316700e2c7c4e8007612480b94f7c6dd30f42d` and signed
tag `v0.1.0-alpha.2` was published. Main CI and the signed-release workflow
passed. The corrected release's TV results are recorded below. Archive is
authorized after PR #7 records Sygix's closure decisions and merges with green CI.

### Published release validation on the real TV

Sygix supplied these results, collected by an external tester on a TCL Google
TV running Android 14. They are real-device evidence reported by Sygix, not
observations collected from the agent VM.

- Release: `v0.1.0-alpha.2`, commit
  `2b316700e2c7c4e8007612480b94f7c6dd30f42d`.
- Published APK SHA-256:
  `764cf89b31e77faa5c1b402768bda5a054bca39965cb72a8c1ae8ea5f647c725`.
  The tester's downloaded checksum matches the published digest, independently
  rechecked against the immutable GitHub release metadata.
- Installed in place with `adb install -r`: versionCode 100001 to 100002,
  without uninstall, data clearing or authorization revocation. App data and
  the private key were retained.

| Scenario | Observed proof | Result |
| --- | --- | --- |
| Start and survive launch-ADB closure | D-pad start at 20:27:11; server ready reporting uid 2000 at 20:27:12.321; launch connection closed at 20:27:12.468. Diagnostic log contained detached and generation-scoped ready markers. The shell-owned server was reparented to init and remained alive 15 seconds after Home/reopening; UI showed Running with the keep-debugging-enabled guidance | Passed |
| Manual restart after reboot with USB debugging retained | After reboot `adb_enabled=1`, server absent and UI Stopped. D-pad start at 20:31:27; accepted-key authorization at 20:31:27.590 without a public-key exchange/new prompt; server ready uid 2000 at 20:31:28.237 and UI Running | Passed |
| Disable/re-enable debugging and recover with retained authorization | Disabling debugging at 20:32:16 caused init to SIGKILL adbd's cgroup and the server disappeared. No authorization clear occurred. Debugging was re-enabled at 20:32:35. D-pad start at 20:33:22 authenticated the accepted key at 20:33:22.937 without a new public key/prompt; server ready uid 2000 at 20:33:23.566 and UI Running | Passed |

The tester left USB debugging enabled, Tsuyu Running and its key authorized;
temporary test files were removed. No device access or manipulation was
performed by this agent while recording these results.

These results satisfy correction tasks 6.3 and 6.5. They do not establish
old-server exit during the in-place update, uninstall cleanup, singleton
handoff, all settings/refusal/error screens, or reduced-motion/couch-distance
presentation on the TV. Sygix accepted these coverage limits for closure and
requires no further real-TV evidence. The process-preserving base-replacement
criterion was withdrawn, not verified.

### Disabled-debugging UI observation

Sygix reported a possible Stopped label while debugging was disabled, but
neither the exact text nor the underlying setting read was captured. Keep
this observation unconfirmed; the three successful recovery scenarios above
are unaffected. Sygix accepted it as non-blocking for closure; it is retained
in openspec/backlog.md for investigation if reproduced.

Code inspection explains two states:

- `StartCoordinator.kt:62` changes Running to Stopped immediately on Binder
  death, keeping the previous debugging reading. This transient death state
  is consistent with the lifecycle scenario requiring stopped/start.
- `MainActivity.kt:57` refreshes on return, and the active settings observer
  also refreshes on debugging changes. `StartCoordinator.kt:30-35` reads
  current liveness/settings: an absent server and disabled debugging produce
  Setup. `HomeViewModel.kt:111-118` then offers debugging setup, or manual
  settings guidance if the read is unknown (`HomeViewModel.kt:107-109`).

Thus a stable Stopped screen after a refresh has read disabled debugging is
not the intended spec/code behavior. There is insufficient evidence to call
this a reproduced defect or to infer a failed settings read. A further TV
check should record the exact text after returning to home, the value of
`adb_enabled` while disabled and whether the setup/settings action is offered.

### Corrected scenario mapping

| Scenario | Implementation / regression | Proof and limit |
| --- | --- | --- |
| Launch connection closes after deterministic readiness | `LaunchCommand.kt:13`, `ServerMain.kt:52`, `ServerMain.kt:117`, `LaunchCommandTest` | Host fixture, scoped-marker emulator check and published alpha.2 real-TV process/UI survival passed |
| Launch deadline after successful authentication | `LocalAdbLauncher.kt:44`, `StartCoordinator.kt:51`, `LocalAdbLauncherTest` | Typed launch error and key-preserving retry passed in tests; corrected recovery sequence passed on TV, but no forced launch-timeout screen was captured on alpha.2 |
| Debugging disabled while running | Binder-death observation, `StartCoordinator.died`, `HomeViewModel`, French running/help/details resources | Alpha.2 process termination and prompt-free recovery passed on TV; exact disabled-state setup presentation remains unconfirmed |
| Key reuse after reactivation and manual reboot | `AdbKeyStore`, `LocalAdbLauncher`, existing `AdbTest` / `StartTest` | Persistence/retry unit tests and published alpha.2 real-TV reactivation/reboot sequence passed |
| Singleton handoff | `ServerMain.kt:43` writes readiness only after successful existing-server handoff | Corrected runtime handoff pending |

### Closure decisions and remaining archive operation

- **3.4 withdrawn, not passed:** Sygix removed this task because replacement
  of the base APK kills the application process. INSTALL_DONT_KILL_APP concerns
  adding splits; the historical `--dont-kill` base-replacement attempt killed
  the manager as well. The criterion was judged neither provable nor useful
  to the product. Remove the corresponding surviving-manager scenario from
  the delta before sync; preserve actual lifecycle evidence without inventing
  a successful test.
- **5.3 superseded, not passed by inference:** PR #4 is merged with signed
  commits and independent review. Sygix accepted the actual delivery and
  retired the unverifiable original stacked/rebase/retarget sequence.
- **6.6 pending archive operation:** signed correction delivery, exact-head
  review, authorized merge and real-TV correction validation are complete.
  Sygix expressly authorized merging PR #7 with the closure decisions, then
  archiving P1, syncing deltas and merging an archive PR after green CI. Mark
  this final operation complete only after archive and strict validation of
  the resulting specs succeed.

**Accepted coverage limits:** runtime package-observation failures, singleton
handoff and some TV settings/refusal/error/navigation/presentation scenarios
remain partial. The exact disabled-debugging setup display is unconfirmed and
tracked in backlog, not a reproduced defect. Sygix requires no further TV
proof and accepted these limits for P1 closure.

**Assessment:** implemented P1 is merged and its three correction scenarios
passed on the published same-signature release. There is no remaining
implementation or TV gate under Sygix's closure decision. The one pending
checkbox records the authorized archive/spec-sync/validation operation itself;
it is not evidence that this operation has already run. P2/P3 remain untouched.

### Procedure for Sygix's TV tester

1. Record the corrected commit/build, APK checksum, signing-certificate match
   and installed version. Update in place; preserve app data and authorizations.
2. With USB debugging enabled and Tsuyu's accepted key intact, start Tsuyu.
   Expect Running without another key prompt. Record readiness markers,
   shell-owned process and live manager Binder. Confirm that launch ADB closes
   while that process and Binder remain live. Repeat recovery trials without
   revoking keys or uninstalling.
3. Disable debugging using the remote, return to Tsuyu and confirm that it
   reflects the actual termination and offers setup. Re-enable USB debugging,
   return and start: expect Running with the same key and no new prompt.
4. Leave USB debugging enabled, reboot the TV, open Tsuyu and start manually.
   Expect Running with the retained key, without a prompt. P1 does not
   automatically restart at boot.
5. If any attempt fails, record the exact screen text, phase, elapsed time,
   marker state (`TSUYU_DETACHED`, generation-scoped `TSUYU_READY` /
   `TSUYU_FAILED`), server presence,
   and sanitized server/startup logs. Retry must recover without revocation.
   Leave USB debugging enabled and stop/remove only test tools/files created
   during verification; do not uninstall Tsuyu or revoke authorizations.

## Original P1 verification (historical)

### Summary

| Dimension | Result |
| --- | --- |
| Completeness | 13/15 tasks complete; all 7 added requirements have implementation evidence |
| Correctness | Automated logic and emulator evidence below; process-preserving replacement and vendor runtime behavior are not fully verified |
| Coherence | Modules, manager identity, stable private key, bounded operations, StateFlow and remote UI follow the accepted design |

Tasks 3.4 and 5.3 remain incomplete: process-preserving replacement runtime
evidence, and PR handoff followed by rebase/retarget after the specification PR
merges. Do not archive before merge and Sygix's real-TV validation.

## Commands and regression evidence

- `./gradlew test`: passed, 37 app tests and 3 server tests, no failures or skips.
- `./gradlew assembleRelease`: passed with minification and packaged server/AIDL.
- `openspec validate --all --strict`: 3 changes passed, 0 failed.
- License/privacy scans: no missing SPDX identifiers; the only IPv4 address in
  added source is loopback. No local environment detail is published.
- Removing checksum validation made
  `AdbTest.invalidMagicLengthChecksumAndEofAreRejected` fail; restoration passed.
- The independent-review race regressions failed without their fixes and
  passed afterward.

Runtime evidence belongs to the initial P1 release build. Release compilation
passed after the first three race fixes. Subsequent settings-reader injection,
error-preservation fixes and ViewModel tests passed `./gradlew test`. The emulator
flow was not repeated, as requested by Sygix.

## Requirement/scenario mapping

App production paths are under `app/src/main/java/fr/sygix/tsuyu/`, app tests
under `app/src/test/java/fr/sygix/tsuyu/`, and server paths under
`server/src/main/java/fr/sygix/tsuyu/server/` with parallel test paths.

| Requirement and scenarios | Implementation | Evidence / limits |
| --- | --- | --- |
| Guided setup: success, refusal, unknown/missing settings, deadline, reboot | `start/AndroidSettings.kt`, `start/StartCoordinator.kt`, `ui/HomeViewModel.kt`, `MainActivity.kt` | `AndroidSettingsTest`, `StartTest`, `HomeViewModelTest`; vendor settings navigation pending |
| Private authentication: accepted/unknown key, refusal/disconnect, unsupported/TLS endpoint, malformed input, timeout, reboot reuse | `adb/AdbPacket.kt`, `adb/AdbSession.kt`, `adb/AdbKey.kt`, `adb/AdbKeyStore.kt`, `start/LocalAdbLauncher.kt` | `AdbTest`, `LocalAdbLauncherTest`, bounded-worker deadline test; emulator Always allow, repeat start and reboot reuse passed |
| Verified shell lifecycle: success, cancellation, unavailable launch, deadlines, concurrent starts/death, reboot restart | `start/ServerBroker.kt`, `start/StartCoordinator.kt`, `start/ServerReadiness.kt`, `ServerMain.kt`, `ServerControl.kt` | Broker/control identity tests, `StartTest`, `ServerReadinessTest`, `HomeViewModelTest`; release runtime shell uid and live manager verified |
| Detached survival: ADB close, launch failure/deadline, reboot | `start/LaunchCommand.kt`, `start/LocalAdbLauncher.kt` | Historical command/close tests and emulator launch-ADB closure; no vendor survival inference. Debugging-off contract is superseded by the correction above |
| Package lifecycle: update, process-preserving update, uninstall, failed update, observation failure, current APK after reboot | `PackageWatch.kt`, `ServerMain.kt`, `start/PackageReplacementReceiver.kt`, `start/ServerShutdown.kt` | `PackageWatchTest`, `ServerShutdownTest`; emulator standard update, current-APK restart/key reuse and uninstall passed; process-preserving update and observation-failure exits are not fully exercised at runtime |
| Remote focus: initial control, all directions/edges, Back/return, settings return, disappearing control, home exit | `ui/HomeScreen.kt`, `ui/HomeViewModel.kt`, `MainActivity.kt` | `NavigationTest` at TV qualifiers; ViewModel Back/settings/cancellation tests; runtime home/help navigation and Back passed; actual home-exit/settings path requires TV confirmation |
| French presentation: stable readable state, recovery, reduced motion | `ui/HomeScreen.kt`, French resources | Strings, minimum body size and safe padding inspected; screenshots delivered; retry recovered from launch timeout; couch-distance/reduced-motion appearance pending on TV |

## Independent-review fixes

| Finding | Resolution | Regression |
| --- | --- | --- |
| Replacement between launch lookup and lifecycle baseline could bless the new APK while executing the old one | Validate the initial package stamp against launched path and manager uid; keep before/after-publication reconciliation | `PackageWatchTest.replacementBetweenLaunchLookupAndBaselineCannotWatchTheNewApk` |
| Cancellation could discard the only refresh behind the startup mutex | Queue refresh rather than silently discard it | `StartTest.queuedRefreshReconcilesServerAfterStartupCancellation`, `HomeViewModelTest.cancelAutomaticallyReconcilesLateLiveServerWithoutAnotherResume` |
| Reconnection could publish before readiness without a later UI event | Bounded asynchronous reconciliation only while a published server is pending; no idle polling | `ServerReadinessTest.publicationWaitsForLifecycleReadinessBeforeReconnectingUi` |
| Queued passive refresh could erase a failure and automatically retry setup | Preserve the typed failure until an explicit retry, a live server or changed prerequisites supersede it | `StartTest.queuedPassiveRefreshPreservesLaunchFailureUntilRetryOrPrerequisiteChange`, `HomeViewModelTest.queuedSetupResumePreservesFailureAndDoesNotAutomaticallyRetry` |

## Runtime results and limits

- Unknown-key system confirmation accepted with Always allow at the D-pad.
- Running verified from UI and actual shell-owned detached process; launch ADB
  closed while the authenticated manager remained live.
- Reboot/manual restart reused the accepted key without another prompt.
- Standard same-signature update exited the old server; manual restart used the
  current APK without another prompt.
- Uninstall from Running removed the package, server and singleton socket.
- One first post-reboot shell operation reached its deadline; retry succeeded.
  Verify on the TV whether this reflects emulator slowness or a startup problem.
- The emulator's `--dont-kill` install still killed the manager. This does not
  exercise process-preserving replacement. Authenticated exit and bounded binder
  death are unit-tested, but task 3.4 remains incomplete.
- The original debugging-off check had no runtime evidence; the supplied TV
  diagnosis above now establishes cgroup termination and the approved contract.
- Screenshots were delivered directly. The isolated emulator was stopped and its
  heavy lock released. No runtime retest of the PR build is planned.

## Original pending real-TV checklist (historical; superseded above)

- Settings/property visibility, manual recovery/refusal and remote return focus.
- APK classpath launch, authenticated provider callback, singleton and shell uid.
- Always allow, bounded retry, reboot/manual restart and stable key reuse.
- Binder survival after launch-stream close; debugging-off termination and
  recovery without Tsuyu changing debugging itself.
- Same-signature update/uninstall terminate the old server; update retains key
  storage and a subsequent launch uses the current APK.
- Process-preserving replacement delivery terminates the old server and verifies
  binder death; observation-failure diagnostics where reproducible.
- Home exit, setup/settings Back, reduced motion and couch-distance readability.

## Assessment

Two incomplete tasks remain before archive. Scenario coverage is partial for
process-preserving replacement, package-observation failure and vendor/UI checks
above. No unresolved product choice is introduced. P2/P3 behavior remains in the
existing dependent changes.
