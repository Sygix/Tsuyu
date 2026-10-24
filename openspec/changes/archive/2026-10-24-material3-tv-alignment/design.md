# Design

## Context

See proposal.md for motivation and scope. Baseline: main at `a019775`, with P1–P3 code integrated; P2 and P3 are archived into `client-access`, `shizuku-api` and `boot-persistence`. The latest brand assets are newer than the mockup source inspection and must remain intact.

Observed implementation:

- `ui/HomeScreen.kt`: `StateScreen` defines an inline dark theme and one full-height column, with explanatory content above a bottom action column. Focus already scales by 1.03 and moves vertically without wrap.
- `access/AccessScreen.kt`: another inline theme, one scrolling column, unframed identity groups, and default TV button styling. Permission actions are already horizontal and refusal-focused.
- `persistence/PersistenceScreen.kt`: delegates to `StateScreen` and adds raw mode/expiry text. `PersistenceViewModel` already supplies loading, mode, error and policy results.
- `HomeViewModel`, `AccessPresentation` and `PersistenceViewModel`: own existing controls, focus selection and resource-backed copy. Some visually similar boards are different values of the same screen state, not new routes.
- `strings.xml`: most mockup copy already exists; home boot-failure wording and split supporting information are presentation differences. Help still describes manual restart after reboot without explaining the conditional P3 path.

## Goals / Non-Goals

**Goals:** one shared Material 3 TV foundation, consistent component states and layouts, same functions and security guarantees, measurable visual acceptance against named boards.

**Non-Goals:** server or IPC changes, a second UI state authority, user-selectable themes, new dependencies, copied Shizuku/Shevery source, or adding a destination for every Penpot page.

## Decisions

### 1. Reference and precedence

Use the Penpot file linked in proposal.md. Page/board identifiers below are the visual baseline, not a new product state machine. Observable requirements in this change and the existing capability contracts override illustrative values and placeholder content. SygixOs supplies organizational precedent only; the user explicitly chose Google Material 3 for Tsuyu.

| Penpot boards | Observed difference from code | Implementation boundary |
| --- | --- | --- |
| DS.1–DS.3, C.1–C.4 | Palette, original brand artwork and component states are documented centrally; code repeats values and mixes custom/default controls | Shared theme, brand, spacing, typography and component definitions |
| H.1–H.6 | Information-left/actions-right; boot status in a dedicated surface rather than a plain line | Recompose existing HomeUi and its controls; retain boot-failure focus override |
| S.1–S.4 | Separated step content and pending-auth progress | Existing developer/debugging/auth/unknown states; preserve local-network disclosure and Android prompt ownership |
| A.1–A.2 | Centered rounded decision surface rather than a plain page column | Existing PermissionActivity and live request token; keep real installed name/icon and all group members |
| A.3–A.6 | Combined identity/revoke row surfaces, explicit loading/empty/error composition | Existing list and cleanup-retry semantics; no confirmation dialog added |
| P.1–P.6 | Separate preparation status/progress and supporting explanation | Existing PersistenceUi; no new settings route or automatic grant |
| E.1–E.4 | Separate policy explanation and outcome information | Existing optional expiry flow; retain keep-policy initial focus and no redundant write when already disabled |
| D.1–D.3 | Split supporting text and service-status surfaces | Existing help/details destinations; explain reboot/update behavior consistently with P3 |
| R.1–R.8 | Consistent two-column recovery/status treatment | Existing typed failures and available actions; no new recovery backend |

All 37 app boards are state-coverage references, not a requirement to create 37 composables or activities. Preserve meaningful states not individually pictured, including per-client delivery errors, pending cleanup and standard Android scheduling without a verified vendor operation.

The same file also holds `client-setup-handoff` boards, placed with their families: H.7–H.8 in Home, A.7 in Access and U.1–U.6 in the 09 · Updates page. They reuse this foundation, but that change specifies them, and they are outside this change's acceptance.

### 2. Foundations and Android units

Create a shared `ui/theme` foundation used at the three activity entry points or common screen roots. Continue `androidx.tv.material3`; introducing a phone component library or a second theme mechanism is unnecessary. Use semantic Material color roles, not repeated literals in each composable.

The Penpot palette is: background `#10161E`, surface `#1E2936`, raised surface `#293645`, primary `#A8D5FF`, on-primary `#003353`, main text `#E6EDF5`, secondary text `#C6D1DF`, outline `#667585`, error `#FFB4AB`, error container `#493133`. The primary/on-primary pair changes the current code's cyan accent to a pale blue matching the supplied blue/cyan/violet mascot; the other roles stay stable. Its measured contrast is 8.52:1; validate contrast after applying disabled opacity too. Preserve real application icons and current launcher/banner assets; the generic S/A/B tiles in Penpot are client placeholders.

Reference canvas is 1920 × 1080; the existing test viewport is 960 × 540 dp at xhdpi. Convert dimensions using density rather than treating canvas pixels as dp/sp. The revised canvas type styles are 64/48/36/32/32/32 px: labels and supporting copy now also meet 16 sp at the reference viewport. Keep the currently readable 20 sp body and 36 sp state-title baseline where needed, adapting wrapping instead of shrinking text. Validate physical rendering rather than assuming a canvas pixel is an Android layout unit.

Use a spacing scale based on 4/8 dp increments. At the reference viewport start with 48 dp horizontal and 27 dp vertical safe inset, increasing it where existing content needs room; the focused 1.03 scale must also stay inside the inset. State-screen content/action proportions follow the approximately 55/45 mockup split, with a clear gutter; allow height and wrapping to grow. Rounded supporting panels follow the reference's 24 px radius (12 dp at reference density), and pill buttons keep the TV Material shape.

### 3. Reusable compositions, feature-owned content

