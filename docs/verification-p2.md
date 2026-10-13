# P2 verification

Status: partial; not ready for merge or archive. The accepted contract is unchanged.

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
| Startup and later provider delivery | `ClientDelivery`, `ProviderBridge` | Registration precedes enumeration; per-provider finite retries; actual foreground/background delivery and failure isolation pending E2E |
| getVersion/getUid/getSELinuxContext/checkPermission/properties | `ClientService` | Consent gates tested; actual shell/platform results pending E2E |
| transaction 1 forwarding | `ClientService.onTransact` | Token/envelope validated, caller identity restored in finally; malformed parcel/oneway/reply/platform failure tests pending |
| newProcess and every IRemoteProcess method | `RemoteProcess`, `ClientService.newProcess` | Owned pipes, argument arrays, timeout unit validation; actual streams, shell uid and death/revoke pending E2E |
| request/checkSelfPermission/rationale | `ClientService` | Signer/membership/UID reuse and persistence tests; pending-token replay, concurrency, screen launch failure and timeout tests pending |
| Manager exit/result/flags | `ClientService`, `ServerControl` | Forged manager calls tested; flags mask combinations and storage/cleanup failures need more tests |
| dispatchPackageChanged/isHidden | `ClientService` | Authenticated non-Sui no-op / false tested; no Sui functionality |
| UserService add/remove/attach | `UserServices`, `UserServiceMain` | Owned component, native package context, process-bound token, versions/daemon/peek/remove implemented; actual native load, child identity reflection, lifecycle failures and hostile inputs pending E2E and further unit tests |
| French permission/list UI, shared UID | `AccessViewModel`, `AccessScreen`, `PermissionActivity` | Refusal focus, horizontal edges, center and empty fallback tested; group disclosure is implemented; Back/caller return, disappearing-row and icons require additional verification |
| Effective revocation and update survival | `ClientService.revoke`, `ConsentStorage` | Authoritative persisted denial before process cleanup; force-stop discards cached capabilities; live child/client and same-signature update proof pending |

## Known gaps and decisions to validate

- Full scenario regression coverage is incomplete; tasks requiring that evidence remain open.
- Delivery and consent callback failures now emit an actionable compatibility diagnostic on Home; it is conservative for the live manager process and still needs failure/recovery runtime proof.
- Expiry/client-death events now close the matching permission screen through authenticated manager IPC; event loss during manager-process death and caller return still need runtime proof.
- UserService callbacks are dispatched outside lifecycle locks with a bounded infrastructure deadline; callback failure/death cleanup still needs lifecycle regression coverage.
- The child PID check uses the Android process implementation's hidden `pid` field; the release E2E must verify it. Failures reject attachment rather than bypass identity checks.
- Keep shared-UID consent and the accepted non-Sui behavior; no upstream manager permission is declared. No contract change is proposed.

## Runtime boundary

One final combined P2/P3 emulator pass will use the release APK and pinned upstream demo. Emulator fixture changes are not product behavior. Vendor/hardware TV validation remains open for Claude: demo permission with D-pad, successful Binder call and revocation; then first AUTO_START preparation with independent UID/package modes and one cold boot with the manager process absent.

TV tasks are never completed using emulator evidence. No archive before merge and TV proof.

## Independent review corrections

- Revocation/publication race: handled with `PrivilegeLifecycle` shared by remote-process creation, UserService creation and revocation. `PrivilegeLifecycleTest` failed without exclusion and passes with per-UID locks.
- Stdin EOF: handled by returning the owning write descriptor, letting AIDL's return-value marshalling close the server writer. Real write/close/EOF completion remains a runtime proof to collect.
- Caller task return: handled by empty permission-activity task affinity; list entry from Home remains in the existing task. Actual task return remains a runtime proof.
- Revoke association: handled by keyed group cards and named actions; `eachRevokeActionIdentifiesItsOwnGroup` asserts both groups.
- Flag mask semantics: handled by masked transitions, zero-mask no-op and invalid-combination rejection. `zeroMaskAndClearingDenialDoNotRevokeAnExistingGrant` failed before the fix.
- Identity invalidation: handled by committing removal of mismatched stored identities. `restoringOldMembershipDoesNotRestoreInvalidatedConsent` failed before the fix.
