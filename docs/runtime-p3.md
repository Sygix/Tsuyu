# P3 combined release evidence

The combined P2/P3 emulator pass and targeted protocol checks use release builds, with development signing only. P2's detailed client/protocol observations are in `runtime-p2.md`.

The final compiled artifact after the unit-tested revocation/rationale correction has SHA-256 `44b488e2b393cb73462574d824d8a6831d400e69b92048a81f84818433970b10`, size 2,117,897 bytes. Its `test assembleRelease` and strict OpenSpec validation pass. The runtime-tested artifact/hash is retained separately in `runtime-p2.md`; the last revocation correction was not attributed to a repeated full E2E.

| Scenario | Observed | Status |
| --- | --- | --- |
| Disclosure before enablement | Effects explained before preparation; Back leaves the global expiry policy unchanged | PASS |
| Missing vendor AUTO_START | Both mode queries report unknown operation; UI identifies ordinary Android scheduling rather than claiming vendor grants | PASS |
| Optional expiry | Initial focus keeps policy; keeping/viewing leaves the unset policy unchanged; explicit action writes zero and displays verified read-back | PASS |
| Policy changed elsewhere | The test fixture restores the original unset policy; Tsuyu does not reapply the earlier explicit action | PASS |
| Absent manager before reboot | Manager backgrounded and killed without force-stop; no PID returned before the Android reboot | PASS |
| Boot delivery/scheduling | WorkManager initializes in the automatically started process, schedules RestartWorker, and reports SUCCESS without manually opening Tsuyu | PASS |
| Emulator daemon fixture | Legacy TCP debugging is restored after reboot as a fixture operation; USB debugging remains enabled | FIXTURE, not a Tsuyu action |
| Server/client return | One shell server appears; the demo receives a new binder, retains consent and executes its Binder operation, then a new native service reports UID 2000 | PASS |
| Same-signature updates | Installing corrected release APKs restarts the server; existing demo/probe consent and the private ADB key remain usable without renewed confirmation | PASS for successful update path |

Virtual-time JUnit independently proves delayed endpoint, finite exhaustion, permanent setup/auth failures, manual/boot overlap, separate event budgets and rejected old epochs. Those tests do not prove precise Android scheduling latency or TCL's vendor gate.

## Boundaries

- The initial combined boot evidence was collected before the final P2 wire/cleanup corrections. P3's domain changes were transferred unchanged onto the reviewed corrected P2 base; final release protocol checks additionally passed after same-signature replacement and fresh server attachment.
- The single tester invocation built the external demo and prepared fixtures. The main agent completed its interrupted pass and performed the subsequent targeted checks; no second tester was invoked.
- Remaining failure permutations, receiver/database process-death tests, update failure/uninstall integration and every UI disappearance case are not claimed complete. Their acceptance tasks remain open.
- No TV evidence or production-signed APK was generated. Claude's single hardware session remains pending, using `tv-validation-p2-p3.md` once an owner-authorized CI release exists.
- All started emulator/build processes were stopped. Useful screenshots were sent directly to the owner.