Extract small shared building blocks for the app header, TV action button, state/status surface, supporting panel, operation progress, app identity and access row. Compose the common information/actions shell for home, setup, persistence, expiry, help and recovery. Use a dedicated centered permission composition and a scrolling access list where the task differs, rather than forcing every screen into one generic template.

Use the original mascot from `mipmap-xxxhdpi/ic_launcher_foreground.png` in a shared brand component. Only remove its transparent outer padding, preserving opaque artwork, its black backing and aspect ratio. In Penpot, `Brand/Mascot` is nested in the compact `App/Header` and larger `Brand/Signature`: H.1–H.6 use a 144 px mark with 36 px corners and editable 48 px app name; the other 31 states use a 96 px mark with 24 px corners and editable 36 px name. At reference density, home uses a 72 dp mark, 18 dp corners and 24 sp name; secondary screens use a 48 dp mark, 12 dp corners and 18 sp name. Brand content is non-interactive, shares the safe area and must not push consent or recovery actions off screen. Keep the original illustrated wordmark/banner intact on DS.3 as a brand reference; do not stretch it into an in-app header or alter launcher assets to obtain a crop. Never substitute the Tsuyu mascot for a requesting client's icon.

Actions remain supplied by existing presentation models, keyed by stable control IDs; identity rows retain their UID grouping. A component accepts already-derived text, role and availability. It does not inspect platform settings, launch coroutines, mutate state or infer permission results. Add presentation fields for existing loading/supporting state only when needed; do not create duplicate booleans that compete with ViewModel StateFlow.

Use existing TV focus scaling and the Remote focus contract as the single navigation authority. The right action column is the only focusable region on state screens; status/progress panels do not steal focus. Permission decisions remain horizontal. List actions stay one per identity group, with scroll-to-focus and existing removal fallback. Long labels wrap and rows grow; focus/navigation must reveal all group members before a decision without a hidden gesture.

### 4. Reconcile illustrative boards with live behavior

- P.6 illustrates an Open settings action for a generic preparation failure. Keep `PersistenceViewModel` routing: settings recovery only for setup-required state; otherwise repair/refresh the actual operation. Do not add a new mutation or alter recovery routing to match a static label.
- P.2 and E.4 illustrate ongoing work. Progress mirrors real pending state; unavailable mutating actions are excluded from focus/activation, while an existing safe return or keep-policy action remains reachable. Do not disable unrelated navigation just to match an illustration.
- R.8 is a compatibility failure while the server may still be live. Preserve live-service status separately and show only the existing recovery actions. A client error is not proof of server death.
- P.3/P.4 distinguish verified preparation from unsupported vendor access. Neither guarantees a future boot. Do not replace current status verification or invent a countdown/percentage.
- Preserve setup's local-network disclosure, shared-UID scope, the effect of revocation on privileged processes and apps, and the global effect of optional expiry changes even when text is moved to supporting panels.
- Human-facing supporting copy uses effects rather than UID/ADB/binder or raw milliseconds; retain exact mode/expiry values in an explicitly labelled technical-details region. This is a presentation split within existing destinations, not a new settings workflow.
- Keep the more specific incomplete-revoke action/cleanup explanation when applicable. A generic error board does not replace the pending-cleanup record or turn its retry into a list refresh.

### 5. Loading, copy and motion

Derive progress from current startup phases, access loading and persistence loading. Use an indeterminate indicator, never a fake completion fraction. It is active only during pending work and respects system animation scale; idle pages remain static. Focus and screen transitions stay within DESIGN.md's timing guidance, with no forced animation when disabled.

Keep all French copy in resources. Preserve existing messages unless grouping or clarity actually changes; split dense passages into body/supporting strings. Update help to distinguish ordinary prepared automatic restart from manual recovery and debugging re-enablement, without promising boot delivery on an unverified TV. Update DESIGN.md's visual-language section to identify Material 3 as the visual source and Penpot as the component/layout reference.

The revised expiry action reads “Désactiver pour toutes les clés”; the title and supporting explanation retain the explicit global-expiration scope, so shortening the button does not narrow the consent.

### 6. Privilege and provenance

Reuse P1/P2/P3 manager operations, deadline handling and typed failures; there is no new privileged endpoint, caller authority or storage format. Error surfaces reflect actual Result/state outcomes, not local guesses. The public Shizuku API and all client-side allow/refuse/revoke results remain governed by P2. No reference-project code is imported; new Kotlin files require the Tsuyu AGPL header and NOTICE changes only if an additional origin is actually introduced.

## Risks / Trade-offs

- [Canvas pixels are not Android layout units] → use the reference density conversion, preserve minimum text size and validate the release on a TCL Google TV running Android 14 before claiming visual parity.
- [Long names/groups or enlarged text exceed static composition] → wrap/grow and D-pad-accessible scrolling; test real measured bounds and focus, not exact screenshots alone.
- [Static artwork hides intermediate/error cases] → use existing presentation state as authority and cover pending cleanup, delivery failure and unsupported prerequisites explicitly.
- [Shared theme changes default on-colors or focus] → verify contrasting focused labels and all action states across access and persistence, not only home.

## Migration Plan

No data migration. Introduce shared foundations, migrate the common state shell, then access compositions, then supporting copy/progress. Existing operation adapters and stored decisions remain untouched. Compare screenshots of each board family at matching viewport plus oversized-content cases. A rollback restores UI composition only and does not undo any user permission or global setting.

Archive `material3-tv-alignment` only after its implementation is merged and validated on the real TV, and before `client-setup-handoff`. It only touches `tv-navigation`, so no manual synchronization of the other current specs is needed.

## Open Questions

None for scope. Device rendering and motion are verification work, not new product decisions. This PR proposes the presentation changes for approval; implementation begins only on a subsequent request.
