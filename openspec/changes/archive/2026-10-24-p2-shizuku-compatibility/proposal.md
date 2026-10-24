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
- Preserve upstream non-Sui behavior for the Sui-reserved AIDL endpoints and explain UID-wide consent/revocation for applications sharing an Android UID.

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

## Confirmed decisions

- Retain Shizuku's non-Sui behavior: `dispatchPackageChanged` is an authenticated no-op and `isHidden` returns `false`. No Sui feature is introduced.
- Applications sharing an Android UID receive grouped access. Display the related applications and explain that authorization and revocation affect the whole group, because Binder identity cannot isolate packages within a UID.

No technical impossibility of UserService support has been established by reference inspection.

Same-signature Tsuyu updates preserve client consent; the old server exits under `local-start` and clients reattach after restart. The upstream demo is required compatibility evidence. SygixOs is optional evidence when a Shizuku-API client build is available, never an implementation blocker.

## Open questions

None. Device validation obligations remain in design.md and tasks.md.
