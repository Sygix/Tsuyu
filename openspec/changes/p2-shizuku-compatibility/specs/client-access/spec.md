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
Tsuyu SHALL list authorized installed apps with name/icon and a visible per-app revoke action, including empty, loading and error states. Revocation SHALL immediately deny further privileged calls, terminate owned privileged processes/UserServices and remove persisted consent. This screen SHALL follow `tv-navigation`.

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
