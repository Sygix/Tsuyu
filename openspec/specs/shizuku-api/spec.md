# shizuku-api Specification

## Purpose
Allow unchanged upstream Shizuku-API clients to use shell-privileged operations and UserServices through Tsuyu's authenticated service.

## Requirements

### Requirement: Upstream wire and attachment contract
Tsuyu SHALL preserve upstream AIDL descriptors, method transaction ids, companion interfaces, Bundle keys and BinderContainer format. It SHALL implement every IShizukuService method and remote transact, advertise upstream API 13/patch 6 and verify caller package ownership during attachment. Possessing a binder SHALL NOT grant privilege.

#### Scenario: Attached client receives metadata
- **WHEN** `ShizukuProvider` receives the binder and `Shizuku` calls `attachApplication`
- **THEN** `bindApplication` supplies version 13, patch 6, shell uid, SELinux context and the caller's current access/rationale state using upstream keys

#### Scenario: Package spoofing or refusal
- **WHEN** a client attaches with another uid's package, or its user has refused access
- **THEN** spoofed attachment is rejected and a refused legitimate client receives no authorized operations

#### Scenario: Unsupported platform facility
- **WHEN** a required system facility is absent
- **THEN** the relevant API reports a failure rather than a success stub, and Tsuyu exposes an actionable compatibility error

#### Scenario: Attachment deadline
- **WHEN** metadata lookup or attachment callback cannot complete within its infrastructure deadline
- **THEN** Tsuyu records an attachment failure and does not retain a privileged half-attached client

#### Scenario: Reattach after reboot
- **WHEN** the server is restarted after reboot and a client receives its new binder
- **THEN** the client's binder-received listener fires and attachment reflects the persisted access decision rather than stale process state

### Requirement: Startup and process-start binder delivery
The shell server SHALL call `sendBinder` on `<client package>.shizuku` with the upstream binder extra for every installed app requesting `moe.shizuku.manager.permission.API_V23`, at server startup and client process starts. Delivery SHALL NOT depend on an Android grant of this permission, and Tsuyu SHALL declare no upstream manager permission.

#### Scenario: Startup delivery
- **WHEN** a requesting client is installed when the server starts
- **THEN** its upstream provider receives BinderContainer and `Shizuku.pingBinder()` becomes true after delivery

#### Scenario: Later process start
- **WHEN** a requesting client process starts or restarts after the server is already running
- **THEN** delivery occurs again and its binder-received listener observes the live server

#### Scenario: Refused client receives only a gated binder
- **WHEN** a refused client calls `Shizuku.checkSelfPermission()` after delivery
- **THEN** it observes denial and cannot execute a shell operation

#### Scenario: Provider or observer absent
- **WHEN** the provider or required process observation facility is absent
- **THEN** Tsuyu records and displays delivery failure without granting access or claiming this client is ready

#### Scenario: Delivery timeout
- **WHEN** one client's provider call times out
- **THEN** other clients can still receive their binders and the failed client is reported separately

#### Scenario: Delivery after reboot
- **WHEN** the server starts again after a TV reboot
- **THEN** binders are redistributed and clients do not reuse the dead pre-reboot binder

### Requirement: Authorized shell operations and remote processes
Tsuyu SHALL enforce attached-client consent on privileged calls, run operations with shell rights, support remote Binder forwarding and implement the complete IRemoteProcess lifecycle. Property and permission calls SHALL expose actual platform results; execution SHALL use validated argument arrays without constructing shell text from untrusted input.

#### Scenario: Authorized API calls
- **WHEN** an allowed client calls `getUid`, `getVersion`, `checkPermission`, `getSELinuxContext`, `getSystemProperty` or `setSystemProperty`
- **THEN** it receives shell uid/version or actual shell permission/context/property results and platform errors for forbidden writes

#### Scenario: Remote transaction
- **WHEN** an allowed client uses `ShizukuBinderWrapper` for a system-service transaction
- **THEN** the server forwards the upstream transaction payload/code/flags under shell identity and returns its reply or exception

#### Scenario: Remote process output
- **WHEN** an allowed client calls `newProcess` with arguments for `id -u`
- **THEN** IRemoteProcess exposes stdin/stdout/stderr descriptors, exit status and liveness; output identifies shell uid and destroy terminates that process

#### Scenario: Denied caller or stolen process binder
- **WHEN** a refused, revoked or unattached client calls a privileged method or another client's IRemoteProcess binder
- **THEN** the server rejects it rather than executing with shell rights

#### Scenario: Missing executable or platform API
- **WHEN** an allowed operation targets a missing executable or unavailable system API
- **THEN** the client receives an explicit exception rather than fabricated data

