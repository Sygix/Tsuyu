# Design

## Context

See `proposal.md` for scope and `android9-compatibility` for the platform baseline. The audited source is `40669b6731ea3c93c9f8f4a795f574f065f9a634`. `TsuyuGraph` always constructs the updater; `MainActivity` loads/checks it; `HomeViewModel` always offers its entry. `ServerControl` directly depends on `UpdateInstaller`, which `ServerMain` constructs. The existing release workflow publishes only `tsuyu.apk` and the installed updater requires that exact asset name.

The manifest contains QUERY_ALL_PACKAGES, INTERNET, ACCESS_NETWORK_STATE and boot permission. It does not contain REQUEST_INSTALL_PACKAGES: the self-installer operates through the shell server. Therefore updater isolation must cover app and server code, not just manifest permissions.

## Goals / Non-Goals

**Goals:** two build-time capabilities, coherent signed upgrades, independently reproducible store APKs, actionable store runbooks and evidence-backed device reporting.

**Non-goals:** this design prepares publication but does not make external account eligibility or store review automatic. The Android baseline and caller-return adaptation are owned by the preceding change.

## Decisions

### 1. Source-set boundaries

Use a `distribution` flavor dimension with `standalone` and `store` in the app and server modules. Keep shared IPC/API modules unflavored where possible; variant-aware dependency resolution must pair the app with the matching server.

```text
shared app + IPC + public Shizuku API
          |
          +-- standalone app ---- standalone server installer
          |      GitHub source, update UI, worker, verification
          |
          +-- store app --------- store server unsupported endpoint
                 migration cleanup, no internal updater
```

Move updater implementation, activity manifest entry and updater-only dependencies/resources into standalone source sets. Keep WorkManager in shared code for restart/key refresh, and INTERNET for local ADB. Restrict ZXing to standalone if the actual call-site inventory confirms its only use is update QR rendering.

At the existing graph boundary, expose a small distribution capability to shared home state; do not make shared home depend on concrete update controllers. A store implementation supplies no updater destination or notices. Keep business logic out of composables and preserve the remaining action order and focus fallback.

Inspect both merged manifests and packaged DEX/dependencies. Class absence and unreachable installation behavior are acceptance evidence; hiding a row or relying solely on shrinker removal is insufficient.

### 2. Privileged IPC compatibility

Retain the manager AIDL transaction positions, including `installUpdate`, to avoid wire incompatibility during package/server replacement. Replace `ServerControl`'s concrete installer dependency with the narrow installation boundary supplied by the server flavor. Standalone delegates to `UpdateInstaller`; store returns a structured unsupported result after manager authentication and closes any received descriptor without reading/installing it. No `UpdateInstaller` implementation is packaged in store.

Keep the public Shizuku AIDL unchanged. The presence of a reserved manager transaction is not an install capability. Continue to authenticate every caller and preserve binder identity/descriptor cleanup on rejection and exceptions. Existing package lifecycle logic stops old servers and resolves the new installed APK before a new server is accepted.

### 3. Persistent updater cleanup and transition evidence

The current scheduler uses unique work names `tsuyu-update` and `tsuyu-update-recheck`. The existing preferences DataStore is named `updates`, with a JSON `state` entry; cached APKs are confined to the app-private `update` cache directory.

Extract a minimal shared transition/cleanup boundary that can reset updater state without importing release parsers, network code or an installer. Use one owner for each DataStore instance. Cleanup cancels both work names, resets automatic-update intent and pending/offered updater state, removes only managed update-cache files, and records the current distribution transition. It must never follow an arbitrary persisted path outside that cache or clear consent, language or persistence data.

Invoke cleanup from application initialization and relevant replacement/background entry points, before exposing variant capabilities. Test WorkManager startup before an activity, an absent old worker class, failed cancellation and a crash during cleanup. Missing update code makes store safe even if cleanup needs retry; failures still produce a bounded, actionable outcome.

Before standalone can resume automatic installation, reconcile the transition state. A confirmed store interval requires new opt-in. Interrupted or unconfirmed transition cleanup must not be treated as proof that an old opt-in remains valid. Test rapid replacements and a store installation never manually opened; do not equate activity launch with migration completion. A limitation that prevents this contract must be resolved before claiming supported migration, not hidden behind a successful package install.

