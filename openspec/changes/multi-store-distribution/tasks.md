# Tasks

## 1. Confirm prerequisites and delivery evidence

- [ ] 1.1 Before distribution code changes, confirm the archived Android 9 change and capture the current release's updater, package-replacement and reboot behavior on the TCL Google TV running Android 14; deliver the signed-version/state baseline used by migration tests.
- [ ] 1.2 Inventory current updater dependencies, graph/navigation references, manifest components, work names, stored fields and server entry points; verify every listed item against source and record the concrete source-set/cleanup mapping.
- [ ] 1.3 Record Play/F-Droid/Amazon readiness inputs and current policy references; verify Fire TV's untested ADB/boot hypotheses are explicitly blocked publication claims rather than assumed device facts.

## 2. Implement build-time distribution capabilities

- [ ] 2.1 Add matching store/standalone app and server flavors, keeping shared IPC/API compatible; verify each app resolves its matching server and generated Gradle task names are documented.
- [ ] 2.2 Move updater code, activity declaration and updater-only dependencies/resources to standalone; inspect merged manifests and both payloads to verify store lacks release discovery, downloads, update workers/UI and installer implementation while retaining local ADB and persistence dependencies.
- [ ] 2.3 Add the narrow graph/home distribution boundary and preserve remaining navigation; verify store has no updater notice/action and standalone behavior remains covered by existing update tests.
- [ ] 2.4 Replace the concrete server installer dependency with flavor-specific behavior while retaining manager IPC transaction positions; verify unauthorized calls fail, store calls return unsupported and close descriptors, and standalone installation checks still pass.
- [ ] 2.5 Add TV-qualified Compose navigation tests for both variants, missing/restored update focus, Back and sub-screen restoration with testTags; verify new UI text is localized in all five languages and document the variant behavior.

## 3. Make migrations preserve data and disable stale work

- [ ] 3.1 Implement idempotent transition cleanup for the two unique work names, managed update cache and saved updater intent using a single DataStore owner; test success, repeated execution, corrupted state, bounded failures and prevention of arbitrary-path deletion.
- [ ] 3.2 Wire cleanup into application/replacement/background entry before capabilities are exposed; test a queued old worker before activity launch, an absent worker class, cancellation failure and interrupted cleanup, proving that no store path can self-install.
- [ ] 3.3 Reconcile transition state before standalone automatic installation resumes; test store-to-standalone fresh opt-in, rapid replacements and store never manually opened, and resolve any unobserved-transition limitation before claiming migration support.
- [ ] 3.4 Run newer-version standalone -> store -> standalone upgrades with active clients and pending updater work; verify key, language, persistence and matching consent preservation, old server death/new binder delivery, and no silent automatic reactivation.
- [ ] 3.5 Document supported migration prerequisites and Amazon's signature exception; verify the guidance never promises same-version/downgrade replacement or data preservation after incompatible-signature uninstall.

## 4. Produce and verify distribution artifacts

- [ ] 4.1 Produce the standalone APK, store APK and store AAB with the same tag-derived versionCode and existing package identity; verify `tsuyu.apk` retains its standalone meaning using the existing release parser tests and artifact inspection.
- [ ] 4.2 Keep all five languages available in store delivery and retain server/process entry points in base; verify bundletool-generated split installations can select every language and start the server, remote process and UserService.
- [ ] 4.3 Inspect packaged native dependencies, ABI coverage and page alignment against current TV store requirements; deliver either verified compliance evidence or a verified no-native-libraries result.
- [ ] 4.4 Update release CI for explicit toolchain/signer selection, complete staging/checksums, production signing failures and draft-only retries; verify missing secrets cannot publish debug/unsigned artifacts and failed/incomplete builds cannot become public releases.
- [ ] 4.5 Add artifact-level checks to CI for store updater exclusion, variant/version parity and expected manifest entries; verify a deliberately wrong artifact/capability fails the corresponding check rather than testing only its implementation shape.
- [ ] 4.6 Update the release runbook with the actual generated Gradle tasks, artifact names and signature-check steps; verify instructions cover `./gradlew test`, both release APK builds and the store bundle task without machine-specific configuration.

