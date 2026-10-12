# Proposal

## Why

After P1 and P2, Tsuyu works only after manual startup. The first successful start must prepare the supported TV to restart the same shell server at subsequent boots and expose any missing persistence prerequisite clearly.

## What Changes

- Explain persistence access by its effect before the first setup mutation and show the resulting access afterwards.
- Use the shell server to grant/check the supported vendor auto-start operation needed for boot delivery.
- Schedule one bounded, idempotent restart after boot using P1's private key and local startup coordinator.
- Distinguish running service from persistence readiness and report denied/missing grants, expired authorization and restart failures with remote-accessible recovery.
- Reestablish P2 binder delivery and preserved client decisions after restart.
- Offer optional global key-expiry disabling after an explicit explanation and choice; preserve the current policy if the user does not choose it.
- Respect later manual USB-debugging disabling and offer setup recovery rather than re-enable it automatically.

## Capabilities

### New Capabilities

- `boot-persistence`: consented persistence preparation, automatic boot restart and readiness/recovery presentation.

### Modified Capabilities

None. Reuse `local-start`, `tv-navigation`, `shizuku-api` and `client-access`. P1 already delegates automatic reboot startup to this capability.

## Dependencies

Depends on `p1-local-start` and `p2-shizuku-compatibility`; base the P3 implementation PR on P2. Merge and archive order is P1 → P2 → P3.

## Non-goals

- Distribution and tagged release: P4.
- TLS pairing, unsupported TCP-less TVs, root, accessibility workaround and fork watchdog layers: outside v1.
- Guaranteeing reboot delivery after the user force-stops/uninstalls Tsuyu or removes required system access: outside the normal enabled-service contract; report recovery instead.
- Validation on the reference TV by the agent: Sygix owns that sign-off.

## Impact

Add a boot receiver, bounded unique background work and manager-only persistence preparation/status operations. Add only the permissions and storage required by approved persistence actions. The reference TV's vendor AUTO_START check is unavailable on a standard emulator, so emulator restart evidence does not establish vendor readiness.

## Confirmed decisions

- Disabling key expiry is optional, never automatic or preselected. Explain that `adb_allowed_connection_time=0` affects every authorized ADB key, then apply it only on explicit user choice and verify the result. Declining leaves the current policy untouched and does not block enablement; with expiry retained, a long powered-off period can require renewed key confirmation.
- Respect later user-disabled USB debugging. Do not automatically re-enable it; explain why automatic startup is unavailable and offer setup recovery. No wireless debugging or close-port option is introduced.

## Open questions

None. The implementation PR must document the optional setting's global effect and the remaining validation assumptions.
