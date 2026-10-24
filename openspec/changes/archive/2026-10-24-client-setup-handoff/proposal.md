# Proposal

## Why

SygixOs opens Tsuyu to configure its home-screen access. The audit in `contract-audit.md` compared that integration with the current application and found three gaps:
- the user has no way back to the opening app once Tsuyu is ready;
- the user cannot recover from a refusal recorded earlier;
- Tsuyu cannot update itself.

Sygix has selected the behavior for each gap. This change specifies it.

## What Changes

- **Return to the opening app:** an explicit « Revenir à <app> » action appears once startup and the persistence outcome are verified. It only appears when the opening app shared its identity at launch. Back keeps working, and Tsuyu never returns automatically.
- **Boot restart in the first-start flow:** keep the existing P3 sequence: pre-start disclosure, then startup, then verified preparation. No step is added after startup. The SygixOs guide is corrected instead.
- **Refusal recovery:** one « Accès des applications » list shows every authorized and refused identity group with its status.
  - « Autoriser une nouvelle demande » resets a refusal to undecided without granting anything.
  - The app's next `Shizuku.requestPermission` opens the normal confirmation, with « Refuser » focused.
- **Tsuyu updates:** follow the SygixOs principle:
  - Versions come from the public GitHub releases and are compared by `versionCode`. Prereleases are opt-in.
  - The manual check is always available. A check also runs automatically once a day.
  - The download is verified before installation.
- **Silent automatic update:** an optional « Mises à jour automatiques » setting, off by default.
  - When enabled, Tsuyu downloads and installs new releases with no user interaction, through its own shell server.
  - It waits until no client is using a privileged process.
- **Restart after an update:** an update installed by Tsuyu always restarts the service by itself, through the existing P3 restart-after-update path. An update therefore requires the boot restart to be prepared. If it is not, the update action first runs the existing preparation flow.

## Capabilities

### New Capabilities

- `self-update`: release discovery, verified download, installation through the service, silent automatic updates and restart after an update.

### Modified Capabilities

- `client-access`: authorized and refused groups in one list, and reset of a refusal to undecided.
- `tv-navigation`: the explicit return to the opening app, and access rows that carry either a revoke or a reset action.

`local-start` and `boot-persistence` are referenced but unchanged:
- the old server exits on replacement;
- the restart after a manager update is the P3 requirement.

## Dependencies

- **P1** is archived.
- **P2** (`client-access`, `shizuku-api`) and **P3** (`boot-persistence`) are archived.
- **`material3-tv-alignment`** (PR #13) provides the shared presentation that the new screens reuse.
- **Archive order:** `material3-tv-alignment` → `client-setup-handoff`.

## Non-goals

- **Automatic return to the caller:** deferred indefinitely.
- **A separate post-start persistence offer:** deferred indefinitely.
- **A manager toggle that grants access directly:** excluded; the decision stays with the client's request.
- **Updates while the service is stopped:** deferred indefinitely; external installation still works.
  - Installing without the shell server would need `REQUEST_INSTALL_PACKAGES` and the TV's unknown-sources setting.
- **Downgrades, signing-key rotation and installing APKs other than Tsuyu's own release asset:** excluded.
- **Release pipeline changes:** none. The existing tagged release with its GitHub asset digest is the update source.
- **SygixOs code:** none. The downstream corrections are recorded in `contract-audit.md` for SygixOs's own change.

## Impact

- **App:**
  - `MainActivity` gains caller identity handling;
  - the access list gains refused rows;
  - home gains an « Mises à jour » destination backed by a new update screen and ViewModel;
  - background checks run through WorkManager.
- **Server:**
  - a manager-only operation installs a verified Tsuyu update as shell;
  - a manager-only operation resets a refusal and reports idle state.
- **Network:** HTTPS requests to the public GitHub releases API and the release asset host.
- **Unchanged:** AIDL for Shizuku clients, storage formats of consent and keys, and signing.
- **New strings and Penpot boards:** see design.md.

## Open questions

None. Sygix selected every option. design.md records the implementation decisions taken under those selections.
