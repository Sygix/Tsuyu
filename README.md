# Tsuyu

Tsuyu (露, "dew") is a remote-driven privileged access service for Android TV / Google TV. It starts a service with ADB (shell) rights on the TV itself, with no computer and no phone. The complete v1 will expose the [Shizuku API](https://github.com/RikkaApps/Shizuku-API) and restart after each reboot; the phase status below describes what currently works.

> Work in progress: no release yet.

## Why

Shizuku and its forks were designed for phones. On a TV, their way to start without a computer breaks:

- the wireless debugging pairing code dies as soon as the system pairing screen loses focus;
- there is no split screen and no notification you can reply to.

Tsuyu starts through the TV's own ADB daemon instead, with a one-time "Always allow" confirmation on the TV screen.

## Local startup (P1)

The implementation starts a local shell-uid server and includes the P2 client contract. P2 integration validation is pending; automatic restart follows in P3.

1. Open Tsuyu with the TV remote and enable developer options and USB debugging when requested.
2. Choose **Démarrer**. On the first system prompt, select **Always allow**, then **Allow**.
3. **Service actif** appears only after the manager binder reports shell uid and lifecycle observation is ready. **Détails** shows technical status; **Aide** explains recovery.

Debugging leaves a port open on the local network, accessible only to authorized devices. Tsuyu uses a private, non-backed-up RSA key and supports legacy local TCP debugging; TVs offering only TLS wireless pairing are outside v1.

The launch shell waits for confirmed server readiness before returning. The server runs in a detached session and survives closure of the launch connection. Keep USB debugging enabled during use: on the reference TV, disabling it kills the daemon's entire cgroup, including the detached server. Re-enable debugging and start Tsuyu again using its retained key and authorization; do not revoke ADB authorizations or reinstall to recover a launch failure. The server also exits on manager replacement/removal or when required lifecycle observation cannot be restored. Same-signature updates preserve its private key; P1 requires manual restart after an update or TV reboot. Tsuyu never re-enables debugging automatically.

Tests use `./gradlew test` and `./gradlew assembleRelease`. The Android TV emulator simulates the reference TV's legacy endpoint after each boot; vendor behavior and couch-distance UI rendering still require validation on the real TV.

## Shizuku API compatibility (P2, validation in progress)

The server implements the upstream API 13 / patch 6 wire contract (client library 13.1.5). Startup enumeration and UID/process observers deliver the binder to manifests requesting `moe.shizuku.manager.permission.API_V23`, without requiring an Android grant. Binder possession alone grants no access.

Permission requests identify installed applications, initially focus **Refuser**, and persist decisions in app-private DataStore. Applications sharing a UID are a disclosed access group: signer or membership changes invalidate consent. **Applications autorisées** exposes revocation, which denies new operations, terminates owned children and force-stops affected clients to discard cached capabilities. Incomplete withdrawals stay visible and retry finishes cleanup rather than only reloading the list; the UID remains denied while cleanup is pending. The list separately identifies applications whose binder delivery failed and offers recovery guidance.

Package visibility is used to resolve verified names/icons for all related applications, including requesting clients without a launcher. It grants no settings-write privilege. Release demo/protocol results and remaining acceptance limits are recorded in [runtime evidence](docs/runtime-p2.md).

Remote processes and UserServices are implemented, including shell identity, descriptors, bind/peek/unbind/remove and native package class loading. A session-ready supervisor confirms target execution and retains process-group ownership after target exit, so ordinary descendants can still be stopped on release or revoke. Core release-emulator behavior is verified with the upstream demo and a separate protocol client; real-TV validation and the remaining scenario matrix are still open. See [verification evidence and gaps](docs/verification-p2.md). The upstream demo is required validation; SygixOs is optional evidence when an API-client build is available. Same-signature updates preserve consent storage; pending requests and launch tokens are process-local.

Tsuyu is not Shizuku and uses its own package name. An app that only checks whether the Shizuku app (`moe.shizuku.privileged.api`) is installed will still report it as missing.

## Credits

Tsuyu is a clean rewrite for TV. Its design and parts of its code come from:

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) (Apache-2.0) and [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API) (MIT), by RikkaW;
- [HmnDev-Tech/shevery](https://github.com/HmnDev-Tech/shevery) (Apache-2.0), a Shizuku fork that served as the starting point.

Details are in [NOTICE](NOTICE).

> Independent project, not affiliated with Shizuku, Google or TCL. Android TV and Google TV are trademarks of Google LLC.

## License

Tsuyu is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE). Files derived from Shizuku or Shevery keep their Apache-2.0 license, and code derived from Shizuku-API keeps its MIT license, as stated in each file header and in [NOTICE](NOTICE).
