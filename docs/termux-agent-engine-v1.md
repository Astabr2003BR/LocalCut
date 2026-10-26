# LocalCut — Termux Agent Engine V1

## Architecture decision

LocalCut remains the Android editor, UI, timeline, preview, project manager and user-facing task controller. Termux is an optional worker. V1 deliberately uses an authenticated TCP socket bound to `127.0.0.1` instead of an exported Android component or a generic Termux command permission. This keeps the first integration isolated and avoids opening an arbitrary shell surface.

## Trust boundary

- Server binds only to `127.0.0.1` on port `43127` by default.
- LocalCut refuses any host other than the literal `127.0.0.1`.
- Every request contains a random bearer-style token validated with constant-time comparison.
- LocalCut stores the token in a dedicated Android Keystore-backed store separate from Bridge credentials.
- Maximum request/response sizes are bounded.
- Protocol and `projectId=localcut` are validated on both sides.
- No generic shell, `/exec`, Python eval, model-output execution, file deletion, Git mutation or build operation exists in V1.

## V1 request contract

One UTF-8 JSON object per connection, terminated by `\n`:

```json
{"protocolVersion":1,"projectId":"localcut","requestId":"...","token":"...","operation":"PING"}
```

Allowed operations:

- `PING`
- `GET_STATUS`
- `GET_CAPABILITIES`

All other operation names fail with `OPERATION_NOT_ALLOWED`.

## Fallback

The Termux runtime is separate from `LocalCutProcessRuntime` and is not started or called during normal editor startup. Missing Termux, missing pairing token, connection refusal, timeout or invalid response therefore returns a failure only to the optional engine caller; it does not disable import, preview, timeline, trim, split, delete, undo/redo or export.

## Incremental roadmap

1. V1 authenticated read-only contract and detection.
2. Explicit pairing/bootstrap UX and physical Android ↔ Termux test.
3. Persistent typed job model; first job `ANALYZE_MEDIA`.
4. `ffprobe`/FFmpeg capability discovery and measured fallback.
5. Processing jobs with progress, cancellation and idempotency.
6. Background recovery/checkpointing.
7. Isolated development agent restricted to `Astabr2003BR/LocalCut`.
8. Approval gates + Android notifications for critical operations.

Critical operations such as merge to main, force push, destructive data changes, signing/keystore changes, secrets changes and production publication remain blocked until explicit user approval.
