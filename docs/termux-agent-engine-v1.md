# LocalCut — Termux Agent Engine V1

## Architecture decision

LocalCut remains the Android editor, UI, timeline, preview, project manager and user-facing task controller. Termux is an optional worker. V1 uses a typed TCP protocol bound to `127.0.0.1` instead of an exported generic command surface or the Termux `RUN_COMMAND` permission. This keeps the integration isolated and avoids granting LocalCut arbitrary shell authority.

## Trust boundary

- Server binds only to `127.0.0.1` on port `43127` by default.
- LocalCut refuses any host other than the literal `127.0.0.1`.
- Authenticated requests use a high-entropy token validated with constant-time comparison.
- LocalCut stores the token in a dedicated Android Keystore-backed store separate from Bridge credentials.
- Termux persists its token in a private credential file with `0600` permissions under a `0700` directory.
- Maximum request/response sizes are bounded.
- Protocol and `projectId=localcut` are validated on both sides.
- No generic shell, `/exec`, Python eval, model-output execution, file deletion, Git mutation or build operation exists in V1.

## Pairing/bootstrap

The long-lived token is not typed or copied by the user in the normal flow.

Termux starts an explicit pairing window with:

```bash
python localcut_engine.py --pair
```

The engine prints only a 12-character one-time pairing code. The code:

- uses a human-safe 32-character alphabet;
- provides 60 bits of search space;
- expires after 5 minutes by default;
- locks after 5 invalid attempts by default;
- is invalidated immediately after the first successful claim;
- is accepted only through the loopback protocol.

The LocalCut client sends a typed `PAIR` request containing no stored token. A successful response returns the engine token once; LocalCut validates it, stores it in Android Keystore, then immediately performs an authenticated `PING`. The engine token is not printed by the normal CLI flow.

## Authenticated request contract

One UTF-8 JSON object per connection, terminated by `\n`:

```json
{"protocolVersion":1,"projectId":"localcut","requestId":"...","token":"...","operation":"PING"}
```

Authenticated V1 operations remain limited to:

- `PING`
- `GET_STATUS`
- `GET_CAPABILITIES`

All other authenticated operation names fail with `OPERATION_NOT_ALLOWED`.

## Fallback

The Termux runtime remains separate from `LocalCutProcessRuntime` and is not started or called during normal editor startup. Missing Termux, missing pairing token, connection refusal, timeout or invalid response therefore returns a failure only to the optional engine caller; it does not disable import, preview, timeline, trim, split, delete, undo/redo or export.

## Incremental roadmap

1. Authenticated read-only contract and detection. **Implemented.**
2. Secure pairing/bootstrap protocol and client API. **Implemented; user-facing pairing UI and physical Android ↔ Termux test remain.**
3. Persistent typed job model; first job `ANALYZE_MEDIA`.
4. `ffprobe`/FFmpeg capability discovery and measured fallback.
5. Processing jobs with progress, cancellation and idempotency.
6. Background recovery/checkpointing.
7. Isolated development agent restricted to `Astabr2003BR/LocalCut`.
8. Approval gates + Android notifications for critical operations.

Critical operations such as merge to main, force push, destructive data changes, signing/keystore changes, secrets changes and production publication remain blocked until explicit user approval.
