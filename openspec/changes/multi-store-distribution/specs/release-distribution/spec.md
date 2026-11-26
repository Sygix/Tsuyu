## Purpose

Provide independently verifiable store-managed and standalone Tsuyu releases with coherent identity, update behavior and publication readiness.

## ADDED Requirements

### Requirement: Distribution-specific update capability
Tsuyu SHALL provide store and standalone builds. Only standalone builds SHALL provide the self-update capability. Store builds SHALL exclude internal release discovery, update downloads, update workers, updater screens and privileged self-installation implementation; they SHALL rely on the distributing store for updates while preserving the public Shizuku API.

#### Scenario: Store installation
- **WHEN** a user opens a store build, starts its service or leaves it in the background
- **THEN** no internal updater action or notice appears, no release-discovery/download request is made and no internal updater job is scheduled

#### Scenario: Standalone installation
- **WHEN** a user installs the standalone build
- **THEN** self-update governs its existing verified manual and opt-in automatic update behavior

#### Scenario: Self-installation attempted on store
- **WHEN** an authenticated manager invokes a retained updater compatibility entry in a store server
- **THEN** it receives an explicit unsupported outcome and no installer session, download or package replacement is started

#### Scenario: Unauthorized updater call
- **WHEN** a client or another unauthorized caller attempts the manager updater operation
- **THEN** the call is rejected regardless of variant and no privileged installation begins

#### Scenario: Store or network unavailable
- **WHEN** the distributing store is missing, offline or its update attempt times out
- **THEN** Tsuyu does not fall back to self-updating; existing service and client-permission behavior continues independently

#### Scenario: Store build after reboot
- **WHEN** a store installation reboots
- **THEN** boot-persistence remains available but no internal updater is started or restored

### Requirement: Store home navigation
Store builds SHALL omit the update entry and update-specific notices from home while preserving the remaining action order and the Remote focus contract. Removal of update UI SHALL NOT remove access management, language selection, the persistence screen with every control `boot-persistence` defines for it, or the platform-appropriate caller-return behavior. A store build SHALL add no updater action to any screen.

#### Scenario: Initial focus and navigation
- **WHEN** store home opens and the user navigates its actions
- **THEN** the main action receives initial focus, D-pad follows visible order without wrapping and no hidden update entry receives focus

#### Scenario: Return from a sub-screen
- **WHEN** the user returns from access, language, persistence or system settings
- **THEN** the initiating control receives focus when present, otherwise the current main action does; Back on home exits normally

#### Scenario: Restored update focus disappears
- **WHEN** activity state from a standalone installation refers to an update control after migration to store
- **THEN** focus falls back to the current main action and no removed updater screen is opened

#### Scenario: Persistence controls on a store build
- **WHEN** automatic startup is enabled on a store build and the user opens the persistence screen
- **THEN** « Désactiver le démarrage automatique » is offered, « Renouveler l'autorisation » is offered and initially focused under the conditions `boot-persistence` sets for it, both behave as on a standalone build and no updater action is shown

### Requirement: Compatible-signature channel migration
An accepted same-package, compatible-signature upgrade between standalone and store SHALL preserve the private ADB key, language, persistence state and matching client consent. Entering store SHALL disable prior auto-update intent, cancel updater work and discard pending update files. A store that replaces a standalone installation with a later store version SHALL be handled as this migration, without an in-app notice.

#### Scenario: Standalone to store
- **WHEN** a newer compatible store release replaces a standalone installation with automatic updating enabled
- **THEN** updater state is neutralized before it can perform new work, private configuration survives and the new server has store capabilities

#### Scenario: Store replaces a standalone installation by itself
- **WHEN** the store updates an installation made from the standalone APK to a later store version without the user asking for a channel change
- **THEN** private configuration and matching client consent survive, home no longer shows « Mises à jour » and no migration notice or dialog appears

#### Scenario: User or installer refuses migration
- **WHEN** the user declines an upgrade or the platform rejects its version, certificate or installer ownership
- **THEN** the existing installation remains authoritative and Tsuyu does not bypass the rejection or report a successful migration

