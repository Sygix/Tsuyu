# P2 verification

Status: partial; not ready for merge or archive. The accepted contract is unchanged.

## OpenSpec verification scorecard

| Dimension | Result |
| --- | --- |
| Completeness | All public endpoints are implemented; complete acceptance criteria remain open where their evidence is missing |
| Correctness | JUnit plus release demo/probe evidence cover core consent, shell operations and lifecycle paths; failure/interleaving permutations remain partially verified |
| Coherence | Server-owned authority with app-private persistence, shared-UID disclosure, manager-only administration and unchanged upstream wire contract |

See [release runtime evidence](runtime-p2.md) for exact builds, observations and remaining proof. Incomplete acceptance tasks are critical before archive; unverified scenarios remain warnings, not implicit passes.

## Commands and regression evidence

- `./gradlew :server:testDebugUnitTest --tests '*AccessAuthorityTest' --no-daemon`: before the authority implementation, two assertions failed (grant persistence and failed-commit propagation). All three pass with the implementation.
- `./gradlew :app:testDebugUnitTest --tests '*AccessNavigationTest' --no-daemon`: the vertical direction assertion failed before fixing permission focus edges; both tests now pass.
- `./gradlew test assembleRelease --no-daemon`: passes, including P1 regression tests.
- `ClientServiceTest.wireIdsRemainSparseAndDoNotCollideWithForwarding`: generated AIDL codes retain explicit upstream ids plus Binder's FIRST_CALL_TRANSACTION offset; forwarding remains code 1.

## Requirement and method map

| Contract | Implementation | Evidence / remaining proof |
| --- | --- | --- |
| Four upstream interfaces, constants and BinderContainer | `api/` | Generated wire tests; upstream AIDL method signatures and explicit ids compared with reference sources |
| Attachment, metadata, permission checks | `ClientService`, `ClientIdentity`, `AccessAuthority` | `ClientServiceTest`, `AccessAuthorityTest`; attachment death/deadline and unreadable persistence need additional regression coverage |
| Startup and later provider delivery | `ClientDelivery`, `ProviderBridge` | Startup/later foreground/background release proof passes; provider saturation/isolation still needs targeted regression coverage |
| getVersion/getUid/getSELinuxContext/checkPermission/properties | `ClientService` | Consent gates, actual shell/platform results, nullable defaults and failure propagation verified by JVM/release probe |
| transaction 1 forwarding | `ClientService.onTransact` | Sync reply and oneway flags/identity pass release assertions; PID-zero gating has a red/green regression; malformed-parcel permutations remain open |
| newProcess and every IRemoteProcess method | `RemoteProcess`, `ClientService.newProcess` | Release proof covers streams/EOF/waits/exit/liveness/SIGTERM-ignoring destruction; owner-death/stolen-descriptor permutations need broader tests |
| request/checkSelfPermission/rationale | `ClientService` | Grant/refusal, codes/replay, client death, atomic bounds, elapsed deadline, group fanout, identity-bound commit and persistence have JUnit proof; activity Back/failure/interleavings remain partially verified |
| Manager exit/result/flags | `ClientService`, `ServerControl` | Forged manager calls tested; flags mask combinations and storage/cleanup failures need more tests |
| dispatchPackageChanged/isHidden | `ClientService` | Authenticated non-Sui no-op / false tested; no Sui functionality |
| UserService add/remove/attach | `UserServices`, `UserServiceMain` | Release proof covers native no-arg and initialized Context construction, PID attachment, existing/absent peek and null removal; failure/hostile/ABI/debug/daemon/version permutations remain open |
| French permission/list UI, shared UID | `AccessViewModel`, `AccessScreen`, `PermissionActivity` | Refusal focus, horizontal edges, center and empty fallback tested; group disclosure is implemented; Back/caller return, disappearing-row and icons require additional verification |
| Effective revocation and update survival | `ClientService.revoke`, `ConsentStorage` | Decision removal precedes cleanup; live native service termination and successful update/grant retention pass; re-request after revoke has a red/green regression, denial-update/failure permutations remain open |

## Known gaps and decisions to validate

