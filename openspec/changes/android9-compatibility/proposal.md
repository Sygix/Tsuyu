# Proposal

## Why

Tsuyu currently excludes TVs below Android 14 despite its Shizuku-compatible service having a viable Android 9 baseline. Supporting Android 9 and later expands installation eligibility while keeping device-dependent debugging and boot support explicit rather than promising compatibility from an OS version alone.

## What Changes

- Lower the minimum Android API to 28 across the app and its packaged modules, preserving the current compile/target SDK levels.
- Adapt locale selection, provider calls, parcelable reads and server platform boundaries for the supported Android versions without weakening client authentication or consent.
- Keep the named return-to-caller action on Android 14 and later; use normal Back navigation on Android 9–13 without inferring caller identity from extras or referrers.
- Preserve all five UI languages, the system-language default, existing private ADB keys and client consent.
- Validate the baseline on an Android 9 emulator and regress the existing TCL Google TV running Android 14; distinguish these results from unverified physical-device compatibility.

## Capabilities

### New Capabilities

- `android-compatibility`: supported Android baseline, version-specific platform boundaries and truthful compatibility outcomes.

### Modified Capabilities

- `tv-navigation`: define language selection across Android versions and restrict the named caller-return action to platforms providing authenticated launch identity.

The unchanged `local-start`, `shizuku-api`, `client-access`, `boot-persistence` and `self-update` contracts remain acceptance criteria on the new baseline; their implementation changes do not introduce parallel behavioral requirements.

## Impact

All module Gradle minimums; `MainActivity`; language gateway/activity lifecycle; server provider bridge, UserServices and hidden API boundaries; targeted tests and compatibility documentation. No public Shizuku protocol change or mandatory new runtime dependency is planned.

## Dependencies and order

This change builds on the current archived capabilities and has no open-change prerequisite. Implement and archive it before `multi-store-distribution`. That change owns distribution variants and public community evidence reporting, and references this baseline without rewriting it.

## Non-goals

Store packaging, signing, publication and the community reporting workflow belong to `multi-store-distribution`. Additional startup mechanisms for devices lacking a usable local legacy ADB endpoint remain a separate future compatibility change. Physical-device support claims require device evidence and are not inferred from emulator success.

## Open questions

No unresolved product choice blocks this change. The availability and exact behavior of older system/firmware facilities are verification gates in the design; a failure that prevents API 28 support must return for a scope decision rather than silently raising the minimum.
