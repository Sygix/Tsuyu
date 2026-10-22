# Proposal

## Why

SygixOs PR #76 plans to install and open Tsuyu to configure its home-screen access. Its guide assumes a startup sequence and a refusal-recovery action that do not match the current Tsuyu application, so the integration needs an evidence-based contract and explicit product decisions before implementation.

## What Changes

- Record the comparison of SygixOs's expected contract with Tsuyu main, the open presentation proposal and the actual Penpot boards in `contract-audit.md`.
- Present the four integration suggestions for owner review in design.md's Open questions, with conservative defaults and unselected Penpot explorations.
- Identify the exact SygixOs guide corrections and separate code compatibility from emulator evidence and outstanding real-TV validation.

This is a decision-stage planning change. No new product behavior is approved by this document. `skip_specs: true` intentionally records that this revision changes documentation only: behavioral deltas and implementation tasks follow the owner's decisions, rather than encoding unapproved suggestions as requirements. It is not ready for apply or archive.

## Capabilities

### New Capabilities

None in this revision.

### Modified Capabilities

None in this revision. Existing owners are `local-start`, `tv-navigation`, and P2/P3's `client-access`, `shizuku-api` and `boot-persistence`. Candidate extensions are confined to Open questions until selected.

## Impact

No application code, public API, dependency, permission, signing configuration or SygixOs file changes. Penpot gains a separate exploratory page; existing boards remain the accepted reference. The audit names relevant existing implementation boundaries without prescribing unapproved replacements.

Dependencies: P1 is archived; P2/P3 code is on main but their changes await archive. PR #13 (`material3-tv-alignment`) proposes presentation only. Reuse those contracts and visual components; do not duplicate their startup, consent, expiry or focus semantics.

## Non-goals

- Implementing the suggestions before owner selection, changing the validated SygixOs change, publishing a release or claiming real-TV compatibility.
- Reopening P1's authentication or P2's Shizuku protocol/security model.
- Changing P3's pre-mutation disclosure, optional global-expiry consent or verified boot-readiness rules.
- Replacing P4's release/signing work. Any updater scope and its delivery stage remain an Open question, not an implicit addition to P4.

## Open questions

The four choices, alternatives, recommendation and conservative default are in design.md, Open questions. No option has been selected. They are repeated under “Décisions à valider” in the PR. The implementation request must follow approval and reconciliation of all planning artifacts.
