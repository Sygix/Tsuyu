# Design

## Context

See proposal.md. P1 owns authenticated local start and P2 owns client service/access; this phase adds preparation and boot orchestration without a second launch path. Verified reference-TV facts: USB debugging persists, the legacy listener returns at boot, vendor AUTO_START defaults to ignore and its shell-granted allow value persists. Key authorization expires after seven days without a connection unless a global policy disables expiry; disabling it is an explicit optional action confirmed in proposal.md.

## Goals / Non-Goals

**Goals:** verified preparation, a bounded unique boot job and an honest readiness/error report using existing state flows.

**Non-Goals:** a permanent watchdog, accessibility service, Wi-Fi reassertion or automatic settings changes without the corresponding consent. Boot tests must not mistake a manually launched app for successful automatic receiver delivery.

## Decisions

### Preparation and privilege boundary

Put the preparation disclosure in initial enablement before any shell-side auto-start mutation. After P1 starts the shell server, a manager-only operation reads the auto-start mode, grants AUTO_START for Tsuyu's resolved installed uid when supported, and reads it back. Use fixed package/operation inputs and validated system-resolved identity; client API callers cannot trigger preparation for other apps. Do not mark a command's zero exit status as grant proof without read-back.

Distinguish supported-and-allowed, supported-and-denied, unsupported and query-failed results. An unsupported vendor operation is not itself failure on a platform without that gate. It is also not proof that another vendor has no boot restrictions. Standard boot scheduling continues and real-TV validation remains a separate claim. Each preparation system call has a 5 s deadline and typed failure; status separates running, scheduling-enabled and verified prerequisite outcomes. Do not introduce WRITE_SECURE_SETTINGS merely to observe USB debugging or grant an unrelated capability.

### Optional global key-expiry action

After shell startup, offer a separate optional explanation/action reachable from setup and persistence details. Initial focus stays on keeping the current policy; enabling Tsuyu or preparing AUTO_START is not consent to this change. French copy explains that preventing expiry avoids renewed confirmation after a long powered-off period and affects all authorized devices, not only Tsuyu. Show the current verified setting; if it is already zero, display that expiry is already disabled rather than requesting a redundant mutation.

An explicit user activation calls a manager-authenticated, fixed-key shell operation to set `adb_allowed_connection_time` to zero and read it back. The shell server performs this narrowly scoped write using its existing system access; no app-side WRITE_SECURE_SETTINGS grant is needed just to run a boot job. This is simpler and exposes less additional app privilege than granting the manager broad settings-write access. Validate that write/read-back on the emulator and include it in Sygix's TV checklist. Never expose an arbitrary settings-write endpoint to clients.

Keep the current global policy untouched when the option is skipped, refused, dismissed or only viewed; never automatically reapply a historical opt-in after a later system change. Report denied/missing/timeout/read-back failures separately from service readiness and keep normal setup usable. Remember only the explicit operation's outcome for explanation, not a competing setting value: current system read-back is the authority. A local timeout is not proof that a remote mutation never happened; reconcile before retry and show unverified until its actual result is known.

### Boot job

Declare RECEIVE_BOOT_COMPLETED and a narrow receiver for system boot delivery. Schedule unique WorkManager work after normal boot, when app-private credential storage is available; avoid `LOCKED_BOOT_COMPLETED` until there is an expressed direct-boot need. WorkManager is preferred over a receiver-held coroutine because receiver/process lifetime is not a reliable launch budget. Do not use a generic foreground service or require an unmetered/internet-capable network for loopback traffic.

Persist completed enablement, the work attempt budget and the last typed startup outcome in the same app-private state authority. Unique work and P1's cross-request/shell singleton protections prevent duplicate starts after process death or simultaneous manual launch. Use at most three attempts, each bounded to P1's relevant operation deadlines, and an overall boot restart budget of five minutes tracked across work rescheduling. Stop permanent setup/auth failures instead of looping. A finite schedule cannot guarantee precise boot timing under OS deferral; readiness does not promise immediate execution.

