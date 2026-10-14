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