- Full scenario regression coverage is incomplete; tasks requiring that evidence remain open.
- Delivery and consent callback failures now emit an actionable compatibility diagnostic on Home; it is conservative for the live manager process and still needs failure/recovery runtime proof.
- Expiry/client-death events now close the matching permission screen through authenticated manager IPC; event loss during manager-process death and caller return still need runtime proof.
- UserService callbacks are dispatched outside lifecycle locks with a bounded infrastructure deadline; callback failure/death cleanup still needs lifecycle regression coverage.
- The child PID check uses the Android process implementation's hidden `pid` field; release native/Context attachment and owned SIGKILL pass on the emulator. TV validation is not inferred from that proof.
- Keep shared-UID consent and the accepted non-Sui behavior; no upstream manager permission is declared. No contract change is proposed.

## Code-review remediation

The review corrections preserve the accepted OpenSpec requirements. All previously pending PR decisions are approved, including clearing consent on revocation and recording refusal on Back.

| Review point | Correction | JVM evidence |
| --- | --- | --- |
| P2.1 hot-path identity/storage cost | Server-owned committed snapshot; identities cached only after protected package observation is registered; package events invalidate both authorities; startup/reconnect reloads storage and invalidates identity; failed writes discard cached state | `AccessAuthorityTest.repeatedPrivilegeChecksDoNotReadTheManagerAgain`, `doubtDiscardsCachedConsentAndUnreadableStorageFailsClosed`; existing signer/membership invalidation tests |
| P2.2 concurrent/blocked infrastructure | Independent bounded SYSTEM, IDENTITY, STORAGE, CALLBACK and DELIVERY pools, bounded queues, explicit timeout/capacity recovery errors | `DeadlineTest` |
| P2.3 process retention | Normal-exit watcher removes the strong server registry entry and closes stdin; output pumps finish at EOF; descriptor ownership remains with a live process binder and finalization closes released binders; maximum 16 active processes per UID | `RemoteProcessTest`; Android Binder-GC timing is not proven by JVM |
| P2.4 delivery storms | PID deduplication, death invalidation; no idle trigger; active/cached UID fallback resolves live PIDs before deduplication | `ClientDeliveryTest.stateChangesDeliverOncePerPidAndDeathAllowsRedelivery` |
| P2.5 permanent compatibility alert | Missing client provider is not a global outage; successful delivery and authenticated new-server publication clear transient status; provider errors name the requested authority | `ClientDeliveryTest.missingClientProviderDoesNotMarkTheServiceAsBroken`, `ManagerProviderTest` |
| P2.6 permission-screen dead end | Back/return finishes an error screen; live undecided requests still record denial | `AccessViewModelTest.backCanLeaveAnUnavailablePermissionRequest`, existing refusal tests |
| P2.7 partial revoke/global lock | All cleanup steps run and failures are aggregated; UserService termination waits run outside the registry monitor | `CleanupTest`, `UserServicesTest.cleanupWaitDoesNotHoldTheGlobalRegistryLock` |
| P2.8 queue monopolization | Maximum two pending requests per UID within the global bound of eight | `ClientServiceTest.pendingRequestsAreFiniteAndDuplicateCodesCannotCreateAnotherPrompt` |
| P2.9 shell-child manager writes | Persistence/status calls require authenticated live-server PID; another PID cannot replace a live session; child attach keeps token/PID validation | `ServerBrokerTest`, `ManagerProviderTest`; residual same-shell-identity trust limitation documented in `SECURITY.md` |
| P2.10 multiprocess remote owner | Remote-process operations check the consenting owner UID/group rather than creator PID | Authorization implementation in `ClientService`; additional real multiprocess ABI evidence remains open |
| Process descendants | Launch under `toybox setsid`; owned termination signals the negative process-group ID | `OwnedProcessTest`; Android session creation and destruction still require runtime validation |
| Death recipients | Unlink failed/replaced attachments and detached/removed UserService callbacks | `UserServicesTest`; broader attach/death races remain open |
| Injected identity | `isHidden` uses the injected caller UID | `ClientServiceTest.clientsCannotAdministerEvenWithGrantedBinder` |
| Shrinker/visibility/naming | Exact hidden-class warnings replace broad package suppression; redundant launcher query removed; shared request/list activity renamed `AccessActivity` | Release build and existing navigation tests |
| Responsibilities/UI logic | Consent queue and owned-process management extracted from Binder adapter; named protocol/capacity constants; `AccessPresentation` supplies labels, actions, focus and navigation to Compose | Existing consent/lifecycle/navigation tests |
| Missing suites | Added `UserServicesTest`, `ClientDeliveryTest`, `RemoteProcessTest`, `ManagerStorageTest`, `ManagerProviderTest`, `AccessViewModelTest` | Full JVM suite; native/runtime permutations remain open |