The boot path uses the same endpoint validation, key and manager handshake as manual start. It does not open Tsuyu's activity or silently turn a failed renewed-key confirmation into approval. Background retries record results for the next home opening. Startup must establish a live shell-uid manager binder before recording success; P2 registers its observers and redistributes client binders. A persisted running Boolean is never liveness evidence.

Check current USB-debugging state before boot/manual startup. If the user disabled it, stop with setup-required status and offer the existing settings step; never write it back to enabled automatically. If the state is unreadable, preserve an unknown status and do not infer consent to modify it. Retry and reboot cannot override the user's system choice.

### Readiness and recovery

ViewModel combines live server state and typed persistence status into one immutable home state, with effect-oriented French labels such as “Démarrage au redémarrage” and distinct verified/unverified/error text. Details show actual completed preparation effects. Changes emit state updates; no idle refresh loop. Retry is user-driven, uses the existing coordinator and updates the last failure only after a current verified result. P1's recovery handles absent endpoint, key confirmation and settings; no alternate TLS flow is introduced.

### Provenance

| Origin | Kept | Removed / reason | Header |
| --- | --- | --- | --- |
| Shevery `manager/src/main/java/moe/shizuku/manager/receiver/BootCompleteReceiver.kt` | Reference pattern: boot receiver delegates startup | Code not copied; root, keyguard old-platform branches, auto-update, watchdog and Wi-Fi conditions are out of scope | New Tsuyu receiver is AGPL-3.0-or-later |
| Shevery `manager/src/main/java/moe/shizuku/manager/worker/AdbStartWorker.kt` | Reference pattern: unique scheduled work | Code not copied; pairing/mDNS/modules and unbounded repeated retry are not needed | New Tsuyu worker is AGPL-3.0-or-later |
| Shevery `manager/src/main/java/moe/shizuku/manager/adb/AdbArm.kt` | Observation that key expiry is a global setting | No copied code or wholesale settings write; Tsuyu changes only expiry on explicit opt-in and never restores disabled debugging | No derived file |
| Shizuku `server/src/main/java/rikka/shizuku/server/ShizukuService.java` | Observation of manager permission-grant pattern | No additional import; P3 preparation is Tsuyu-specific and grants only approved access | Existing P1/P2 provenance remains |

If implementation actually adapts source rather than the described clean rewrite, add its exact path, original copyright and Apache-2.0 provenance header here and in NOTICE before publishing. Keep all new Kotlin/AIDL/Gradle files under their required AGPL headers.

## Risks / Trade-offs

- [Vendor mode queries and worker scheduling differ across platforms] → deterministic fake tests plus emulator boot tests; Sygix confirms actual boot delivery from a stopped app process on the reference TV.
- [The emulator does not retain the reference TV's configured legacy endpoint] → restore simulated legacy TCP debugging after every emulator boot before judging the receiver/job; this is a fixture operation, never a Tsuyu product action. Prove the job starts without manually opening Tsuyu and also test delayed availability.
- [Long powered-off period invalidates key authorization when expiry remains enabled] → disclose the limitation and offer optional expiry disabling; retain renewed-confirmation recovery if declined, failed or later changed in system settings.
- [User force-stop blocks ordinary Android boot receivers] → report the standard limitation and recover when reopened; do not add an out-of-scope evasion layer.
- [Missing vendor operation on emulator] → treat it as unsupported in tests, not a successful emulation of vendor AUTO_START grant.

## Migration Plan

Existing P1/P2 users see the persistence explanation before preparation. There is no automatic historical grant migration. Stack on P2 and retain its consent/key storage. Real-TV validation and archive follow P1 → P2 → P3 after the corresponding merges; no tag or release is created by the agent.

## Open Questions

None. Optional expiry disabling and respect for manual debugging disabling are confirmed in proposal.md.