### 4. Artifacts and version identity

| Release asset | Build | Consumer |
| --- | --- | --- |
| `tsuyu.apk` | standalone release APK | Existing/new GitHub updater clients |
| `tsuyu-store.apk` | store release APK | F-Droid reference binary and Amazon submission |
| `tsuyu-store.aab` | store release bundle | Play submission |

Use `fr.sygix.tsuyu`, existing provider authorities and the current tag-derived versionCode for both variants. Do not add a variant offset or change how older clients derive their expected code. Test newer-version transitions; a store need not offer same-version replacement. Explain installer/update-ownership constraints rather than bypassing them.

Generate all artifacts from the same clean tagged revision. Keep release-version input deterministic for local/reproducibility builds and fail production publication when it is absent or invalid. Preserve release immutability: a failed draft can be retried, but an already published release must not be silently overwritten.

### 5. Split delivery and languages

The server launch uses the installed base APK as its classpath. Keep all server entry classes and their dependencies in base, with explicit shrinker retention for `app_process` entry points. Validate `ServerMain`, `UserServiceMain` and process-supervisor launching from bundletool-generated installations and an actual Play testing-track installation.

Disable language splitting for the store AAB so all five languages remain available to the app's selector offline. Density/ABI splits remain subject to runtime validation; inspect any packaged native dependencies for applicable 32/64-bit and 16 KB page-size requirements. If no native libraries are present, record that finding rather than manufacturing architecture-specific artifacts. Keep strict installed-path validation unless an observed delivery format requires a reviewed adjustment.

### 6. Signing and reproducibility

Reuse the existing developer release certificate for the distributed APKs. Configure Play App Signing to use that signing identity and a distinct upload key before open/production rollout. The current Play default may generate its own signing identities; compare actual delivered public certificates on the tested OS ranges. Internal App Sharing can re-sign builds and is not certificate-compatibility evidence; use a testing track for migration tests.

F-Droid receives the store flavor through a recipe with `Binaries`/per-build binary, the allowed developer certificate fingerprint and the exact tagged version input. Build a genuinely unsigned comparison artifact; the current local release fallback to debug signing must not contaminate reproduction. Copy the reference signature using current compatible tooling and verify the result. A mismatch blocks that version instead of falling back to a different signer.

Pin and document the reproducibility toolchain: Gradle, JDK distribution/version, AGP, SDK platform revision, build tools and signer. Build twice from clean environments, including F-Droid's target environment, and compare with apksigcopier/apksigner; use diffoscope for differences. Diagnose baseline profiles, R8 ordering, embedded revision metadata and SDK revision differences from evidence, without disabling optimizations indiscriminately.

Release CI builds/tests without production secrets on PRs; only the protected production build uses signing inputs. Select the signer explicitly rather than taking the newest installed executable. Upload complete artifacts and checksums before publishing the draft. New third-party actions remain subject to the existing allowlist and SHA pinning rules.

### 7. Package visibility and declarations

Keep QUERY_ALL_PACKAGES for the initial compatibility-preserving design and prepare its required core-interoperability justification. The observed manager needs client names/icons in `AccessViewModel` and the shared launch identity label in `MainActivity`; the shell server independently enumerates client manifests and verifies identity. Removing broad visibility is not assumed safe merely because enumeration runs as shell.

As a submission preflight, verify client names/icons, shared-UID groups and caller labels under actual installed visibility. If a narrower declaration is demonstrated to preserve the full contract, document it as a targeted implementation adjustment; if Play rejects the necessary permission or service behavior, the channel remains pending for a scope decision. Do not silently drop identity information or change Shizuku semantics to gain approval.

Audit merged permissions, dependency-added services and network behavior for privacy/declaration content. Do not declare absence of collection before checking dependencies and store transformations.

### 8. Publication and community evidence are separate gates

Implement the protocol and README matrix described by `device-validation`, with physical/emulator evidence separated. Require artifact identity, client permission/process/UserService checks, boot with the manager absent, signed replacement and variant-specific checks. Keep a passing historical row when a later release regresses, and link all validated rows to reviewed evidence.

