<h1 align="center"><img src=".github/assets/readme-logo.png" alt="Tsuyu" width="640"></h1>

[![test](https://github.com/Sygix/Tsuyu/actions/workflows/test.yml/badge.svg)](https://github.com/Sygix/Tsuyu/actions/workflows/test.yml)
[![release](https://img.shields.io/github/v/release/Sygix/Tsuyu?include_prereleases&sort=semver)](https://github.com/Sygix/Tsuyu/releases)
[![license](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)

Tsuyu (梅雨, "rainy season") is a remote-driven privileged access service for Android TV / Google TV. It starts a service with ADB (shell) rights on the TV itself, with no computer and no phone. The complete v1 will expose the [Shizuku API](https://github.com/RikkaApps/Shizuku-API) and restart after each reboot; the phase status below describes what currently works.

> Work in progress: alpha pre-releases (`tsuyu.apk`) are published on the [Releases](https://github.com/Sygix/Tsuyu/releases) page; P2 and P3 still await validation on the real TV.

## Why

Shizuku and its forks were designed for phones. On a TV, their way to start without a computer breaks:

- the wireless debugging pairing code dies as soon as the system pairing screen loses focus;
- there is no split screen and no notification you can reply to.

Tsuyu starts through the TV's own ADB daemon instead, with a one-time "Always allow" confirmation on the TV screen.

## Local startup (P1)

The implementation starts a local shell-uid server, includes the P2 client contract and P3 boot scheduling. Combined release integration validation is pending.

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

## Boot persistence (P3, validation in progress)

**Démarrage au redémarrage** explains preparation before mutation. On explicit enablement, the shell verifies both UID and package vendor `AUTO_START` modes independently. A missing vendor operation enables ordinary Android scheduling but does not establish vendor readiness. The Home screen distinguishes live service from verified/unverified boot access; opening status never repairs access automatically.

After completed enablement, normal boot and `MY_PACKAGE_REPLACED` schedule unique WorkManager startup without opening Tsuyu or requiring Internet. It shares the local coordinator, key, singleton server and client consent. Initial scheduling waits twenty seconds; at most three attempts share a persisted five-minute budget once execution begins. Permanent setup/authentication failures stop retries. Android scheduling may defer execution; this is not a permanent watchdog. Force-stop, uninstall or removed debugging access prevent the normal automatic path.

USB debugging must remain enabled. Tsuyu never re-enables it. **Expiration des autorisations** offers a separate optional global change: disabling expiry for **all authorized ADB keys**, not just Tsuyu. Initial focus keeps the existing policy. Skip, Back, status viewing, boot and normal enablement never write that policy. Explicit disabling uses a fixed shell setting write and read-back, with no manager `WRITE_SECURE_SETTINGS` grant. A timeout is unverified until a fresh read reconciles it. Later system changes are never overwritten automatically. Keeping expiry can require renewed confirmation after a long powered-off interval.

See [P3 verification](docs/verification-p3.md) and the [single TV procedure](docs/tv-validation-p2-p3.md). JVM/emulator proof does not prove vendor boot delivery.

## Credits

Tsuyu is a clean rewrite for TV. Its design and parts of its code come from:

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) (Apache-2.0) and [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API) (MIT), by RikkaW;
- [HmnDev-Tech/shevery](https://github.com/HmnDev-Tech/shevery) (Apache-2.0), a Shizuku fork that served as the starting point.

Details are in [NOTICE](NOTICE).

> Independent project, not affiliated with Shizuku, Google or TCL. Android TV and Google TV are trademarks of Google LLC.

## License

Tsuyu is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE). Files derived from Shizuku or Shevery keep their Apache-2.0 license, and code derived from Shizuku-API keeps its MIT license, as stated in each file header and in [NOTICE](NOTICE).
