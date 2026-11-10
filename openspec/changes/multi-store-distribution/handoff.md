# Implementation and publication handoff

## Entry point

Change: `multi-store-distribution`.
Planning branch: `docs/spec-android9-stores`.
Prerequisite: implemented and archived `android9-compatibility`.

This planning PR does not deliver code, APKs, account setup or store approval. After scope approval and the prerequisite, read proposal, design, three deltas and tasks, then use `/opsx-apply multi-store-distribution` in a dedicated implementation session.

## Build and migration deliverables

1. Store/standalone source sets and matching server capabilities.
2. Store payload with no internal updater implementation and a safe authenticated unsupported manager transaction.
3. Tested cleanup of old jobs/files/opt-in across newer-version variant migrations, including background-first and never-manually-opened store installations.
4. Three coherent release artifacts: standalone `tsuyu.apk`, `tsuyu-store.apk`, `tsuyu-store.aab`.
5. A reproducible, developer-signed store APK and prepared F-Droid recipe.
6. Five-language metadata, real TV screenshots, privacy/declaration drafts, release runbooks, community protocol and evidence-linked README matrix.

Confirm generated Gradle task names after flavor creation. Expected app tasks include:

```sh
./gradlew test
./gradlew :app:testStoreReleaseUnitTest :app:testStandaloneReleaseUnitTest
./gradlew :app:lintStoreRelease :app:lintStandaloneRelease
./gradlew :app:assembleStoreRelease :app:assembleStandaloneRelease :app:bundleStoreRelease
```

The module library tasks depend on the final variant wiring; the required suite must include both server behaviors. Verify compiled artifacts, not only unit tests or debug manifests.

## Publication worksheet

Track each channel independently as **prepared**, **testing**, **submitted**, **live**, or **blocked with reason**. A built artifact is not a live listing. The following console/release actions require the owner's authorization and supplied account inputs.

### Google Play

1. Confirm account identity/eligibility and whether an app entry or signing enrollment already exists.
2. Create/select the application and Android TV form factor. Confirm the current target/API, AAB, ABI/page-size and TV asset requirements.
3. Before open/production rollout, configure Play App Signing with the existing developer release signing identity using Play's encrypted import flow. Register a separate upload key. Compare public certificates; do not expose signing secrets in issues or logs.
4. Fill the five-language listing, screenshots, support contact, public privacy URL, audience/content rating, advertising/data declarations and app-access/reviewer instructions.
5. Complete any required package-visibility declaration from the audited core-interoperability use case. Document any dependency-added service permissions accurately.
6. Upload the store AAB to an internal testing track; install through that track and record the delivered certificate and split layout.
7. Execute startup, client, language, boot and newer-version migration tests against that installation. Internal App Sharing alone does not establish production signing compatibility.
8. If applicable to the account, run the currently required closed test (the checked policy specifies at least 12 testers opted in continuously for 14 days for covered newer personal accounts), then apply for production access.
9. Resolve store review feedback and obtain authorization for the production rollout; record the actual live URL only after availability is verified.

### F-Droid

1. Ensure public sources, licenses, complete metadata and a stable tagged reference `tsuyu-store.apk` are available after an authorized release.
2. Reproduce that exact APK from its clean source revision with the documented toolchain; verify that signature copying validates against the pinned developer certificate.
3. Prepare the fdroiddata metadata for `fr.sygix.tsuyu` with the store flavor, deterministic version input, stable-tag update detection, reference binary URL and AllowedAPKSigningKeys.
4. Run the current fdroidserver metadata lint/update-detection/build workflow; record the tool version and results. A byte or signature mismatch blocks this version, not a fallback signer.
5. With authorization, submit the metadata contribution and respond to maintainer build/policy feedback.
6. Once published, install the repository-delivered APK, verify its certificate and variant, and test a newer-version upgrade over compatible existing data.

### Amazon Appstore

1. Obtain a community Fire TV tester and complete local legacy ADB, client and boot feasibility checks. Record Android and Fire OS versions and distinguish reported from reviewed evidence.
2. Resolve the publication-only DRM and package-coexistence questions with the owner. The current design prepares the existing store APK; do not silently introduce another application identity.
3. Review Amazon's documented re-signing/wrapper and resulting license/privacy obligations, seeking store clarification where the delivered behavior is unclear.
4. Complete the developer account and Fire TV listing, device targeting, privacy fields, graphics and reviewer setup instructions for actually supported Android-based devices.
5. Submit the store APK through the available testing flow. Retrieve/install the actual Amazon-delivered build and record its public signing identity.
6. Run the full protocol against that transformed build. A successful sideload of the uploaded APK is not this test.
7. Document incompatible-signature switching as reinstall/reconfiguration, and publish only after the owner authorizes the validated submission.

Amazon can remain blocked while the other channels proceed. A firmware with no usable local ADB remains unsupported by the current startup mechanism; an Android version match does not override that result.

## Community protocol to ship

Create `docs/community-testing.md` and a structured compatibility issue template during implementation. Each step needs a visible expected result and a place for pass/fail/not-tested plus sanitized evidence:

1. Identify public build/version, variant/channel, artifact hash or URL, brand, Android/Fire OS versions and date. Separate emulator from physical device.
2. State fresh install or upgrade. Preserve an existing installation unless the tester explicitly chooses otherwise.
3. Check D-pad focus, Back, screen readability, all language choices and the presence/absence of update UI for the variant.
4. Enable debugging through the existing guided flow; check authorization refusal, then explicit acceptance with 'Always allow'. Record missing settings/endpoint accurately.
5. Verify live shell service and survival after the launch connection closes; reopening the manager must reflect actual liveness.
6. Use a baseline-compatible Shizuku-API demo: binder callback/ping, deny/allow, process shell output, UserService, revoke and effective cleanup. Add SygixOs where its own minimum permits.
7. Enable persistence after its disclosure. Reboot with the manager process absent; verify new binder and retained matching consent. Explain why force-stop is a distinct negative case.
8. Disable/re-enable debugging; verify truthful recovery and stored-key reuse where supported.
9. Install a newer provided signed version and check retained language/key/persistence/consent plus old-server death/new-server attachment.
10. Run standalone verified-update/opt-in checks or store no-updater checks. Record old pending work cancellation for cross-variant migration. Deep network/payload evidence can be maintainer-supplied; a screenshot alone cannot prove absence of background code.
11. For failure, give exact stage, expected/observed state, bounded-timeout outcome, reproduction count and a relevant sanitized log excerpt or screenshot. Do not request full device dumps.

Review checklist: artifact identified, required stages evidenced, result reproducible or limitation precisely bounded, private details removed, appropriate confidence label. The README links the reviewed report and release. Community validation means reviewed submitted evidence, not an unperformed maintainer reproduction. A later regression gets a version-specific entry without erasing the historical success.

## Completion and archive

Complete local tasks with strict OpenSpec validation, required tests, compiled-artifact checks and the real Android 14 regression. Keep actual Play delivery, F-Droid inclusion and Amazon hardware/store results separately pending until performed.

No task authorizes a merge, tag, account change or external submission by itself. After implementation merge and required real-TV validation, archive this change and validate the resulting specs; the publication-readiness requirements remain binding for later submissions.
