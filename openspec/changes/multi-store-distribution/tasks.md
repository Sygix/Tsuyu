# Tasks

## 1. Confirm prerequisites and delivery evidence

- [ ] 1.1 Before distribution code changes, confirm the archived `consent-and-persistence-controls` and `android9-compatibility` changes and capture the current release's updater, package-replacement and reboot behavior on the Android 14 TV emulator; deliver the signed-version/state baseline used by migration tests.
- [ ] 1.2 Inventory current updater dependencies, graph/navigation references, manifest components, work names, stored fields and server entry points; verify every listed item against source and record the concrete source-set/cleanup mapping.
- [ ] 1.3 Record Play and F-Droid readiness inputs and current policy references, including the current F-Droid position on embedded dependency metadata; verify every unavailable input is listed as a blocked publication step rather than an assumed fact.

## 2. Implement build-time distribution capabilities

- [x] 2.1 Add matching store/standalone app and server flavors, keeping shared IPC/API compatible; verify each app resolves its matching server and generated Gradle task names are documented.
- [x] 2.2 Move updater code, activity declaration and updater-only dependencies/resources to standalone; inspect merged manifests and both payloads to verify store lacks release discovery, downloads, update workers/UI and installer implementation while retaining local ADB and persistence dependencies.
- [x] 2.3 Add the narrow graph/home distribution boundary and preserve remaining navigation; verify store has no updater notice/action, store keeps the persistence screen with « Désactiver le démarrage automatique » and « Renouveler l'autorisation », and standalone behavior remains covered by existing update tests.
- [x] 2.4 Replace the concrete server installer dependency with flavor-specific behavior while retaining manager IPC transaction positions; verify unauthorized calls fail, store calls return unsupported and close descriptors, and standalone installation checks still pass.
- [ ] 2.5 Add TV-qualified Compose navigation tests for both variants, missing/restored update focus, Back and sub-screen restoration with testTags; verify with a resource comparison that this change adds no user-visible string in any of the five languages and no channel-change notice, and document the variant behavior.

## 3. Make migrations preserve data and disable stale work

- [x] 3.1 Implement idempotent transition cleanup for the two unique work names, managed update cache, saved updater intent, keeping the recorded versionCode, using a single DataStore owner; test success, repeated execution, corrupted state, bounded failures and prevention of arbitrary-path deletion.
- [ ] 3.2 Run the store cleanup at every process start until it completes, started at application initialization, with a logged cause and no notice on failure; test a queued old worker before activity launch, an absent worker class, cancellation failure followed by a successful retry at the next start, and interrupted cleanup, proving that no store path can self-install.
- [x] 3.3 Implement the standalone replacement rule: persist the versionCode observed at process start and the versionCode of each installation the updater starts, and gate every automatic check, download and installation on a bounded startup comparison that turns automatic updating off and drops the offer and files when the installed versionCode matches neither. Add JUnit tests that fail without the rule for: store-to-standalone return, manual installation of a later standalone APK, update installed by the updater (setting kept), unchanged version after restart, missing record (recorded as the first observation, setting kept), a worker that becomes runnable before any activity, and an unreadable or timed-out record (no automatic work in that process).
- [ ] 3.4 Run newer-version standalone -> store -> standalone upgrades with active clients and pending updater work; verify key, language, persistence and matching consent preservation, old server death/new binder delivery, and « Mises à jour automatiques : Désactivées » after the return.
- [x] 3.5 Document in the README the supported migration prerequisites, that Google Play may replace an installation made from the GitHub APK with its store build at a later version, how to return to the GitHub build with a later version, and that automatic updating is off after any replacement the updater did not perform; verify the guidance never promises same-version/downgrade replacement and that the app shows no notice for these cases.
- [ ] 3.6 Test a store-style replacement of a running installation with automatic startup disabled, and one while a client holds a remote process and a UserService; verify `binderDied`, the end of the client's process and UserService, the stopped home with its start action focused and no success message, and a new binder only when persistence is ready.

## 4. Produce and verify distribution artifacts

- [x] 4.1 Add the committed version file and make the build read versionName and versionCode from it only, failing when the versionCode differs from the existing formula applied to the versionName or a value is missing, and remove the version environment variable; produce the standalone APK, store APK and store AAB with that version and the existing package identity. Test the formula check, verify a build of a tagged revision without any variable carries the recorded version, and verify `tsuyu.apk` retains its standalone meaning using the existing release parser tests and artifact inspection.
- [ ] 4.2 Keep all five languages available in store delivery and retain server/process entry points in base; verify bundletool-generated split installations can select every language and start the server, remote process and UserService.
- [x] 4.3 Inspect packaged native dependencies, ABI coverage and page alignment against current TV store requirements; deliver either verified compliance evidence or a verified no-native-libraries result.
- [ ] 4.4 Update release CI: fail before building when the tag differs from `v` plus the recorded versionName; add a second set of production secrets for the Play upload key next to the release-key set; sign both APKs with the release key and the AAB with the upload key; check each APK signer against the release certificate and the AAB signer against the upload certificate; keep explicit toolchain/signer selection, complete staging/checksums and draft-only retries. Verify with deliberately wrong inputs that a mismatching tag, a missing secret set and swapped identities each fail the job, that missing secrets cannot publish unsigned or debug-signed artifacts and that failed/incomplete builds cannot become public releases.
- [x] 4.5 Add artifact-level checks to CI for store updater exclusion, variant/version parity with the version file and expected manifest entries; verify a deliberately wrong artifact/capability fails the corresponding check rather than testing only its implementation shape.
- [x] 4.6 Update the release runbook and the CI/release section of AGENTS.md with the version-bump commit that precedes each tag, the actual generated Gradle tasks, artifact names and signature-check steps; verify instructions cover `./gradlew test`, both release APK builds and the store bundle task without machine-specific configuration.