#### Scenario: Replacement restart fails or times out
- **WHEN** the server restart after a channel migration fails or exceeds its deadline
- **THEN** home shows the `boot-persistence` restart failure with its recovery action focused and does not report the service as running because the package version changed

#### Scenario: Client and reboot continuity
- **WHEN** migration succeeds with persistence enabled and the service restarts immediately or after reboot
- **THEN** old binders die, clients observe the new binder through Shizuku.addBinderReceivedListenerSticky and checkSelfPermission() reflects preserved matching decisions

### Requirement: Silent store-side updater cleanup
A store build SHALL run the cleanup of standalone updater work, state and files at every process start until it completes, starting at application initialization; a standalone build clears the completion record. The cleanup SHALL be safe to repeat and bounded by a deadline. A failure SHALL be logged with its cause and retried at the next process start, without any user notice.

#### Scenario: Old pending worker races with startup
- **WHEN** stored updater work becomes runnable before the first activity launch in the store installation
- **THEN** it cannot discover, download or install a release; persistent updater scheduling is cancelled without depending on opening the UI

#### Scenario: Migration cleanup fails
- **WHEN** cancelling updater work, resetting updater state or deleting managed update files fails or exceeds its deadline on a store build
- **THEN** no notice is shown, the failure is logged with its cause, the same cleanup runs again at every process start until it completes, and no release discovery, download or installation happens in the meantime

#### Scenario: Cleanup completes
- **WHEN** a store build starts with standalone updater work, state or files still present and the cleanup succeeds
- **THEN** both updater jobs are cancelled, the stored offer and managed update files are gone, and later process starts skip the cleanup

#### Scenario: Cleanup after reboot
- **WHEN** the TV reboots before an incomplete cleanup was retried
- **THEN** the cleanup runs at the first process start after boot, before any worker of that process

### Requirement: Fresh opt-in after an out-of-band replacement
A standalone build SHALL persist the versionCode observed at its last process start and of any installation its updater started. At process start, before any automatic work, a build whose installed versionCode matches neither value SHALL turn automatic updating off and discard its stored offer and update files. A start without any recorded versionCode is the first observation and keeps the setting. Automatic installation SHALL resume only after a fresh opt-in.

#### Scenario: Store back to standalone
- **WHEN** a newer compatible standalone release replaces a store installation and Tsuyu starts, after a versionCode was recorded or the store cleanup completed
- **THEN** private configuration survives, the update screen shows « Mises à jour automatiques : Désactivées » with no stored offer, and no automatic check, download or installation runs until the user enables the setting again

#### Scenario: Standalone replaced outside its updater
- **WHEN** a standalone build with automatic updating enabled starts and its installed versionCode is neither the one observed at its last process start nor one its own updater started installing, as after a manual installation of a later standalone APK
- **THEN** before any automatic work the setting is turned off, the stored offer and update files are removed and the update screen shows « Mises à jour automatiques : Désactivées »

#### Scenario: Update installed by the standalone updater
- **WHEN** a standalone build starts after its own updater installed the running version
- **THEN** « Mises à jour automatiques » keeps the value the user chose and « Mis à jour vers X » is shown under self-update

#### Scenario: Unchanged version after restart or reboot
- **WHEN** a standalone build starts, or the TV reboots, with the versionCode observed at its last process start
- **THEN** « Mises à jour automatiques » keeps the value the user chose

#### Scenario: First start without any record
- **WHEN** a standalone build starts with automatic updating enabled and no versionCode was ever recorded, as on the first release that introduces this rule
- **THEN** the running versionCode is recorded and « Mises à jour automatiques » keeps the value the user chose

#### Scenario: Version record unavailable or timeout
- **WHEN** the stored versionCodes cannot be read or updated within their bounded deadline at a standalone process start
- **THEN** no automatic check, download or installation runs in that process, the cause is logged, « Rechercher les mises à jour » stays available and the rule is evaluated again at the next process start

