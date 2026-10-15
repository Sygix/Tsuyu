# Security policy

## Supported versions

Only the latest release published on the [Releases](https://github.com/Sygix/Tsuyu/releases) page receives security fixes.

## Reporting a vulnerability

Please do not open a public issue or discussion for a security problem.

Report it privately through [GitHub private vulnerability reporting](https://github.com/Sygix/Tsuyu/security/advisories/new). Include the affected version, the device type and the steps to reproduce. Do not include personal data such as IP addresses, serial numbers, account names, server URLs or tokens.

Once a fix is released, the advisory is published and you are credited unless you ask otherwise.

## Release integrity

Release APKs are built and signed by GitHub Actions from signed `v*` tags. Only install APKs downloaded from the [Releases](https://github.com/Sygix/Tsuyu/releases) page of this repository.

## Shell trust boundary

Grant access only to applications you trust with Android's shell identity. A remote process or UserService runs as UID 2000, the same OS identity as the server; this is not a sandbox between mutually hostile shell applications.

Manager persistence and status operations require the PID of the live, launch-token-authenticated server, in addition to UID 2000. Child attachment instead requires its single-use launch token and owned PID. A different shell PID cannot replace an already authenticated live server. These checks prevent direct consent writes by an ordinary child, but do not provide cryptographic isolation from arbitrary shell code capable of inspecting or interfering with other shell processes during a fresh manager handshake. This residual same-UID trust limit also applies to upstream-style shell services.
