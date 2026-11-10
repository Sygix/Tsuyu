## Purpose

Make TV compatibility claims traceable to repeatable, release-specific tests and reviewed community evidence without exposing private device information.

## ADDED Requirements

### Requirement: Identifiable compatibility evidence
A compatibility report SHALL identify the Tsuyu version, variant, distribution source, verifiable public artifact, TV brand, Android version, applicable Fire OS version and test date. It SHALL distinguish physical-device testing from emulator testing and include the outcome and evidence for each required protocol stage.

#### Scenario: Complete report
- **WHEN** a contributor submits results for a known release artifact
- **THEN** a maintainer can associate each result with its tested software, device family and protocol stage without guessing from a screenshot alone

#### Scenario: Missing identity or evidence
- **WHEN** a report lacks artifact identity, OS information or required results
- **THEN** it remains reported/unverified and the missing information is requested before a validated claim is added

#### Scenario: Emulator report
- **WHEN** the baseline protocol passes on an emulator using a provisioned ADB fixture
- **THEN** the result is recorded as emulator/API evidence and does not establish that any consumer TV firmware exposes local ADB

### Requirement: Repeatable community test protocol
Tsuyu SHALL provide a public step-by-step protocol and structured report template covering installation, remote navigation, languages, local ADB authorization, verified service startup, Shizuku client access, revocation, boot recovery and signed upgrades. Each stage SHALL state its expected result, permitted evidence and how to report a failure or an unavailable test.

#### Scenario: Successful service and client stages
- **WHEN** a tester follows the startup and client stages with the supplied client instructions
- **THEN** evidence covers shell identity, Shizuku.pingBinder(), the binder-received listener, permission grant, remote-process output and bindUserService behavior rather than only a running home screen

#### Scenario: Refusal and revocation stages
- **WHEN** a tester refuses debugging or client access and later revokes a granted client
- **THEN** the report distinguishes setup refusal from client denial, records checkSelfPermission() and verifies that revoked privileged processes/services cease under the existing contracts

#### Scenario: Missing local endpoint or facility
- **WHEN** the device lacks a usable endpoint, settings screen, boot facility or required client test capability
- **THEN** the report names the blocked stage and observable recovery state rather than replacing missing results with a pass

#### Scenario: Timeout stage
- **WHEN** a startup, provider, persistence or installation operation reaches its deadline
- **THEN** the tester records the affected action, observed error/recovery and reproducibility without treating an unfinished operation as successful

#### Scenario: Reboot and upgrade stages
- **WHEN** a tester validates reboot and a newer signed upgrade
- **THEN** the report covers boot with the manager process absent, current server/binder recovery, preserved private configuration and matching consent; force-stop is distinguished from ordinary process absence

### Requirement: Channel-specific verification evidence
Compatibility validation SHALL exercise the tested distribution's update behavior. Store-delivery claims SHALL identify whether the tested installation came from the store or from a locally generated artifact. Channel-migration evidence SHALL use a newer compatible version and verify the release-distribution preservation and updater-neutralization contract.

#### Scenario: Standalone validation
- **WHEN** the standalone update stages are executed
- **THEN** the report covers manual verification/installation and explicit automatic-update opt-in using known signed release artifacts

#### Scenario: Store validation
- **WHEN** a store build is tested
- **THEN** the report confirms the absent updater UI and checks build/runtime evidence for absence of updater network and installation work; an ordinary UI screenshot alone does not prove that absence

#### Scenario: Delivered Amazon or Play build
- **WHEN** a report is used to validate store-transformed or split delivery
- **THEN** it identifies the actual store installation and certificate evidence rather than treating a sideloaded universal APK as equivalent

#### Scenario: Migration test unavailable
- **WHEN** a tester has no suitable newer artifact or cannot complete a migration stage
- **THEN** the stage is marked not tested and the report does not establish migration compatibility

### Requirement: Reviewed public compatibility matrix
The README SHALL list tested brands and OS versions with the tested release, variant/channel, evidence link and qualification. Status SHALL distinguish maintainer-tested, community-validated with reviewed evidence, reported/unverified, partial and unsupported results. A row SHALL describe only the tested configuration, not every model or firmware of that brand.

#### Scenario: Reviewed community result
- **WHEN** a maintainer verifies artifact identity and complete required evidence from a community report
- **THEN** its row is marked community-validated with the report link, without implying the maintainer independently reproduced the physical test

#### Scenario: Partial result
- **WHEN** startup passes but boot, client or another required stage is missing or fails
- **THEN** the row records the working and unverified/failing scope and is not promoted to full validation

#### Scenario: Regression in a later version
- **WHEN** reviewed evidence shows that a newer release fails a previously successful stage
- **THEN** the matrix records the affected release and current limitation while retaining the historical validated version

#### Scenario: No physical baseline report
- **WHEN** only emulator results exist for Android 9
- **THEN** they appear separately from the physical-TV matrix and no physical Android 9 TV is labelled validated

### Requirement: Privacy-preserving test evidence
The protocol and report template SHALL request only the device and application information needed for compatibility review. Public evidence SHALL omit serials, network addresses, accounts, private keys and unrelated device logs. Test instructions SHALL preserve an existing installation unless the tester explicitly chooses a fresh-install procedure.

#### Scenario: Focused diagnostic evidence
- **WHEN** a contributor reports a failure
- **THEN** instructions request the precise stage, expected/observed state, reproduction count and sanitized relevant screenshot or log excerpt rather than a full device dump

#### Scenario: Private data in a report
- **WHEN** evidence contains personal or machine-specific details
- **THEN** the maintainer requests a sanitized replacement and avoids copying those details into the README or repository artifacts

#### Scenario: Existing setup under test
- **WHEN** the tester already has a configured Tsuyu installation
- **THEN** the protocol offers an upgrade path and does not require uninstalling, revoking the ADB key or discarding consent simply to start testing
