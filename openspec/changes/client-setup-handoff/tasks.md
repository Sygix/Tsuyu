# Tasks

## 1. Verify device behavior on the TV before code

- [ ] 1.1 On a TCL Google TV running Android 14, with persistence prepared, the server running and the app process absent, install a same-signer higher `versionCode` build from a shell PackageInstaller session.
  - Record whether any dialog appears.
  - Record whether `MY_PACKAGE_REPLACED` is delivered.
  - Record whether the P3 restart brings the new server up.
  - Repeat with AUTO_START reverted to `ignore`.
  - Done when the outcomes are recorded in `docs/` with structure only.
- [ ] 1.2 On the same TV, launch `MainActivity` from a test app in its own task, without `NEW_TASK` and with `ActivityOptions.setShareIdentityEnabled(true)`. Record:
  - `getLaunchedFromUid()` / `getLaunchedFromPackage()`, with and without sharing;
  - the result after recreation;
  - that `finish()` shows the caller again.
- [ ] 1.3 With the unchanged Shizuku-API demo, record the following after a manual `AccessAuthority` clear of a refused group:
  - binder delivery;
  - refusal callback;
  - rationale before and after reattachment;
  - a new request.

## 2. Return to the opening application

- [ ] 2.1 Resolve the shared launch identity in `MainActivity` into a ViewModel-held caller (label, uid) and add the return control when the service is running and the persistence outcome is final.
  - JUnit covers: present, absent without identity, absent while pending or failed, missing package.
- [ ] 2.2 Compose D-pad tests at `@Config(qualifiers = "w960dp-h540dp-xhdpi")` cover:
  - initial focus on appearance;
  - restoration after a sub-screen or settings;
  - disappearance fallback;
  - Back with and without the action.
  - Activation finishes the activity.

## 3. Access list and refusal reset

- [ ] 3.1 Add the manager-only server operations that list refused groups and clear a deny decision, with deadline and typed result.
  - JUnit proves that reset never writes allow and never terminates processes.
  - JUnit proves that a Shizuku client binder cannot reach these operations.
- [ ] 3.2 Rename the destination to « Accès des applications »:
  - status rows ordered authorized, then refused;
  - « Autoriser une nouvelle demande » action;
  - empty, loading and error states.
  - `AccessViewModelTest` and `AccessNavigationTest` cover reset success, failure retry, row removal focus order and Back to the home entry.

## 4. Updates

- [ ] 4.1 Release source and comparison:
  - eligibility, `versionCode` formula, prerelease opt-in, rate-limit retry time and bounded timeouts;
  - JUnit with recorded API responses covers every scenario of « Release discovery ».
- [ ] 4.2 Checks:
  - the home-open check when auto-update is off;
  - a periodic WorkManager check when it is on;
  - the 24-hour and retry-time gates.
  - JUnit drives these with a fake clock and network.
- [ ] 4.3 Download and ordered verification into cache, deleting temporaries after handoff, failure and cold start.
  - JUnit covers each failed check and the withdrawn release.
- [ ] 4.4 Server `installUpdate`:
  - shell PackageInstaller session fixed to `fr.sygix.tsuyu`, with digest recomputation, deadline and typed result;
  - preconditions re-read before commit.
  - JUnit with a fake installer covers mismatch, rejection, timeout and an unauthorized caller.
- [ ] 4.5 Route « Mettre à jour vers X » and enabling auto-update through the P3 disclosure/preparation when not ready.
  - JUnit covers: continue after ready, stop after refusal or failure, setting stays off.
- [ ] 4.6 Silent auto-update:
  - server idle query, 15-minute re-evaluation up to 24 h, file drop;
  - prerequisite-lost causes on home and the update screen.
  - JUnit with fake idle states and clock.
- [ ] 4.7 Update screen and home entry:
  - French strings, testTags, release-notes QR;
  - « Mis à jour vers X » shown once on home.
  - Compose D-pad tests cover initial focus, edges, Back, toggles, running state, return from preparation and disappearing offer.

## 5. Documentation and integration

- [ ] 5.1 Update README for:
  - caller return and the identity-sharing requirement;
  - the access list and refusal reset;
  - updates, with auto-update off by default.

  Check the license headers on new files. `NOTICE` stays unchanged unless code is taken.
- [ ] 5.2 Run `openspec validate --all --strict`, `openspec-verify-change` and `./gradlew test`, and record the results in the PR.
- [ ] 5.3 Validate on the real TV with `./gradlew assembleRelease`:
  - SygixOs opening Tsuyu, setup, then « Revenir à SygixOs »;
  - reset of a refused SygixOs group, then a new request;
  - manual update and silent update to a test release, then automatic restart with the client regaining its binder;
  - restart failure shown when debugging is off;
  - reboot after update.
- [ ] 5.4 After merge and TV validation, archive `material3-tv-alignment` then `client-setup-handoff` with `openspec archive`, then run `openspec validate --all --strict`.
