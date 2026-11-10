# Backlog: undecided topics

None of these topics is specified or decided. Each one will become an OpenSpec change (`openspec/changes/<id>/`) or be attached to an existing change when it is addressed. The leads below are working assumptions, not decisions.

## 1. TVs whose adbd does not listen on TCP

**Observation:** on the reference TV, the firmware sets adbd's TCP port at every boot. A TV that does not do this only offers wireless debugging over TLS, and pairing it from the TV itself is impossible.

**Leads:**
- a one-time pairing from a computer or a phone, with a key Tsuyu can use afterwards;
- an accessibility service that reads the code and the port on the pairing screen, without taking focus.

**To decide:** whether these TVs are supported, in which phase, and which lead is chosen.

## 2. Enabling USB debugging

**Observation:** USB debugging survives a reboot, unlike wireless debugging. While it is on, adbd's TCP port stays open on the local network; only authorized keys get in. The setup steps and the README/SECURITY explanation of this exposure are decided.

**To decide:** whether Tsuyu offers to close the port once the server is running.

## 3. API version reported to clients

**Observation:** Shizuku-API is at version 13.1.5; Shevery reports 14.3.

**To decide:** the version Tsuyu reports, and which AIDL methods it really implements (UserService, remote process, etc.), after checking what SygixOs and the demo app use.

## 4. Stopped label while USB debugging is disabled

**Observation:** during P1 release validation, Sygix reported that home might show “Service arrêté” while debugging was disabled. The exact screen text and settings read were not captured. This is unconfirmed and was explicitly accepted as non-blocking for P1 closure.

**Expected current behavior:** Binder death first invalidates Running to Stopped; a subsequent refresh reading debugging disabled selects Setup and offers debugging guidance.

**Follow-up:** if reproduced, record the exact home text/action after returning from settings and the contemporaneous `adb_enabled` value, then compare it with the lifecycle refresh. Do not revoke authorizations or uninstall merely to investigate this observation.

## 5. ADB key in the Android Keystore

**Observation:** the adb private key is a PKCS#8 file in app-private, no-backup storage. A key held by the Android Keystore could not be extracted even by code running as the app.

**To decide:** whether to import the existing key into the Keystore (with file fallback where TV firmware lacks suitable RSA support) or use it for new installations only, and how to keep the TV's « Always allow » approval across the migration.

## 6. Amazon Appstore and Fire TV

**Status:** postponed by Sygix and removed from `multi-store-distribution`. Nothing about this channel is specified.

**Observation (public sources and reports, checked in November 2026):**
- Amazon publishes no Fire OS emulator image; only generic Android TV images exist, so Fire OS behavior cannot be tested in the development environment, and no Fire TV device is available to the project.
- An on-device block of local ADB connections was reported as shipped by over-the-air update in February 2024 to Fire OS 7 and Fire OS 8, which together cover every Android-based Fire TV meeting the API 28 minimum. Fire OS 6 is reported as affected too and is below that minimum (API 25).
- Newer Fire TV devices run Vega OS, which is not Android.
- Amazon documents that it removes the submitted signature, re-signs the APK and adds a wrapper, even with DRM disabled. An Amazon-delivered build would therefore not be a compatible-signature upgrade of the other channels: switching would mean reinstalling and reconfiguring.

**What would reopen it:** a tester with an unpatched Android-based Fire TV at API 28 or later, and a reviewed feasibility result from that device covering local legacy ADB, client operations and restart after reboot.

**To decide then:** whether to publish for the remaining devices, the console DRM setting, and whether the Amazon build keeps the existing package identity (reinstall when signatures differ) or needs its own.

## 7. "In French" wording left in current specs

**Observation:** the app ships five languages, but some current requirements still describe the interface as French. `android9-compatibility` renames the `tv-navigation` presentation requirement to "Couch-readable localized presentation" and states once, in "Quoted labels identify string resources", that labels quoted in French identify string resources rather than the displayed language. These occurrences remain as known spec debt:
- `local-start`: the debugging guidance requirement ("explain in French") and two of its scenarios ("manual French instructions", "a distinct French recovery instruction");
- `boot-persistence`: the home status requirement ("using French resources") and one scenario ("understandable French");
- `client-access`: the request-screen requirement ("explain the effect of access in French").

**Follow-up:** reword them to "in the selected language" in a later change, after `consent-and-persistence-controls` is archived, since it has open deltas on `client-access` and `boot-persistence`.