## 5. Establish reproducible store APK builds

- [ ] 5.1 Make release variants built without signing inputs unsigned by removing the debug-signing fallback, and exclude AGP dependency metadata from both outputs with `dependenciesInfo { includeInApk = false; includeInBundle = false }`; verify that signature verification reports the keyless APKs and AAB as unsigned, that no dependency-metadata block is present in them, that the independent recipe needs neither production keys nor a version variable, and that the developer documentation explains how to sign a keyless build for a device test.
- [ ] 5.2 Rebuild the store APK in two clean pinned toolchains, including the F-Droid target environment, and verify reference-signature copying and signature validation; diagnose byte differences until reproduction succeeds and record exact tool versions.
- [ ] 5.3 Prepare the fdroiddata recipe with store flavor, stable-tag update detection reading versionName and versionCode from the version file, automatic version updates, reference binary URL and allowed public developer certificate; verify metadata lint, that update detection reports a new stable tag's version from the file, and a local build, without submitting externally as part of implementation.
- [ ] 5.4 Document the reproduction procedure and source/binary/license provenance, including known tooling constraints actually encountered; verify another clean run can follow it and a mismatch blocks release eligibility rather than changing signer.

## 6. Prepare submission and community materials

- [ ] 6.1 Verify manager-side package visibility for client names/icons, shared groups and caller labels; deliver the truthful QUERY_ALL_PACKAGES core-functionality justification and actual merged-permission/service inventory for submission.
- [ ] 6.2 Write five-language listing metadata and changelogs, capture TV emulator screenshots and prepare store-sized assets; verify descriptions match each variant, recommend enabling automatic startup on store builds so the service resumes after a store update, and make no unsupported device claims.
- [ ] 6.3 Write the privacy document and draft store declarations from audited app/dependency network behavior; verify unresolved hosting/contact/account inputs are listed as publication prerequisites rather than invented values.
- [ ] 6.4 Add `docs/community-testing.md` and a structured compatibility issue template covering each device-validation stage with expected outcomes, sanitized evidence and fresh-install/upgrade distinctions; dry-run the protocol on the Android 14 TV emulator and fix ambiguous instructions.
- [ ] 6.5 Add the README compatibility matrix with reviewed release-specific links, separate emulator evidence and clear partial/unverified statuses; verify no physical Android 9 configuration or other untested device is marked validated.
- [ ] 6.6 Write concrete Play and F-Droid publication runbooks using the handoff's steps; verify signing/account gates, delivered-artifact tests, permission/privacy declarations and the « Availability » status vocabulary are covered.
- [ ] 6.7 Add the README « Availability » table with one row for GitHub releases, Google Play and F-Droid, each with one status among prepared, testing, submitted, live and blocked and the next action or missing input where it applies, and the README recommendation to enable automatic startup on store builds; verify no row is live without a verified installation from that channel.

## 7. Integrated acceptance and handoff

- [ ] 7.1 Run both variants' unit/navigation tests, server tests, lint and release artifact checks, including the repository-required `./gradlew test`; reuse exact-head CI evidence where applicable and record the artifact hashes/variants used for runtime tests.
- [ ] 7.2 Regress baseline and Android 14 TV emulator startup, clients, boot, migrations and split installs with signed release builds; deliver evidence for store updater absence and unchanged standalone verified updating without claiming a store-delivered test from a sideloaded build.
- [ ] 7.3 Verify headers/NOTICE, public diff hygiene, OpenSpec verification and `openspec validate --all --strict`; deliver an implementation-complete report separately from each store's external publication status.

## Workflow follow-up

- After the implementation is reviewed, obtain explicit merge/tag/publication authorization from the owner; specification approval does not authorize those actions.
- Run a real Play testing-track installation and certificate/migration check after account setup and an authorized upload, then satisfy any required closed-test period and production review.
- Submit the prepared F-Droid contribution after an authorized stable release provides its reference APK, then verify the actually delivered developer-signed installation.
- Keep missing external prerequisites as blocked rows of the « Availability » table. They are not checked implementation tasks and do not become fabricated runtime evidence.
- Archive this change after `consent-and-persistence-controls` and `android9-compatibility`, after merge, then run strict validation on the merged specs. Store readiness requirements continue to govern later submissions.
