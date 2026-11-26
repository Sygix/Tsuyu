## ADDED Requirements

### Requirement: Disable automatic startup
While automatic startup is enabled, the persistence screen SHALL offer « Désactiver le démarrage automatique ». Activating it SHALL stop future automatic boot and update restarts and the periodic authorization refresh, clear the stored authorization status and return the screen to its disclosure state. It SHALL NOT stop the running service, revoke client access or change debugging settings. The action SHALL follow `tv-navigation`.

#### Scenario: User disables automatic startup
- **WHEN** automatic startup is enabled and the user activates « Désactiver le démarrage automatique »
- **THEN** the screen shows the persistence disclosure with « Activer Tsuyu », the service state is unchanged and the action is no longer offered

#### Scenario: Reboot after disabling
- **WHEN** the TV reboots or Tsuyu is updated after automatic startup was disabled
- **THEN** no automatic restart or authorization refresh runs and home reports the service stopped with its start action

#### Scenario: User keeps automatic startup
- **WHEN** the user leaves the persistence screen with Back without activating the action
- **THEN** automatic startup stays enabled and home restores focus to its initiating action

#### Scenario: Storage unavailable or timeout
- **WHEN** the disabled state cannot be written within its deadline
- **THEN** the screen reports the persistence error with a retry, and automatic startup is not reported as disabled

#### Scenario: Focused action disappears
- **WHEN** disabling removes the focused action
- **THEN** focus moves to the current main action without edge wrapping

### Requirement: Renew debugging authorization while running
While automatic startup is enabled and the last authorization refresh reported a required authorization or a failure, the persistence screen SHALL offer « Renouveler l'autorisation » as its first, initially focused action. Activating it SHALL authenticate to adb with the app's key, show the authorization guidance while the system prompt waits and record that result, without restarting or stopping the server.

#### Scenario: User accepts the system prompt
- **WHEN** the user activates « Renouveler l'autorisation » and accepts « Toujours autoriser »
- **THEN** the warning disappears, the action is no longer offered and the running server was neither restarted nor stopped

#### Scenario: User refuses the system prompt
- **WHEN** the user refuses the system prompt or it is not answered within its deadline
- **THEN** the screen keeps the authorization-required message and the renewal action for another attempt

#### Scenario: Debugging unavailable
- **WHEN** adb is not reachable or the key cannot be loaded
- **THEN** the screen reports that the authorization could not be renewed and keeps the renewal action

#### Scenario: User cancels the wait
- **WHEN** the user activates « Annuler » while the system prompt waits
- **THEN** the renewal stops, the previous warning stays and focus returns to the renewal action

### Requirement: Authorization warning follows real authentication
The authorization warning SHALL clear only after an adb authentication with the app's key succeeds: a launch, the periodic refresh or a renewal. Manual recovery, repair or restart completion without authentication SHALL leave it unchanged.

#### Scenario: Repair while running
- **WHEN** the user activates « Réparer le démarrage » while the server is already running and the authorization warning is present
- **THEN** the warning remains until a renewal or a launch actually authenticates

#### Scenario: State after reboot
- **WHEN** the TV reboots before the authorization was renewed
- **THEN** the warning persists and a successful automatic restart that authenticates clears it

#### Scenario: Refresh fails again
- **WHEN** the periodic refresh fails after a renewal succeeded
- **THEN** the warning returns with the failure message and the renewal action
