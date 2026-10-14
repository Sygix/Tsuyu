# Single hardware validation session

Executor: Claude with access to the reference TCL Google TV running Android 14. Keep this session limited to vendor/hardware behavior. JVM/emulator scenarios are not duplicated here. Do not mark a TV task complete without the actual output and D-pad observations.

## APK prerequisite

- Install **the combined P2/P3 production-signed CI release** as `tsuyu.apk`, plus the pinned upstream Shizuku-API demo as `demo.apk`. Never replace the existing production signature with a development signature.
- This session creates no tag. The production APK becomes available only after the owner authorizes a release tag on the selected reviewed commit and the existing release workflow completes. Download its `tsuyu.apk` asset with `gh release download <approved-tag> --pattern tsuyu.apk`.
- The demo is external test material from Shizuku-API 13.1.5, snapshot `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5`. Use `demo-shizuku-api13.1.5-debug.apk`, SHA-256 `2ba45bedb6e7d2d37ac3e75680047423219fd60458fc9d2e04aaefd11ab463ea`.

## Commands and checks

Use a locally selected device with ADB; do not publish its address, serial or logs containing personal data. The demo package is `rikka.shizuku.demo`.

```sh
adb install -r tsuyu.apk
adb install -r demo.apk
```

1. Open Tsuyu, read the persistence disclosure and enable it. Accept **Always allow** if needed. Read both modes without manually granting them:

```sh
adb shell appops get --uid fr.sygix.tsuyu AUTO_START
adb shell appops get fr.sygix.tsuyu AUTO_START
```

Expected: UID and package modes are independently `allow`; Tsuyu separately shows active service and prepared reboot access. Save only sanitized mode output.

2. In the upstream demo, request permission. Expected: Tsuyu identifies the demo; **Refuser** initially focused; D-pad selects **Autoriser**; completion returns to the demo. Execute its privileged Binder operation and retain only a sanitized success assertion, without publishing profile names. Revoke in Tsuyu and verify denial. Then request and explicitly grant again from the unchanged demo, leaving that grant for the reboot retention check. Revocation clears consent; it is distinct from choosing “Refuser”, whose rationale the demo intentionally treats as a recorded refusal.

3. Leave Tsuyu's process absent without force-stopping it, then perform **one cold reboot**. Do not open Tsuyu after restart. Do not use force-stop, which disables normal Android receiver delivery.

```sh
adb shell am kill fr.sygix.tsuyu
adb shell pidof fr.sygix.tsuyu
```

Expected before reboot: no manager process. Power-cycle the TV once. Reconnect ADB after the daemon returns; allow up to the finite five-minute execution budget under boot load. Observe the server and open only the demo:

```sh
adb shell ps -A -o USER,PID,NAME
adb shell monkey -p rikka.shizuku.demo 1
```

Expected: one Tsuyu shell server restarted without launching its UI; the demo receives a fresh binder, retains explicit consent and succeeds with shell identity without another consent prompt. Record only relevant sanitized process rows and demo results.

Report APK tag/checksums, both modes, D-pad/caller return, Binder success/revocation, absent-manager evidence and the one cold-boot result. Expiry policy, endpoint delay/exhaustion, unsupported operations and manual/boot overlap belong to JVM/emulator evidence only.
