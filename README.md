# Tsuyu

Tsuyu (露, "dew") gives Android TV / Google TV apps the privileged access they need, through the [Shizuku API](https://github.com/RikkaApps/Shizuku-API), and is driven entirely with the remote. It starts its service with ADB (shell) rights on the TV itself, with no computer and no phone, and starts it again after each reboot.

> Work in progress: no release yet.

## Why

Shizuku and its forks were designed for phones. On a TV, their way to start without a computer breaks:

- the wireless debugging pairing code dies as soon as the system pairing screen loses focus;
- there is no split screen and no notification you can reply to.

Tsuyu starts through the TV's own ADB daemon instead, with a one-time "Always allow" confirmation on the TV screen.

## Shizuku API compatibility

Apps built with Shizuku-API work with Tsuyu without any change. Tsuyu delivers its service to every app that requests the Shizuku API permission, and asks you, on the TV, before an app gets access.

Tsuyu is not Shizuku and uses its own package name. An app that only checks whether the Shizuku app (`moe.shizuku.privileged.api`) is installed will still report it as missing.

## Credits

Tsuyu is a clean rewrite for TV. Its design and parts of its code come from:

- [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) (Apache-2.0) and [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API) (MIT), by RikkaW;
- [HmnDev-Tech/shevery](https://github.com/HmnDev-Tech/shevery) (Apache-2.0), a Shizuku fork whose TV work inspired this project.

Details are in [NOTICE](NOTICE).

> Independent project, not affiliated with Shizuku, Google or TCL. Android TV and Google TV are trademarks of Google LLC.

## License

Tsuyu is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE). Files derived from Shizuku or Shevery keep their Apache-2.0 license, and code derived from Shizuku-API keeps its MIT license, as stated in each file header and in [NOTICE](NOTICE).