#### Scenario: Bounded operations and API wait semantics
- **WHEN** an infrastructure operation exceeds its deadline or a client uses `waitForTimeout`
- **THEN** the former reports failure and the latter honors the upstream timeout/result semantics; `waitFor` retains upstream process-completion semantics without blocking unrelated service work

#### Scenario: Processes after reboot
- **WHEN** the TV reboots
- **THEN** old process/service binders die and clients must obtain the restarted server before creating new processes

### Requirement: Full UserService lifecycle
Tsuyu SHALL support upstream bind, peek, unbind and removal semantics, tags, version replacement, daemon and non-daemon lifetime, process naming, Context/no-arg constructors and supported ABI/debug options. A service SHALL load only code belonging to the authorized caller and attach using an unforgeable launch token.

#### Scenario: Demo service runs as shell
- **WHEN** the upstream demo calls `Shizuku.bindUserService`
- **THEN** its connection receives its own IUserService binder and the demo service reports shell uid and can load its native library

#### Scenario: Peek and unbind
- **WHEN** a client peeks an absent/existing service or unbinds with remove false/true
- **THEN** upstream absent/version results, callback detach and service destruction behavior are preserved

#### Scenario: Version and client death
- **WHEN** an authorized client changes its service version or dies
- **THEN** incompatible service instances are replaced, non-daemon services without clients are destroyed and daemon instances remain available until removal or server death

#### Scenario: Refusal or spoofed component
- **WHEN** access is refused/revoked or a service request names another uid's package or a forged attachment token
- **THEN** no UserService is launched or attached for that request

#### Scenario: Missing service or ABI
- **WHEN** the requested class, supported constructor, executable or required platform loading facility is absent
- **THEN** Tsuyu reports a service-launch error and removes the failed pending instance rather than leaving a permanent starting record

#### Scenario: Service attachment timeout
- **WHEN** the launched service does not attach within its startup deadline
- **THEN** its pending record and owned process are cleaned up and a later bind can retry

#### Scenario: UserServices after reboot
- **WHEN** the TV reboots and a client reconnects to Tsuyu
- **THEN** old service binders are dead and a new authorized bind can launch a new service; daemon mode does not imply reboot survival

### Requirement: Manager-only upstream administrative methods
Tsuyu SHALL enforce manager identity for `exit`, permission-result dispatch and UID flag management, and authenticate service attachment and package dispatch. Client apps SHALL NOT gain these powers through the public binder. The full AIDL surface SHALL remain callable with upstream-compatible results for legitimate callers.

#### Scenario: Manager updates access
- **WHEN** Tsuyu invokes `getFlagsForUid`, `updateFlagsForUid` or `dispatchPermissionConfirmationResult` for a valid pending request
- **THEN** current flags and callbacks match `client-access`; an app calling those methods cannot impersonate Tsuyu

#### Scenario: Authorized lifecycle methods
- **WHEN** a legitimate service attaches or the manager exits the server
- **THEN** the matching pending service receives attachment or clients observe server binder death respectively

#### Scenario: Denied administration
- **WHEN** a client calls `exit`, result dispatch, UID flag updates or package dispatch without the required identity
- **THEN** it cannot change server, package or access state

#### Scenario: Missing package or pending record
- **WHEN** an administrative request refers to an unavailable package or expired pending service
- **THEN** it is rejected and no unrelated record is changed

#### Scenario: Administrative timeout
- **WHEN** a manager-side system lookup or callback exceeds its deadline
- **THEN** Tsuyu exposes the incomplete operation without claiming success

#### Scenario: Administrative identity after reboot
- **WHEN** the server starts again after reboot
- **THEN** it resolves the installed manager identity again and rejects pre-reboot request and launch tokens

### Requirement: Upstream non-Sui method behavior
Tsuyu SHALL preserve the upstream non-Sui behavior of the reserved AIDL methods: authenticated manager `dispatchPackageChanged` calls have no effect and legitimate `isHidden` calls return `false`. These endpoints SHALL NOT introduce hidden-app or other Sui functionality.

#### Scenario: Non-Sui calls
- **WHEN** the manager calls `dispatchPackageChanged` or an attached client calls `isHidden(uid)`
- **THEN** dispatch changes no package state and `isHidden` returns `false`

#### Scenario: Unauthorized dispatch
- **WHEN** an ordinary client attempts `dispatchPackageChanged`
- **THEN** the call is rejected without changing package or consent state

#### Scenario: Sui absent
- **WHEN** these methods run on a device without Sui
- **THEN** their documented results need no Sui service or platform lookup and have no system-wait timeout

#### Scenario: Non-Sui behavior after reboot
- **WHEN** clients attach to the restarted server after reboot
- **THEN** `isHidden` still returns `false` and administrative dispatch remains an authenticated no-op
