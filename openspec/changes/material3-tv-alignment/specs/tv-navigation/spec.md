## MODIFIED Requirements

### Requirement: Couch-readable French presentation
Tsuyu SHALL use French resource strings, a dark Material 3 TV interface, safe-area padding and body text of at least 16 sp. Focus SHALL be visible without layout resizing; service state SHALL be expressed in text rather than color alone. Idle screens SHALL not animate or poll continuously, and transitions SHALL respect system animation scale.

#### Scenario: Readable stable state
- **WHEN** a screen has no pending operation or state change
- **THEN** it presents a stable textual status, visible focus and no repeating animation or refresh

#### Scenario: Error recovery
- **WHEN** an operation fails
- **THEN** the French screen names a likely cause and offers a visible recovery action usable with the remote

#### Scenario: Reduced motion
- **WHEN** system animations are disabled
- **THEN** state and focus remain legible without forced motion

#### Scenario: Material presentation across journeys
- **WHEN** the user moves between home, setup, access, persistence, expiry, help, details and recovery
- **THEN** the same cyan/navy visual roles, typography hierarchy, rounded surfaces and focused-action treatment apply throughout

## ADDED Requirements

### Requirement: Shared visual roles and action states
Tsuyu SHALL present consistent resting, focused, unavailable and destructive action states across existing screens. Only the currently focused action SHALL carry the focus treatment. Attention statuses SHALL pair their visual treatment with readable text. Appearance SHALL NOT imply a permission or operation succeeded without the owning capability's verification.

#### Scenario: Focused action
- **WHEN** D-pad navigation moves between actions
- **THEN** the focused action uses the cyan container and contrasting label with a slight scale that does not reflow its neighbours; the previous action returns to its resting style

#### Scenario: Unavailable or destructive action
- **WHEN** an existing operation makes an action unavailable or a revoke action is presented
- **THEN** an unavailable action cannot receive focus or activate, and removal is named explicitly; a focused revoke remains unmistakably focused

#### Scenario: Action disappears
- **WHEN** availability or a completed operation removes the focused control
- **THEN** the Remote focus contract selects the current main action, with the authorized-list fallback governed by `client-access`

### Requirement: Information and action composition
State screens SHALL place explanatory content on the left and a vertically ordered action column on the right at the reference TV viewport. Text, controls and focus enlargement SHALL remain inside a five-percent safe area. Content SHALL wrap or scroll accessibly rather than shrink below the established body-text minimum or overlap actions.

#### Scenario: State-screen layout
- **WHEN** home, setup, persistence, expiry, help, details or recovery opens
- **THEN** its title and explanatory/status content form a left information hierarchy and its existing actions form a separate right column, with no additional navigation destination

#### Scenario: Remote action column
- **WHEN** a state screen opens or its user presses a directional key or Back
- **THEN** initial focus follows the Remote focus contract and existing safety defaults, up/down follows the visible action order, left/right and column edges do not wrap, and Back exits home or returns to the previous screen

#### Scenario: Return or changed action set
- **WHEN** the user returns from an app sub-screen or Android settings, or a state update removes the initiating action
- **THEN** focus returns to that action when present, otherwise to the current main action without escaping to a decorative status or panel

#### Scenario: Long content and names
- **WHEN** text enlargement, long application names or a shared group needs more space
- **THEN** identities, warnings and focused controls remain readable and reachable with D-pad scrolling inside the safe area, without truncating essential consent information

### Requirement: Centered permission surface and grouped access rows
Permission requests SHALL use a centered distinct surface containing the verified identity, access explanation and horizontal refusal/authorization actions. Authorized-app lists SHALL group each identity set and its visible revoke action in one row surface. These compositions SHALL preserve `client-access` semantics and the Remote focus contract.

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
- **WHEN** the list opens, the user revokes a group or the focused row disappears
- **THEN** the first revoke action initially holds focus, up/down follows visible rows without wrapping, left/right cannot leave its action, Back returns home to the list entry, and removal selects next row, previous row or return in that order; revoked clients observe denial under `client-access`

#### Scenario: Empty loading or incomplete list operation
- **WHEN** the list is empty, loading, unavailable or has an incomplete revoke
- **THEN** a readable state replaces any misleading success, the existing return/retry action remains reachable, and retry of incomplete cleanup remains a revoke retry rather than a simple refresh

### Requirement: Operation-bound feedback and supporting information
Pending operations SHALL have a visible textual loading state and indeterminate progress without fabricated percentages. Service liveness, boot preparation and attention messages SHALL be visually distinct. Supporting panels SHALL retain mandatory warnings and current verified information without initiating an operation when merely displayed.

#### Scenario: Verified operation completion
- **WHEN** startup or persistence verification completes successfully
- **THEN** progress stops and the corresponding verified textual result appears; boot preparation never substitutes for live service verification

#### Scenario: Refused prerequisite or optional policy
- **WHEN** the user declines debugging, persistence preparation or optional expiry disabling
- **THEN** the presentation retains the owning capability's incomplete or unchanged outcome, without a success badge or a mutation triggered by viewing a panel

#### Scenario: Missing system feature
- **WHEN** settings, a startup facility or a vendor operation is unavailable
- **THEN** its distinct unavailable/unverified result and existing manual guidance or recovery action are visible without relabelling it as verified readiness

#### Scenario: Operation deadline
- **WHEN** the owning operation reaches its bounded deadline
- **THEN** loading ends and actionable error or unverified-outcome text appears; a possibly completed global-policy write is reconciled before retry under `boot-persistence`

#### Scenario: Feedback after reboot
- **WHEN** Tsuyu opens after reboot
- **THEN** current observed liveness and preparation results replace any previous progress or cached success, with recovery focused when the last restart failed

#### Scenario: Optional policy navigation
- **WHEN** the expiry explanation opens, the user navigates, leaves or returns
- **THEN** keeping the policy initially holds focus, up/down follows the right action column without edge wrapping, left/right stays in the column, Back preserves policy and restores the initiating action, and a removed control falls back under the Remote focus contract

#### Scenario: Required warnings and technical details
- **WHEN** setup, access, persistence, expiry or help presents explanatory content
- **THEN** it retains the applicable local-network, debugging, group-wide access, process-termination and global-policy warnings from the owning capability; internal protocol names, numeric identifiers and raw policy units are confined to Détails or explicitly labelled technical details rather than the primary explanation
