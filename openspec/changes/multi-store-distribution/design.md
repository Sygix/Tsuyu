# Design

## Context

See `proposal.md` for scope and `android9-compatibility` for the platform baseline. The audited source is `40669b6731ea3c93c9f8f4a795f574f065f9a634`. `TsuyuGraph` always constructs the updater; `MainActivity` loads/checks it; `HomeViewModel` always offers its entry. `ServerControl` directly depends on `UpdateInstaller`, which `ServerMain` constructs. The existing release workflow publishes only `tsuyu.apk` and the installed updater requires that exact asset name. `app/build.gradle.kts` takes the version from a `TSUYU_VERSION` environment variable, falls back to a fixed development version, and signs release builds with the debug key when no keystore variable is set.

The manifest contains QUERY_ALL_PACKAGES, INTERNET, ACCESS_NETWORK_STATE and boot permission. It does not contain REQUEST_INSTALL_PACKAGES: the self-installer operates through the shell server. Therefore updater isolation must cover app and server code, not just manifest permissions.

## Goals / Non-Goals

**Goals:** two build-time capabilities, coherent signed upgrades, one committed version source, independently reproducible store APKs, actionable store runbooks and evidence-backed device reporting.

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

The persistence screen stays in shared code. A store build therefore offers the same controls as a standalone build, including « Désactiver le démarrage automatique » and « Renouveler l'autorisation » added by `consent-and-persistence-controls`, and adds no updater action. This change adds no user-visible string.

Inspect both merged manifests and packaged DEX/dependencies. Class absence and unreachable installation behavior are acceptance evidence; hiding a row or relying solely on shrinker removal is insufficient.

### 2. Privileged IPC compatibility

Retain the manager AIDL transaction positions, including `installUpdate`, to avoid wire incompatibility during package/server replacement. Replace `ServerControl`'s concrete installer dependency with the narrow installation boundary supplied by the server flavor. Standalone delegates to `UpdateInstaller`; store returns a structured unsupported result after manager authentication and closes any received descriptor without reading/installing it. No `UpdateInstaller` implementation is packaged in store.

Keep the public Shizuku AIDL unchanged. The presence of a reserved manager transaction is not an install capability. Continue to authenticate every caller and preserve binder identity/descriptor cleanup on rejection and exceptions. Existing package lifecycle logic stops old servers and resolves the new installed APK before a new server is accepted.

### 3. Persistent updater cleanup and fresh opt-in

The current scheduler uses unique work names `tsuyu-update` and `tsuyu-update-recheck`. The existing preferences DataStore is named `updates`, with a JSON `state` entry; cached APKs are confined to the app-private `update` cache directory.

**Store cleanup.** Extract a minimal shared cleanup boundary that can reset updater state without importing release parsers, network code or an installer. Use one owner for each DataStore instance. Cleanup cancels both work names, resets automatic-update intent and pending/offered updater state while keeping the recorded versionCodes described below, and removes only managed update-cache files. Keeping the record lets a later standalone build detect the store interval by itself. It must never follow an arbitrary persisted path outside that cache or clear consent, language or persistence data.

A store build runs this cleanup at every process start, from application initialization and therefore before any activity, receiver or worker of that process, until one run completes; a completed run is recorded so later starts skip the work. Each step is idempotent, so a crash or partial failure is repaired by the next start. The store build has no updater screen, and the user can do nothing about a failed cleanup, so a failure shows no notice: it returns a Result, is logged with its cause and bounded by a deadline, and is retried at the next process start. Safety does not depend on it: the store payload contains no updater code, so a stale job cannot discover, download or install anything while cleanup is pending.

**Standalone replacement rule.** The owner decided that automatic updating returns to off after a store interval, and that a missing record is the first observation: no store build exists before the release that introduces the rule, so no store interval can precede it, and existing users keep their setting. The standalone build detects any replacement it did not perform itself instead of relying on the store build having run:

- `lastObservedVersionCode`: the installed versionCode, written at process start once the comparison below is done;
- `startedInstallVersionCode`: the versionCode of the verified file, written before the standalone updater commits its installer session and cleared when the installer reports a failure.

