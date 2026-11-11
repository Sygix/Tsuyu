# Android 9 compatibility verification

## Current status

Independent review of `e33956d` returned AFTER FIXES: a non-interruptible platform locale setter can finish after its caller times out. The corrective regression and full suite now pass. Independent targeted re-review of `bf5474a` marked the finding handled and found no new defect in the changed code. That approval covers the fix only, not the incomplete runtime gates.

The new non-interruptible-writer regression failed before correction because an authoritative read could still return the old value while the setter remained outstanding. The correction keeps a process-wide pending-write barrier across activity/gateway instances, does not cancel a dispatched setter on timeout, and requires a settled authoritative read before accepting another selection. An uncertain result removes selection confirmation, disables competing choices, gates other interactive activity content and offers retry/Back in all five languages. The focused language suite passed, including this regression, ViewModel reconciliation and D-pad confirmation/focus checks on API 28/33/34. The full test/build/lint command then passed with 169 app and 87 server tests (256 total), zero failures/errors/skips. One integration run lost its Gradle daemon; a successful rerun used one worker and a 1 GiB daemon heap. This is test execution evidence, not a diagnosis of the daemon loss.

Subsequent real-activity Compose Back tests passed on API 28/33/34: the production Back handler finishes Language without changing its confirmed choice. Activity `isFinishing` is a JVM lifecycle proxy; native Back and Home language-entry focus were separately observed on the API 28/34 fixtures. The full repository unit suite then passed with 172 app and 87 server tests (259 total), zero failures/errors/skips. The five resource sets contain the uncertainty/retry wording, and README describes the older-platform return behavior.

Implementation in progress. The owner deferred the physical-TV baseline before code changes and authorized implementation/emulator work on 2026-11-11. No new physical-TV result is claimed; tasks 1.1 and 5.4 remain pending.

## Fixture inventory

The initial installation contains Android 14 TV and generic system images. The SDK repository offers Android TV x86 images for API 28, 29, 30, 31 and 33. Their availability is not a runtime result; each installed/executed fixture will be recorded below.

The provider signatures are grounded in the reference documented in design section 2. Emulator local-ADB provisioning will be described as a fixture, not evidence that a consumer firmware exposes a local endpoint.

| API | Image availability | Execution |
| --- | --- | --- |
| 28 | Task-installed TV image removed for sequential testing; initialized AVD data retained | Release, languages, proxied shell handshake and full reference-client contract observed |
| 29 | Task AOSP x86_64 image and AVD cleaned after its pass | Provider/Binder, permissions, native UserService, remote process and missing-provider isolation observed |
| 30 | TV fixture could not authorize; alternate AOSP x86_64 fixture passed after operational recovery; cleaned | Provider/Binder, permissions, native UserService, remote process and missing-provider isolation observed on AOSP |
| 31 | Task AOSP x86_64 image and AVD cleaned after its pass | AttributionSource provider path and the same actual client/failure-isolation contract observed |
| 33 | TV image available, removed to keep fixtures sequential | No separate API 33 runtime; locale transition covered by API 33 unit tests and API 34 runtime |
| 32 | No fixture selected | Not executed |
| 34 | Pre-existing images retained; task-owned AOSP TV-profile AVD removed after its checks | Native migration, localized entries, shell server, authenticated caller return and repeated system-mode cold starts observed |

The intended emulator provisioning is host-side ADB to the emulator followed by enabling its legacy TCP listener. Authorization and manager self-connection must then be observed; provisioning alone does not establish successful local-start or reboot persistence. No physical Android 9 device is available. Physical Android 14 checks are deferred by the owner.

On the API 28 image, `adb tcpip` set the advertised property but did not create an observable guest TCP listener. For the binder/client smoke fixture, `adb reverse tcp:5555 tcp:5555` connected the manager's guest-loopback endpoint to the host-side emulator ADB transport. This exercises the same guest shell and Android APIs, but does not prove native local-ADB availability, RSA authorization dialogs or reboot persistence on consumer firmware. The reverse mapping was removed and the emulator stopped after the pass.

## Platform boundary inventory

