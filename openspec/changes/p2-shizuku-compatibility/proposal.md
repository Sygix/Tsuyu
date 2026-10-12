# Proposal

## Why

A local shell server alone cannot serve existing client applications. Tsuyu must implement the upstream Shizuku contract and request access through a remote-friendly confirmation screen.

## What Changes

- Implement the complete upstream `IShizukuService`, companion interfaces and remote Binder transaction without changing clients.
- Report upstream server API 13 and patch 6, distinct from the client library's 13.1.5 release number.
- Deliver the binder to requesting clients at startup and subsequent process starts.
- Ask for access over the requesting app with its name/icon and “Autoriser” / “Refuser”, initially focusing refusal.
- Persist server-managed access decisions, show authorized apps and support effective revocation.
- Support remote processes and the full UserService binding, peeking, unbinding and removal lifecycle.

## Capabilities

### New Capabilities

- `shizuku-api`: upstream wire contract, binder delivery, authorized shell operations and UserService lifecycle.
- `client-access`: server-managed consent, request presentation and authorized-app revocation.

### Modified Capabilities

None. Reuse P1's `local-start` and `tv-navigation` unchanged.

## Dependencies

Depends on `p1-local-start`. Base the P2 implementation PR on the P1 branch. `p3-boot-persistence` follows this change. Merge and archive order is P1 → P2 → P3.

## Non-goals

- Self-granted reboot access and boot restart: P3.
- Release signing/distribution: P4.
- Upstream manager package impersonation, Android declarations of `moe.shizuku.manager.permission.*`, root/Sui, rish and extra fork layers: outside v1.
- TVs without local legacy TCP support: outside v1, as established in P1.

## Impact

Add MIT upstream AIDL/constants/parcelable contracts, a narrow public service alongside P1's manager IPC, persistent consent storage and request/list screens. Adapt provider delivery and process observation from Shizuku only as required. Preserve shell rather than root execution rights. Demo and SygixOs compatibility become acceptance evidence; real-TV validation is Sygix's responsibility.

## Open questions

- The upstream AIDL includes Sui-only `dispatchPackageChanged` and `isHidden` despite Sui being excluded. Conservative default, **to confirm**: retain upstream non-Sui behavior (authenticated no-op and `false`), not a hidden-app feature. Their bodies are already empty/false in upstream Shizuku; all other AIDL functionality remains required.
- Shared Android UIDs cannot be separated by Binder identity. Conservative default, **to confirm**: make the UID-wide access effect explicit when listing related apps; never suggest that revoking only one package isolates another app with the same uid.

These points must also appear in the implementation PR. No technical impossibility of UserService support has been established by reference inspection.
