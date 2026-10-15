# P3 verification

Status: partial acceptance coverage; core release boot/client/expiry evidence passes, while remaining scenario criteria and real-TV proof stay open. Accepted requirements are unchanged. P3 is stacked on P2, whose evidence is recorded separately.

## OpenSpec verification scorecard

| Dimension | Result |
| --- | --- |
| Completeness | Implementation exists for all six requirements; full task criteria remain partially verified and incomplete tasks remain open |
| Correctness | JVM evidence plus core release boot/client/expiry and successful replacement proof; remaining failure/process-death permutations partially verified |
| Coherence | Same P1 coordinator/key/handshake; P2 consent unchanged; one app-private authority with independently bounded event records; no upstream permission declaration or generic settings endpoint |

## Evidence

- `./gradlew :server:testDebugUnitTest --tests '*PersistenceControlTest' --no-daemon`: removing preparation/explicit-expiry mutations caused two assertion failures. They pass with the implementation.
- `./gradlew :app:testDebugUnitTest --tests '*RestartBudgetTest' --no-daemon`: removing budget reservation failed the persisted retry/deadline assertion. Both tests pass with the implementation.
- `./gradlew test assembleRelease --no-daemon`: PASS, including P1/P2 regression tests.
- `PersistenceControlTest`: separate UID/package read-back, one-mode denial, unsupported vendor operation, denied operation, timeout, no implicit expiry write, explicit write, already-zero no-op and no reapplication after system change.
- `ServerControlTest`: every persistence manager endpoint rejects another UID before any operation.
- `RestartBudgetTest`: persisted attempt count, total deadline, process rescheduling, new-boot reset, permanent setup/auth failures and success termination.
- `RestartRunnerTest`: virtual-time delayed endpoint then live handshake, finite exhaustion, disabled/unknown debugging, manual/boot overlap and permanent authentication failure.
- `PersistenceControllerTest`: viewing/enablement cannot disable expiry, explicit action/read-back, already-disabled no-op, later system policy retained, disabled debugging/missing shell cannot prepare enablement.
- `PersistenceNavigationTest`: conservative optional focus, explicit center activation, edge cancellation, disappearance fallback, details entry restoration and read-only status.
- Release observations and exact limits: `runtime-p3.md`; final P2 protocol assertions and APK hashes: `runtime-p2.md`.

## Requirement/scenario map

| Requirement | Implementation | Remaining scenario evidence |
| --- | --- | --- |
| Consented preparation | `PersistenceController.enable`, `PersistenceControl`, disclosure resources | Actual vendor UID/package modes and cold-boot gate remain TV-only; command malformed-query and per-mode grant failures need broader JVM coverage |
| Optional global expiry | Fixed-key manager-only operation, current policy read-back, expiry screen | Emulator explicit write/read-back passes; timeout reconciliation, activity destruction and richer unavailable/denied cases need broader proof |
| Respect debugging disabling | Preflight in `RestartRunner`; P1 coordinator for manual/retry | P1 JVM disabled/unknown/deadline guards and new runner tests; actual emulator setting remains outside TV |
| Bounded automatic restart | `BootReceiver`, unique `RestartWorker`, persisted `RestartBudget`/`PersistenceStore` | Core absent-process boot, fresh binder/consent/shell result pass; receiver/database process-death regressions remain incomplete |
| Restart after update | Same receiver/worker; old-server cleanup before update startup; installed APK resolved by P1 | Successful same-signature replacement passes; failure/timeout and uninstall integration remain unverified |
| Status/recovery/navigation | Immutable Home state plus persistence/expiry ViewModels and French resources | Live readiness/removal checks on resume; specific outcome recovery labels and more focus/Back integration cases remain to verify |

## Critical / warning items

- CRITICAL before archive: incomplete task acceptance criteria and real-TV proof. The passed core combined E2E does not close unexecuted permutations.
- WARNING: no real-TV evidence exists yet. Only Claude's final vendor/hardware session can validate those scenarios.
- WARNING: no production-signed CI APK can be issued by this session because release CI requires an owner-authorized tag. The local release APK is an emulator-only development-signed build.
- WARNING: a finite work schedule cannot promise precise boot timing under Android deferral; standard scheduling on a missing vendor operation does not claim verified vendor readiness.