### Requirement: Compatible artifact identity and versioning
Each published release SHALL provide `tsuyu-X.Y.Z-standalone.apk`, the same file as `tsuyu.apk`, `tsuyu-X.Y.Z-store.apk` and the Play `tsuyu-X.Y.Z-store.aab` for the same source version X.Y.Z. They SHALL use the existing package identity and the version of the committed version file, without per-variant offsets. The standalone asset name SHALL remain compatible with installed updater clients.

#### Scenario: Existing updater finds a release
- **WHEN** a previously published standalone version checks a new release
- **THEN** it finds the standalone APK, under its versioned name or as `tsuyu.apk`, whose versionCode equals the value it computes from the tag, with published size and digest, rather than selecting the store artifact

#### Scenario: Artifact parity
- **WHEN** the three artifacts are inspected for a release
- **THEN** their version name, version code and source revision agree with the committed version file while updater capability follows the declared variant

#### Scenario: Same or lower version offered
- **WHEN** the target channel offers the installed version or a lower one
- **THEN** the platform keeps the existing installation, Tsuyu bypasses no version check, and the README « Availability » section states that changing channel needs a later version on the target channel

### Requirement: Committed version source
The versionName and versionCode of every build SHALL come from a version file committed in the repository, and from no other input. The recorded versionCode SHALL equal the existing build formula applied to the recorded versionName, so installed updater clients compute the same value from the tag. A release tag SHALL match the recorded versionName.

#### Scenario: Build from the tagged revision without release inputs
- **WHEN** the tagged revision is built without any version variable or signing input
- **THEN** the artifacts carry the versionName and versionCode of the version file, identical to the published ones

#### Scenario: Tag does not match the version file
- **WHEN** a release tag differs from `v` followed by the recorded versionName
- **THEN** the release job fails before building or signing and nothing is staged or published

#### Scenario: Inconsistent version file
- **WHEN** the recorded versionCode differs from the build formula applied to the recorded versionName, or either value is missing or malformed
- **THEN** every build fails with an error naming the version file

### Requirement: Authenticated release publication
Published APKs SHALL carry the expected developer release certificate before any store transformation. The Play bundle SHALL be signed with the registered Play upload key, an identity distinct from the release certificate. Release publication SHALL fail for a missing or swapped signing identity, mismatched certificates, incomplete artifacts or failed required checks. The artifacts SHALL be associated with their source tag and checksums before the release becomes public.

#### Scenario: Complete signed release
- **WHEN** an authorized release build passes its checks, both APKs verify against the release certificate and the AAB verifies against the upload certificate
- **THEN** both APKs and the AAB are staged with their source association and checksums before publication

#### Scenario: Signing configuration missing
- **WHEN** the release-key inputs or the upload-key inputs are absent or invalid
- **THEN** publication fails rather than publishing an unsigned or debug-signed substitute

#### Scenario: Signing identities swapped
- **WHEN** an APK is signed with the upload key or the AAB is signed with the release key
- **THEN** the signer check fails and nothing is published

#### Scenario: Partial upload or failed check
- **WHEN** a required build/check fails or an artifact upload is incomplete
- **THEN** the release does not become public as a complete release and retry does not silently replace an already published release

### Requirement: Unsigned keyless release builds
A release variant built without signing inputs SHALL produce unsigned artifacts and SHALL NOT fall back to debug signing.

#### Scenario: Release build without a keystore
- **WHEN** a release variant is built with no signing input, locally or by an independent rebuilder
- **THEN** the resulting APKs and AAB contain no signature and signature verification reports them as unsigned

#### Scenario: Release build with signing inputs
- **WHEN** a release variant is built with the signing inputs of its identity
- **THEN** signature verification reports that identity's certificate and no debug certificate

### Requirement: Store-delivered runtime equivalence
The store-delivered installation SHALL preserve the supported Android baseline, all five selectable languages and the existing service/client contracts, including from split APK delivery. Publication readiness SHALL be verified against the delivered format and applicable ABI/page-size requirements rather than inferred solely from a universal APK build. A store-initiated update SHALL apply no idle gate; the service SHALL resume by itself only when `boot-persistence` is ready.