| Area | Initial boundary | Required verification |
| --- | --- | --- |
| Module manifests | All modules require API 34 | Merged manifests and transitive dependency minimums |
| ProviderBridge | Unconditional API 31 attribution call; acquisition signature changed at API 29 | Acquisition/call/release on API 28, 29, 30 and 31+ |
| UserServices | Typed parcelable getter requires API 33 | Older getter with unchanged type/ownership validation |
| MainActivity | Launch identity requires API 34 | Guarded identity, absent named return on older versions |
| AppLanguageGateway | LocaleManager requires API 33 | Legacy persistence, localized activity resources and platform migration |
| ClientIdentity and update verification | SigningInfo requires API 28 | Preserved signature-based identity at baseline |
| Process lifecycle | Timed waits and forcible destruction available at baseline | Actual Android process/child ownership and cleanup |
| Observers/UserService construction | Hidden interfaces vary by platform | Runtime binder/client lifecycle and actionable failure |
| Persistence | Vendor operations and expiry setting are firmware-dependent | Unsupported/unknown distinct from verified readiness |
| AdbKeyStore | API 28 Conscrypt reloads an RSA private key without the CRT interface | Use the general RSA private-key interface; reconstruct only the existing required exponent and cryptographically verify the pair without rewriting the key |
| ClientIdentity.observe | API 28 rejects the system-context application thread as an app caller | Use throwing HiddenApi receiver registration with a null application thread, explicit shell package and manager user; preserve invalidation and bounded failure |

Resolved call-site inventory: the four module Gradle files now use 28 without manifest overrides; `MainActivity.resolveCaller` guards the API 34 launch identity; `AppLanguageGateway` isolates API 33 locales in `PlatformLanguage`; `ProviderBridge` selects the API 28/29/30/31+ signatures and uses the verified throwing helper for acquisition/release; `UserServices.component` guards API 33 parcelables. `ClientIdentity` retains the API 28 signing-certificate identity model and now registers its package watcher with the throwing helper. `ClientDelivery` uses the existing UID/process observer compatibility methods. `HiddenApiAccess`, `ServerMain` and `UserServiceMain` retain their isolated reflection boundary; the actual API 28 server and native UserService construction succeeded. `ProcessSession`/`OwnedProcess` keep their bounded wait/termination contracts; revocation killed the observed client and service. `AdbKeyStore` now handles the observed non-CRT private-key provider without rewriting its key. Static lint and resolved manifest checks found no remaining baseline blocker; later fixture checks remain runtime gates rather than assumed successes.

## Results

### API 28 initial runtime pass

The 3,103,525-byte release APK installed and opened successfully on the Android TV API 28 x86 emulator, with no Tsuyu crash observed. Selecting French before setup localized Home, the unavailable access screen, restart preparation and updates. Returning Home restored focus to Language. French remained after force-stop and cold launch. Selecting German localized the language screen; the German Home screen was not checked. This does not verify a live permission-request screen or the complete language matrix.

The fixture was configured with host-side `adb root` and `adb tcpip` for a legacy listener. An initial offline transition recovered, but the manager handshake was not attempted in this pass. No successful local-start, client binder delivery, reboot or physical-device result is inferred. A crash from the system TV application was observed separately; no causal relation to Tsuyu was established. The emulator was stopped after this pass.

### API 28 server and reference-client pass

Two genuine runtime incompatibilities were found and corrected without clearing private data: a cast to `RSAPrivateCrtKey` when reopening the ADB key, then system-context package-receiver registration rejected by ActivityManager. The RSA regression reproduced the cast failure before correction; it now checks identical exported public identity/signatures and unchanged stored bytes for a non-CRT private key. An incompatible exponent is rejected without replacing storage. Observer tests cover package-filter/user scope, cache invalidation on replacement and registration-failure behavior.

With the loopback transport fixture described above, the updated release loaded the existing key, launched a shell-UID server and reached the running Home state. The baseline-compatible Shizuku-API demo at tree `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5` received its binder. Its permission request appeared in German with Deny initially focused. Denying returned the refusal callback; allowing a fresh request enabled the demo's user-manager operation. Binding its native UserService delivered `onServiceConnected` and reported UID 2000 and x86 JNI execution. Revocation removed both the client and UserService processes. After client relaunch its binder arrived again and a privileged action required a new permission request, demonstrating that revocation was effective.