Red/green mutation check: restoring per-call storage reads, global-only request limits, positive-PID signaling, UID-only broker acceptance and retrying refusal on error produces three server-suite failures and two app-suite failures. Restoring the corrections passes. New lifecycle/delivery suites additionally cover timeout, version replacement, daemon/non-daemon detach, revocation, provider absence and normal process exit. Not every new assertion has an individually reverted-fix run; those assertions are regression guards, not claimed red/green evidence.

### OpenSpec verification refresh

- Completeness: 6/21 original tasks remain checked; all 15 open acceptance tasks remain critical before archive. See the exact unchecked task list in `openspec/changes/p2-shizuku-compatibility/tasks.md`.
- Correctness: all eleven added requirements have implementation mappings above and in the original method map. Unexecuted device scenarios, actual Binder-GC release, complete hostile/death permutations and client-specific missing-provider presentation remain warnings. Missing-provider handling now avoids a misleading service-wide outage; no separate client-diagnostic UI is implemented.
- Coherence: checked in-memory authority is server-owned and committed before publishing success; protected package changes and manager reconnection are doubt boundaries. Same-UID shell isolation has the explicit security limitation above. Formatting remains uneven in older startup/UserService/UI code; extraction reduces the main Binder service's responsibilities but is not a claim that every existing file has been reformatted.
- No TV execution, archival, merge or tag is part of this correction pass.

### Decisions to validate

- Defensive capacities: two pending consent requests and sixteen active remote processes per UID; independent infrastructure pools with bounded queues.
- Remaining same-shell-identity trust boundary described in `SECURITY.md`.

## Runtime boundary

The combined P2/P3 emulator pass and targeted corrections used release APKs, the pinned demo and a distinct external protocol probe. Emulator fixture changes are not product behavior. Vendor/hardware TV validation remains open for Claude: demo permission with D-pad, successful Binder call and revocation; then first AUTO_START preparation with independent UID/package modes and one cold boot with the manager process absent.

TV tasks are never completed using emulator evidence. No archive before merge and TV proof.

## Independent review corrections

- Revocation/publication race: handled with `PrivilegeLifecycle` shared by remote-process creation, UserService creation and revocation. `PrivilegeLifecycleTest` failed without exclusion and passes with per-UID locks.
- Stdin EOF: handled by returning the owning write descriptor; actual write/close/EOF completion passes the release probe.
- Caller task return: handled by empty permission-activity task affinity; refusal/grant return to the demo passes release verification.
- Revoke association: handled by keyed group cards and named actions; `eachRevokeActionIdentifiesItsOwnGroup` asserts both groups.
- Flag mask semantics: handled by masked transitions, zero-mask no-op and invalid-combination rejection. `zeroMaskAndClearingDenialDoNotRevokeAnExistingGrant` failed before the fix.
- Identity invalidation: handled by committing removal of mismatched stored identities. `restoringOldMembershipDoesNotRestoreInvalidatedConsent` failed before the fix.

Further release corrections are covered in `runtime-p2.md`: nullable removal/property contracts, readable foreground, initialized Context constructor, marshalable checked errors, owned SIGKILL/death confirmation, deadline/identity-bound consent and PID-zero oneway forwarding. `ClientServiceTest` also covers matching request codes, replay, client death, duplicate/global request bounds and shared-group callbacks. Independent code-delta reviews found no remaining blocking/medium defect at the final reviewed P2 code head.
