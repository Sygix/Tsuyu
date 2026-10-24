# SygixOs integration contract audit

## Scope and evidence

Read-only comparison on 2026-10-17: Tsuyu main `a019775`; PR #13, `material3-tv-alignment`, head `13293b5`; SygixOs PR #76 `tsuyu-home-setup`, head `df9504244a7d97c0e7bc0935b2532c179a3b05c0`, design D2/D4/D5 and Suggestions. All source paths below are relative to Tsuyu. Code conformity is not a new real-TV claim.

P2/P3 implementations were on main at audit time; both changes have since been archived. PR #13 contains no implementation. Penpot's existing Home, Setup, Restart, Expiry and Access pages were inspected, not inferred from the PR description.

## Point-by-point contract

| Point | Verdict | Evidence and qualification |
| --- | --- | --- |
| “Démarrer” | Different by state | `strings.xml:34-35` defines both “Activer Tsuyu” and “Démarrer”. `ui/HomeViewModel.kt:162-172` uses enable for SETUP and start for STOPPED. Penpot H.1 says “Activer Tsuyu”; H.2 says “Démarrer”. It is not a universal first-run instruction. |
| “Démarrage au redémarrage” | Label conformant; sequence different | `res/values/strings.xml:56`, `HomeViewModel.kt:176`; H.1–H.6 and P.1 match. This is a destination, not a toggle. `MainActivity.kt:52-58`, `persistence/PersistenceController.kt:46-50,65-79` already connect first start to pre-start disclosure and verified preparation. P.1's initial action is “Activer Tsuyu”. SygixOs 9.7's separate step 3 misdescribes this path. |
| “Autoriser” / “Refuser” | Conformant | `res/values/strings.xml:48-49`, `access/AccessScreen.kt:57`; Penpot A.1/A.2 have the same labels. Android's debugging confirmation is system-owned and its exact wording is not proven by these app resources. |
| Permission initial focus and caller return | Conformant in code and recorded emulator run | `access/AccessPresentation.kt:32-47`, `AccessScreen.kt:63`, `AccessViewModel.kt:50-53,103-108`, `AccessActivity.kt:34`; `AccessNavigationTest.kt:24-36`. A.1/A.2 focus Refuser. Back refuses. `docs/runtime-p2.md:17` records caller return. Not verified on the reference TV or with the SygixOs PR #76 build. |
| MainActivity in caller's task without NEW_TASK | Conformant by manifest; end-to-end not verified | `app/src/main/AndroidManifest.xml:44-51` exports a standard activity without task overrides; `MainActivity.kt:42-44` finishes on home Back. Explicit component launch does not require MAIN/LEANBACK filter matching. No automatic success return exists. A real caller-task/settings/subactivity/recreation test is outstanding. |
| Binder for a client requesting API_V23 | Conformant in code and recorded emulator demo/probe | `server/.../ClientDelivery.kt:82-110` filters requested manifest permission and sends BinderContainer to `<package>.shizuku` via `sendBinder`. `ClientService.kt:300-302` reports server API 13/patch 6, compatible with client library 13.1.5. Binder delivery precedes permission. No three-second delivery SLA exists. No SygixOs build or real-TV validation recorded. |
| Persistent refusal / shouldShowRequestPermissionRationale | Rationale conformant; manager recovery different | `server/.../AccessAuthority.kt:35-43` persists deny and reports rationale true; `ConsentRequests.kt:104-107`, `ClientService.kt:137-138,183-186` expose it. `ClientServiceTest.kt:139-149` covers refusal/rationale. But `ClientService.kt:240-250` lists allowed/pending-cleanup groups only. Penpot A.3–A.6 also has authorized apps only. Opening Tsuyu cannot currently undo a plain refusal. |
| UserService running as shell | Conformant in code and recorded emulator demo/probe | `server/.../ClientService.kt:288-295`, `UserServices.kt:113-126,171,191-208` gate launch, use shell app_process and remove owned processes. `docs/runtime-p2.md:19,30-31,56` records UID 2000 and lifecycle. `unbindUserService(..., remove=true)` is the appropriate per-sequence teardown. Not verified with SygixOs or on the real TV. |

