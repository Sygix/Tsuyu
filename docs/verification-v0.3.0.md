# v0.3.0 language verification

## Accepted scope

Follow SygixOs v0.5.0: English, French, German, Spanish and Italian, with system language by default, English fallback and an immediate persistent manual override. The owner approved implementation without a separate OpenSpec change and emulator validation instead of real-TV validation. The target release is v0.3.0.

## Design

The existing shared [Tsuyu · Material 3 TV Penpot file](https://design.penpot.app/#/workspace?team-id=52a5041f-08a1-80f1-8008-c3c379c88195&file-id=cd444aaf-0d8c-801b-8008-ce19b8b9f465&page-id=9144815c-f568-809d-8008-d7361f07b102) now includes a Language entry on all Home boards, a Home return-focus board, and four Language boards covering automatic selection, independent selection/focus, immediate switching and unsupported-system fallback. PNG previews were shown to the owner.

## Automated checks

- `TSUYU_VERSION=v0.3.0 ./gradlew test assembleRelease --no-daemon`: passed. Local release builds use the development signing configuration; production signing remains in CI.
- `LanguageTest`: first supported system locale, regional variants, English fallback, native preference persistence/reset, unchanged system language, localized resources and failed-write recovery.
- `UpdateTimeTest`: unsupported system language uses English date formatting; ordered locale fallback preserves a supported regional locale. The test failed with the previous first-requested-locale formatter and passed with effective-language resolution.
- `LanguageNavigationTest`: initial selection focus, independent check/focus, immediate selection, D-pad edges, visible options and read-error retry.
- `HomeViewModelTest.languageReturnKeepsItsFocusThroughServiceDeathAndPreparationFailureWithoutStartingAnything`: passed; deliberately restoring the old focus policy made this test fail, then restoring the change made it pass.
- Existing navigation tests explicitly use a French locale where asserting French copy.
- `openspec validate --all --strict`: all six existing capabilities passed; no change artifacts or current specs were edited.
- `TSUYU_VERSION=v0.3.0 ./gradlew test lintRelease --no-daemon`: passed; app lint reports 0 errors and 11 warnings. The previous `QueryAllPackagesPermission` error is addressed with a suppression scoped to that permission declaration. Tsuyu needs package visibility to resolve names and icons for arbitrary client packages and shared-UID group members returned by the privileged server, including clients without launcher activities. A fixed package list or intent-based `<queries>` cannot describe every app requesting the Shizuku API manifest permission. The permission is retained; other lint checks remain enabled.

## Android TV emulator

The local release APK was installed on an Android TV 14 emulator. Screenshots were shown to the owner. The following passed:

| Scenario | Observed |
| --- | --- |
| Fresh system default, English and French | Correct translated UI; System selected and initially focused |
| Select each of the five languages | Immediate translated screen, selected choice focused after recreation |
| D-pad bounds and independent selection | No wrap at either edge; moving focus leaves the saved check in place |
| Manual override after force-stop/relaunch | French retained |
| Manual override with system language changed to Japanese | French retained; system language unaffected by the app |
| Reset to System with Japanese system language | English fallback; System still selected, not explicit English |
| System mode with system language changed to French | UI follows French automatically |
| Back from Language | Home Language action regains focus in English, French and German |
| Help, updates, app-access error and persistence in English/French/German | Localized, no observed text clipping |
| Update dates | French, German and English formats match the UI language |
| Unsupported-system date fallback after review fix | Japanese system + System formats the timestamp in English; French override still formats it in French |
| Unsupported-system explanation after review fix | Explicitly describes System mode in both English and French, without contradicting a manual French override; both fit in two lines |
| Side effects | Service remains stopped; no server launch from language changes; crash buffer empty |

## Limits

- Real-TV rendering, overscan and couch-distance readability were explicitly waived for this change.
- The emulator settings only support one system language; the `ja,fr` ordered-list case was verified by unit tests, not runtime.
- No client permission prompt was opened: no API client fixture or running service was available. The shared access resources are translated and existing consent navigation tests pass.
- Concurrent service death/preparation failure while returning from Language was verified by the ViewModel regression test, not a live service on the emulator.
- An accessibility inspection was inconclusive: the visual check and Compose selected semantics are tested, but the emulator UI hierarchy did not expose a selected node. Spoken selection announcements were not verified.
- The emulator was stopped and its system language restored after verification.