## 5. Establish reproducible store APK builds

- [ ] 5.1 Provide a truly unsigned reproducibility build and deterministic tagged version input; verify neither production keys nor the debug-signing fallback are required by the independent recipe.
- [ ] 5.2 Rebuild the store APK in two clean pinned toolchains, including the F-Droid target environment, and verify reference-signature copying and signature validation; diagnose byte differences until reproduction succeeds and record exact tool versions.
- [ ] 5.3 Prepare the fdroiddata recipe with store flavor, stable tag/version mapping, reference binary URL and allowed public developer certificate; verify metadata lint, update detection and build locally without submitting externally as part of implementation.
- [ ] 5.4 Document the reproduction procedure and source/binary/license provenance, including known tooling constraints actually encountered; verify another clean run can follow it and a mismatch blocks release eligibility rather than changing signer.

## 6. Prepare submission and community materials

- [ ] 6.1 Verify manager-side package visibility for client names/icons, shared groups and caller labels; deliver the truthful QUERY_ALL_PACKAGES core-functionality justification and actual merged-permission/service inventory for submission.
- [ ] 6.2 Write five-language listing metadata and changelogs, capture real TV screenshots and prepare store-sized assets; verify descriptions match each variant and make no unsupported device claims.
- [ ] 6.3 Write the privacy document and draft store declarations from audited app/dependency network behavior; verify unresolved hosting/contact/account inputs are listed as publication prerequisites rather than invented values.
- [ ] 6.4 Add `docs/community-testing.md` and a structured compatibility issue template covering each device-validation stage with expected outcomes, sanitized evidence and fresh-install/upgrade distinctions; dry-run the protocol on the Android 14 TV and fix ambiguous instructions.
- [ ] 6.5 Add the README compatibility matrix with reviewed release-specific links, separate emulator evidence and clear partial/unverified statuses; verify no untested Fire TV or physical Android 9 configuration is marked validated.
- [ ] 6.6 Write concrete Play, F-Droid and Amazon publication runbooks using the handoff's steps; verify signing/account gates, delivered-artifact tests, permission/privacy declarations and the public-channel status vocabulary are covered.

## 7. Integrated acceptance and handoff

- [ ] 7.1 Run both variants' unit/navigation tests, server tests, lint and release artifact checks, including the repository-required `./gradlew test`; reuse exact-head CI evidence where applicable and record the artifact hashes/variants used for runtime tests.
- [ ] 7.2 Regress baseline and Android 14 TV startup, clients, boot, migrations and split installs; deliver evidence for store updater absence and unchanged standalone verified updating without claiming a store-delivered test from a sideloaded build.
- [ ] 7.3 Verify headers/NOTICE, public diff hygiene, OpenSpec verification and `openspec validate --all --strict`; deliver an implementation-complete report separately from each store's external publication status.

## Workflow follow-up

- After the implementation is reviewed, obtain explicit merge/tag/publication authorization from the owner; specification approval does not authorize those actions.
- Run a real Play testing-track installation and certificate/migration check after account setup and an authorized upload, then satisfy any required closed-test period and production review.
- Submit the prepared F-Droid contribution after an authorized stable release provides its reference APK, then verify the actually delivered developer-signed installation.
- Obtain reviewed community Fire TV evidence before supported Amazon publication; resolve console choices and wrapper/license/privacy checks, then test the actual Amazon-delivered artifact.
- Keep missing external prerequisites as pending channel statuses. They are not checked implementation tasks and do not become fabricated runtime evidence.
- Archive this change after merge and the required real-TV validation, then run strict validation on the merged specs. Store readiness requirements continue to govern later submissions.