`server/.../` abbreviates `server/src/main/java/fr/sygix/tsuyu/server/`; app Kotlin paths abbreviate `app/src/main/java/fr/sygix/tsuyu/`. Test filenames are under the matching `src/test/java/` tree.

## Refusal nuance

Tsuyu does not permanently forbid a denied client from calling requestPermission again: `ConsentRequests.enqueue` still permits it (`ConsentRequests.kt:55-63`). The upstream demo deliberately stops re-requesting when rationale is true (`docs/runtime-p2.md:18`). Therefore the problem is the missing user-facing reset, not an API requirement to silently ignore every later request. Revocation clears a grant and permits a later request, but the manager cannot select a refused app to perform that reset today.

## Existing mockup versus proposed additions

- H.1/H.2 distinguish first preparation and stopped service. H.5 shows service and prepared-access status separately. S.3 guides the user through Android's key prompt, but cannot establish the device's exact localized text.
- P.1 already explains preparation and exposes optional expiry; E.1 keeps “Conserver la politique actuelle” focused. A post-start offer changes sequencing and needs approval, not a second copy of P3.
- A.1/A.2 already match refusal-first consent. A.3–A.6 cannot support a refused-app recovery guide because they contain no refused row.
- PR #13 changes layout/branding/components only and explicitly preserves P2/P3 outcomes. It fixes neither refusal recovery nor caller-return automation.
- Penpot now holds the selected boards with their families: H.7 and H.8 in Home, A.7 in Access, and U.1–U.6 in a new Updates page. See design.md.

## Suggestions and selection

| Suggestion | Assessment | Selection by Sygix |
| --- | --- | --- |
| Return immediately when service becomes active | The milestone can precede preparation/read-back and hide errors | Explicit « Revenir à … » after verified startup and persistence outcome, only with a platform-shared caller identity (design D1) |
| Offer boot restart just after first start | Already fulfilled by one consented sequence | Keep the existing P3 sequence; correct the SygixOs guide (design D2) |
| Change a refusal in Tsuyu | Real missing capability for SygixOs's rationale branch | Reset to undecided in one « Accès des applications » list (design D3) |
| Integrated updates | Valuable maintenance | SygixOs-style verified updates plus silent auto-update, off by default, with automatic restart (design D4–D7) |

## Corrections for SygixOs

1. Revise 9.7:
   - Account for « Activer Tsuyu » as well as « Démarrer », and for the existing preparation disclosure/start sequence.
   - Do not instruct the user to activate an independent switch after successful preparation.
   - Treat « Démarrage au redémarrage » as status/repair when needed.
2. Revise 9.9:
   - Below the first Tsuyu release that ships this change, « Ouvrir Tsuyu » cannot recover a persisted refusal. That release is planned as v0.2.0; gate on its `versionCode`.
   - From that release, guide the user to « Accès des applications » → « Autoriser une nouvelle demande », then request permission again.
3. Launch `MainActivity` explicitly, without `FLAG_ACTIVITY_NEW_TASK`. Pass `ActivityOptions.setShareIdentityEnabled(true)` so Tsuyu can offer « Revenir à SygixOs ». Back stays valid. Keep refusal-focused permission and per-sequence UserService teardown.
4. Keep the three-second binder wait as a bounded SygixOs policy with retry/fallback:
   - Tsuyu updates now restart the service by themselves, so SygixOs must tolerate `binderDied` followed by a new binder.
   - Verify client state again after a reset or reattachment.

The APK asset, release digest/size and cross-application signing-certificate equality are not established by the Kotlin audit or Penpot. They require inspection of the actual release artifacts during SygixOs's existing installation-verification task; no new release or certificate claim is made here.

## Validation limits

`docs/runtime-p2.md`, `docs/runtime-p3.md` and the verification reports contain emulator evidence. `docs/tv-validation-p2-p3.md` is a checklist, not a completed TV session. The last rationale/revocation correction has JVM coverage but has not been rerun end-to-end (`docs/runtime-p3.md:5`). MainActivity caller-task behavior, SygixOs integration and all new exploratory behaviors still require real-device validation. No emulator or TV session was started for this audit.
