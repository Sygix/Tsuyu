# Design

## Context

See proposal.md for motivation and contract-audit.md for the point-by-point evidence. Baselines inspected: Tsuyu main `a019775`, presentation PR #13 head `13293b5`, SygixOs PR #76 head `df9504244a7d97c0e7bc0935b2532c179a3b05c0`. SygixOs is read-only for this work.

Current boundaries relevant to the suggestions:

- `MainActivity` is exported with standard task behavior. Home Back calls `finish()`; no caller-return mode or automatic success finish exists.
- `MainActivity.handleAction` already routes START/ENABLE through `PersistenceController.startRoute()`. If not previously enabled, the disclosure precedes startup. `PersistenceController.enable()` starts the server, prepares and reads back persistence, then saves enablement when schedulable. This is already one consented sequence, not a later switch.
- `AccessAuthority` stores allow/deny decisions for verified identity groups. `ClientService.accessList()` exposes authorized and pending-cleanup groups, not ordinary refused groups. `clear(uid)` exists; no denied-list/reset UI exists.
- `HomeViewModel`, `AccessViewModel` and `PersistenceViewModel` own presentation state. A new route cannot rely on composable-local duplicate state.
- P2 defines live request identity, refusal focus, client callbacks, revocation and shell UserServices. P3 defines disclosure, preparation and optional expiry. PR #13 changes presentation, not those outcomes.

## Goals / Non-Goals

**Goals:** make the actual integration contract inspectable; distinguish current behavior, device assumptions and unselected recommendations; let the owner approve the smallest useful scope.

**Non-Goals:** choose a new startup mode, invent caller authentication from intent extras, grant client access on manager entry, or add a second startup/authorization authority. Full scope exclusions are in proposal.md.

## Decisions

### Evidence and planning boundary

Keep the code and current requirements unchanged in this revision. The user explicitly requested unmade product decisions in the PR, so the alternatives below are review material, not normative behavior. There are no candidate implementation tasks or SHALL clauses hidden outside Open questions. `skip_specs` is only appropriate for this documentation-stage revision; it must be removed if approved behavioral scope is added.

### Existing security and error contracts

Continue to treat manager identity as the authority for access administration; a package label/referrer supplied by another app is not authentication. Existing public client binders do not authorize manager operations. Reuse verified installed identities and shared-UID groups. Existing bounded startup, provider, storage and preparation outcomes distinguish denial, unsupported facilities, timeout and unverified mutation. Finishing a screen proves neither binder delivery nor client authorization. No privileged endpoint, copied source or dependency is introduced by these documents; existing provenance and licenses remain unchanged.

### Visual evidence

