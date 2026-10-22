# Tasks

This is a decision-stage documentation change, not a behavior implementation plan. Q1–Q4 remain for owner review as requested; none is converted into a build task. File-existence completion in the CLI does not remove this approval boundary.

## 1. Verify device assumptions before behavior changes

- [ ] 1.1 On the reference TV, install `./gradlew assembleRelease` output and exercise an explicit caller-task MainActivity launch without NEW_TASK: first preparation, already-running entry, settings and persistence sub-screen Back, cancellation, recreation and caller disappearance. Record actual labels, focus and destination; only captured outcomes count as verified.
- [ ] 1.2 With the unchanged Shizuku-API demo and an API-client SygixOs build, record binder delivery, version, refusal callback/rationale, permission-screen return and shell UID 2000 inside a bound UserService, then remove it and verify process/binder death. Include process restart and TV reboot; distinguish current refused-app UI absence from future recovery behavior.

## 2. Reconcile the selected scope before apply

- [ ] 2.1 Record the owner's answers to Q1–Q4, reconcile proposal/design/Penpot and the PR, and add only selected behavioral deltas and verifiable implementation tasks. Acceptance: no unselected choice outside Open questions; `skip_specs` is removed if behavior changes; affected requirements preserve all existing scenarios and `openspec validate --all --strict` passes.

## Workflow follow-up

- Any approved behavior implementation plan must include JUnit state/permission tests, Compose D-pad tests with `@Config(qualifiers = "w960dp-h540dp-xhdpi")`, French resource strings, testTags, required license headers and NOTICE if a new source is adapted, README changes, `./gradlew test`, and release-build validation on the real TV. The final acceptance scenarios depend on Q1–Q4 and are not preselected here.
- Revalidate the upstream demo and SygixOs recovery path after any selected access change, including client cache/reattachment, refusal, missing feature, bounded timeout and post-reboot outcomes.
- Do not archive this decision-stage revision. After approved implementation, merge and real-TV validation, archive dependencies in order and run `openspec archive <id>` followed by `openspec validate --all --strict`.
