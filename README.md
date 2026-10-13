# Tsuyu

Tsuyu (露, "dew") is a remote-driven privileged access service for Android TV / Google TV. It starts a service with ADB (shell) rights on the TV itself, with no computer and no phone. The complete v1 will expose the [Shizuku API](https://github.com/RikkaApps/Shizuku-API) and restart after each reboot; the phase status below describes what currently works.

> Work in progress: no release yet.

## Why

Shizuku and its forks were designed for phones. On a TV, their way to start without a computer breaks:

- the wireless debugging pairing code dies as soon as the system pairing screen loses focus;
- there is no split screen and no notification you can reply to.

Tsuyu starts through the TV's own ADB daemon instead, with a one-time "Always allow" confirmation on the TV screen.

## Local startup (P1)

The current implementation starts a local shell-uid server. Client API support and automatic restart are planned in P2 and P3 respectively.

1. Open Tsuyu with the TV remote and enable developer options and USB debugging when requested.
2. Choose **Démarrer**. On the first system prompt, select **Always allow**, then **Allow**.
3. **Service actif** appears only after the manager binder reports shell uid and lifecycle observation is ready. **Détails** shows technical status; **Aide** explains recovery.

Debugging leaves a port open on the local network, accessible only to authorized devices. Tsuyu uses a private, non-backed-up RSA key and supports legacy local TCP debugging; TVs offering only TLS wireless pairing are outside v1.

The launch shell waits for confirmed server readiness before returning. The server runs in a detached session and survives closure of the launch connection. Keep USB debugging enabled during use: on the reference TV, disabling it kills the daemon's entire cgroup, including the detached server. Re-enable debugging and start Tsuyu again using its retained key and authorization; do not revoke ADB authorizations or reinstall to recover a launch failure. The server also exits on manager replacement/removal or when required lifecycle observation cannot be restored. Same-signature updates preserve its private key; P1 requires manual restart after an update or TV reboot. Tsuyu never re-enables debugging automatically.

Tests use `./gradlew test` and `./gradlew assembleRelease`. The Android TV emulator simulates the reference TV's legacy endpoint after each boot; vendor behavior and couch-distance UI rendering still require validation on the real TV.

## Planned Shizuku API compatibility

P2 will let apps built with Shizuku-API work without changes. Tsuyu will deliver its service to every app that requests the Shizuku API permission, and ask you, on the TV, before an app gets access. The upstream demo is required validation; SygixOs is optional evidence when an API-client build is available.

Tsuyu is not Shizuku and uses its own package name. An app that only checks whether the Shizuku app (`moe.shizuku.privileged.api`) is installed will still report it as missing.

## Credits

Tsuyu is a clean rewrite for TV. Its design and parts of its code come from:

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) (Apache-2.0) and [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API) (MIT), by RikkaW;
- [HmnDev-Tech/shevery](https://github.com/HmnDev-Tech/shevery) (Apache-2.0), a Shizuku fork that served as the starting point.

Details are in [NOTICE](NOTICE).

> Independent project, not affiliated with Shizuku, Google or TCL. Android TV and Google TV are trademarks of Google LLC.

## License

Tsuyu is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE). Files derived from Shizuku or Shevery keep their Apache-2.0 license, and code derived from Shizuku-API keeps its MIT license, as stated in each file header and in [NOTICE](NOTICE).
