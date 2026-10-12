# Design

## Context

See proposal.md for scope. P0 has one `app` module, a `BasicText` screen and JUnit only; there is no protocol, server, ViewModel or current capability spec. Device facts in AGENTS.md establish legacy local debugging, RSA confirmation and reboot behavior on the reference TV only.

Reference snapshots: Shizuku `b844bc491f1790c72328e1a8e5b2349f8978f0ea`; Shizuku-API `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5`; Shevery `95ea592bd704112c329ebf42b0c73eeb59547daa`.

## Goals / Non-Goals

**Goals:** small protocol and state machines testable without Android; a packaged shell entry point with authenticated manager IPC; one state authority for the TV UI.

**Non-Goals:** a generic ADB client, a public command endpoint or an ongoing foreground service to hold the shell process alive. P2 adds the public client API.

## Decisions

### Modules and launch

Keep `app` for setup/UI and ADB connection. Add Android library modules `server` for the shell entry point and `ipc` for minimal manager-only AIDL. Package server classes into the application APK and launch with `app_process` using the installed APK as classpath. This avoids an executable copied to a writable app directory and native starter machinery. R8 keep rules preserve entry points and IPC class names. A library module rather than another installed APK keeps installation and upgrades atomic.

The launch command contains a fixed executable and entry point, a validated installed APK path and correctly quoted arguments, never client-supplied shell text. The server checks its uid at entry, resolves the installed manager identity and delivers the internal binder through Tsuyu's provider. Provider caller identity must be shell; manager methods enforce the full package/user uid resolved by the server. Other apps cannot obtain manager powers by possessing a binder. Include liveness/death notifications and a launch generation token to reject stale callbacks. Identity handshakes and singleton detection must be verified before considering startup complete.

Detach with a new session, ignore launch-session hang-up and redirect all standard streams to a shell-owned diagnostic file or null device before backgrounding the fixed `app_process` invocation. Close the ADB connection after the authenticated handshake and prove binder survival. Turning debugging off later does not kill the independent process; observe the live binder before deciding a new start is required.

Watch the installed APK/package lifecycle and exit on replacement or removal, never keep executing the old classpath after an update. P3 receives MY_PACKAGE_REPLACED and restarts through the same coordinator; P1 exposes manual recovery. Same-signature updates preserve non-backed-up app-private key storage. Failure to establish lifecycle observation is a startup error. Use an installed-APK filesystem observer, as in upstream ApkChangedObservers, rather than an unauthenticated app broadcast.

### Discovery and protocol

Probe only loopback and candidates from available system configuration, with a standard legacy endpoint fallback when property visibility is restricted. Validate the endpoint by the ADB handshake rather than treating a property as proof of a listener. Do not port-scan the LAN, persist a transient port or invoke TLS discovery. An unavailable listener is reported as unavailable, not proof that the hardware lacks TCP support.

Implement the bounded legacy subset: little-endian header, magic/checksum validation where required by negotiated protocol, bounded payload length before allocation, `CNXN`, RSA `AUTH`, `OPEN`, `OKAY`, `WRTE`, `CLSE`, stream-id checks, fragmentation and EOF. Sign the already-hashed authentication token using ADB's PKCS#1 v1.5 encoding, not a second SHA-1 hash. Public-key encoding includes Android's RSA modulus/Montgomery fields and terminator. Store a stable key atomically in private non-backed-up storage; corrupt storage yields an explicit error instead of silently replacing an accepted key.

### State, cancellation and deadlines

Use interfaces for ADB transport, key storage, server launch/observation and system settings. A single coordinator serializes starts; ViewModel maps its StateFlow into immutable UI state. States distinguish setup, connecting, awaiting authorization, launching, running, stopped and typed errors. Composables render state and emit events only.

Use 5 s per candidate connection, 120 s for the system key prompt, 30 s for launch/manager handshake and 5 s for settings/provider operations. Socket deadlines and resource closure complement coroutine cancellation; `withTimeout` alone cannot interrupt arbitrary blocking I/O. A blocked Binder call runs on a bounded worker, and caller waiting expires without claiming it has killed the remote operation. Cap abandoned work and reject excess attempts. Reconcile actual server state after late completion. Cancel setup observation on leaving the screen; observe setting changes while relevant and refresh on lifecycle return instead of idle polling.

### Presentation

Follow DESIGN.md using shared TV buttons, quiet dark surfaces and a cool dew accent. Home exposes state and its single main action plus help; guided setup uses numbered steps and a settings action; technical details live under “Détails”. French network disclosure: “Le port reste ouvert sur le réseau local, mais seuls les appareils autorisés peuvent s’y connecter.” Centralize resource strings and semantic test tags. Focus behavior is owned by `tv-navigation` and reused by later phases.

### Provenance

| Origin | Kept | Removed / reason | Header |
| --- | --- | --- | --- |
| Shizuku `manager/src/main/java/moe/shizuku/manager/adb/AdbProtocol.kt`, `AdbMessage.kt`, `AdbClient.kt` | Legacy packet constants, encoding and stream/authentication sequence | TLS, pairing, unbounded allocation/waits, logging of payloads and swallowed close errors; outside scope or unsafe | Apache-2.0, original upstream copyright from repository notices, Sygix line and exact source path |
| Shizuku `manager/src/main/java/moe/shizuku/manager/adb/AdbKey.kt` | RSA public-key wire format and prehashed token signing | Certificates, TLS trust/key managers, private-key logging and silent replacement; no pairing needed | Apache-2.0 provenance header |
| Shizuku `server/src/main/java/rikka/shizuku/server/ShizukuService.java` | Shell main-loop and manager identity/delivery pattern | Public API until P2, rish, Sui, upstream manager package and permission declarations | Apache-2.0 provenance header if code is adapted |
| Shizuku `server/src/main/java/rikka/shizuku/server/ApkChangedObservers.kt` | Installed APK observation pattern | Multi-package client handling until P2; manager replacement/removal is mandatory now | Apache-2.0 provenance header if code is adapted |
| Shevery `manager/src/main/java/moe/shizuku/manager/worker/AdbStartWorker.kt` | Reference observation only: TV path depends on a configured TCP listener | Entire worker; Wi-Fi, modules, pairing and notification machinery are unnecessary | No copied code; no new derived file |

Verify original copyrights against each project's notices before copying, because some source files have no headers. Add full license texts and NOTICE provenance for actual imports. New Tsuyu files use AGPL-3.0-or-later. No new source origin beyond these is authorized by this design.

## Risks / Trade-offs

- [Property visibility, settings intents and hidden provider APIs vary] → mark these as implementation assumptions; exercise emulator paths and list real-TV validation before release. Do not claim emulator results establish vendor behavior.
- [APK classpath launch, singleton mechanism and provider callback are unverified on the TV] → first device-validation task belongs to Sygix; agent implements against emulator evidence, leaving TV sign-off pending under the explicit task authorization.
- [ADB refusal may look like a stalled prompt] → report a timeout honestly without inventing a refusal signal.
- [Late launch after cancellation] → reconcile binder state and serialize retry; do not kill an unrelated shell process.

## Migration Plan

No user data migration from P0. Deliver P1 independently with manual startup. Stack P2 on P1 and P3 on P2; do not archive before each merge and real-TV validation. Failure can be recovered by reopening setup and retrying; key material remains private across application updates.

## Open Questions

None beyond device validation assumptions above.
