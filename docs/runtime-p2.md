# P2 release runtime evidence

One tester invocation prepared the pinned upstream demo and emulator fixtures. The main agent continued that same combined P2/P3 pass after the tester reached its step limit, then performed targeted checks for the defects found in the release build. No real TV was accessed.

## Builds

- Upstream Shizuku-API snapshot: `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5`, library 13.1.5, server API 13 / patch 6.
- Demo: `demo-shizuku-api13.1.5-debug.apk`, SHA-256 `2ba45bedb6e7d2d37ac3e75680047423219fd60458fc9d2e04aaefd11ab463ea`.
- External protocol probe uses the unchanged upstream client library and AIDL. It is test material, not bundled into Tsuyu. SHA-256 `28b71c48978d52592dd78daae88345ef6065add75e949d85af3f84a723c7b127`.
- Final combined release code: P2 `8bdf1b94f0d1d2bfe5ff347dcb1c27e74669e7db`, stacked P3 `e51f3018e9c4f0859d187494be309309205e1414`. APK SHA-256 `2ce03abff23e42d42b731e2fae082b67912dee817f8fcc30df255e3014b964ce`, 2,117,897 bytes. Development signing fallback only; not the production CI signature.

## Observations

| Scenario | Observed result | Status |
| --- | --- | --- |
| Upstream demo startup/later foreground delivery | “Binder received”; actual IUsersManager result after consent | PASS |
| Consent and caller return | Refusal initially focused; explicit refusal returns to demo; explicit grant returns and enables the operation | PASS |
| Upstream refusal rationale | The demo reports refusal; its own rationale branch intentionally does not request again | PASS; fresh demo identities were used for subsequent grant/reboot fixtures |
| Native no-argument UserService | Demo reports shell UID 2000 and successful JNI loading on the emulator ABI | PASS |
| Revocation while native service is bound | The shell service process disappears; the persisted grant is removed | PASS |
| Non-launchable requesting client | Probe has no MAIN/launcher intent; its verified name/icon are available in Tsuyu's permission screen | PASS |
| Process-start delivery in background | After terminating only the probe, an explicit background receiver starts a new process and receives a shell binder without opening an activity | PASS |
| API metadata/permission/property | Version 13/patch 6; actual UID 2000, nonempty context; granted and denied permissions match the shell's package permissions; property fallback and upstream null default work | PASS |
| Failure propagation | Missing executable and forbidden read-only property mutation raise IPC errors; the server remains usable | PASS |
| Complete remote-process basics | `id -u` returns 2000; stdout/stderr, waits, exit value, liveness and explicit destruction | PASS |
| Stdin EOF | Write to `cat`, close client stdin, observe completion and matching output | PASS |
| Uncooperative child | A child explicitly ignoring SIGTERM still terminates through owned-process destruction | PASS |
| Transaction 1 | Payload/code and synchronous reply reach the target under UID 2000; oneway flags and identity reach the target | PASS |
| Administrative gating/non-Sui | Ordinary client cannot exit the server; `isHidden` is false | PASS |
| Context UserService | Constructor receives initialized application context and the requesting package; service runs as shell | PASS |
| Peek/remove lifecycle | Existing peek returns version 7; upstream null-connection removal succeeds; death callback arrives; absent peek returns -1 | PASS |

The probe ended with `ALL PROTOCOL ASSERTIONS PASSED`. Demo and probe evidence are distinct; probe success is not attributed to the upstream demo.

## Corrections driven by runtime evidence

- Upstream removal passes a null unused connection; Kotlin endpoints now accept it for removal, with a red/green regression.
- Permission/list body text previously inherited an unreadable foreground; its content color is corrected and guarded by a red/green text-layout assertion.
- Android asynchronous Binder calls supply PID zero. Transaction 1 now uses a live API-13 attachment for the authenticated consenting UID only in that case. Sync and other endpoints retain PID lookup. This boundary is UID-scoped under the accepted shared-UID contract. The denied/unattached UID regression guards against target effects.
- Java Android process destruction is not sufficient proof for a child ignoring SIGTERM. Owned PID SIGKILL and bounded confirmation are now required; failed cleanup stays retryable.
- Context construction now follows upstream default-Application initialization. Null property defaults and checked Binder failures retain usable upstream results.
- Revocation is distinct from a persisted user refusal: it clears the decision so an unchanged client can request consent again. The corresponding regression failed with the previous denied-record behavior. The last correction is unit-tested; prior live revoke/termination proof remains separate from re-request behavior.

## Remaining proof

Full failure/interleaving coverage is still incomplete: provider saturation/isolation, callback-queue saturation, every malformed parcel, storage/identity timeout combinations, all UserService debug/ABI/version/daemon permutations, and every activity/focus disappearance case. The method map and open tasks retain these limits. SygixOs has no tested API-client build in this pass.

TV proof is reserved for Claude's single vendor/hardware session. Nothing here completes a TV task. Screenshots were sent directly to the owner. Emulator and build processes were stopped after verification.

## Supervised-process correction verification

The final combined correction APK has SHA-256 `6a798eb5b43c41b7ab11059f1524e7c1f3e7679f20ab0c70ae95b45539d803ff`. It uses the existing development signing fallback, not the production key. This evidence supersedes the earlier direct-PID cleanup limitation for the scenarios below; the older artifacts and observations remain historical.

| Check | Observed result | Scope |
| --- | --- | --- |
| Unchanged external protocol client | `ALL PROTOCOL ASSERTIONS PASSED`: metadata, actual shell identity, process streams/EOF/waits/exit/destruction, synchronous missing executable, property failure, Binder forwarding, Context construction and peek/remove/death callback | Final combined APK, actual authorized API client |
| Actual upstream demo native service | Connected service reports UID 2000 and successful JNI loading; revoking the demo removes its client, native service and supervisor and restores focus to the next row | Combined correction APK before the final expected-stdin-close logging-only adjustment |
| Owner-process death | Force-stopping the external API client leaves no owned supervisor after bounded state polling | Final combined APK |
| Session and target lifetimes | A separate disposable Android adapter probe passes stdin/stdout/stderr/EOF, exit status, missing and non-executable targets, a background descendant surviving target-leader exit until explicit destruction, and three immediate destructions | Direct adapter test, not API authorization proof; same supervisor/launcher code as the final APK |
| Abrupt owner death | Killing the adapter-owner process closes control; its known target and descendant disappear and no adapter supervisor remains | Direct adapter test; not a claim of an additional full API/server-death scenario |

The initial tester pass found that Android's timed `LocalSocket.connect` overload throws `UnsupportedOperationException`. The corrected adapter and existing-server handoff use the implemented overload under a bounded infrastructure call. Runtime additionally exposed startup-pool handoff rejection and supervisor descriptors retaining pipe EOF; a bounded startup queue and explicit native descriptor closure correct those failures. The final client run has no unexpected stream-error report during normal process completion.

The adapter probe is external test material and is not bundled into Tsuyu. A temporary probe-fixture use of an unavailable Java file-reading method was corrected separately; that fixture failure is not attributed to the app. The emulator, test owners and build daemons were stopped. Useful screenshots were sent directly to Simon.

Remaining boundaries: real-TV behavior, actual Binder-GC/backpressured-output release, exhaustive hostile/death permutations and a complete API-level descendant-revoke/abrupt-server-death matrix are not inferred from these checks.