For subsequent checks, the external demo fixture gained two test-only probes: its system-properties button invokes the upstream private `Shizuku.newProcess` helper reflectively with fixed `sh -c 'id -u'`; its status label opens Tsuyu in the demo task with forged caller/referrer extras. No shipped code depends on either probe. The remote process returned UID 2000 and exit 0. After another force-stop/relaunch, the binder arrived again and the granted decision still allowed that remote process. The forged launch showed running Home without a named or replacement completion button, and Back returned to the demo's activity with its binder intact.

The client's native UserService was bound again successfully after those restart/return checks. The screenshot of the callback and UID was shown to the owner. These checks establish API 28 runtime behavior, not firmware availability of a local TCP endpoint.

The persistence screen reported both vendor startup modes unavailable, not granted. The global key-expiry setting read as absent (`null`), not zero. These are fixture outcomes, not physical-TV readiness claims.

### API 30 TV fixture limitation

The TV image installed both APKs and reached the authorization step with the reverse transport configured, but no system authorization prompt appeared. It did not start a server, so no provider, binder or UserService result is claimed for that fixture. The image also refused `adb root`, so preauthorizing only the fixture's public key was unavailable. No private key was copied or replaced. The temporary authorization helper was removed, the reverse mapping removed and the emulator stopped. A rootable AOSP image is the alternate provider/API fixture; it is not equivalent to a TV firmware test.

The alternate AOSP API 30 fixture initially could not allocate its required userdata partition. Task-owned completed API 34 and failed TV API 30 AVDs, plus the task-installed API 28 image, were removed to free space; the API 28 AVD data and original key were retained. After allocation was possible, the emulator itself hung and exited with segmentation fault (139) before Tsuyu installation. This is a host/emulator fixture failure, not a Tsuyu crash, and the API 30 provider path remains unverified. No further repeated launch is claimed as a result; the emulator process exited and the heavy lock was released.

### Intermediate provider runtime matrix

The initial API 30 emulator crash was resolved operationally after stopping confirmed idle Gradle daemons and unused Kotlin analysis servers, using the supported software renderer with Vulkan disabled and a TV-sized 720p display. A single cause for the earlier crash is not asserted. Each launch required at least 3 GiB of available host memory; boot monitoring stopped on unsafe pressure. The host OOM counter was four before these controlled runs and remained four afterwards. No build ran alongside an emulator.

| API | Runtime path | Observed manager/client result | Failure isolation |
| --- | --- | --- | --- |
| 28 | TV x86; package-only provider call; proxied loopback transport | Shell server, Binder delivery, permission callbacks, remote process and native UserService UID 2000 | Refused/revoked operations failed; package/type validation and missing-provider/release paths additionally covered by unit tests |
| 29 | AOSP x86_64 TV-profile; package + authority; proxied loopback transport | Manager handshake, demo Binder, grant, native x86_64 UserService callback UID 2000 and remote process UID 2000/exit 0 | Missing-provider client reported with recovery guidance while the demo continued remote processes and fresh UserServices |
| 30 | AOSP x86_64 TV-profile; package + attribution tag + authority; proxied loopback transport | Same observed shell/Binder/UserService/remote-process contract as API 29 | Same observed missing-provider isolation as API 29 |
| 31 | AOSP x86_64 TV-profile; AttributionSource + authority; native guest loopback listener | Same observed shell/Binder/UserService/remote-process contract as API 29, without a reverse mapping | Same observed missing-provider isolation as API 29 |

The fault fixture is a separate no-code APK requesting API_V23 but declaring no Shizuku provider. It uses only a framework Activity to trigger process observation. Its missing delivery appeared by name in app access with recovery guidance; it never gained a privileged Binder. The working demo remained authorized and returned UID 2000 after the fault was reported. These are actual platform smoke checks, not inferred unit-test equivalence. Native release/return and locale evidence on API 34 remains a separate pass below.

Only one task-added intermediate image was installed at a time. After each pass, its emulator exited, its task AVD was deleted and its SDK image uninstalled. API 31's zero-filled image extents were made sparse to reclaim space; SHA-256 before/after confirmed identical image bytes. Final intermediate cleanup restored approximately 11 GiB free and released the heavy lock. No physical-TV or native firmware authorization claim is implied.

### API 34 independent runtime pass details

