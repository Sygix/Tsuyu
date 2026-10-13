# tv-navigation Specification

## Purpose

Provide a readable, predictable remote-only TV interface with consistent focus, safe areas and recoverable navigation across Tsuyu screens.

## Requirements

### Requirement: Remote focus contract
Every Tsuyu screen SHALL define initial focus on its main action, except permission confirmation which SHALL initially focus refusal. D-pad moves SHALL follow visible spatial order without edge wrap-around. Back SHALL leave a sub-screen or exit home, and return navigation SHALL restore the initiating control or the current main action if it disappeared.

#### Scenario: Home and guided setup
- **WHEN** home, a setup step, an error or help opens
- **THEN** focus is on enable/start/retry, the step action, recovery or the help return action respectively

#### Scenario: Navigation at edges
- **WHEN** the user presses a direction at the first or last available control
- **THEN** focus stays within the visible controls without wrapping or escaping to off-screen content

#### Scenario: Back and return from settings
- **WHEN** the user presses Back in setup/help or returns from system settings
- **THEN** the previous screen appears with focus restored to its initiating control, or its main action when the control no longer exists

#### Scenario: Changed state removes focused control
- **WHEN** startup completion or another state transition removes the focused action
- **THEN** focus moves to the new main action or the first remaining visible control without requiring touch

#### Scenario: Home exit
- **WHEN** the user presses Back on home
- **THEN** Tsuyu exits to the previous application or TV launcher

### Requirement: Couch-readable French presentation
Tsuyu SHALL use French resource strings, a dark TV interface, safe-area padding and body text of at least 16 sp. Focus SHALL be visible without layout resizing; service state SHALL be expressed in text rather than color alone. Idle screens SHALL not animate or poll continuously, and transitions SHALL respect system animation scale.

#### Scenario: Readable stable state
- **WHEN** a screen has no pending operation or state change
- **THEN** it presents a stable textual status, visible focus and no repeating animation or refresh

#### Scenario: Error recovery
- **WHEN** an operation fails
- **THEN** the French screen names a likely cause and offers a visible recovery action usable with the remote

#### Scenario: Reduced motion
- **WHEN** system animations are disabled
- **THEN** state and focus remain legible without forced motion
