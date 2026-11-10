## MODIFIED Requirements

### Requirement: Return to the opening application
On Android 14 and later, when another installed app opens `MainActivity` in its own task and shares its identity through the platform launch identity, Tsuyu SHALL offer « Revenir à <label> » once the service is verified running and the persistence step has a final outcome. Activation SHALL finish Tsuyu so the caller is shown again. Tsuyu SHALL NOT name a caller from intent extras or the referrer, and SHALL NOT return automatically. On Android 9–13, Tsuyu SHALL omit this action and retain normal Back navigation without resolving a named caller.

#### Scenario: Return after verified setup
- **WHEN** on Android 14 or later SygixOs opens Tsuyu with a shared identity and startup then preparation complete
- **THEN** « Revenir à SygixOs » appears as the main action with focus, and activating it shows SygixOs again; SygixOs then observes the binder through `Shizuku.addBinderReceivedListenerSticky`

#### Scenario: Already running at entry
- **WHEN** on Android 14 or later the caller opens Tsuyu while the service is running and persistence has a final outcome
- **THEN** the return action is present at once and receives initial focus

#### Scenario: Pending or failed setup
- **WHEN** startup or preparation is pending, refused by the user or failed
- **THEN** no return action is shown; recovery keeps focus and Back still returns to the caller

#### Scenario: Identity not shared or caller missing
- **WHEN** the launch carries no shared identity, Tsuyu launched itself or the caller package is no longer installed
- **THEN** no return action exists and Back keeps its current behavior

#### Scenario: Recreation and sub-screens
- **WHEN** on Android 14 or later the activity is recreated or the user returns from a sub-screen or Android settings
- **THEN** the return action keeps its label and availability, and focus is restored to the initiating control when present, otherwise to the return action

#### Scenario: State after reboot
- **WHEN** the TV reboots while Tsuyu was opened by a caller
- **THEN** no return action persists; a new caller launch is required

#### Scenario: Return action disappears
- **WHEN** a state change removes the focused return action
- **THEN** focus moves to the current main action under the Remote focus contract

#### Scenario: Android 9 through Android 13
- **WHEN** a client opens Tsuyu in its task on Android 9–13 and setup finishes or the service is already running
- **THEN** no named or replacement completion action appears, focus follows the current main action and Back finishes home to reveal the previous activity; lifecycle refresh does not call unavailable launch-identity APIs

#### Scenario: Untrusted identity on an older platform
- **WHEN** an Android 9–13 launch supplies a package name through extras or a referrer
- **THEN** it cannot create a caller label or named return action, and Back retains normal task-stack behavior

#### Scenario: Startup deadline before caller return
- **WHEN** startup or preparation times out after a client launch
- **THEN** no completed-setup return action appears; the owning recovery action has focus and Back remains available without granting client access

## ADDED Requirements

### Requirement: Language selection across supported Android versions
Tsuyu SHALL offer system language plus English, French, German, Spanish and Italian from home on every supported Android version, including before setup. The selected language SHALL apply across all screens and survive process death and reboot. System mode SHALL resolve supported system languages with English fallback; changing language SHALL NOT change access or persistence settings.

#### Scenario: Language selected
- **WHEN** the user selects a supported language on Android 9 or later
- **THEN** the visible screen and subsequently opened home, permission, persistence and available update screens use it, with the selected language still focused after recreation

#### Scenario: System language or unsupported locale
- **WHEN** system mode is selected and the system locale changes or contains no supported language
- **THEN** Tsuyu follows the supported system language or falls back to English without persisting that fallback as an explicit language choice

#### Scenario: User leaves without a selection
- **WHEN** the user presses Back without selecting a different language
- **THEN** the current choice remains and home restores focus to its language entry

#### Scenario: Platform locale facility unavailable
- **WHEN** the device runs Android 9–12 without the newer per-app locale facility
- **THEN** the same language choices and persistence are provided without loading or calling that unavailable facility

#### Scenario: Language persistence fails or times out
- **WHEN** storing or applying a selection fails or exceeds its bounded deadline
- **THEN** Tsuyu exposes an actionable error with retry/Back, keeps the last confirmed choice and does not claim the requested choice was saved

#### Scenario: Choice survives reboot
- **WHEN** the app starts after process death or a TV reboot
- **THEN** its first interactive screen uses the saved choice or current system mode rather than requiring the language screen to be opened first

#### Scenario: Android upgrade preserves language
- **WHEN** a device with an explicit Tsuyu language choice upgrades from Android 9–12 to Android 13 or later
- **THEN** that choice is preserved when no newer explicit platform choice exists, and subsequent platform language changes are not overwritten by the old preference

#### Scenario: Language remote navigation
- **WHEN** the language screen opens or the user navigates its controls
- **THEN** the selected choice has initial focus, directions follow visible order without wrapping, Back returns to the initiating home entry, and a disappearing focused control falls back under the Remote focus contract