#### Scenario: Play-delivered startup
- **WHEN** a device installs the store build from a Play testing track
- **THEN** the packaged server starts from the installed application and an authorized client can use binder delivery, remote processes and UserServices under shizuku-api

#### Scenario: Language selection after split delivery
- **WHEN** the user chooses any supported language without downloading another language package
- **THEN** the existing cross-version language contract works for all screens

#### Scenario: Permission refused
- **WHEN** the user refuses debugging or a client permission on a store-delivered installation
- **THEN** the corresponding local-start or client-access refusal contract applies unchanged

#### Scenario: Missing packaged facility or incompatible ABI
- **WHEN** a delivered artifact lacks a required server class, resource or usable architecture
- **THEN** that artifact fails runtime acceptance and the affected devices are not claimed as supported

#### Scenario: Delivered startup timeout
- **WHEN** server or client startup reaches its deadline after store installation
- **THEN** existing recovery behavior is shown and delivery is not marked validated without resolving or documenting the failure

#### Scenario: Delivered upgrade and reboot
- **WHEN** the store replaces a configured installation and the device subsequently reboots
- **THEN** startup uses the current installed package, its retained key and matching client decisions under boot-persistence

#### Scenario: Store update without persistence prepared
- **WHEN** the store replaces a running installation whose automatic startup is not enabled or was disabled
- **THEN** the old server exits, attached clients observe `binderDied` and receive no new binder, and the next home shows the service stopped with its start action focused and no update-success message

#### Scenario: Store update while a client is busy
- **WHEN** the store replaces the package while a client holds a remote process or a UserService
- **THEN** that process and that UserService end with the manager package lifecycle and the client observes `binderDied`; it receives a new binder through Shizuku.addBinderReceivedListenerSticky after the restart only when `boot-persistence` is ready

### Requirement: Developer-signed F-Droid delivery
F-Droid publication SHALL use a reproducible store APK with the developer's expected release certificate. The source recipe SHALL identify the source revision and reference binary, and SHALL take the version of each release from the committed version file at its stable tag. A failed reproducibility or certificate check SHALL block that version's F-Droid publication rather than silently switching signing identity.

#### Scenario: Matching rebuild
- **WHEN** the independent recipe reproduces the release APK and its signature verifies against the allowed developer certificate
- **THEN** the matching developer-signed store APK is eligible for F-Droid publication

#### Scenario: Rebuild mismatch
- **WHEN** rebuilt contents or the reference certificate differ
- **THEN** the mismatch is diagnosed and the version remains unpublished through that recipe

#### Scenario: New stable tag detected
- **WHEN** a stable tag is published whose version file records a higher versionCode
- **THEN** the recipe's update check reports that versionName and versionCode from the file, without a manual recipe edit and without a version variable

#### Scenario: Compatible F-Droid upgrade
- **WHEN** a newer F-Droid-delivered store APK replaces a compatible standalone installation
- **THEN** the channel-migration contract applies without uninstalling or resetting its private data

### Requirement: Store publication readiness
Each store submission SHALL include accurate TV listing assets, privacy information, required declarations and reviewer setup instructions. The README SHALL carry an « Availability » table with one row per channel (GitHub releases, Google Play, F-Droid) giving one status among prepared, testing, submitted, live and blocked, with the next action for a blocked or submitted row. Device-specific support SHALL follow device-validation evidence and store-delivered runtime acceptance.

#### Scenario: Play signing and testing
- **WHEN** the Play channel is prepared for publication
- **THEN** its delivered signing identity is checked for compatible upgrades and applicable account testing, TV-quality and permission declarations are completed before production rollout

#### Scenario: Store review requires action
- **WHEN** a store rejects, delays or requests information about a submission
- **THEN** that channel's row in the README « Availability » table shows submitted or blocked with the next action, and no document or listing presents it as live

#### Scenario: Channel becomes available
- **WHEN** an installation from the store listing has been verified on a device
- **THEN** that channel's row shows live with the listing link

#### Scenario: Account or publication inputs missing
- **WHEN** account verification, a privacy URL, required declarations or applicable testing cannot be completed
- **THEN** the affected row shows blocked with its missing input while independently ready channels can proceed
