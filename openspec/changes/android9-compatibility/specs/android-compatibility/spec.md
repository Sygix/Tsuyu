## Purpose

Define Tsuyu's Android baseline and preserve its authenticated service contracts across platform versions without overstating device compatibility.

## ADDED Requirements

### Requirement: Android baseline
Tsuyu SHALL support installation and its existing service contracts on Android 9 / API 28 and later when the required device facilities are available. All packaged modules SHALL share this minimum. Installation eligibility SHALL NOT imply that a firmware provides a usable local ADB endpoint or boot delivery.

#### Scenario: Baseline installation
- **WHEN** a user installs a valid Tsuyu release on an Android TV running API 28 with its required facilities
- **THEN** the manager opens and the existing local-start, client-access, shizuku-api and boot-persistence contracts apply without requiring a newer Android API

#### Scenario: User refuses debugging
- **WHEN** a user on a supported Android version refuses the debugging setup or key authorization
- **THEN** Tsuyu remains in the corresponding recoverable state under local-start and does not interpret OS compatibility as authorization

#### Scenario: Required facility missing
- **WHEN** a supported Android version runs on a device without a usable local legacy ADB endpoint
- **THEN** Tsuyu reports the missing prerequisite and offers the existing recheck/manual guidance without claiming the service can start

#### Scenario: Facility lookup times out
- **WHEN** a platform lookup exceeds its existing bounded deadline
- **THEN** the owning capability exposes its timeout/recovery state rather than concluding that the whole Android version is incompatible

#### Scenario: Supported device reboots
- **WHEN** a configured device reboots
- **THEN** Tsuyu resolves current platform state and follows boot-persistence; its Android version alone never supplies a successful restart result

### Requirement: Version-compatible authenticated platform operations
Tsuyu SHALL use platform operations available on the running Android version while preserving the authentication, consent, lifecycle and failure semantics of its existing capabilities. An absent or failing platform operation SHALL produce an actionable compatibility failure, not a successful placeholder or expanded privilege.

#### Scenario: Client contract on Android 9
- **WHEN** an installed requesting client starts on API 28 while Tsuyu is running
- **THEN** its binder-received listener fires, Shizuku.pingBinder() succeeds and checkSelfPermission() reflects its actual decision; authorized remote processes and bindUserService follow shizuku-api

#### Scenario: Refused or revoked client
- **WHEN** a refused or revoked client receives a binder on any supported version
- **THEN** checkSelfPermission() reports denial after any required reattachment and privileged operations fail under client-access

#### Scenario: Provider or service facility absent
- **WHEN** a required provider, observer or user-service loading facility is unavailable
- **THEN** the owning shizuku-api failure is exposed and no client is marked ready or given access from an incomplete operation

#### Scenario: Platform operation deadline
- **WHEN** provider delivery or service attachment exceeds the corresponding deadline
- **THEN** the operation follows the existing timeout/cleanup contract without blocking unrelated clients indefinitely

#### Scenario: Binder recovery after reboot
- **WHEN** the configured device reboots and the server restarts successfully
- **THEN** clients obtain a new binder through Shizuku.addBinderReceivedListenerSticky and matching decisions are restored; old process and UserService binders remain dead

### Requirement: Compatibility-preserving updates
Same-signature Tsuyu updates across supported Android versions SHALL preserve private ADB keys, selected language, persistence configuration and matching client decisions. Server replacement and restart SHALL remain governed by local-start and boot-persistence; the standalone updater SHALL retain self-update verification and failure semantics.

#### Scenario: Update succeeds
- **WHEN** a compatible signed update replaces Tsuyu on API 28 or a later supported version
- **THEN** the old server exits, the stored key and preferences survive, and reattached clients observe their preserved matching decision through checkSelfPermission()

#### Scenario: Update refused
- **WHEN** the user refuses an installation or the installer rejects its signature or version
- **THEN** the existing installation and private state remain and no successful replacement is reported

#### Scenario: Installer or restart unavailable
- **WHEN** the device cannot complete the requested install or deliver package replacement
- **THEN** Tsuyu exposes the existing install/restart failure and manual recovery rather than silently changing its update or consent policy

#### Scenario: Update outcome times out
- **WHEN** installation or replacement restart exceeds its deadline
- **THEN** the owning capability reconciles installed version or live service state before retry and does not fabricate a completed upgrade

#### Scenario: Reboot after update
- **WHEN** the updated device reboots
- **THEN** startup resolves the current installed APK and surviving private state rather than a previous classpath or cached running result
