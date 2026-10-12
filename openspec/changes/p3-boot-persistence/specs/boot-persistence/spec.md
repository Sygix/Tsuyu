## Purpose

Prepare supported TVs for a consented, bounded shell-service restart after boot and expose persistence readiness and recovery honestly.

## ADDED Requirements

### Requirement: Consented persistence preparation
Tsuyu SHALL explain each persistence mutation by its effect before applying it and display verified outcomes afterwards. The first successful shell start SHALL prepare the supported vendor auto-start access through manager-authenticated operations with read-back verification. A running server SHALL NOT alone imply persistence is ready.

#### Scenario: First setup succeeds
- **WHEN** the user enables Tsuyu after seeing the persistence explanation and the shell server grants and verifies required auto-start access
- **THEN** home separately reports running state and verified boot preparation

#### Scenario: User declines setup
- **WHEN** the user leaves the persistence explanation without enabling its preparation
- **THEN** Tsuyu applies no persistence mutation and does not claim reboot readiness

#### Scenario: Vendor operation missing
- **WHEN** the platform does not recognize the vendor auto-start operation
- **THEN** Tsuyu distinguishes an absent vendor gate from an operation failure, continues standard boot scheduling and does not claim vendor access was granted

#### Scenario: Grant denied or read-back timeout
- **WHEN** a required grant is denied or its verification exceeds the deadline
- **THEN** Tsuyu reports unverified persistence with a repair/retry action while retaining an accurate live-service state

#### Scenario: Grants after reboot
- **WHEN** home opens after a reboot
- **THEN** readiness reflects current system access and actual startup result rather than a cached successful grant

### Requirement: Bounded automatic boot restart
After successful enablement, Tsuyu SHALL schedule an idempotent background restart when boot delivery is available, reusing `local-start` and its private key. It SHALL wait only within a finite retry budget for required system services, avoid duplicate servers and never depend on wireless pairing, Internet connectivity or manual app launch for the normal supported boot path.

#### Scenario: Normal supported boot
- **WHEN** the TV reboots with USB debugging enabled, a still-authorized key and required boot access available
- **THEN** Tsuyu starts one shell server without opening its UI and P2 distributes the new binder; allowed clients observe granted access and shell uid after reattachment

#### Scenario: Delayed daemon
- **WHEN** boot delivery arrives before local debugging is ready
- **THEN** bounded background retries eventually start one server or store a recoverable startup failure

#### Scenario: User refuses renewed authorization
- **WHEN** the key requires renewed confirmation and the user refuses it
- **THEN** background startup grants nothing, records setup-required state and the next app opening exposes the key-confirmation recovery flow

#### Scenario: Boot support or endpoint unavailable
- **WHEN** boot delivery, system services or a supported local TCP endpoint is unavailable
- **THEN** the next app opening explains the missing prerequisite and offers repair/manual start without falsely claiming automatic restart

#### Scenario: Retry budget exhausted
- **WHEN** background startup exceeds its total deadline or finite attempts
- **THEN** it releases resources, stores an actionable failure and stops automatic retry until a subsequent relevant boot or user recovery

#### Scenario: Repeated boot and manual start requests
- **WHEN** repeated boot events or a manual start overlap
- **THEN** they use the same startup coordination and converge on one live shell server

### Requirement: Persistence status and recovery navigation
Home SHALL show boot-preparation status, completed access effects and actionable startup errors using French resources. Its repair/details screens SHALL follow `tv-navigation`, preserve current service and client-access states and avoid changing settings merely by viewing status.

#### Scenario: Verified ready state
- **WHEN** live-service and persistence checks succeed
- **THEN** home displays both results in understandable French and details identify what Tsuyu changed

#### Scenario: Failed boot recovery
- **WHEN** a background failure is present and the user opens Tsuyu
- **THEN** its recovery action receives initial focus and retry/repair can proceed without a phone or computer

#### Scenario: User leaves repair
- **WHEN** the user presses Back in repair/details
- **THEN** home restores focus to its initiating action and no new persistence mutation occurs

#### Scenario: Missing access or status deadline
- **WHEN** access has been removed or a status lookup times out
- **THEN** readiness becomes unavailable/unverified with a visible recovery action rather than silently claiming success

#### Scenario: Focused recovery disappears
- **WHEN** a successful state update removes the focused retry/repair action
- **THEN** focus moves to the current main action with no edge wrapping

#### Scenario: Status after another reboot
- **WHEN** a later reboot succeeds or fails differently
- **THEN** current results replace the previous boot outcome while client authorization continues to follow `client-access`
