# Release and public-repository readiness

## Assessment

- **First release: not yet ready for sign-off.** The correction stack passes JVM/build checks and targeted release-emulator tests. The current version still needs the reference-TV acceptance session, particularly independent AUTO_START read-backs, absent-manager cold boot, fresh client binder with retained consent, and remote permission/revocation behavior. Remaining scenario coverage is explicitly tracked in the two OpenSpec changes; their original tasks are not declared complete by this correction pass.
- **Public repository: no blocker found in the performed publication checks.** This is a targeted source/history/configuration assessment, not a claim that the first release or all security scenarios are validated. Neither visibility nor release settings were changed.

## Checked evidence

| Area | Result |
| --- | --- |
| Corrected P2 | `./gradlew test assembleRelease --no-daemon -q` passes; 95 debug JVM/Compose tests across app and server |
| Corrected combined P3 | Same command passes; 119 debug JVM/Compose tests; unchanged protocol client passes all assertions |
| Runtime cleanup | Direct adapter proves retained group lifetime after target exit and abrupt owner death; actual demo native service runs as UID 2000 and is removed on revoke; final API-client death leaves no supervisor |
| OpenSpec | Strict validation passes all four items; completeness remains 6/21 P2 and 2/17 P3 original tasks, with unproven criteria still open |
| Source provenance | Required SPDX headers present; repository license, MIT/Apache notices and license texts retained; no new third-party dependency introduced |
| Targeted history privacy scan | No private-key blocks, GitHub-token patterns, credential-bearing HTTP URLs or local home paths found in the scanned Git history; correction diffs contain no local home paths or non-loopback IPv4 addresses |
| Tracked artifacts | No tracked APK, keystore, private-key file or build/cache directory found in the inspected source tree |
| Commit integrity | P2 and P3 commits are signed; fixes are autosquashed; P3 includes the corrected P2 base |
| Release workflow | Tag-triggered production signing, required-secret checks, signature verification and staged publication are configured; expected production secret names exist, with no values inspected |
| Actions/public scanning | Actions are SHA-pinned with scoped permissions and the existing allowlist; CodeQL is intentionally skipped while private and must be observed on its first public run |

## Remaining validation

Follow `tv-validation-p2-p3.md` on a TCL Google TV running Android 14. Record observations for the actual production-signed artifact without conflating JVM, emulator and hardware evidence. Complete the remaining acceptance criteria before archive. The full Binder-GC/backpressure and hostile/death matrix is not established by the targeted runtime pass.

A successful production-signed release workflow has not been executed in this correction pass. Creating a tag, publishing a release, merging the PRs and making the repository public require Simon's explicit action authorization.