The initial Tsuyu APK hash was `f90bdf0fc8b53505f43c82f04a260d93caf57ab54cce8afbb9b9dfcc02084bfd`. A dedicated AOSP API 34 x86_64 image used the TV display profile; this is not TV firmware. Only the generated legacy language preference was transferred from the API 28 fixture before first launch, with matching app ownership; no private key or token was copied. After unrooting the fixture, first launch migrated German to the platform locale `[de]` and rendered German Home. French selection then localized Language, Home, access management, persistence, updates and the real client permission request. Back from Language restored the Home language-entry focus. Deny initially held focus in the French permission request and returned the demo's refusal callback.

Without a reverse transport, the manager reached running state and the server process had shell UID. The demo received its binder. Opening Tsuyu from the demo with real platform identity produced the named return using the demo label; D-pad activation returned to the demo, with no automatic return. After installing the corrected release over the same fixture, selecting system mode changed the language to English. Two separate force-stop/cold-launch checks both retained English and an empty platform app-locale list; the original German preference was not reapplied. Screenshots were shown to the owner and the emulator was stopped. This native pass does not simulate a stalled Binder setter; that case is covered by the non-interruptible regression.

### API 28 update and boot follow-up

The real authenticated manager-to-server installer accepted a same-signature replacement from versionCode 1 to 299. A test-only instrumentation fixture compared private-file fingerprints internally: private ADB key, saved German language and client consent remained unchanged. No private key material was exported. The authorized demo subsequently returned remote UID 2000/exit 0 without a new grant. The real installer rejected an incorrect digest with DIGEST, a lower version with INSTALL_FAILED_VERSION_DOWNGRADE and an incompatible signer with INSTALL_FAILED_UPDATE_INCOMPATIBLE; the installed version remained 299.

A stalled input pipe exposed a reproducible product defect: after the 60-second process deadline, the installer output-reader thread threw an uncaught InterruptedIOException and Android terminated the shell server. The corrected reader uses FutureTask so worker failures are delivered to the owning operation instead of Android's uncaught exception handler, including failures after process destruction. Two regression tests failed against the old reader and passed with the correction: ordinary output-read failure reports INSTALLER/InterruptedIOException and abandons, while timeout-induced read failure retains TIMEOUT, abandons and produces no uncaught reader failure.

For the corrected native pass, a same-version replacement installed the corrected 299 test build without resetting any data. A real stalled-pipe install then returned TIMEOUT in 62.7 seconds under shell UID 2000, with installed version 299. Internal comparisons again confirmed unchanged key/language/consent. A subsequent authenticated install accepted the corrected same-signature version 399. PackageManager confirmed 399, and all three comparisons remained true. Successful self-replacement terminates the instrumented manager process, so the instrumentation's process-terminated message is not itself treated as a product crash; installed-version readback and subsequent operation establish the result. This exercises the server installer directly, not an end-to-end updater UI download; application reconciliation remains covered by its unit tests.

The updated API 28 fixture then underwent an actual reboot with no launch of the manager UI during boot. The reverse mapping was absent after reboot. Boot delivery started the manager process and an automatic startup failure was observed while the endpoint was unavailable. Restoring the reverse mapping let the scheduled retry launch a shell server before the UI was reopened. Home subsequently reported automatic startup success, and the demo received its Binder and returned remote UID 2000/exit 0. Version 399 and unchanged key/language/consent were confirmed again. The attempt to remove the manager with am kill before reboot did not remove its process; therefore that separate pre-reboot-absence precondition is not claimed.

Task 5.3 remains partial: permanent endpoint unavailability through budget exhaustion, refused renewed firmware RSA authorization, and actual debugging disable/re-enable were not established on this proxied fixture. The delayed-endpoint boot result is not firmware authorization or adbd-cgroup evidence. No physical-TV check is implied. The emulator was stopped, the heavy lock was free and the host OOM counter remained four.

After the correction, the full `./gradlew test lint assembleRelease` command passed with 172 app and 89 server tests (261 total), zero failures/errors/skips. The three real-activity Back cases are included. No build ran concurrently with the emulator.

### Interim OpenSpec verification

