# local-start Specification

## Purpose

Enable a shell-uid service locally from an Android TV remote, with private-key authentication and an honest, recoverable startup state.

## Requirements

### Requirement: Guided debugging setup
Tsuyu SHALL guide users to enable developer options and USB debugging, detect completion and explain in French that the port remains open on the local network but only authorized devices can connect. It SHALL offer no close-port action.

#### Scenario: Setup succeeds
- **WHEN** the user enables USB debugging and returns from settings
- **THEN** setup detects the enabled state and advances to local authentication without requiring wireless pairing

#### Scenario: User declines debugging
- **WHEN** the user returns without enabling USB debugging
- **THEN** setup remains incomplete and offers to reopen settings without changing debugging itself

#### Scenario: Settings unavailable
- **WHEN** the device cannot open the developer settings screen or expose its state
- **THEN** Tsuyu gives manual French instructions and distinguishes an unknown state from disabled debugging

#### Scenario: Settings check times out
- **WHEN** a settings check exceeds its deadline
- **THEN** Tsuyu stops waiting and offers retry or manual setup instructions

#### Scenario: Setup after reboot
- **WHEN** the app opens after a TV reboot
- **THEN** it reads current debugging and server state rather than displaying the pre-reboot running state

### Requirement: Private local ADB authentication
Tsuyu SHALL discover and validate a local legacy TCP ADB endpoint and authenticate with its own persistent, app-private RSA key. It SHALL bound connection and authentication waits, reject malformed protocol input and never expose keys or accept a TLS pairing flow.

#### Scenario: Previously accepted key
- **WHEN** the local daemon recognizes Tsuyu's key
- **THEN** authentication succeeds without a new system confirmation

#### Scenario: Unknown key accepted
- **WHEN** the daemon requests a public key and the user selects “Always allow” and accepts on the system screen
- **THEN** Tsuyu completes authentication with the same private key and proceeds to launch

#### Scenario: Authorization refused or dismissed
- **WHEN** the user refuses or dismisses the system key prompt
- **THEN** Tsuyu never reports authentication success and offers retry after the rejection or bounded wait ends

#### Scenario: No supported endpoint
- **WHEN** debugging is enabled but discovery finds no usable legacy TCP endpoint, or the endpoint requests TLS
- **THEN** Tsuyu explains that v1 requires local TCP debugging, offers a recheck and does not claim certainty about unavailable device information

#### Scenario: Authentication timeout or bad packet
- **WHEN** connection, prompt waiting or packet validation fails
- **THEN** the connection closes, no launch occurs and a distinct French recovery instruction is shown

#### Scenario: Key reuse after reboot
- **WHEN** the TV reboots and the user starts Tsuyu again
- **THEN** the existing app-private key is reused; if authorization has expired, Tsuyu returns to the system confirmation step

#### Scenario: Key reuse after debugging is re-enabled
- **WHEN** the user re-enables debugging after it terminated the server and starts Tsuyu again
- **THEN** Tsuyu reuses the existing private key and retained authorization without requiring revocation or reinstallation

### Requirement: Verified shell server lifecycle
Tsuyu SHALL launch only its packaged server with shell rights through authenticated local ADB. Running state SHALL require an authenticated, live manager connection reporting shell uid. Duplicate starts SHALL converge on one server; server death SHALL invalidate running state.

#### Scenario: Launch succeeds
- **WHEN** authentication succeeds and the packaged server starts and returns a valid live manager connection
- **THEN** home shows running only after shell uid is verified

#### Scenario: User cancels startup
- **WHEN** the user leaves the startup flow before completion
- **THEN** pending app-side startup work is cancelled and the next home observation reflects actual server state

#### Scenario: Launch facilities missing
- **WHEN** the executable, packaged server or manager connection facility is unavailable
- **THEN** Tsuyu shows a launch-specific error with retry instructions instead of running

#### Scenario: Launch deadline exceeded
- **WHEN** the server does not establish a valid manager connection within the launch deadline
- **THEN** Tsuyu releases app-side resources and offers retry without starting duplicate live servers; it identifies a launch timeout rather than suggesting authorization failed after successful authentication

#### Scenario: Concurrent requests and death
- **WHEN** two starts overlap or the live server dies
- **THEN** starts are serialized and a death notification changes running state to stopped with a start action

#### Scenario: Manual restart after reboot
- **WHEN** a TV reboot terminates the server
- **THEN** P1 exposes a manual start action; automatic reboot restart is owned by `boot-persistence` in P3

### Requirement: Detached server survival
Tsuyu SHALL launch its server in a new session, with redirected standard streams and no hang-up dependency on the launch stream. It SHALL wait for deterministic server readiness before releasing the launch shell. Closing the ADB connection SHALL NOT terminate an already running server. Tsuyu SHALL tell users to keep debugging enabled during use because disabling it can terminate the server through the device's adbd cgroup policy.

#### Scenario: Launch connection closes
- **WHEN** Tsuyu closes its ADB connection after verifying launch
- **THEN** the shell server continues running and its manager binder remains live

#### Scenario: Debugging disabled while running
- **WHEN** the user disables debugging while the server is running
- **THEN** Tsuyu observes actual server liveness, invalidates running state if the server dies and offers debugging setup followed by manual start with the stored key; it never re-enables debugging automatically or promises server survival

#### Scenario: Detachment unavailable or timed out
- **WHEN** session detachment, redirection or its launch verification is unavailable or exceeds its deadline
- **THEN** Tsuyu reports a startup failure instead of claiming durable running state

#### Scenario: Reboot ends detached server
- **WHEN** the TV reboots
- **THEN** the detached process dies and restart follows the phase-specific startup contract

### Requirement: Manager package lifecycle
The running server SHALL exit when Tsuyu is replaced or removed, releasing owned resources and invalidating its manager binder. The private ADB key SHALL survive same-signature updates; removal SHALL NOT leave an orphan server using a removed APK. Automatic restart after replacement belongs to `boot-persistence`.

#### Scenario: Same-signature update while running
- **WHEN** Tsuyu is replaced by an update signed with the same key
- **THEN** the old server exits and its binder dies, app-private key storage survives and a subsequent launch uses the current installed APK

#### Scenario: Uninstall while running
- **WHEN** Tsuyu is uninstalled while the server is running
- **THEN** the server exits rather than remaining privileged with a removed manager package

#### Scenario: Unconfirmed or failed update
- **WHEN** the user refuses an update or installation fails without replacing the package
- **THEN** the existing server and private key are not changed by Tsuyu

#### Scenario: Package observation unavailable or timed out
- **WHEN** required package lifecycle observation cannot be established or bounded reconciliation fails
- **THEN** launch fails or the server exits with a lifecycle-specific diagnostic instead of running without replacement/removal protection

#### Scenario: Updated package after reboot
- **WHEN** Tsuyu starts after an update and TV reboot
- **THEN** startup resolves the current installed APK and reuses the surviving private key rather than retaining the old classpath
