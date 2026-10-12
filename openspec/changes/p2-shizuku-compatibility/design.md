# Design

## Context

See proposal.md and P1's design. P2 extends the packaged shell server and manager IPC; it does not replace local startup or TV navigation. Reference snapshots are recorded in P1. Upstream AIDL has 20 methods with explicit sparse ids; `ShizukuApiConstants` additionally reserves Binder transaction 1 for remote forwarding. Library release 13.1.5 uses server constants 13 and patch 6.

## Goals / Non-Goals

**Goals:** upstream wire compatibility, exact caller identity checks at every privilege boundary, complete remote process/UserService support and consent that can be revoked.

**Non-Goals:** forwarding upstream Android permission grants, rish transaction handling or unneeded phone manager architecture. Unknown device behavior remains a validation assumption, not an impossibility justification.

## Decisions

### API implementation matrix

Copy upstream AIDL unchanged except mandatory MIT headers into a focused API-contract module. Keep all explicit method ids and companion interfaces; generated transaction ids must be compared against upstream generated stubs, since AIDL ids and wire codes are not interchangeable. Import the minimal MIT BinderContainer and constants, retaining their parcelable class name and keys. Separate internal manager IPC from public API so no accidental control method becomes client-accessible.

| Methods / transaction | Implementation and observed result | Authority |
| --- | --- | --- |
| `attachApplication` | Check uid/package, register death-linked client, return upstream metadata Bundle through `bindApplication` | Legitimate client identity; no grant yet |
| `getVersion`, `getUid`, `getSELinuxContext` | Actual upstream API, shell uid and process context | Allowed attached client |
| `checkPermission` | Check named permission for shell, not for the requesting app | Allowed attached client |
| `getSystemProperty`, `setSystemProperty` | Platform access preserving default and failures | Allowed attached client; SELinux still applies |
| `newProcess` | Argument-array process launch and complete IRemoteProcess binder | Allowed attached client; process binder checks owner |
| remote transaction 1 | Validate token/envelope, clear caller identity, forward target/code/flags/data, restore identity in finally | Allowed attached client |
| `requestPermission`, `checkSelfPermission`, `shouldShowRequestPermissionRationale` | Pending request/callback, current grant and upstream refusal rationale | Attached client, scoped to its own uid/pid |
| `addUserService`, `removeUserService` | Validated owned component, lifecycle keyed by user/package/tag-or-class | Allowed attached client |
| `attachUserService` | One-time random launch-token lookup and live record attachment | Authenticated manager bridge or shell-owned child |
| `exit` | Terminate server and owned child processes; binder death follows | Manager only |
| `dispatchPermissionConfirmationResult` | Match uid/pid/requestCode to pending request, apply access decision, dispatch callback | Manager only |
| `getFlagsForUid`, `updateFlagsForUid` | Upstream permission-mask/flag semantics backed by server access state | Manager only |
| `dispatchPackageChanged`, `isHidden` | Preserve AIDL endpoints; non-Sui semantics are an Open question in proposal.md | No unauthenticated state change |

No method is omitted and no technical impossibility has been established. Any discovered impossibility must identify the device/runtime evidence here and revise all affected artifacts and the PR before claiming compatibility.

### Delivery and hidden platform boundary

Use shell package enumeration and upstream provider calls with BinderContainer to requesting manifests, independent of permission grant or V3 metadata. Register UID/process observation before startup enumeration; deduplicate concurrent delivery, release external provider references and retry individual failed deliveries within a finite budget. A foreground-only process observer is insufficient: upstream BinderSender combines process and UID callbacks, which still requires background-start testing. Failure to register must surface as a degraded compatibility state, not silent success.

Isolate hidden Android framework access in adapters with narrow signatures for the supported Android range. Prefer maintained compatible hidden-API bridge artifacts already used by upstream over manually guessed transaction numbers; verify maintenance/license and dependency closure before adoption. Do not import root, rish or Sui transitive layers. Validate actual Android 14+ signatures against the emulator; list vendor-TV provider/process behavior as pending Sygix checks.

### Authorization and storage

Keep a single server-owned permission authority. The app supplies app-private DataStore persistence through authenticated manager IPC, while the server owns live decisions and checks every privileged request. Bind stored grants to user/package/signing identity and current uid resolution, not uid alone. Commit a grant before publishing success; on unreadable storage, fail closed with an actionable manager error. Avoid duplicate app/server files with competing grant truth.

Authenticate UI actions against a server-issued pending identifier and its uid/pid/requestCode; extras alone are untrusted. Resolve app labels/icons from verified installed identity. Handle concurrent requests through a bounded queue; refuse stale/dead requests and discard replayed confirmations. Use a 120 s user-decision deadline, scoped to the live request. Refuse Back and screen-launch failure. Server callbacks keep the upstream requestCode and result Bundle.

