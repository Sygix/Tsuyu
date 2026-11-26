# Tasks

## 1. Consent decisions

- [x] 1.1 Answer requests of a group with a recorded refusal with denial and no confirmation screen; verified by `ClientServiceTest.explicitRefusalIsAnsweredWithoutAnotherPromptUntilReset` (fails with the check removed).
- [x] 1.2 Record only explicit answers; expiry, lookup expiry, screen failure and cancellation end in denial without a decision; verified by `ClientServiceTest.lateConfirmationReturnsDenialWithTheOriginalCodeWithoutRecordingARefusal`.

## 2. Persistence controls

- [x] 2.1 Add the disabled state to the persistence store and cancel the key-refresh work; verified by `PersistenceStoreTest.disablingStopsFutureRestartsAndClearsTheKeyWarningAcrossReload` and `PersistenceControllerTest.disablingCancelsKeyRefreshAndReturnsToTheDisclosureWithoutStoppingTheService`.
- [x] 2.2 Add the renewal operation with the interactive authorization wait and record only its authentication result; verified by `PersistenceControllerTest.renewalPromptsWithoutRestartingAndRecordsOnlyItsRealAuthenticationResult`.
- [x] 2.3 Stop manual recovery and restart completion from clearing the authorization warning; record it after a successful adb launch; verified by `PersistenceStoreTest.refreshWarningSurvivesReloadAndOnlyAnExplicitAuthenticationClearsIt`.
- [x] 2.4 Add both actions to the persistence screen with testTags, renewal first when offered, strings in English, French, German, Spanish and Italian.

## 3. Validation

- [x] 3.1 `./gradlew test` and `./gradlew lintDebug` pass.
- [ ] 3.2 Run the persistence and permission scenarios on the Android TV emulator: disable, renewal prompt and cancel, refused client re-request with the Shizuku-API demo app.
- [ ] 3.3 Validate on the real TV with `./gradlew assembleRelease`, then archive this change.
