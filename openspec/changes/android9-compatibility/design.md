# Design

## Context

See `proposal.md` for the scope. The audited base is `40669b6731ea3c93c9f8f4a795f574f065f9a634`. All four modules require API 34. The app directly uses `LocaleManager` and `Activity.getLaunchedFromPackage`; the server directly uses an API 31 provider-call signature and API 33 typed parcelable reads. Existing consent and installation certificate checks already fit API 28.

`local-start`, `shizuku-api`, `client-access`, `boot-persistence` and `self-update` remain the behavioral authorities. New compatibility code must preserve their errors and bounded waits instead of treating a platform-version branch as permission to simplify them.

## Goals / Non-Goals

**Goals:** small compatibility boundaries, one authoritative locale choice per platform path, stable public Shizuku wire behavior, and verifiable evidence for each platform branch.

**Non-goals:** distribution behavior belongs to the subsequent change. Firmware support beyond observed local ADB and boot capabilities is not inferred from SDK level.

## Decisions

### 1. Baseline and dependencies

Set minSdk 28 in `app`, `server`, `api` and `ipc`, retaining compileSdk/targetSdk 37. Do not force a library manifest override. The declared WorkManager/DataStore Android AARs require API 24; the checked TV Material, Core, Activity and Lifecycle AARs require API 23. Complete transitive resolution and lint are implementation acceptance checks, not a claim that every library has already been verified.

Shizuku-API 13.1.5 declares minSdk 24 at upstream tree `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5`; Shizuku declares minSdk 24 at `b844bc491f1790c72328e1a8e5b2349f8978f0ea`. API 28 is consistent with those reference minimums, but their implementations do not validate Tsuyu's rewritten server automatically.

### 2. Provider compatibility boundary

Keep `ProviderBridge` responsible for bounded provider acquisition/call/release. Use the existing Rikka hidden compatibility dependency for acquisition and release, with its throwing APIs, and an isolated version-selected call adapter. The published reference source exposes these branches:

| API | External acquisition | Provider call identity arguments |
| --- | --- | --- |
| 28 | authority, user, token | calling package |
| 29 | authority, user, token, tag | calling package, authority |
| 30 | authority, user, token, tag | calling package, attribution tag, authority |
| 31+ | authority, user, token, tag | AttributionSource, authority |

Method, argument and extras follow those identity arguments. Use the real shell identity and current user, never client-supplied attribution. Isolate references to `AttributionSource` behind the API 31 boundary so an older runtime does not load them on startup. Release an acquired provider in `finally`; a missing provider retains `ProviderUnavailable`, while invocation failures remain distinct from absence. The existing Deadline ownership remains unchanged.

Reference: `RikkaW/HiddenApi` tree `08db66c03e0b9470bfae344b9798471cd4ccf614`, `compat/src/main/java/rikka/hidden/compat/ActivityManagerApis.java` and `stub/src/main/java/android/content/IContentProvider.java`. The installed dependency version must expose the chosen APIs before use. This is API usage, not planned source copying. Any source adaptation requires its actual license/provenance header and NOTICE review before commit.

### 3. Other server boundaries

Replace the API 33-only read in `UserServices.component` with a guarded compatible parcelable read, preserving type, owning-package and class-name validation. Audit `ClientDelivery`, `ClientIdentity`, `HiddenApiAccess`, `UserServiceMain`, `ServerMain`, package watching and process ownership for hidden signature changes. Keep the signature-set identity model introduced by API 28; do not change consent interpretation or the upstream API version.

The existing timed process waits and forcible destruction are available at this baseline. Runtime tests must still check actual child ownership, revocation and process cleanup; successful JVM tests do not establish Android hidden-API behavior.

### 4. Language ownership and lifecycle

Retain `AppLanguageGateway` as the interface and split its platform implementation by API level. API 33+ uses `LocaleManager`; API 28–32 owns the selected language in app-private preferences. Reuse the existing DataStore dependency behind a language store and load its confirmed value through a bounded startup state before rendering interactive activity content.

Use a shared activity-language integration boundary to apply a localized configuration context to activity/Compose resource consumers, not only a visible label. The context, configuration and Compose resource environment must agree. Apply this to home, language, permission, persistence and update activities. Recreate affected activity content on a committed change and restore focus; do not perform language I/O inside composables. Selection failure retains the previous confirmed value and exposes retry/Back through the ViewModel.