Revocation first updates the authoritative denial, then terminates owned remote processes and UserServices. A UserService binder already sent directly to a client cannot be made safe by checking the main server alone: destroy its process, escalating termination for an uncooperative owned child. Likewise an arbitrary Binder capability already returned by a forwarded system-service transaction cannot be retroactively rescinded; force-stop affected client processes to clear cached capabilities, as upstream does. Report partial cleanup honestly. Identify processes by owned launch records, not an unvalidated client pid. Do not claim future calls are revoked while an existing shell child remains callable.

### Remote process and UserService lifecycle

Use argument arrays for both client processes and `app_process` child entry points. Validate component ownership, class/process-name inputs, supported ABI/debug executable and options; tokens are random, single-use and never logged. Create the client's package Context/classloader in the child using upstream loading semantics, retaining native-library search paths and Context/no-arg constructor behavior. The child returns its binder through Tsuyu's authenticated provider bridge and links its lifetime to server death. Service records carry user identity, tag/class, version, daemon flag, token, owned process and callback set.

Support no-create peek (absent -1, existing service version for API 13), reuse/version replacement, unbind without removal, remove with destruction and non-daemon cleanup when no callbacks remain. Never key records by package alone across users. Startup is capped at 30 s; failed child launch/attachment removes the record and process and reports the cause. Test split/native library loading with the actual demo rather than only fake binders.

Remote processes expose pipes with bounded pump buffers, explicit closure and owner checks. `waitForTimeout` validates unit/range and uses interruptible waiting; `exitValue` retains the upstream alive-process exception. Full upstream `waitFor` and arbitrary client Binder forwarding can intentionally wait indefinitely: silently capping them would violate the requested complete API. Infrastructure preparation/delivery/UI waiting remains bounded; intentional client waits must not hold coordinator locks or serialize unrelated clients, and owner death/revocation closes their resources. A Binder call that timed out locally cannot be forcibly cancelled; bounded executors limit abandoned calls. This compatibility constraint is a technical trade-off, not a missing method.

### TV UI

Use P1's shared components and navigation contract. Confirmation is a dedicated activity above the caller, initially focused on refusal; the icon/name and access effect come from verified ViewModel state. Authorized-app rows have visible revoke actions, stable keys and unit-tested focus fallback. Return home restores its list entry. UI handles service death, expired requests, empty/loading/error states and partial revocation without logic in composables. Back cannot authorize.

### Provenance

| Origin | Kept | Removed / reason | Header |
| --- | --- | --- | --- |
| Shizuku-API `aidl/src/main/aidl/moe/shizuku/server/{IShizukuService,IRemoteProcess,IShizukuApplication,IShizukuServiceConnection}.aidl` | Entire wire contracts | Nothing except added license headers | MIT, Copyright (c) 2021 RikkaW |
| Shizuku-API `shared/src/main/java/rikka/shizuku/ShizukuApiConstants.java`, `provider/src/main/java/moe/shizuku/api/BinderContainer.java` | ABI constants and parcelable format | Unneeded annotations/dependencies only | MIT header |
| Shizuku-API `server-shared/src/main/java/rikka/shizuku/server/Service.java` | Access checks, remote forwarding, process semantics | rish, broad generic managers, old-client side layers beyond the selected upstream contract | MIT header |
| Shizuku-API `server-shared/src/main/java/rikka/shizuku/server/{UserService,UserServiceManager,UserServiceRecord}.java`, `api/RemoteProcessHolder.java` | Constructors, lifetime/option behavior and process descriptor semantics | Unbounded infrastructure, process-token logging and generic unused hooks | MIT header |
| Shizuku `server/src/main/java/rikka/shizuku/server/{ShizukuService,BinderSender,ShizukuUserServiceManager}.java` | Provider delivery, process observation, manager/owner validation and package lifecycle | Permission declarations/grants, Sui, root, rish and phone manager extras | Apache-2.0 provenance with original copyrights and Sygix line |
| Shizuku `starter/src/main/java/moe/shizuku/starter/ServiceStarter.java` | Child startup and token/provider return pattern | String-interpolated shell commands, upstream manager id and old platform branches | Apache-2.0 provenance header |
| Shevery | Comparison only | No code copied; API inflation and unrelated layers excluded | No derived files |

Retain only source actually needed, verify exact upstream copyright notices and update NOTICE/licenses. New Tsuyu code uses AGPL-3.0-or-later. The demo remains external test material, not bundled into Tsuyu.

## Risks / Trade-offs

- [Hidden APIs, activity launch, native loading and background process delivery are not verified on the reference TV] → emulator evidence plus explicit Sygix real-TV checks; no fabricated platform guarantee.
- [Client/library cached permission state after revoke] → live server denial plus client restart; verify after reattachment and distinguish caches from server authorization.
- [Storage unavailable while manager starts] → bound handshake and fail closed; never grant from an unchecked cache.

## Migration Plan

Stack on P1; introduce empty consent storage and no preauthorized clients. P2 remains manually started until P3. Do not archive before merge and Sygix validation; archive P1 first. Update README with actual client compatibility and the accepted package-presence-check limitation.

## Open Questions

The two conservative product defaults in proposal.md remain to confirm and must be repeated in the implementation PR; they are not added as normative decisions here.