[Penpot exploratory page](https://design.penpot.app/#/workspace?team-id=52a5041f-08a1-80f1-8008-c3c379c88195&file-id=cd444aaf-0d8c-801b-8008-ce19b8b9f465&page-id=b41ac809-9298-800b-8008-ce292740e460), “Client setup · proposals”, contains three UNSELECTED boards. A named version preserves the preceding state. The existing 44 boards and shared components were preserved; three new boards make 47 across ten pages. The new boards are alternatives supporting the questions below, not changes to PR #13's accepted reference or additional application routes.

## Risks / Trade-offs

- [SygixOs describes a button that depends on state] → correct its guide from actual startup routing; keep the external integration test before coding.
- [A launch referrer is mistaken for caller authentication] → no privileged consequence may follow merely from entry context; a proposed return affordance needs a separately verified lifecycle/identity design after selection.
- [Static success board implies a verified future boot] → existing P3 read-back/status semantics prevail; rendering is not execution evidence.
- [Resetting a server decision leaves an upstream client cache stale] → validate with the unchanged upstream library and a reattached client before defining the final recovery contract.
- [Immediate return hides preparation or an error] → keep the service-live and persistence-result milestones separate in the decision; never finish on cached RUNNING alone.

## Migration Plan

This revision migrates no data and changes no running behavior. Publishing the exploratory page does not select its variants. Rollback of these documents leaves existing code and Penpot originals intact.

Archive order for implemented behavior remains P2 → P3 → `material3-tv-alignment` where applicable. This decision-stage change is not ready for archive. After selection, reconcile the affected capability deltas and document their archive order before implementation; never edit `openspec/specs/` directly. A future refusal-recovery extension belongs to `client-access`; any preparation change belongs to `boot-persistence`; navigation belongs to `tv-navigation`, avoiding duplicate requirements.

## Open questions

All recommendations below are **to confirm**. The conservative default until selection is the existing behavior. These questions intentionally block a behavior-changing apply, while allowing the requested audit PR to be reviewed now.

### Q1 — Return to the opening application

- **Recommendation, to confirm:** retain a caller-return improvement, using an explicit “Revenir à …” action after verified startup and the persistence outcome, rather than immediately closing on service liveness. This avoids hiding preparation, optional policy explanation and failures. CS.2 illustrates this option; it is not selected.
- Alternatives: automatic return only after the agreed sequence completes; or retain Back-only navigation. Immediate return as soon as the server is live is not recommended because it can race preparation and hides useful recovery.
- Conservative default: Back-only, unchanged. An ordinary launcher entry must keep working without a claimed caller.
- Selection must also settle whether a client opts into the return flow or any external opening qualifies. An explicit launch alone does not reliably authenticate the application to name, especially across recreation; arbitrary intent text must not become a trusted return destination. Cancel, lost caller, caller death, recreation and already-running entry need observable fallback behavior before this is specified.
- Any selected variant must preserve separate Shizuku permission consent and must not promise delivery within SygixOs's three-second local wait. That wait remains a client policy, not a Tsuyu guarantee.

### Q2 — Boot preparation in the startup sequence

- **Recommendation, to confirm:** retain the goal of one understandable sequence, but reuse the already implemented pre-start disclosure → start → verified preparation path. Do not add a second mandatory post-start consent page. Correct SygixOs's instruction that the user must activate a separate setting afterwards.
- Alternative: separate manual service start from a post-start persistence offer. CS.1 illustrates this unselected variant only; it would change P3, not merely its styling. Its “Préparer le redémarrage” action is exploratory wording, not an accepted replacement for the existing labels.
- Conservative default: existing P3 route, with global key expiry still separately optional and initially focused on keeping the policy. Choosing a new post-start offer also requires deciding what skipping means, what is stored and whether manual-only operation is a new supported state.
- Neither service startup nor a return action may imply persistence readiness without its checks. Unsupported vendor access, partial preparation, refusal and timeout retain distinct outcomes.

### Q3 — Reconsider a persisted refusal in Tsuyu

- **Recommendation, to confirm:** retain this improvement as the priority functional extension: show refused identity groups and offer “Autoriser une nouvelle demande”. Clear the matching recorded refusal without granting access; the client then makes a fresh `Shizuku.requestPermission`, with Refuser initially focused and normal callback/return semantics. CS.3 illustrates this unselected option.
- Alternatives: a manager-owned permission review that can explicitly grant; or leave refusal recovery unavailable. A direct allow switch needs an additional deliberate consent design and is not the conservative recommendation.
- Conservative default: current authorized-only list, with no claim that opening Tsuyu can clear a refusal.
- Placement also needs selection: extend the current list into “Accès des applications” with status rows (recommended), or keep separate authorized/refused lists. CS.3 intentionally does not settle its home entry.
- Before normative deltas, verify the unchanged API client's cached rationale/reattachment behavior: denial should remain until fresh consent; after reset and appropriate reattachment, `shouldShowRequestPermissionRationale()` should cease reporting a recorded refusal; a new request must again receive its matching result. Define missing identity, shared-group changes, storage/server failure, timeout/reconciliation, concurrent requests, reboot and focus fallback. Resetting a refusal is not revocation and must not reuse its process-cleanup success wording.

### Q4 — Integrated Tsuyu updates

- **Recommendation, to confirm:** retain the need for an accessible update path, but defer implementation to a separate distribution follow-up after P4's release contract is settled. It is useful maintenance, not required for this caller handoff.
- Alternatives: include a minimal user-initiated updater in this change; or rely on externally installed updates. No automatic background installation is implied by any option.
- Conservative default: current GitHub release/install path. No updater screen is proposed and no update dependency is introduced here.
- A follow-up needs decisions about trigger, prerelease policy, unavailable/offline releases, installer cancellation, minimum compatible API and signing-key rotation. Use Tsuyu's own installed signing identity as the update trust anchor, not a runtime dependency on SygixOs being installed. P1/P3 already own old-server shutdown, same-signature key/consent preservation and replacement restart; do not duplicate them in an updater.
