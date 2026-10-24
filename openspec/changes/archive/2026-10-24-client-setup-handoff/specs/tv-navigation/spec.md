## MODIFIED Requirements

### Requirement: Centered permission surface and grouped access rows
Permission requests SHALL use a centered distinct surface containing the verified identity, access explanation and horizontal refusal/authorization actions. The « Accès des applications » list SHALL group each identity set, its textual status and its single visible action (revoke, reset of a refusal or cleanup retry) in one row surface. These compositions SHALL preserve `client-access` semantics and the Remote focus contract.

#### Scenario: Live request succeeds
- **WHEN** a live client calls `Shizuku.requestPermission(requestCode)` and the user selects Autoriser in the centered surface
- **THEN** its verified identity and any shared group are visible before consent; the matching callback receives PERMISSION_GRANTED and `checkSelfPermission()` observes the grant under `client-access`

#### Scenario: Refusal and remote navigation
- **WHEN** the request opens and the user navigates or refuses
- **THEN** Refuser initially holds focus, left/right reaches only the opposite action without wrapping, up/down stays within the decision, and refusal or Back returns to the caller with PERMISSION_DENIED

#### Scenario: Missing request or unavailable service
- **WHEN** the client disappears or the service/request cannot be resolved
- **THEN** no authorizing action remains usable; dismissal returns to the caller or a non-authorizing recovery action receives focus

#### Scenario: Expired request
- **WHEN** the existing pending-request deadline expires
- **THEN** the surface stops accepting confirmation, the surviving client receives denial, and no late activation grants access

#### Scenario: Consent after reboot
- **WHEN** the server restarts after a TV reboot
- **THEN** no old permission surface can confirm a pre-reboot request and `checkSelfPermission()` reflects only the persisted matching decision after reattachment

#### Scenario: List navigation and revocation
- **WHEN** the list opens, the user revokes or resets a group or the focused row disappears
- **THEN** the first row action initially holds focus, up/down follows visible rows without wrapping, left/right cannot leave its action, Back returns home to the list entry, and removal selects next row, previous row or return in that order; revoked clients observe denial and reset clients stay denied until a new request under `client-access`

#### Scenario: Empty loading or incomplete list operation
- **WHEN** the list is empty, loading, unavailable or has an incomplete revoke or reset
- **THEN** a readable state replaces any misleading success, the existing return/retry action remains reachable, and retry of incomplete cleanup remains a revoke retry rather than a simple refresh

## ADDED Requirements

### Requirement: Return to the opening application
When another installed app opens `MainActivity` in its own task and shares its identity through the platform launch identity, Tsuyu SHALL offer « Revenir à <label> » once the service is verified running and the persistence step has a final outcome. Activation SHALL finish Tsuyu so the caller is shown again. Tsuyu SHALL NOT name a caller from intent extras or the referrer, and SHALL NOT return automatically.

#### Scenario: Return after verified setup
- **WHEN** SygixOs opens Tsuyu with a shared identity and startup then preparation complete
- **THEN** « Revenir à SygixOs » appears as the main action with focus, and activating it shows SygixOs again; SygixOs then observes the binder through `Shizuku.addBinderReceivedListenerSticky`

#### Scenario: Already running at entry
- **WHEN** the caller opens Tsuyu while the service is running and persistence has a final outcome
- **THEN** the return action is present at once and receives initial focus

#### Scenario: Pending or failed setup
- **WHEN** startup or preparation is pending, refused by the user or failed
- **THEN** no return action is shown; recovery keeps focus and Back still returns to the caller

#### Scenario: Identity not shared or caller missing
- **WHEN** the launch carries no shared identity, Tsuyu launched itself or the caller package is no longer installed
- **THEN** no return action exists and Back keeps its current behavior

#### Scenario: Recreation and sub-screens
- **WHEN** the activity is recreated or the user returns from a sub-screen or Android settings
- **THEN** the return action keeps its label and availability, and focus is restored to the initiating control when present, otherwise to the return action

#### Scenario: State after reboot
- **WHEN** the TV reboots while Tsuyu was opened by a caller
- **THEN** no return action persists; a new caller launch is required

#### Scenario: Return action disappears
- **WHEN** a state change removes the focused return action
- **THEN** focus moves to the current main action under the Remote focus contract
