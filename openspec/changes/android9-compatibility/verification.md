# Android 9 compatibility verification

## Current status

Implementation in progress. The owner deferred the physical-TV baseline before code changes and authorized implementation/emulator work on 2026-11-11. No new physical-TV result is claimed; tasks 1.1 and 5.4 remain pending.

## Fixture inventory

The initial installation contains Android 14 TV and generic system images. The SDK repository offers Android TV x86 images for API 28, 29, 30, 31 and 33. Their availability is not a runtime result; each installed/executed fixture will be recorded below.

The provider signatures are grounded in the reference documented in design section 2. Emulator local-ADB provisioning will be described as a fixture, not evidence that a consumer firmware exposes a local endpoint.

| API | Image availability | Execution |
| --- | --- | --- |
| 28 | Android TV x86 image installed; dedicated 1080p AVD created | Release installed and launched; language smoke checks passed; local-ADB handshake pending |
| 29, 30, 31, 33 | Android TV x86 images available in SDK repository, not installed | Not executed |
| 32 | No fixture selected | Not executed |
| 34 | Android TV x86 and generic x86_64 images already installed | Not executed for this change |

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

### Static and unit checks

- `./gradlew test assembleRelease :app:lintRelease :server:lintRelease`: passed after the RSA and observer adaptations. Full unit reports contain 164 app and 87 server tests, zero failures/errors/skips. The release APK built successfully after adding specific hidden provider/receiver classes to the existing R8 warning declarations; no stub class is packaged as a substitute for a platform implementation. This run includes the additional multi-version navigation tests.
- README, AGENTS and OpenSpec runtime context now describe minimum API 28 and firmware-dependent eligibility. README documents the older-platform Back behavior and private/platform language ownership. Current requirement files under `openspec/specs` were not edited.

- All four `lintRelease` tasks passed after the compatibility edits. The XML reports contain no errors/fatal issues and no `NewApi` or `InlinedApi` issues. Existing warnings outside those categories remain (13 app, eight server); lint cannot establish hidden-API runtime behavior.
- Focused server reports: 20 `ProviderBridgeTest` cases across API 28/29/30/31/34 and eight `UserServicesTest` cases across API 28/34 passed. Provider tests exercise the real runtime interface signatures under Robolectric, shell identity/payload, acquisition failure, missing provider and release after success/failure. Actual binder smoke checks remain pending.
- Focused app run passed with 32 cases: language gateway/store/migration/loading/navigation, localized activity resources and guarded caller lookup. Real DataStore reopening preserves each explicit language and system mode. Legacy write failure keeps the confirmed choice; pending load/save and timeout/retry are covered. Migration tests preserve an existing platform choice and do not reapply the legacy choice after returning to system mode.
- The caller regression first failed with `NoSuchMethodError` on API 28 before adding the API 34 guard, then passed on API 28 and 33 with forged extras/referrer. Task-stack Back and authenticated API 34 runtime return remain pending.

- `:app:processReleaseMainManifest :server:processReleaseManifest :api:processReleaseManifest :ipc:processReleaseManifest`: passed with the resolved dependencies. All four merged manifests declare minimum 28; the app retains target 37, and all module build files retain compile 37. No manifest minimum override was added.

- `:server:testDebugUnitTest --tests fr.sygix.tsuyu.server.UserServicesTest`: eight tests passed across API 28 and 34 after guarding the typed parcelable getter. Before the fix, all four API 28 tests failed with `NoSuchMethodError`, while the API 34 cases passed. Coverage includes valid service lifecycle, type/package/class-name rejection and cleanup without holding the global registry lock. These are Robolectric results, not device results.
- The installed HiddenApi compat 4.4.0 binary exposes throwing `getContentProviderExternal(String, int, IBinder, String)` and `removeContentProviderExternal(String, IBinder)` methods, checked using `javap`.
- Disassembly of that acquisition helper confirms the three-argument platform call on API 28 and the four-argument call on API 29 and later.
- Cached AAR manifests inspected so far declare minimums at or below API 24. This cache-wide check is supplementary; resolved merged-manifest verification remains pending.