The available physical regression device is a TCL Google TV running Android 14. Android 9 is initially emulator-tested. Fire OS 7/8 correspond to Android 9/11 in Amazon's documentation, but usable local ADB and reliable boot on a specific Fire TV remain hypotheses. Test those before declaring the device supported. A community developer may supply evidence; maintainer review is labelled as review, not independent physical reproduction.

Prepare Fastlane-format metadata for five languages, real TV screenshots, a privacy document, store-specific checklists and reviewer setup steps. Runtime screenshots are produced during implementation. A stable public privacy URL and account inputs are required before submission, not guessed in these artifacts.

### 9. Amazon's signing exception

Amazon documents removing the submitted signature, re-signing and adding a wrapper even when DRM is disabled. Accept the owner's separate-installation exception, but verify the delivered certificate and behavior. Treat an incompatible signature as requiring reinstall/reconfiguration and disclose data loss before a channel switch.

Do not treat the uploaded store APK as proof of the Amazon-delivered product. Check wrapper licensing/privacy and execute the protocol against the delivered build. Amazon publication can remain blocked independently of Play/F-Droid preparation.

## Risks / Trade-offs

- [Old persisted worker runs before UI cleanup] -> Remove its implementation from store, perform idempotent process-entry cleanup and test early/background entry explicitly.
- [A transition was never observed by an activity] -> Reconcile durable transition evidence before automatic-install opt-in can resume; test crash and rapid replacement sequences.
- [Play signing defaults diverge from the release certificate] -> Configure before rollout and inspect actual testing-track APK certificates on relevant OS versions.
- [F-Droid bytes differ despite matching code] -> Pin toolchain inputs, compare clean builds and block publication until the mismatch is explained and resolved.
- [Restricted permission or privileged behavior is not accepted by a store] -> Provide accurate declarations and leave that channel pending; artifact readiness does not imply approval.
- [Amazon wrapper or firmware changes behavior] -> Review the transformed artifact and require Fire TV runtime evidence before supported publication.

## Migration Plan

1. Implement and archive `android9-compatibility` first. This change modifies only self-update requirements; there is no competing delta to its navigation/compatibility requirements.
2. Build both variants and verify newer-version standalone -> store -> standalone upgrades on existing configured data, with active and pending clients/work.
3. Keep `tsuyu.apk` as the standalone artifact so currently installed clients can upgrade directly.
4. Validate generated split delivery and real Play testing-track delivery, then reproduce the developer-signed store APK for F-Droid.
5. Publish channel readiness honestly: prepared, testing, submitted or live. Execute the publication runbooks in `handoff.md` only with the owner's authorization.
6. Roll back behavior through a newer corrected signed release; do not instruct users to downgrade or uninstall a compatible installation. An Amazon signature change remains the documented exception.

## Open Questions

Publication-only inputs, with conservative proposals **to confirm** before the affected submission:

- Play account type/eligibility and any existing signing configuration; verify before enrolling or importing keys.
- Public privacy-policy hosting location and listing contact details; keep the repository document ready until those are supplied.
- Amazon DRM selection: proposed disabled; the wrapper remains subject to verification regardless.
- Amazon application coexistence: proposed existing package identity with incompatible-signature reinstall, not simultaneous editions. A request for coexistence requires an identity decision before submission.

These are not implemented SHALLs or assumed console settings. Account operations, external approvals and unavailable Fire TV testing remain separate from completing the local distribution implementation.

## Sources checked

Policies were checked on 2026-11-10 and must be refreshed before submission.

- Play self-update policy: https://support.google.com/googleplay/android-developer/answer/9888379
- Play signing: https://support.google.com/googleplay/android-developer/answer/9842756
- Play TV quality: https://developer.android.com/docs/quality-guidelines/tv-app-quality
- Play target policy: https://support.google.com/googleplay/android-developer/answer/11926878
- Play package visibility: https://support.google.com/googleplay/android-developer/answer/10158779
- Play account testing: https://support.google.com/googleplay/android-developer/answer/14151465
- F-Droid reproduction: https://f-droid.org/docs/Reproducible_Builds/
- F-Droid inclusion: https://f-droid.org/docs/Inclusion_Policy/
- Amazon signing/wrapper: https://developer.amazon.com/docs/app-submission/understanding-submission.html
- Fire OS versions: https://developer.amazon.com/docs/fire-tv/fire-os-overview.html
