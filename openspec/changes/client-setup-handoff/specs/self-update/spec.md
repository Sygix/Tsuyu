## ADDED Requirements

### Requirement: Release discovery
Tsuyu SHALL obtain versions with one unauthenticated HTTPS request to the public releases of its repository, with bounded connect, read and total timeouts and no personal data, identifier, token or cookie. It SHALL offer the eligible release whose `versionCode`, computed with the build formula, is the highest and strictly greater than the installed one. Prereleases SHALL be offered only when « Inclure les préversions » is enabled, which is disabled by default.

#### Scenario: Newer release available
- **WHEN** a check succeeds and an eligible release has a greater `versionCode`
- **THEN** « Version X disponible » is stored and shown, with « Mettre à jour vers X »

#### Scenario: Up to date or ineligible releases
- **WHEN** no eligible release is newer, or a release is a draft or lacks an exact `vX.Y.Z` or `vX.Y.Z-(alpha|beta|rc).N` tag or a fully uploaded HTTPS `tsuyu.apk` asset with published size and `sha256:` digest
- **THEN** « Tsuyu est à jour » is shown and ineligible releases are ignored without error

#### Scenario: Prerelease opt-in
- **WHEN** the newest eligible release is a prerelease and « Inclure les préversions » is off
- **THEN** it is not offered; turning the option on recomputes the offer from known releases without a new request

#### Scenario: Network unavailable or timeout
- **WHEN** the request cannot connect, times out or returns an unreadable answer
- **THEN** the check ends with a stored cause (« Réseau indisponible », « Délai dépassé », « Réponse illisible ») and a previously offered version stays offered

#### Scenario: Rate limit
- **WHEN** the API limits the request
- **THEN** the published retry time, otherwise one hour later, is persisted and no automatic check is sent before it, even after reboot; a manual check still sends its request

#### Scenario: State after reboot
- **WHEN** the TV reboots
- **THEN** the update screen shows the stored offer or last result without a new request until an automatic check is due

### Requirement: Update checks
« Rechercher les mises à jour » SHALL always send a check. An automatic check SHALL run at most once per 24 hours, only with a validated network and after any stored retry time: when home opens while « Mises à jour automatiques » is off, and as a periodic background job while it is on. Automatic checks SHALL be silent and only change stored state shown on the update screen and home.

#### Scenario: Automatic check due
- **WHEN** home opens more than 24 hours after the last check with auto-update off
- **THEN** a background check runs without dialog or focus change

#### Scenario: Automatic check not due
- **WHEN** the last check is less than 24 hours old or the retry time is in the future
- **THEN** no request is sent

#### Scenario: Automatic check fails
- **WHEN** an automatic check fails
- **THEN** no message interrupts the user; the cause and time are stored and shown on the update screen

#### Scenario: Background job after reboot
- **WHEN** the TV reboots with auto-update on
- **THEN** the periodic check stays scheduled and respects the 24-hour and retry-time rules

### Requirement: Verified update download
Before installation Tsuyu SHALL re-read the offered release, download `tsuyu.apk` over HTTPS into app cache within a bounded total time and check in order: published size, published SHA-256, package `fr.sygix.tsuyu`, `versionCode` equal to the tag and above the installed one, signing certificates identical to the installed ones. Any failure SHALL delete the file, install nothing and store its cause.

#### Scenario: Verified file
- **WHEN** every check passes
- **THEN** the file is handed to installation and its temporary copy is deleted after handoff or failure

#### Scenario: Mismatching file
- **WHEN** size, digest, package, version or certificates differ
- **THEN** the file is deleted and « Mise à jour refusée : fichier non conforme » names the failed check

#### Scenario: Release withdrawn
- **WHEN** the re-read shows the offered release is no longer eligible
- **THEN** nothing is downloaded and the offer is recomputed

#### Scenario: Download timeout or network loss
- **WHEN** the download exceeds its total time or the network drops
- **THEN** the partial file is deleted and the cause is stored with « Rechercher les mises à jour » available

#### Scenario: Interrupted by reboot
- **WHEN** the TV reboots during a download
- **THEN** leftover files are deleted at the next cold start and no installation resumes from them

### Requirement: Installation through the service
Tsuyu SHALL install a verified update through a manager-only server operation that opens a PackageInstaller session as shell for package `fr.sygix.tsuyu` only, recomputes SHA-256 while copying and commits without user dialog. The operation SHALL be unreachable through the Shizuku client binder and SHALL accept no package name or path. It SHALL require the service running and `boot-persistence` ready, both re-read before commit.

#### Scenario: Manual update
- **WHEN** the user activates « Mettre à jour vers X » with the service running and persistence ready
- **THEN** progress is shown, the package is replaced without any dialog and the restart follows « Restart after a Tsuyu update »

#### Scenario: Preparation required
- **WHEN** persistence is not ready when the user activates « Mettre à jour vers X »
- **THEN** the existing P3 disclosure and preparation run first; a verified ready outcome continues the update, and a refusal or failed preparation installs nothing and returns focus to « Mettre à jour vers X »

#### Scenario: Service stopped
- **WHEN** the service is not running
- **THEN** update actions are unavailable and « Démarrez Tsuyu pour installer la mise à jour. » is shown; checking stays available

#### Scenario: Installer refuses or session fails
- **WHEN** the copied digest differs, Android rejects the package (signature, downgrade, storage) or the device requires a confirmation
- **THEN** the session is abandoned, the running server and key are untouched and the installer cause is shown

