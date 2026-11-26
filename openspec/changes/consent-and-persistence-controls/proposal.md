# Proposal

## Why

An independent review of the released code found that a refusal never stopped a client from reopening the consent screen, that an unanswered or unopenable request was recorded as a user refusal, and that the persistence screen offered no way to undo automatic startup or to renew an expired debugging authorization while the service was running. The repair action also cleared the authorization warning without authenticating, so the next boot restart failed unattended.

## What Changes

- A recorded refusal answers later permission requests of the same identity group with denial and no confirmation screen, until the user resets it in « Accès des applications ». Only an explicit answer (« Refuser » or Back on the request) is recorded; an expired, abandoned or unopenable request ends in denial without recording a refusal.
- The persistence screen gains « Désactiver le démarrage automatique »: automatic boot restart and the periodic authorization refresh stop, the screen returns to its disclosure state and the running service is not stopped.
- The persistence screen gains « Renouveler l'autorisation » while the authorization refresh reports a required authorization or a failure: Tsuyu opens an authenticated adb connection that shows the system prompt without restarting the server, and only a real authentication clears the warning.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client-access`: "Authenticated server-managed consent" records only explicit refusals and answers recorded refusals without a prompt.
- `boot-persistence`: adds disabling automatic startup and renewing the debugging authorization from the persistence screen.

## Impact

- Server consent handling (`ConsentRequests`), persistence store/controller, persistence screen presentation and strings in all five languages, the key-refresh work scheduling.
- Depends on the archived P2/P3 changes; independent of the open `android9-compatibility` and `multi-store-distribution` changes, which modify other requirements. Archive order: this change first.

## Non-goals

- Reverting the vendor auto-start grants when automatic startup is disabled: they stay as prepared and are harmless without scheduled work.
- Moving the adb private key into the Android Keystore (backlog).
- Stopping the service from the persistence screen.
