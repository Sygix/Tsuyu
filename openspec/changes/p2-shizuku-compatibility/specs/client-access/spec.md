## Purpose

Ensure client applications receive shell access only after explicit TV-user consent, with understandable requests and effective revocation.

## ADDED Requirements

### Requirement: Authenticated server-managed consent
Tsuyu SHALL manage access independently of Android permission grants, authenticate requesting uid/pid/package and bind confirmations to live pending requests. It SHALL persist decisions without allowing uid reuse or package replacement by an unrelated signer to inherit consent. All privileged operations SHALL check current authorization.

#### Scenario: User grants access
- **WHEN** an attached client calls `Shizuku.requestPermission(requestCode)` and the user authorizes its verified pending request
- **THEN** its upstream result listener receives the same requestCode with PERMISSION_GRANTED and `checkSelfPermission()` observes granted access

#### Scenario: User refuses
- **WHEN** the user chooses “Refuser” or dismisses an undecided request with Back
- **THEN** the matching listener receives PERMISSION_DENIED, no privilege is granted and rationale follows the server's recorded refusal state

#### Scenario: Missing requesting app or screen
- **WHEN** a request's client dies, its package disappears or the confirmation screen cannot be opened
- **THEN** the request expires without granting access; a surviving client receives denial and Tsuyu offers recovery for the screen failure

#### Scenario: Pending-request deadline
- **WHEN** a request remains unanswered past its bounded deadline
- **THEN** it expires with denial and a late confirmation cannot authorize it or another request

#### Scenario: Decisions after reboot
- **WHEN** the TV reboots and the server restarts
- **THEN** persisted consent remains for matching installed identities, pending requests do not survive and a new unrelated installation inherits no access

### Requirement: Remote permission confirmation
The request screen SHALL open over the requesting app, identify it with verified name/icon and explain the effect of access in French. It SHALL show “Autoriser” and “Refuser”, initially focus “Refuser” and follow `tv-navigation`. Completing or leaving the request SHALL return to the requesting app without granting on dismissal.

#### Scenario: Request screen navigation
- **WHEN** a live request opens
- **THEN** refusal holds focus, left/right reaches the visible opposite action without wrapping, up/down cannot escape the screen and center activates only the focused action

#### Scenario: Back and caller return
- **WHEN** the user presses Back or completes confirmation
- **THEN** the requesting app reappears; Back refuses the pending request rather than approving it

#### Scenario: Request disappears under focus
- **WHEN** the requesting process dies or the request expires while its action is focused
- **THEN** confirmation actions become invalid and the screen closes or shows a non-authorizing recovery action with valid focus

### Requirement: Authorized apps and effective revocation
Tsuyu SHALL list authorized installed apps with name/icon and a visible revoke action for each app or shared-UID group, including empty, loading and error states. Revocation SHALL immediately deny further privileged calls, terminate owned privileged processes/UserServices and remove persisted consent. This screen SHALL follow `tv-navigation`.

#### Scenario: Successful revoke
- **WHEN** the user revokes an authorized app
- **THEN** it leaves the authorized list, its later `checkSelfPermission()` observes denial after any required reattachment, its privileged calls fail and its owned processes/services cease

#### Scenario: User leaves without revoking
- **WHEN** the user presses Back before activating revoke
- **THEN** existing grants remain and home restores focus to the authorized-apps entry

#### Scenario: Missing app or server
- **WHEN** the app is uninstalled or the server is unavailable
- **THEN** stale entries are reconciled or an actionable unavailable state appears; Tsuyu does not falsely report a completed live revoke

#### Scenario: Revocation timeout
- **WHEN** revocation cannot complete within its deadline
- **THEN** Tsuyu distinguishes persisted denial from unfinished process cleanup and offers retry rather than displaying full success

#### Scenario: Navigation and disappearing row
- **WHEN** the list opens, the user navigates at its edges or a focused revoked/uninstalled row disappears
- **THEN** focus begins on the first revoke action or empty-state return action, does not wrap and moves to the next row, previous row or return action in that order

#### Scenario: Revocation survives reboot
- **WHEN** a revoked client reconnects after reboot
- **THEN** it has no inherited grant and must request access again through the live server

### Requirement: Explicit shared-UID consent
Tsuyu SHALL treat installed applications sharing an Android user/UID as one access group. Permission requests and authorized-app views SHALL identify all related applications and explain that granting or revoking affects the group. Persisted consent SHALL be invalidated when the verified group identity changes.

#### Scenario: Group authorization
- **WHEN** a client sharing its uid calls `Shizuku.requestPermission` and the user grants after seeing the related applications
- **THEN** attached group members observe granted access through `checkSelfPermission()` and the requesting client receives its matching granted callback

#### Scenario: Group refusal and revocation
- **WHEN** the user refuses the group request or revokes a displayed group
- **THEN** no group member receives access from that request, or every member's existing grant and owned privileged children are revoked respectively

#### Scenario: Identity resolution unavailable
- **WHEN** related package identities cannot be resolved
- **THEN** Tsuyu does not offer an incomplete group authorization and reports an actionable identity error

#### Scenario: Group lookup timeout
- **WHEN** group identity lookup exceeds its deadline
- **THEN** confirmation cannot grant access using an unchecked member set and exposes retry or refusal

#### Scenario: Membership change after reboot
- **WHEN** reboot or package reconciliation reveals a changed member/signing identity set
- **THEN** previous group consent is invalidated and clients observe denial until the changed group is explicitly authorized

#### Scenario: Group view navigation
- **WHEN** a shared-UID group appears in a request or authorized-app screen
- **THEN** related names are visible without a hidden gesture, actions retain the screen's initial focus and edge/Back/restoration behavior from `tv-navigation`