#### Scenario: Installation timeout
- **WHEN** the session does not report a result within its deadline
- **THEN** the session is abandoned when still open, « Délai dépassé » is shown and the installed version is re-read before any retry

#### Scenario: Unauthorized caller
- **WHEN** a Shizuku client or any caller other than the authenticated manager attempts installation
- **THEN** no session is opened and the client's API calls behave as before

### Requirement: Restart after a Tsuyu update
After an update installed by Tsuyu replaces the package, the old server SHALL exit under `local-start` and the new server SHALL start from the new APK through the `boot-persistence` restart after a manager update, without user interaction. Clients SHALL regain binders with preserved matching consent. A failed restart SHALL be shown on home exactly like a failed boot restart, with recovery focused.

#### Scenario: Update then automatic restart
- **WHEN** a Tsuyu-installed update succeeds
- **THEN** a client observes `binderDied`, then a new binder through `Shizuku.addBinderReceivedListenerSticky`, and `checkSelfPermission()` reflects its preserved decision

#### Scenario: First home after update
- **WHEN** the user opens Tsuyu after the update
- **THEN** home shows « Mis à jour vers X » once, then the current service status

#### Scenario: Replacement delivery blocked
- **WHEN** the device does not deliver package replacement or debugging is off
- **THEN** the P3 outcome for that cause is stored and home offers its recovery; the service is not reported as running

#### Scenario: Restart timeout or key prompt
- **WHEN** the restart exhausts the shared startup budget or the key requires « Autoriser le débogage »
- **THEN** the P3 timeout or authentication outcome is shown with its recovery action

#### Scenario: Reboot after update
- **WHEN** the TV reboots after the update
- **THEN** boot restart uses the new APK and the preserved key

### Requirement: Silent automatic update
« Mises à jour automatiques » SHALL be disabled by default and persisted; enabling it SHALL require `boot-persistence` ready, running the existing preparation first when needed. While enabled, an automatic check that finds an eligible release SHALL download, verify and install it through the service without any user interaction once the server is idle, as defined by « Idle gate for silent installation ».

#### Scenario: Silent update when idle
- **WHEN** auto-update is on, a newer eligible release exists and the server is idle
- **THEN** it is installed and restarted without any screen or dialog, and the update screen later shows « Mis à jour vers X »

#### Scenario: Enabling refused
- **WHEN** the user enables the setting but refuses or fails persistence preparation
- **THEN** the setting stays off and nothing is installed

#### Scenario: Prerequisite lost
- **WHEN** the service is stopped, debugging is off or preparation is no longer ready at install time
- **THEN** nothing is installed, the cause is stored and shown on home and on the update screen

#### Scenario: State after reboot
- **WHEN** the TV reboots with auto-update on and a verified file pending
- **THEN** the file is deleted at cold start and the next automatic check downloads it again

### Requirement: Idle gate for silent installation
A silent installation SHALL wait until a manager-only server query reports idle: no client-owned remote process, no live UserService, no pending permission request and Tsuyu not in the foreground. While busy, the verified file SHALL be kept and idle re-evaluated at most every 15 minutes for up to 24 hours, then dropped.

#### Scenario: Idle server
- **WHEN** the query reports idle
- **THEN** installation proceeds immediately

#### Scenario: Client busy
- **WHEN** a client holds a remote process or UserService or a permission request is pending
- **THEN** installation waits and does not interrupt it; after 24 hours without idle the file is dropped and the next daily check starts over

#### Scenario: Binder-only clients
- **WHEN** clients only hold the binder without processes or UserServices
- **THEN** the server is idle; those clients observe `binderDied` and a new binder after restart

#### Scenario: Idle query unavailable or timeout
- **WHEN** the service is unreachable or the idle query exceeds its deadline
- **THEN** the server is treated as busy and re-evaluated later

#### Scenario: Reboot while waiting
- **WHEN** the TV reboots while installation waits
- **THEN** the pending file is deleted at cold start and the next automatic check starts over

### Requirement: Update screen navigation
Home SHALL offer « Mises à jour » after « Accès des applications ». The update screen SHALL show status, progress, last result with cause, last check time and a non-focusable release-notes QR code on the left, and on the right, in order: « Mettre à jour vers X » when offered, « Rechercher les mises à jour », « Mises à jour automatiques : Activées/Désactivées », « Inclure les préversions : Activées/Désactivées » and « Retour ». It SHALL follow the Remote focus contract.

#### Scenario: Initial focus
- **WHEN** the screen opens
- **THEN** « Mettre à jour vers X » holds focus when present, otherwise « Rechercher les mises à jour »

#### Scenario: Edges and Back
- **WHEN** the user presses up/down at the column edges, left/right or Back
- **THEN** focus does not wrap or leave the column, and Back returns home with focus on « Mises à jour »

#### Scenario: Toggles and running operation
- **WHEN** the user activates a toggle or a check/download is running
- **THEN** the toggle keeps focus with its new value; during work, unavailable actions are not focusable and « Retour » stays reachable

#### Scenario: Return from preparation
- **WHEN** the user returns from the persistence disclosure or Android settings
- **THEN** focus returns to the initiating action when present, otherwise to the current main action

#### Scenario: Focused action disappears
- **WHEN** an offer disappears or an update starts
- **THEN** focus moves to « Rechercher les mises à jour »
