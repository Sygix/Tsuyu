# Design

## Context

See proposal.md for scope and `contract-audit.md` for the evidence behind it. Current boundaries:

- **`MainActivity`:**
  - exported with standard task behavior;
  - Back on home calls `finish()`;
  - START/ENABLE go through `PersistenceController.startRoute()`, which already chains disclosure, startup and verified preparation.
- **`AccessAuthority`:**
  - persists allow and deny decisions per verified identity group;
  - `ClientService.accessList()` returns authorized and pending-cleanup groups only;
  - `clear(uid)` exists but has no UI.
- **`local-start`:** the running server exits when Tsuyu is replaced or removed.
- **`boot-persistence` (P3):**
  - when persistence is enabled, `MY_PACKAGE_REPLACED` schedules a restart through `RestartWorker`/`RestartRunner` with the shared startup budget;
  - preparation makes Tsuyu's AUTO_START modes allow, which unblocks that delivery on the reference TV.
- **Releases:**
  - CI publishes a signed `tsuyu.apk` asset for each `vX.Y.Z[-(alpha|beta|rc).N]` tag;
  - GitHub exposes the asset size and a `sha256:` digest;
  - `versionCode` is derived from the tag in `app/build.gradle.kts`.

## Goals / Non-Goals

**Goals:**
- implement Sygix's four selections with the smallest new privileged surface;
- reuse the existing start, restart and consent authorities;
- never leave Tsuyu stopped after one of its own updates without a visible cause.

**Non-Goals:** listed in proposal.md.

## Decisions

### D1. Caller return relies on Android's shared launch identity

- **Identity:** Android 14 lets a launching app share its identity: `ActivityOptions.setShareIdentityEnabled(true)` on the caller side, then `getLaunchedFromUid()` / `getLaunchedFromPackage()` on Tsuyu's side.
  - Tsuyu uses only this platform-verified identity. Intent extras or the referrer never name the caller.
  - Without a shared identity, a self-launch, or a caller package that is no longer installed, no return action exists and Back keeps its behavior.
- **When it appears:** the action is labelled « Revenir à <label> » with the installed app label. It appears only after both:
  - the service is verified running;
  - the persistence step has a final outcome (prepared, unsupported, refused or failed).

  While startup or preparation is pending, or after a failure, the action is absent and recovery stays first.
- **What it does:** activation finishes `MainActivity`. Because it runs in the caller's task (no `NEW_TASK`), the caller's activity is shown again. Tsuyu reports nothing to the caller: its API state stays the source of truth.
- **Focus:** when the action appears, it becomes the main action and receives focus once. If the user had already moved focus, it is restored to the initiating control instead.
- **Recreation:** the launched-from identity belongs to the activity record and survives recreation. The presentation state keeps the resolved label in the ViewModel.
- **Alternatives rejected:** automatic return, rejected by Sygix because it hides preparation and errors; trusting extras, rejected because they authenticate nothing.

### D2. Boot restart stays in the existing pre-start sequence

There is no new behavior. `contract-audit.md` records the SygixOs guide corrections.

### D3. Refusal reset is a server-side clear of a deny decision

- **List:** « Accès des applications » replaces « Applications autorisées ». The server returns authorized, refused and pending-cleanup groups for currently installed identities. Each row shows a status text and exactly one action:
  - « Retirer l'accès » for authorized groups (P2 revocation, unchanged);
  - « Autoriser une nouvelle demande » for refused groups;
  - the existing revoke retry for pending cleanup.
- **Ordering:** rows are sorted by status (authorized, then refused), then by label. Shared-UID groups keep all members visible.
- **Reset:**
  - A new manager-only server operation clears the deny decision for that group. It never writes allow.
  - Its result separates success, missing identity, storage failure and timeout.
  - On success the row leaves the list. The client's next `checkSelfPermission()` stays denied. After reattachment `shouldShowRequestPermissionRationale()` returns false, and a new `requestPermission` opens the usual confirmation with « Refuser » focused.
  - Resetting is not revocation: it neither terminates processes nor reuses the revoke wording.
- **Alternative rejected:** a manager grant toggle, because consent stays bound to a live client request.

