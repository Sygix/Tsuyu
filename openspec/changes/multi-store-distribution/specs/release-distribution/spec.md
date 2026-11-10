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
Store builds SHALL omit the update entry and update-specific notices from home while preserving the remaining action order and the Remote focus contract. Removal of update UI SHALL NOT remove access management, language selection, persistence recovery or the platform-appropriate caller-return behavior.

#### Scenario: Initial focus and navigation
- **WHEN** store home opens and the user navigates its actions
- **THEN** the main action receives initial focus, D-pad follows visible order without wrapping and no hidden update entry receives focus

#### Scenario: Return from a sub-screen
- **WHEN** the user returns from access, language, persistence or system settings
- **THEN** the initiating control receives focus when present, otherwise the current main action does; Back on home exits normally

#### Scenario: Restored update focus disappears
- **WHEN** activity state from a standalone installation refers to an update control after migration to store
- **THEN** focus falls back to the current main action and no removed updater screen is opened

### Requirement: Compatible-signature channel migration
An accepted same-package, compatible-signature upgrade between standalone and store SHALL preserve the private ADB key, language, persistence state and matching client consent. Entering store SHALL disable prior auto-update intent, cancel updater work and discard pending update files. Returning to standalone SHALL require fresh opt-in before automatic installation resumes.

#### Scenario: Standalone to store
- **WHEN** a newer compatible store release replaces a standalone installation with automatic updating enabled
- **THEN** updater state is neutralized before it can perform new work, private configuration survives and the new server has store capabilities

#### Scenario: Old pending worker races with startup
- **WHEN** stored updater work becomes runnable before the first activity launch in the store installation
- **THEN** it cannot discover, download or install a release; persistent updater scheduling is cancelled without depending on opening the UI

#### Scenario: Store back to standalone
- **WHEN** a newer compatible standalone release replaces a store installation
- **THEN** private configuration survives and automatic updating remains disabled until the user explicitly enables it again

#### Scenario: User or installer refuses migration
- **WHEN** the user declines an upgrade or the platform rejects its version, certificate or installer ownership
- **THEN** the existing installation remains authoritative and Tsuyu does not bypass the rejection or report a successful migration

#### Scenario: Cleanup or replacement recovery fails
- **WHEN** migration cleanup fails or restart exceeds its deadline
- **THEN** store self-update remains impossible by construction, the incomplete operation is reported with recovery and no success is inferred from the package version alone

#### Scenario: Client and reboot continuity
- **WHEN** migration succeeds with persistence enabled and the service restarts immediately or after reboot
- **THEN** old binders die, clients observe the new binder through Shizuku.addBinderReceivedListenerSticky and checkSelfPermission() reflects preserved matching decisions

### Requirement: Compatible artifact identity and versioning
Each published release SHALL provide standalone `tsuyu.apk`, store `tsuyu-store.apk` and Play `tsuyu-store.aab` artifacts for the same source version. They SHALL use the existing package identity and tag-derived versionCode without per-variant offsets. The standalone asset name SHALL remain compatible with installed updater clients.

#### Scenario: Existing updater finds a release
- **WHEN** a previously published standalone version checks a new release
- **THEN** it finds the standalone tsuyu.apk with matching tag-derived versionCode, published size and digest rather than selecting the store artifact

#### Scenario: Artifact parity
- **WHEN** the three artifacts are inspected for a release
- **THEN** their version name, version code and source revision agree while updater capability follows the declared variant

#### Scenario: Same or lower version offered
- **WHEN** a channel has no newer eligible version or an attempted switch would downgrade
- **THEN** no immediate cross-channel replacement is promised and no version check is bypassed

### Requirement: Authenticated release publication
Published APKs SHALL carry the expected developer release certificate before any store transformation. Release publication SHALL fail for missing signing inputs, mismatched certificates, incomplete artifacts or failed required checks. The downloadable artifacts SHALL be associated with their source tag and checksums before the release becomes public.

#### Scenario: Complete signed release
- **WHEN** an authorized release build passes its checks and signature verification
- **THEN** both APKs and the AAB are staged with their source association and checksums before publication

#### Scenario: Signing configuration missing
- **WHEN** production signing inputs are absent or invalid
- **THEN** publication fails rather than publishing an unsigned or debug-signed substitute

#### Scenario: Partial upload or failed check
- **WHEN** a required build/check fails or an artifact upload is incomplete
- **THEN** the release does not become public as a complete release and retry does not silently replace an already published release

### Requirement: Store-delivered runtime equivalence
The store-delivered installation SHALL preserve the supported Android baseline, all five selectable languages and the existing service/client contracts, including from split APK delivery. Publication readiness SHALL be verified against the delivered format and applicable ABI/page-size requirements rather than inferred solely from a universal APK build.

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

### Requirement: Developer-signed F-Droid delivery
F-Droid publication SHALL use a reproducible store APK with the developer's expected release certificate. The source recipe SHALL identify the source revision, version and reference binary. A failed reproducibility or certificate check SHALL block that version's F-Droid publication rather than silently switching signing identity.

#### Scenario: Matching rebuild
- **WHEN** the independent recipe reproduces the release APK and its signature verifies against the allowed developer certificate
- **THEN** the matching developer-signed store APK is eligible for F-Droid publication

#### Scenario: Rebuild mismatch
- **WHEN** rebuilt contents or the reference certificate differ
- **THEN** the mismatch is diagnosed and the version remains unpublished through that recipe

#### Scenario: Compatible F-Droid upgrade
- **WHEN** a newer F-Droid-delivered store APK replaces a compatible standalone installation
- **THEN** the channel-migration contract applies without uninstalling or resetting its private data

### Requirement: Store publication readiness
Each store submission SHALL include accurate TV listing assets, privacy information, required declarations and reviewer setup instructions. Channel publication status SHALL distinguish prepared artifacts, testing, submitted review and live availability. Device-specific support SHALL follow device-validation evidence and store-delivered runtime acceptance.

#### Scenario: Play signing and testing
- **WHEN** the Play channel is prepared for publication
- **THEN** its delivered signing identity is checked for compatible upgrades and applicable account testing, TV-quality and permission declarations are completed before production rollout

#### Scenario: Store review requires action
- **WHEN** a store rejects, delays or requests information about a submission
- **THEN** that channel stays pending with the next action recorded and is not advertised as live

#### Scenario: Amazon transformed installation
- **WHEN** an Amazon-delivered build has been re-signed or wrapped by the store
- **THEN** its actual signature, runtime behavior and privacy/license implications are checked; incompatible-signature migration is documented as requiring reinstallation and reconfiguration

#### Scenario: Fire TV evidence unavailable
- **WHEN** no reviewed Fire TV result establishes usable local ADB, client operations and reboot recovery
- **THEN** Amazon device support remains unverified and publication for those devices is not represented as validated

#### Scenario: Account or publication inputs missing
- **WHEN** account verification, a privacy URL, required declarations or applicable testing cannot be completed
- **THEN** the affected submission remains blocked with its missing input recorded while independently ready channels can proceed