Both live in the `updates` DataStore. At process start, before any automatic check, download or installation, the build compares its installed versionCode with both values. When it equals neither, the package was replaced out of band: automatic updating is turned off, the stored offer and managed update files are dropped, both records are rewritten for the current version, and the update screen shows the existing « Désactivées » state with no new text. When it equals one of them, the user's setting is kept and `startedInstallVersionCode` is cleared once consumed.

The comparison is one bounded suspend step owned by the updater's state holder. Every automatic entry point (periodic worker, recheck worker, home-opened check, silent installation) awaits it; none may read the automatic-update setting before it has completed in the current process. If the records cannot be read or written within the deadline, the step returns a failure Result: no automatic work runs in that process, the cause is logged, manual checking stays available and the next process start tries again.

Consequences accepted by the owner: a manual installation of a later standalone APK also resets the opt-in. A fresh installation has no record and automatic updating is already off, so the rule changes nothing there.

Test rapid replacements, a store installation never opened, a worker that becomes runnable before any activity, and the crash and timeout paths of both mechanisms. Do not equate activity launch with migration completion.

### 4. Artifacts and version identity

| Release asset | Build | Consumer |
| --- | --- | --- |
| `tsuyu.apk` | standalone release APK | Existing/new GitHub updater clients |
| `tsuyu-store.apk` | store release APK | F-Droid reference binary |
| `tsuyu-store.aab` | store release bundle | Play submission |

Use `fr.sygix.tsuyu` and existing provider authorities for both variants. Do not add a variant offset. Test newer-version transitions; a store need not offer same-version replacement. Explain installer/update-ownership constraints rather than bypassing them.

**Version file.** The owner chose a committed version file over a tag-supplied variable. A `version.properties` file at the repository root holds two keys, `versionName` (for example `1.4.0` or `1.4.0-rc.1`) and `versionCode`. Gradle reads both for every variant and reads no version variable; the `TSUYU_VERSION` input and the fixed development fallback are removed. The existing `versionCodeOf` formula stays in the build as a consistency check: the build fails, naming the file, when `versionCode` differs from the formula applied to `versionName` or when a key is missing or malformed. Installed standalone clients compute the expected code from the tag with the same formula, so they keep finding the next release.

A release is two steps: a commit that bumps the file, then a signed tag `v<versionName>` on that commit. The release job compares the tag with the file first and fails before building or signing when they differ. A build of the tagged revision needs no variable and yields the published version, which is what the F-Droid recipe and any independent rebuilder rely on. Builds between releases carry the last committed version.

Generate all artifacts from the same clean tagged revision. Preserve release immutability: a failed draft can be retried, but an already published release must not be silently overwritten.

### 5. Split delivery and languages

The server launch uses the installed base APK as its classpath. Keep all server entry classes and their dependencies in base, with explicit shrinker retention for `app_process` entry points. Validate `ServerMain`, `UserServiceMain` and process-supervisor launching from bundletool-generated installations and an actual Play testing-track installation.

Disable language splitting for the store AAB so all five languages remain available to the app's selector offline. Density/ABI splits remain subject to runtime validation; inspect any packaged native dependencies for applicable 32/64-bit and 16 KB page-size requirements. If no native libraries are present, record that finding rather than manufacturing architecture-specific artifacts. Keep strict installed-path validation unless an observed delivery format requires a reviewed adjustment.

A store decides by itself when it updates the package, and the store build has no idle gate. The update follows the existing package-replacement lifecycle: the old server exits, client processes and UserServices owned through it end, and clients observe `binderDied`. The service starts again without the user only when `boot-persistence` is ready; otherwise the next home shows it stopped with its start action. The README and the store listing therefore recommend enabling automatic startup on store builds.

### 6. Signing and reproducibility

Two signing identities are used and never interchanged:

| Artifact | Signed with | Checked against |
| --- | --- | --- |
| `tsuyu.apk`, `tsuyu-store.apk` | existing developer release key | release certificate |
| `tsuyu-store.aab` | Play upload key | upload certificate |