### D4. Updates follow SygixOs's `self-update` principle

- **Source:**
  - One unauthenticated HTTPS request to the public releases of the Tsuyu repository, with bounded connect, read and total timeouts.
  - Eligible releases are non-draft, carry an exact `vX.Y.Z` or `vX.Y.Z-(alpha|beta|rc).N` tag and a fully uploaded `tsuyu.apk` asset with size and `sha256:` digest, and are served over HTTPS.
  - The highest `versionCode` computed with the build formula is offered if it is strictly greater than the installed one.
  - Prereleases are offered only when « Inclure les préversions » is on; it is off by default.
  - A rate limit stores the retry time from the response, or one hour later. No automatic request is sent before it, even after reboot.
- **Checks:**
  - **Manual:** « Rechercher les mises à jour » always sends a request.
  - **Automatic:** at most once per 24 h. While auto-update is off, it runs when home opens. While auto-update is on, it also runs as a periodic WorkManager job with a network constraint.
  - An automatic failure is silent and only updates the stored result shown on the update screen.
- **Download:**
  - Into app cache, with a bounded total time.
  - Then ordered checks: size, SHA-256, package `fr.sygix.tsuyu`, `versionCode` equal to the tag and greater than installed, signing certificates identical to the installed ones.
  - Any failure deletes the file and records a cause. Temporary files are deleted after handoff, after failure and at cold start.
- **Release notes:** a non-focusable QR code pointing to the release page, in the update screen's supporting panel.

### D5. Installation goes through Tsuyu's own shell server

- **Why the server installs:**
  - The shell uid installs packages without a confirmation dialog.
  - The app needs no `REQUEST_INSTALL_PACKAGES` and no unknown-sources toggle.
  - This is the only way to satisfy "no interaction" on the reference TV.
- **New manager-only operation on the existing manager binder:** `installUpdate(file descriptor, expected size, expected SHA-256, expected versionCode)`.
  - Shizuku clients cannot reach it: it is absent from the client binder and gated by the existing manager authentication.
  - The server opens a PackageInstaller session as shell, restricted to package `fr.sygix.tsuyu`, and recomputes SHA-256 while copying. A mismatch abandons the session.
  - It commits with replace-existing and no downgrade. Android itself rejects a different signer.
  - The result separates success, verification failure, installer failure with its status, and timeout.
  - No shell command is built from input: the session API is used directly.
- **Preconditions:** both are re-read immediately before install.
  - The service is running.
  - `boot-persistence` is ready: the same preparation as boot restart, including AUTO_START allow.
- **When preparation is not ready:**
  - « Mettre à jour vers X » opens the existing P3 disclosure → preparation flow, then continues the update only after a verified ready outcome.
  - Enabling « Mises à jour automatiques » follows the same rule.
  - Refusing preparation leaves the update unapplied and the setting off.
  - Rationale: the existing P3 restart-after-update path then applies unchanged, so restart needs no second mechanism.
- **Install timeline:**
  1. Commit replaces the APK.
  2. Android kills the app process.
  3. The server exits through `local-start`'s package lifecycle observation; clients see `binderDied`.
  4. `MY_PACKAGE_REPLACED` reaches `BootReceiver`, and `RestartRunner` starts the new server from the new APK within the shared budget.
  5. The new server delivers binders to clients and to the manager. Consent and key survive (same signer).
- **Failure:** a restart that fails records the P3 outcome and shows it on home with recovery focused, exactly like a failed boot restart. Tsuyu never stops silently.
- **Without the server:** when the service is stopped, update actions are unavailable with « Démarrez Tsuyu pour installer la mise à jour. ». Checking still works.

### D6. Silent automatic update

- **Setting:** « Mises à jour automatiques », off by default, persisted.
- **Flow:** when it is on and an automatic check finds an eligible release, the job downloads and verifies it, then installs through D5 without any UI.
- **Idle gate:** installation waits until the server reports idle state:
  - no client-owned remote process;
  - no live UserService;
  - no pending permission request;
  - Tsuyu not in the foreground.

  The idle query is a manager-only read. While the service is busy, the verified file is kept and the gate is re-evaluated at most every 15 minutes for up to 24 h. After that the file is dropped and the next daily check starts over.
