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

## Final corrected stack

- Final combined correction APK: SHA-256 `6a798eb5b43c41b7ab11059f1524e7c1f3e7679f20ab0c70ae95b45539d803ff`, development-signed release build. `./gradlew test assembleRelease --no-daemon -q` passes, with 119 debug JVM/Compose tests across app and server (the release variant also runs).
- P3 is rebased onto the corrected P2 supervisor, retained partial-revocation state and per-client delivery diagnostics. The final artifact passes the unchanged authorized external protocol client in full; process-owner death leaves no supervisor after bounded polling. Detailed distinction between API, demo and direct-adapter evidence is in `runtime-p2.md`.
- On the final emulator boot/update pass, legacy TCP is explicitly restored as the emulator fixture. The shell server then starts and the client receives its binder and retained consent without opening Tsuyu's activity. This does not establish the vendor AUTO_START gate or precise scheduling latency.
- The pass preserves the existing private key and client consent through same-signature replacement. It does not repeat every earlier optional-expiry, failure/update/uninstall and focus scenario. Typed state, ledger migration and recovery are covered by the current JVM suite rather than an invented new full boot matrix.
- Real-TV/vendor validation and production-signed release execution remain open. No archive, merge, tag, release publication or repository visibility change was performed. The emulator and started processes are stopped.