The `production` environment gains a second set of secrets for the upload key next to the existing release-key set. The release job signs each artifact with its own identity, then verifies every signer explicitly: the APKs with the APK signature verifier against the release certificate, the AAB's JAR signature against the upload certificate. A missing set, an invalid key or swapped identities fail the job before anything is staged.

Configure Play App Signing to use the existing release signing identity as the app signing key before open/production rollout, with the separate upload key registered. The current Play default may generate its own signing identities; compare actual delivered public certificates on the tested OS ranges. Internal App Sharing can re-sign builds and is not certificate-compatibility evidence; use a testing track for migration tests.

A release variant built without signing inputs is unsigned. The debug-signing fallback of the `release` signing configuration is removed: no signing configuration is attached when the keystore inputs are absent. Local device tests of a keyless release build need an explicit signing step, documented for developers; they cannot upgrade a production-signed installation, which was already the case with the debug fallback.

F-Droid receives the store flavor through a recipe with `Binaries`/per-build binary and the allowed developer certificate fingerprint. It takes the version from `version.properties` at each stable tag (tag-based update check with the file as check data, automatic version updates), so no recipe edit and no variable is needed per release. The unsigned build above is the comparison artifact. Copy the reference signature using current compatible tooling and verify the result. A mismatch blocks that version instead of falling back to a different signer.

Exclude AGP's dependency metadata block from both outputs with `dependenciesInfo { includeInApk = false; includeInBundle = false }`. This is a recalled F-Droid scanning requirement, not re-verified for this design: confirm it against the current F-Droid documentation in task 1.3.

Pin and document the reproducibility toolchain: Gradle, JDK distribution/version, AGP, SDK platform revision, build tools and signer. Build twice from clean environments, including F-Droid's target environment, and compare with apksigcopier/apksigner; use diffoscope for differences. Diagnose baseline profiles, R8 ordering, embedded revision metadata and SDK revision differences from evidence, without disabling optimizations indiscriminately.

Release CI builds/tests without production secrets on PRs; only the protected production build uses signing inputs. Select the signer explicitly rather than taking the newest installed executable. Upload complete artifacts and checksums before publishing the draft. New third-party actions remain subject to the existing allowlist and SHA pinning rules.

### 7. Package visibility and declarations

Keep QUERY_ALL_PACKAGES for the initial compatibility-preserving design and prepare its required core-interoperability justification. The observed manager needs client names/icons in `AccessViewModel` and the shared launch identity label in `MainActivity`; the shell server independently enumerates client manifests and verifies identity. Removing broad visibility is not assumed safe merely because enumeration runs as shell.

As a submission preflight, verify client names/icons, shared-UID groups and caller labels under actual installed visibility. If a narrower declaration is demonstrated to preserve the full contract, document it as a targeted implementation adjustment. Do not silently drop identity information or change Shizuku semantics to gain approval.

**Planned fallback if Play refuses QUERY_ALL_PACKAGES.** The shell server sees every package. It would return the labels and icons of client packages to the authenticated manager over the manager IPC, so the app no longer needs the permission. This is not implemented in this change: it adds a manager-only server operation and needs its own change, with the minimal-surface and identity checks that implies. Until then a refusal leaves the Play row of the « Availability » table blocked.

Audit merged permissions, dependency-added services and network behavior for privacy/declaration content. Do not declare absence of collection before checking dependencies and store transformations.

### 8. Publication and community evidence are separate gates

Implement the protocol and README matrix described by `device-validation`, with physical/emulator evidence separated. Require artifact identity, client permission/process/UserService checks, boot with the manager absent, signed replacement and variant-specific checks. Keep a passing historical row when a later release regresses, and link all validated rows to reviewed evidence.

The available physical regression device is a TCL Google TV running Android 14. Android 9 is initially emulator-tested. A community developer may supply evidence; maintainer review is labelled as review, not independent physical reproduction.

Channel status is public in one place: a README « Availability » table with a row for GitHub releases, Google Play and F-Droid, each carrying one status among prepared, testing, submitted, live and blocked, plus the next action or missing input for a submitted or blocked row. A row becomes live only after an installation from that channel has been verified. The same section states that changing channel needs a later version on the target channel.

