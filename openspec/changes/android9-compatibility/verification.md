# Android 9 compatibility verification

## Status

18 of 22 tasks are verified. Open: 1.1 (physical Android 14 baseline, deferred by the owner), 4.5 (the non-French rendering check added by the specification review), 5.3 (partial reboot/recovery matrix) and 5.4 (physical Android 14 regression, deferred). No physical Android 9 device has been tested. Emulator and AOSP fixtures establish platform API behavior, not consumer firmware support.

## Fixtures

| API | Fixture | Transport | Observed |
| --- | --- | --- | --- |
| 28 | Android TV x86 image | Proxied guest loopback to the emulator's adb | Release install, languages, shell server, reference client contract, signed updates, delayed-endpoint reboot |
| 29 | AOSP x86_64, TV display profile | Proxied guest loopback | Provider/Binder delivery, permissions, native UserService, remote process, missing-provider isolation |
| 30 | AOSP x86_64, TV display profile | Proxied guest loopback | Same contract as API 29. The Android TV API 30 image never showed the authorization prompt and is recorded as an unavailable fixture |
| 31 | AOSP x86_64, TV display profile | Native guest loopback listener | Same contract as API 29 through the AttributionSource provider path |
| 32, 33 | Not executed | | API 33 locale transition covered by unit tests; no runtime claim |
| 34 | AOSP x86_64, TV display profile | Native guest loopback listener | Locale migration and system mode, localized entries, shell server, authenticated caller return |

On API 28 `adb tcpip` did not create a guest TCP listener, so a reverse mapping connected the manager's loopback endpoint to the emulator's adb transport. This exercises the same guest shell and Android APIs but not native local-ADB availability, the firmware RSA authorization dialog or vendor boot behavior.

## Platform boundaries

| Area | Boundary | Resolution and evidence |
| --- | --- | --- |
| Module manifests | All modules required API 34 | All four merged manifests declare minimum 28 with target 37; no manifest override |
| ProviderBridge | Acquisition signature changes at API 29, attribution at 30, AttributionSource at 31 | Version-specific calls; 20 Robolectric cases on API 28/29/30/31/34 and runtime delivery on API 28–31 and 34 |
| UserServices | Typed parcelable getter | Typed getter from API 34 only (the API 33 platform getter is unreliable), deprecated getter with type check below |
| MainActivity | Launch identity requires API 34 | Guarded; below 34 no named return and Back only; forged extras/referrer ignored on API 28 and 33 |
| Languages | LocaleManager requires API 33 | Private store below 33, one-time migration to the platform setting from 33; a corrupt or unknown stored value falls back to the system language |
| Update verification | Archive certificates are collected on API 28 only with `GET_SIGNATURES` | Both flags requested below API 29 so the signer comparison still applies |
| AdbKeyStore | API 28 Conscrypt reloads the RSA key without the CRT interface | General RSA interface; the existing key is verified and never rewritten |
| Package watch | API 28 rejects the system-context application thread | Throwing hidden-API registration with a null thread; a registration failure is logged and identities are then resolved on every call |
| Boot persistence | `appops --uid` does not exist before API 29 | Uid mode addressed by numeric uid below API 29, `--uid <package>` from 29 |

## Results

**Reference client on API 28.** With the existing key, the release launched a shell-uid server and reached running Home. The Shizuku-API demo received its binder; its permission request appeared localized with « Refuser » focused; refusal returned the denial callback; a fresh grant enabled privileged calls; the native UserService reported uid 2000; revocation stopped the client and service processes; after relaunch the binder arrived again and a privileged call required a new grant. A remote process returned uid 2000 and exit 0. A forged-caller launch showed no named return and Back returned to the demo with its binder intact.

**Provider matrix (API 29–31).** Each fixture delivered the binder, honored grants, ran a native UserService and remote process as uid 2000, and reported a client declaring the permission without a provider by name with recovery guidance while the demo kept working.

**Languages and caller return (API 28 and 34).** French and German localized Home, access, persistence, updates and the permission request on API 28 and survived a cold start. On API 34 a legacy German choice migrated once to the platform setting; system mode then survived two cold starts without reapplying German. Opening Tsuyu from the demo with a shared identity produced the named return, activated with the D-pad.

**Signed updates (API 28).** The authenticated server installer accepted same-signature upgrades to versions 299 and 399 and rejected a wrong digest, a downgrade and a different signer. The adb key, language and client consent were unchanged after each replacement. A stalled installer pipe returned TIMEOUT without terminating the server, after the reader failure fix (regression tests fail with the old reader). This exercises the server installer directly, not the updater UI.

**Reboot (API 28).** After a real reboot with the endpoint unavailable, boot delivery started the manager and recorded a startup failure; restoring the endpoint let the scheduled retry launch the server before the UI was reopened, Home reported automatic startup success and the demo regained its binder. Not established: manager absence before reboot, endpoint unavailable until the budget is exhausted, a refused renewed authorization, and debugging disabled then re-enabled (task 5.3).

**Persistence readiness.** On API 28 both vendor startup modes were reported unavailable rather than granted, and the global key-expiry setting read as absent. The fixture has no vendor `AUTO_START` operation; the numeric-uid form for older firmware is covered by unit tests only.

## Automated checks

- `./gradlew test lintDebug assembleRelease` passes on the current head (app 191, server 97 tests); `openspec validate --all --strict` passes.
- Regression tests added after independent review, each failing when its fix is reverted: the language gate routes Back to the activity's handler instead of finishing; a corrupt language file, an unknown stored language and an unreadable legacy store never block the screens; the Android 9 uid mode uses a numeric uid; a session-creation read failure is an installer outcome; a package-watch registration failure does not stop startup.
- Leaving the language gate of a pending permission request sends a denial to the server (`AccessLanguageGateTest`, fails when the permission screen's handler is removed).
- Verified by review only, without a test that fails when they are removed: the language screen staying usable while a write is uncertain, and the guard that skips recreation of a finishing activity.
- Known limit: if the legacy language store stays readable while every write fails on Android 13 or later, an unrecorded migration can be applied again at the next screen.
