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

### Requirement: Optional global key-expiry disabling
Tsuyu SHALL offer an explicit optional action to disable global ADB key expiry and explain its effect on all authorized keys beforehand. It SHALL preserve the current policy without explicit choice, verify a requested change and report failure without blocking normal enablement. Viewing or rebooting SHALL NOT apply this setting change.

#### Scenario: Explicit opt-in succeeds
- **WHEN** the user chooses expiry disabling after its global-effect explanation
- **THEN** Tsuyu sets `adb_allowed_connection_time` to zero through authenticated shell access and shows success only after read-back verification

#### Scenario: Optional action declined
- **WHEN** the user keeps the current policy, skips the option or presses Back before activating it
- **THEN** the policy remains untouched, Tsuyu can still be enabled and the long-offline renewed-confirmation limitation is explained when expiry remains enabled

#### Scenario: Setting or privilege unavailable
- **WHEN** the setting cannot be read/written or the shell server is unavailable
- **THEN** Tsuyu reports that the optional change is unavailable, offers recovery and does not claim expiry was disabled

#### Scenario: Optional operation times out
- **WHEN** the write or read-back exceeds its deadline
- **THEN** the outcome is unverified, Tsuyu reconciles actual state before retry and does not claim that a timed-out write definitely had no effect

#### Scenario: Optional setting after reboot
- **WHEN** the TV reboots or the user changes the expiry policy elsewhere
- **THEN** Tsuyu reports the current observed policy and does not restore a stored opt-in automatically

#### Scenario: Optional action navigation
- **WHEN** the explanation opens or returns from details
- **THEN** keeping the current policy holds initial focus, D-pad reaches the explicit disabling action without wrapping, Back preserves the setting and returns focus to the initiating control; if an action disappears, focus falls back under `tv-navigation`

### Requirement: Respect manual debugging disabling
Tsuyu SHALL respect USB debugging being disabled in system settings and SHALL NOT automatically re-enable it during startup, retry or reboot. It SHALL explain the unavailable automatic-start prerequisite and offer the existing setup step without modifying debugging on the user's behalf.

#### Scenario: User disables debugging
- **WHEN** USB debugging is disabled after Tsuyu enablement
- **THEN** a subsequent startup stops with setup-required status and offers settings recovery without changing debugging

#### Scenario: User restores debugging
- **WHEN** the user explicitly re-enables USB debugging in system settings and starts recovery
- **THEN** Tsuyu rechecks the prerequisite and follows `local-start` with the existing key

#### Scenario: Debugging state unavailable or timed out
- **WHEN** debugging state cannot be read or its check exceeds the deadline
- **THEN** Tsuyu reports unknown/unverified state with recovery rather than assuming permission to enable it

#### Scenario: Disabled debugging after reboot
- **WHEN** the TV reboots with USB debugging disabled
- **THEN** boot startup neither re-enables it nor claims success, and the next app opening explains the required manual setup step

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