Prepare Fastlane-format metadata for five languages, real TV screenshots, a privacy document, store-specific checklists and reviewer setup steps. Runtime screenshots are produced during implementation. A stable public privacy URL and account inputs are required before submission, not guessed in these artifacts.

### 9. Play may take over an installation made from the GitHub APK

The store and standalone builds share a package name and, once Play App Signing uses the release key, a signing certificate. Google Play can then update an installation made from the GitHub APK to its own build when it offers a later version, without the user choosing to change channel. The owner accepts this: the channel-migration contract applies (data and consent kept, updater neutralized), the app shows no notice and the README documents the behavior, including that a return to the GitHub build needs a later standalone version and resets automatic updating to off.

## Risks / Trade-offs

- [Old persisted worker runs before UI cleanup] -> Remove its implementation from store, perform idempotent process-start cleanup and test early/background entry explicitly.
- [Store cleanup never ran before a return to standalone] -> When a versionCode was recorded, the comparison alone resets the opt-in. An installation from before the rule with automatic updating on, replaced by a store build whose cleanup never completed, then by a later standalone build, keeps its setting: accepted, since any store process start, including a stale worker, runs the cleanup first.
- [An installation started by the updater is interrupted by process death, then the same version arrives another way] -> The opt-in survives for that one version. Accepted: it is the version the user's own updater had verified and was installing.
- [A manual installation of a later standalone APK turns automatic updating off] -> Accepted by the owner; documented in the README.
- [Play silently replaces an installation made from the GitHub APK] -> Accepted by the owner; documented in the README, no in-app notice.
- [Importing the existing release key into Play App Signing gives Google a copy of the key the standalone updater trusts] -> Recorded consequence of the decision to use the same key on every channel; accepted and not reopened. A compromise of that copy would let a third party produce an APK that passes the standalone updater's certificate check.
- [Play signing defaults diverge from the release certificate] -> Configure before rollout and inspect actual testing-track APK certificates on relevant OS versions.
- [The upload key and the release key are confused in CI] -> Separate secret sets and an explicit signer check per artifact; a swap fails the job.
- [A store update stops the service while automatic startup is off or a client is working] -> Same lifecycle as any package replacement; the stopped state is shown truthfully and the README and listing recommend automatic startup.
- [F-Droid bytes differ despite matching code] -> Pin toolchain inputs, compare clean builds and block publication until the mismatch is explained and resolved.
- [A release is tagged without the version bump] -> The release job fails on the tag/file comparison before building.
- [Restricted permission or privileged behavior is not accepted by a store] -> Provide accurate declarations and leave that channel blocked; the server-side label fallback of section 7 is the planned answer for package visibility. Artifact readiness does not imply approval.

## Migration Plan

1. Archive order: `consent-and-persistence-controls`, then `android9-compatibility`, then this change. The first modifies `client-access` "Authenticated server-managed consent" and adds `boot-persistence` requirements; this change modifies only `self-update` requirements and refers to the others, so there is no competing delta.
2. Introduce the version file in the first release built from this change; that release's tag matches the file like any later one.
3. Build both variants and verify newer-version standalone -> store -> standalone upgrades on existing configured data, with active and pending clients/work.
4. Keep `tsuyu.apk` as the standalone artifact so currently installed clients can upgrade directly.
5. Validate generated split delivery and real Play testing-track delivery, then reproduce the developer-signed store APK for F-Droid.
6. Publish channel readiness in the README « Availability » table. Execute the publication runbooks in `handoff.md` only with the owner's authorization.
7. Roll back behavior through a newer corrected signed release; do not instruct users to downgrade or uninstall a compatible installation.

## Open Questions

Publication-only inputs, **to confirm** before the affected submission:

- Play account type/eligibility and any existing signing configuration; verify before enrolling or importing keys.
- Public privacy-policy hosting location and listing contact details; keep the repository document ready until those are supplied.

These are not implemented SHALLs or assumed console settings. Account operations and external approvals remain separate from completing the local distribution implementation.

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