- **Rationale:** Binder-only clients are not "busy". The API already defines how they re-attach after `binderDied`, and SygixOs tears down its UserService per sequence.
- **Missing prerequisites:** if debugging is off, the service is stopped or preparation is lost, the job records the cause and installs nothing. Home and the update screen show it.
- **Prereleases:** they follow the same opt-in as manual updates.

### D7. Update screen

- **Entry point:** home gains a « Mises à jour » entry, after « Accès des applications ».
- **Layout:** the screen uses the `material3-tv-alignment` state-screen shell.
  - **Left:** status (« À jour », « Version X disponible », progress, last error with cause, last check time) and the release-notes QR.
  - **Right column, in order:**
    - « Mettre à jour vers X » (only when offered);
    - « Rechercher les mises à jour »;
    - « Mises à jour automatiques : Activées/Désactivées »;
    - « Inclure les préversions : Activées/Désactivées »;
    - « Retour ».
- **Focus:**
  - Initial focus goes to « Mettre à jour vers X » when present, otherwise to « Rechercher les mises à jour ».
  - Toggles keep focus after activation.
- **Effect of updating:** the app process ends when the update is applied. Home of the new version shows « Mis à jour vers X » once, then normal status.

### Privileged surface

The server gains three manager-only operations: install a verified Tsuyu update, clear a deny decision, and read idle state.
- None of them is reachable through the Shizuku client binder.
- None accepts a package name or a path: the update package is fixed and the payload is a file descriptor.
- Each has a bounded deadline and a typed result.

### Provenance

No code is taken from Shizuku, Shevery or SygixOs. SygixOs's `self-update` capability is a behavioral reference only. New files carry the Tsuyu AGPL header.

## Risks / Trade-offs

- **`MY_PACKAGE_REPLACED` delivery on the reference TV after a shell install is not yet verified.**
  - Mitigation: task 1.1 verifies it before code.
  - P3's "replacement delivery unavailable" outcome remains the visible fallback.
- **A shell PackageInstaller session may require a user action on some firmware.**
  - Mitigation: task 1.1 verifies it.
  - If confirmation is required, the update fails with a typed installer status and auto-update reports it. No silent retry loop.
- **Key expiry.** An unattended restart can hit the « Autoriser le débogage » prompt when the key expired.
  - Mitigation: P3 preparation includes the optional expiry policy.
  - Otherwise the P3 AUTHENTICATION outcome is shown.
- **A client mid-operation.**
  - Mitigation: the idle gate (D6). Manual updates are user-initiated and warn that clients reconnect.
- **The opening app does not share its identity.**
  - Mitigation: no return action, Back only. SygixOs adopts `setShareIdentityEnabled(true)`.

## Migration Plan

- No storage migration.
- New preferences:
  - auto-update, default off;
  - prereleases, default off;
  - check result and retry time;
  - pending verified file metadata.
- Rollback removes the update screen and server operations. Installed versions remain usable through external installation.

Archive order: `material3-tv-alignment` → `client-setup-handoff`. This change MODIFIES the `tv-navigation` access-row requirement added by `material3-tv-alignment`.

## Penpot

The boards live with their family in the `material3-tv-alignment` file and reuse its shared components:
- **02 · Home:**
  - **H.7:** running, opened by an app with « Revenir à SygixOs »;
  - **H.8:** first home after an update.
- **04 · Access:**
  - **A.7:** « Accès des applications » with an authorized row and a refused row.
- **09 · Updates:**
  - **U.1:** up to date;
  - **U.2:** update available, with the release-notes QR;
  - **U.3:** downloading and verifying;
  - **U.4:** silent update waiting for idle;
  - **U.5:** update failed;
  - **U.6:** service stopped.

When an update needs preparation, it opens the existing P.1 disclosure; there is no dedicated board. H.1–H.6 and A.3–A.6 stay the `material3-tv-alignment` baseline.

The separate post-start offer explored during the audit was not selected and was removed. A named file version keeps it.

## Open questions

None.
