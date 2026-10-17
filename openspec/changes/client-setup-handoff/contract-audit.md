# SygixOs integration contract audit

## Scope and evidence

Read-only comparison on 2026-10-17: Tsuyu main `a019775`; only open Tsuyu PR #13, `material3-tv-alignment`, head `13293b5`; SygixOs PR #76 `tsuyu-home-setup`, head `df9504244a7d97c0e7bc0935b2532c179a3b05c0`, design D2/D4/D5 and Suggestions. All source paths below are relative to Tsuyu. Code conformity is not a new real-TV claim.

P2/P3 implementations are already on main; their planning changes still await archive. PR #13 contains no implementation. Penpot's existing Home, Setup, Restart, Expiry and Access pages were inspected, not inferred from the PR description.

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
- New CS.1–CS.3 are separately marked UNSELECTED on “Client setup · proposals”; see design.md for the alternatives. They do not supersede the originals.

## Suggestions: assessment, not selection

| Suggestion | Assessment | Product selection |
| --- | --- | --- |
| Return immediately when service becomes active | Useful goal, but the milestone can precede preparation/read-back and hide errors | Q1 compares explicit return after outcome, automatic return after sequence, and existing Back |
| Offer boot restart just after first start | Goal already largely fulfilled by one consented sequence; moving the offer is a P3 behavior change | Q2 compares retaining that sequence with a separate post-start offer |
| Change a refusal in Tsuyu | Real missing capability; important for SygixOs's rationale branch | Q3 compares reset-to-undecided with a direct consent review and selects neither |
| Integrated updates | Valuable maintenance; broader release/installer policy than handoff | Q4 compares a separate distribution follow-up, in-scope updater and external updates |

## Corrections for Claude in SygixOs

1. Revise 9.7: account for “Activer Tsuyu” as well as “Démarrer” and the existing preparation disclosure/start sequence. Do not instruct the user to activate a nonexistent independent switch after successful preparation. Treat “Démarrage au redémarrage” as status/repair when needed. Keep separate service-liveness and preparation outcomes.
2. Revise 9.9: for current Tsuyu, “Ouvrir Tsuyu” cannot recover a persisted refusal. Do not promise that recovery until an implementation providing it is shipped and the supported Tsuyu version/capability is defined. Do not invent a new minimum version in this audit. Reinstallation would discard the private key and all consent and is not an appropriate silent substitute.
3. Keep explicit `MainActivity` launch without `FLAG_ACTIVITY_NEW_TASK`, user Back, refusal-focused permission and per-sequence UserService teardown as the current baseline. Automatic return and CS boards are proposals only.
4. Keep the three-second binder wait as a bounded SygixOs policy with retry/fallback; no Tsuyu guarantee makes a slow delivery impossible. Verify client state again after any reset/reattachment.

The APK asset, release digest/size and cross-application signing-certificate equality are not established by the Kotlin audit or Penpot. They require inspection of the actual release artifacts during SygixOs's existing installation-verification task; no new release or certificate claim is made here.

## Validation limits

`docs/runtime-p2.md`, `docs/runtime-p3.md` and the verification reports contain emulator evidence. `docs/tv-validation-p2-p3.md` is a checklist, not a completed TV session. The last rationale/revocation correction has JVM coverage but has not been rerun end-to-end (`docs/runtime-p3.md:5`). MainActivity caller-task behavior, SygixOs integration and all new exploratory behaviors still require real-device validation. No emulator or TV session was started for this audit.
