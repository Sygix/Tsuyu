# P1 verification

## Summary

| Dimension | Result |
| --- | --- |
| Completeness | 13/15 tasks complete; all 7 added requirements have implementation evidence |
| Correctness | Automated logic and emulator evidence below; process-preserving replacement and vendor runtime behavior are not fully verified |
| Coherence | Modules, manager identity, stable private key, bounded operations, StateFlow and remote UI follow the accepted design |

Tasks 3.4 and 5.3 remain incomplete: process-preserving replacement runtime
evidence, and PR handoff followed by rebase/retarget after the specification PR
merges. Do not archive before merge and Sygix's real-TV validation.

## Commands and regression evidence

- `./gradlew test`: passed, 35 app tests and 3 server tests, no failures or skips.
- `./gradlew assembleRelease`: passed with minification and packaged server/AIDL.
- `openspec validate --all --strict`: 3 changes passed, 0 failed.
- License/privacy scans: no missing SPDX identifiers; the only IPv4 address in
  added source is loopback. No local environment detail is published.
- Removing checksum validation made
  `AdbTest.invalidMagicLengthChecksumAndEofAreRejected` fail; restoration passed.
- The three independent-review race regressions failed without their fixes and
  passed afterward.

Runtime evidence belongs to the initial P1 release build. After review fixes,
automated regressions and release compilation passed. The final settings-reader
injection and ViewModel tests passed `./gradlew test`. The emulator flow was not
repeated, as requested by Sygix.

## Requirement/scenario mapping

App production paths are under `app/src/main/java/fr/sygix/tsuyu/`, app tests
under `app/src/test/java/fr/sygix/tsuyu/`, and server paths under
`server/src/main/java/fr/sygix/tsuyu/server/` with parallel test paths.

| Requirement and scenarios | Implementation | Evidence / limits |
| --- | --- | --- |
| Guided setup: success, refusal, unknown/missing settings, deadline, reboot | `start/AndroidSettings.kt`, `start/StartCoordinator.kt`, `ui/HomeViewModel.kt`, `MainActivity.kt` | `AndroidSettingsTest`, `StartTest`, `HomeViewModelTest`; vendor settings navigation pending |
| Private authentication: accepted/unknown key, refusal/disconnect, unsupported/TLS endpoint, malformed input, timeout, reboot reuse | `adb/AdbPacket.kt`, `adb/AdbSession.kt`, `adb/AdbKey.kt`, `adb/AdbKeyStore.kt`, `start/LocalAdbLauncher.kt` | `AdbTest`, `LocalAdbLauncherTest`, bounded-worker deadline test; emulator Always allow, repeat start and reboot reuse passed |
| Verified shell lifecycle: success, cancellation, unavailable launch, deadlines, concurrent starts/death, reboot restart | `start/ServerBroker.kt`, `start/StartCoordinator.kt`, `start/ServerReadiness.kt`, `ServerMain.kt`, `ServerControl.kt` | Broker/control identity tests, `StartTest`, `ServerReadinessTest`, `HomeViewModelTest`; release runtime shell uid and live manager verified |
| Detached survival: ADB close, debugging off, launch failure/deadline, reboot | `start/LaunchCommand.kt`, `start/LocalAdbLauncher.kt` | Command/close tests; emulator detached process and closed launch ADB verified; debugging-off is unit-only, pending on TV |
| Package lifecycle: update, process-preserving update, uninstall, failed update, observation failure, current APK after reboot | `PackageWatch.kt`, `ServerMain.kt`, `start/PackageReplacementReceiver.kt`, `start/ServerShutdown.kt` | `PackageWatchTest`, `ServerShutdownTest`; emulator standard update, current-APK restart/key reuse and uninstall passed; process-preserving update and observation-failure exits are not fully exercised at runtime |
| Remote focus: initial control, all directions/edges, Back/return, settings return, disappearing control, home exit | `ui/HomeScreen.kt`, `ui/HomeViewModel.kt`, `MainActivity.kt` | `NavigationTest` at TV qualifiers; ViewModel Back/settings/cancellation tests; runtime home/help navigation and Back passed; actual home-exit/settings path requires TV confirmation |
| French presentation: stable readable state, recovery, reduced motion | `ui/HomeScreen.kt`, French resources | Strings, minimum body size and safe padding inspected; screenshots delivered; retry recovered from launch timeout; couch-distance/reduced-motion appearance pending on TV |

## Independent-review fixes

| Finding | Resolution | Regression |
| --- | --- | --- |
| Replacement between launch lookup and lifecycle baseline could bless the new APK while executing the old one | Validate the initial package stamp against launched path and manager uid; keep before/after-publication reconciliation | `PackageWatchTest.replacementBetweenLaunchLookupAndBaselineCannotWatchTheNewApk` |
| Cancellation could discard the only refresh behind the startup mutex | Queue refresh rather than silently discard it | `StartTest.queuedRefreshReconcilesServerAfterStartupCancellation`, `HomeViewModelTest.cancelAutomaticallyReconcilesLateLiveServerWithoutAnotherResume` |
| Reconnection could publish before readiness without a later UI event | Bounded asynchronous reconciliation only while a published server is pending; no idle polling | `ServerReadinessTest.publicationWaitsForLifecycleReadinessBeforeReconnectingUi` |

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
- Debugging-off runtime observation is unavailable after adb is disabled; unit
  evidence does not establish vendor behavior.
- Screenshots were delivered directly. The isolated emulator was stopped and its
  heavy lock released. No runtime retest of the PR build is planned.

## Pending real-TV checklist

- Settings/property visibility, manual recovery/refusal and remote return focus.
- APK classpath launch, authenticated provider callback, singleton and shell uid.
- Always allow, bounded retry, reboot/manual restart and stable key reuse.
- Binder survival after launch-stream close and debugging disabling, without
  Tsuyu changing debugging itself.
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