| Dimension | Result |
| --- | --- |
| Completeness | 19/22 tasks checked; all five added/modified requirements have implementation mappings; remaining tasks below block archive |
| Correctness | Static/unit mapping covers all five added/modified requirements; provider paths have API 28/29/30/31 evidence, language/caller behavior API 28/34 evidence, and signed install/rejections/timeout plus delayed-endpoint reboot API 28 evidence. Scenario coverage is partial: task 5.3 and physical regression remain unverified |
| Coherence | Version-specific provider/locale references are isolated; throwing compatibility helpers, bounded operations, signature identities and private key storage remain in use; no reference source was copied |

Critical outstanding tasks: 1.1 (deferred physical baseline), 5.3 (partial reboot/recovery runtime matrix), and 5.4 (deferred physical regression). Complete each unchecked task in `tasks.md`; do not infer completion from its code mapping. This report is interim and does not authorize archive or merging.

Verification/provenance check: tracked Kotlin/Java/AIDL files all have SPDX headers; no new origin project was incorporated, so NOTICE requires no change. The public diff scan found no local paths or address/version candidates. `openspec validate --all --strict` passed all eight items. The incomplete scenario matrix above remains explicitly unapproved rather than being presented as complete implementation acceptance.

Coverage warnings: the actual API 28 authorization dialog was not verified with the proxied transport. The failed API 30 TV authorization path remains a separate unavailable fixture; subsequent AOSP success is not a claim about that TV image. Physical-TV and reboot scenarios retain the limits listed above; installer coverage is direct server-operation evidence rather than updater UI evidence.

### Static and unit checks

- `./gradlew test assembleRelease :app:lintRelease :server:lintRelease`: passed after the RSA and observer adaptations. Full unit reports contain 164 app and 87 server tests, zero failures/errors/skips. The release APK built successfully after adding specific hidden provider/receiver classes to the existing R8 warning declarations; no stub class is packaged as a substitute for a platform implementation. This run includes the additional multi-version navigation tests.
- README, AGENTS and OpenSpec runtime context now describe minimum API 28 and firmware-dependent eligibility. README documents the older-platform Back behavior and private/platform language ownership. Current requirement files under `openspec/specs` were not edited.

- All four `lintRelease` tasks passed after the compatibility edits. The XML reports contain no errors/fatal issues and no `NewApi` or `InlinedApi` issues. Existing warnings outside those categories remain (13 app, eight server); lint cannot establish hidden-API runtime behavior.
- Focused server reports: 20 `ProviderBridgeTest` cases across API 28/29/30/31/34 and eight `UserServicesTest` cases across API 28/34 passed. Provider tests exercise the real runtime interface signatures under Robolectric, shell identity/payload, acquisition failure, missing provider and release after success/failure. Actual binder smoke checks succeeded on API 28/29/30/31/34 as recorded above.
- Focused app run passed with 32 cases: language gateway/store/migration/loading/navigation, localized activity resources and guarded caller lookup. Real DataStore reopening preserves each explicit language and system mode. Legacy write failure keeps the confirmed choice; pending load/save and timeout/retry are covered. Migration tests preserve an existing platform choice and do not reapply the legacy choice after returning to system mode.
- The caller regression first failed with `NoSuchMethodError` on API 28 before adding the API 34 guard, then passed on API 28 and 33 with forged extras/referrer. Native task-stack Back on API 28 and authenticated API 34 runtime return were subsequently observed as described above.

- `:app:processReleaseMainManifest :server:processReleaseManifest :api:processReleaseManifest :ipc:processReleaseManifest`: passed with the resolved dependencies. All four merged manifests declare minimum 28; the app retains target 37, and all module build files retain compile 37. No manifest minimum override was added.

- `:server:testDebugUnitTest --tests fr.sygix.tsuyu.server.UserServicesTest`: eight tests passed across API 28 and 34 after guarding the typed parcelable getter. Before the fix, all four API 28 tests failed with `NoSuchMethodError`, while the API 34 cases passed. Coverage includes valid service lifecycle, type/package/class-name rejection and cleanup without holding the global registry lock. These are Robolectric results, not device results.
- The installed HiddenApi compat 4.4.0 binary exposes throwing `getContentProviderExternal(String, int, IBinder, String)` and `removeContentProviderExternal(String, IBinder)` methods, checked using `javap`.
- Disassembly of that acquisition helper confirms the three-argument platform call on API 28 and the four-argument call on API 29 and later.
- Inspected cached AAR manifests declare minimums at or below API 24. This cache-wide check is supplementary to the completed resolved release merged-manifest verification.
