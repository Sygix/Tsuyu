## MODIFIED Requirements

### Requirement: Authorized apps and effective revocation
Tsuyu SHALL present one « Accès des applications » list of the installed identity groups that hold an authorized, refused or pending-cleanup decision, with name/icon, a textual status and exactly one action per group: « Retirer l'accès » for authorized groups, « Autoriser une nouvelle demande » for refused groups and the revoke retry for pending cleanup. It SHALL include empty, loading and error states. Revocation SHALL immediately deny further privileged calls, terminate owned privileged processes/UserServices and remove persisted consent. This screen SHALL follow `tv-navigation`.

#### Scenario: Successful revoke
- **WHEN** the user revokes an authorized app
- **THEN** it leaves the list, its later `checkSelfPermission()` observes denial after any required reattachment, its privileged calls fail and its owned processes/services cease

#### Scenario: Refused group is listed
- **WHEN** an installed client whose request was refused is present
- **THEN** its group appears with status « Accès refusé » and the action « Autoriser une nouvelle demande », after the authorized groups

#### Scenario: User leaves without revoking
- **WHEN** the user presses Back before activating an action
- **THEN** existing decisions remain and home restores focus to the « Accès des applications » entry

#### Scenario: Missing app or server
- **WHEN** the app is uninstalled or the server is unavailable
- **THEN** stale entries are reconciled or an actionable unavailable state appears; Tsuyu does not falsely report a completed live revoke or reset

#### Scenario: Revocation timeout
- **WHEN** revocation cannot complete within its deadline
- **THEN** Tsuyu distinguishes persisted denial from unfinished process cleanup and offers retry rather than displaying full success

#### Scenario: Navigation and disappearing row
- **WHEN** the list opens, the user navigates at its edges or a focused revoked, reset or uninstalled row disappears
- **THEN** focus begins on the first row action or empty-state return action, does not wrap and moves to the next row, previous row or return action in that order

#### Scenario: Revocation survives reboot
- **WHEN** a revoked client reconnects after reboot
- **THEN** it has no inherited grant and must request access again through the live server

## ADDED Requirements

### Requirement: Refusal reset to undecided
Activating « Autoriser une nouvelle demande » SHALL clear the recorded refusal of that identity group through a manager-only server operation with a bounded deadline. It SHALL NOT grant access, terminate processes or reuse revocation wording. The client's next `Shizuku.requestPermission` SHALL open the normal confirmation with « Refuser » initially focused.

#### Scenario: Reset then new request
- **WHEN** the user resets a refused group and the client later calls `Shizuku.requestPermission(requestCode)` after reattachment
- **THEN** `checkSelfPermission()` stays denied until the decision, `shouldShowRequestPermissionRationale()` returns false, the confirmation opens with « Refuser » focused and the listener receives the matching result

#### Scenario: Refused again
- **WHEN** the user refuses the new request
- **THEN** the listener receives PERMISSION_DENIED, the group returns to the list as refused and rationale reports the recorded refusal again

#### Scenario: Client or identity missing
- **WHEN** the client is uninstalled or its identity no longer matches the recorded group before the reset completes
- **THEN** the row is removed, no decision is written for another identity and the list shows the current state

#### Scenario: Reset storage failure or timeout
- **WHEN** the server cannot persist the reset or the deadline expires
- **THEN** the row keeps status « Accès refusé », an error names the cause and the action remains focused for retry; no partial grant exists

#### Scenario: Reset after reboot
- **WHEN** the TV reboots after a successful reset
- **THEN** the group stays undecided, no request survives and the client must call `requestPermission` again
