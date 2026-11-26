# Design

## Context

See proposal.md - Why. Consent decisions are stored per identity group by the shell server; the manager only relays the user's answer. The persistence store holds `enabled`, the restart ledger and the last authorization-refresh outcome.

## Goals / Non-Goals

**Goals:** make the recorded decision the single source of whether a prompt may open; make the authorization status reflect only real adb authentications.

**Non-Goals:** see proposal.md.

## Decisions

- **Refusal enforcement in the server.** The pending-request queue checks the recorded decision under the same per-uid lock that creates requests: allowed answers granted, refused answers denied, otherwise a request is created. The client receives its result through the unchanged `dispatchRequestPermissionResult` callback, so Shizuku-API clients observe `PERMISSION_DENIED` and `shouldShowRequestPermissionRationale() == true`.
- **Only explicit answers are recorded.** Completion carries whether the answer came from the user. Timeout, expiry on lookup, screen-open failure and cancellation complete with denial without writing a decision.
- **Authorization status source.** A successful adb launch, a successful periodic refresh or a successful renewal write `CURRENT`; manual recovery and restart completion no longer do, because both can succeed without authenticating when a server is already alive.
- **Renewal** reuses the local adb client with the interactive authorization wait used at first start; it neither launches nor stops the server. Authentication refusal or timeout records "authorization required"; any other start failure records "failed".
- **Disabling** clears `enabled` and the authorization status and cancels the unique key-refresh work. Restart reservation already requires `enabled`, so queued boot or update restarts become no-ops.

## Risks / Trade-offs

- A client refused before this change keeps its refusal and is now answered without a prompt; the reset action in « Accès des applications » is the documented way back.
- Disabling does not stop a running service; the next manual start shows the persistence disclosure again.