On API 33+ after an OS upgrade, migrate a legacy explicit preference once only when the platform has no explicit application locale. Record completion so later platform choices, including returning to system mode, are not overwritten. Existing API 33+ installations continue using the platform as their source of truth. System mode reads the unmodified system configuration, not the already localized app context.

The legacy store and platform integration require targeted lifecycle tests: cold process, pending load, failed persistence, activity recreation, permission entry and migration. A locale context that changes only home fails acceptance.

### 5. Caller return

Guard `MainActivity.resolveCaller` at API 34. Below that boundary return no named identity and leave normal Back/task behavior intact, as explicitly selected by the owner. Do not read extras/referrer as an identity fallback and do not add a replacement completion button. On API 34+ retain existing platform identity and focus behavior.

### 6. Device assumptions and validation

Before code, capture the reference TV's current startup/client/boot baseline and the available emulator/API fixtures. Treat usable local ADB, boot delivery and optional vendor operations on other firmware as unverified. API 28 runtime tests run after the compatibility build exists; that emulator fixture establishes API behavior only. No physical Android 9 TV is currently available, and this limitation must remain explicit.

| Evidence layer | Required checks | What it proves |
| --- | --- | --- |
| Unit/lint | Locale decisions and failure states, type/ownership validation, guarded APIs, dependency minimums | Logic and static API correctness |
| API 28 runtime | Manager startup, provider handoff, demo permissions/process/UserService, update, reboot | Baseline runtime behavior on the recorded fixture |
| API 29 and 30 boundaries | Provider call/acquisition, binder delivery and failure isolation | Each older provider signature |
| API 31 boundary | Attribution-based provider handoff | New provider signature path |
| API 33 boundary | Platform locale selection and migration | Locale API transition |
| TCL Google TV running Android 14 | Full existing client/start/persistence regression and caller return | Existing physical-device regression |
| Later Android smoke | Launch, language, binder and package replacement when an image is available | Observed later-runtime coverage only |

Unavailable test images are recorded as gaps; the corresponding branch is not called runtime-verified. Physical TV-only claims stay blocked until community evidence exists. Each failure records its stage and recovery, not personal device output. Newer client apps that themselves require a newer OS are not used as proof of Tsuyu's API 28 failure; use a compatible Shizuku-API demo for that baseline.

### 7. Existing persistence and updates

Retain `PersistenceSnapshot.schedulable` semantics when vendor AUTO_START is unsupported. An absent expiry setting is unknown/unavailable, not observed disabled expiry. Verify the existing PackageInstaller flow on API 28 with increasing signed versions and bounded outcomes. Preserve the app-private key and all matching consent; do not introduce a reset to make a compatibility test pass.

## Risks / Trade-offs

- [Hidden API signatures differ from SDK stubs or vendor implementations] -> Exercise every provider branch on runtime fixtures; report actual failure and stop a claimed support path rather than silently falling back to unsafe identity.
- [Language choice is applied too late or only to Compose] -> Gate first interactive content on the confirmed state, cover all activity entry points and test resource configuration plus focus after recreation.
- [Older firmware lacks local ADB or boot delivery] -> Preserve actionable unavailable states and distinguish runtime/API evidence from physical compatibility.
- [A test client has its own higher minimum] -> Use a baseline-compatible reference client; use SygixOs on the Android 14 reference TV where supported.

## Migration Plan

1. Preserve package identity and the existing signing certificate; install a newer signed build over an existing configured installation.
2. Verify private key, language, persistence and matching client decisions through server replacement and reboot.
3. Update README and implementation-time AGENTS/OpenSpec context minimum references only when the lowered minimum is implemented; current requirement files change through archive.
4. If a regression requires rollback, ship corrected code as a newer signed version. Do not prescribe downgrade flags or uninstalling existing users' data.
5. Merge and validate this change, then archive it before implementing/archiving `multi-store-distribution`. The second change adds no competing delta to these requirements.

## Sources

- Android launch identity API: https://developer.android.com/reference/android/app/Activity#getLaunchedFromPackage()
- Locale API: https://developer.android.com/reference/android/app/LocaleManager
- Parcelable API: https://developer.android.com/reference/android/os/Bundle#getParcelable(java.lang.String,java.lang.Class)
- Shizuku API baseline: https://github.com/RikkaApps/Shizuku-API/blob/a27f6e4151ba7b39965ca47edb2bf0aeed7102e5/settings.gradle
- Provider signatures: https://github.com/RikkaW/HiddenApi/blob/08db66c03e0b9470bfae344b9798471cd4ccf614/stub/src/main/java/android/content/IContentProvider.java
