## MODIFIED Requirements

### Requirement: Authenticated server-managed consent
Tsuyu SHALL manage access independently of Android permission grants, authenticate requesting uid/pid/package and bind confirmations to live pending requests. It SHALL persist decisions without allowing uid reuse or package replacement by an unrelated signer to inherit consent. All privileged operations SHALL check current authorization. Only an explicit user answer SHALL be recorded as a decision.

#### Scenario: User grants access
- **WHEN** an attached client calls `Shizuku.requestPermission(requestCode)` and the user authorizes its verified pending request
- **THEN** its upstream result listener receives the same requestCode with PERMISSION_GRANTED and `checkSelfPermission()` observes granted access

#### Scenario: User refuses
- **WHEN** the user chooses “Refuser” or dismisses an undecided request with Back
- **THEN** the matching listener receives PERMISSION_DENIED, no privilege is granted and rationale follows the server's recorded refusal state

#### Scenario: Missing requesting app or screen
- **WHEN** a request's client dies, its package disappears or the confirmation screen cannot be opened
- **THEN** the request expires without granting access or recording a refusal; a surviving client receives denial and Tsuyu offers recovery for the screen failure

#### Scenario: Pending-request deadline
- **WHEN** a request remains unanswered past its bounded deadline
- **THEN** it expires with denial without recording a refusal, a late confirmation cannot authorize it or another request, and the client's next request opens the confirmation again

#### Scenario: Decisions after reboot
- **WHEN** the TV reboots and the server restarts
- **THEN** persisted consent remains for matching installed identities, pending requests do not survive and a new unrelated installation inherits no access

#### Scenario: Manager update preserves decisions
- **WHEN** Tsuyu is updated with the same signing key and its shell server restarts
- **THEN** persisted matching client decisions survive and `checkSelfPermission()` after new-binder attachment reflects the preserved grant or denial

## ADDED Requirements

### Requirement: Recorded refusal answers without a prompt
A recorded refusal SHALL answer later permission requests of the same identity group with denial and SHALL NOT open the confirmation screen until the user resets it in « Accès des applications ».

#### Scenario: Refused client requests again
- **WHEN** a client whose identity group has a recorded refusal calls `Shizuku.requestPermission(requestCode)` again
- **THEN** its listener receives PERMISSION_DENIED for that requestCode, `shouldShowRequestPermissionRationale()` returns true and no confirmation screen opens

#### Scenario: Refusal reset
- **WHEN** the user resets the refused group and the client requests permission again
- **THEN** the confirmation screen opens following "Refusal reset to undecided"

#### Scenario: Refusal after reboot
- **WHEN** the TV reboots and a client with a persisted refusal requests permission
- **THEN** it receives PERMISSION_DENIED without a confirmation screen