## Confirmed decisions

- Twenty-second initial work delay, thirty-second exponential retry backoff, maximum three persisted attempts and five-minute overall execution budget per relevant event honor the accepted bounded/tolerant contract without a watchdog. Boot/update events have independently retained bounded ledgers within the same DataStore authority, so alternating retries cannot reset either budget; the single coordinator/shell singleton still prevents duplicate servers. Old-epoch work exits before reservation.
- Prepare only after explicit disclosure/enablement. Read-only reopening and boot do not re-grant removed vendor access or change global expiry.
- The optional expiry operation reconciles current read-back before another explicit mutation; previous opt-in is not a preference to reapply.
- Use the existing tag-triggered release workflow; no signing/provisioning change, merge or tag is performed.

## Independent review corrections

- New boots cannot be suppressed by unfinished old work: event-specific unique names and current boot/update epoch checks; `RestartEventsTest` covers old epochs and interrupted third-attempt reset.
- Independent event ledgers prevent alternating boot/update jobs from resetting the attempt/deadline counters. `RestartLedgerTest` failed with the single-last-record policy and passes with event-keyed reservation.
- Failed fresh status reads invalidate cached modes and expiry, and Home rejects error/loading readiness; `failedFreshReadInvalidatesEarlierVerifiedModes` covers it.
- Explicit successful repair records `RECOVERED`, distinct from automatic `RUNNING`; the controller test starts with `EXHAUSTED` and asserts recovery.
- Main-action versus details entry preserves the actual initiating focus; `persistenceEntryRestoresTheActualInitiatingControl` covers both.

## Final correction pass

The branch is rebased onto the current corrected P2 implementation. Earlier independent review results do not constitute approval of this final delta; Simon requested direct correction without a reviewer subagent.

| Review point | Implementation | Evidence |
| --- | --- | --- |
| Unknown/unloaded activation state routes START incorrectly | `PersistenceController.startRoute` reads configuration before deciding; unreadable configuration produces an explicit error route | `PersistenceControllerTest.routingWaitsForConfigurationAndReadFailureIsVisible` |
| Preparation failure redisplays disclosure | DENIED/FAILED/TIMEOUT snapshots produce an actionable repair state | `deniedFailedAndTimedOutPreparationExposeRepairInsteadOfDisclosure` |
| Preparation budget equals server worst case | Client preparation budget is 45 seconds, exceeding seven bounded five-second server commands | Controller regression checks the larger budget; server command boundary remains bounded |
| Update cleanup delayed or suppressed when persistence is disabled | `PackageReplacementReceiver` immediately delegates cleanup independently of boot work; `RestartRunner` no longer owns update cleanup | Replacement receiver/shutdown regression coverage; boot/update startup continues through the common coordinator |
| Settings launch crashes | Shared `SettingsNavigation` failure handling and explicit persistence settings-error state | Navigation failure regression and ViewModel presentation assertions |
| One grant exception skips the other mode | Each mode returns its own typed preparation result before the final snapshot | `PersistenceControlTest.oneFailedGrantDoesNotSkipTheOtherModeOrClaimReadiness` |
| Stringly typed and duplicated state | Shared IPC `Preparation`, domain `RestartOutcome`, one event ledger with legacy migration, explicit storage interface methods and faithful test fake | `PersistenceStoreTest` exercises real DataStore and the fake with the same history; migration removes duplicated fields |
| Redundant layer and mixed UI/test responsibilities | `RestartLedger` merged into `RestartBudget`; dedicated `PersistenceScreenUi` and details variants; persistence tests moved into their domain package; affected presentation/controller code expanded | JVM/Compose suite and source review |

- `./gradlew test assembleRelease --no-daemon -q`: PASS under the shared lock, including P1/P2 regressions.
- `openspec validate --all --strict`: 4 passed, 0 failed. Completeness remains 2/17 original tasks; incomplete hardware, receiver/process-death and full-scenario criteria remain open.
- The combined correction release passes the external protocol client and supervised-process checks described in `runtime-p2.md`; this pass does not repeat the entire historical boot/optional-expiry matrix.
- Remaining real-TV/vendor AUTO_START checks and production-signed release execution are not established by emulator or JVM results. No archive, merge, tag or visibility change is performed.
