# LocalCut Bridge V1 — Technical Protocol Specification

Date: 2026-10-19
Status: Approved architecture baseline
Architecture: C — bidirectional relay
Parent design: `docs/superpowers/specs/2026-10-19-localcut-studio-bridge-design.md`

## 1. Goal

LocalCut Bridge V1 exposes a small, deterministic and auditable control surface so ChatGPT/LocalCut Controller can inspect and operate the Android editor without embedding an OpenAI key in the APK and without weakening offline editing.

Target topology:

`ChatGPT / LocalCut Controller <-> HTTPS/WSS Relay <-> LocalCut Android Agent <-> Editor Command Bus <-> Project Session / Renderer`

The phone opens the connection outward. V1 never opens an inbound listening port on Android.

## 2. Current repository baseline

The bridge must preserve the current V0 behavior and overrides. At the time of this specification, the working Android editor already has media import, audio import, preview, timeline selection, trim, split, move, delete, undo/redo, autosave/final preview and MP4 export. Timeline trim/move is clamped against adjacent clips to avoid the `OVERLAPPING_CLIPS` failure fixed in Build 12.

The repository currently rebuilds from `localcut-source-full-build9.zip` and overlays files from `overrides/`. New bridge code must therefore be additive and must not bypass the current editor session/model contracts.

## 3. Design principles

1. Editor core is authoritative. The relay/controller never edits project files directly.
2. Every write is a typed editor command executed through one command bus.
3. Source media is immutable from bridge tools.
4. Every write is revision-checked, idempotent and undoable when the operation is reversible.
5. Read tools may observe state but never mutate it.
6. Bridge is opt-in and can be disabled without affecting local editing.
7. No arbitrary shell, arbitrary filesystem access, generic Android intents or raw SQL are exposed.
8. Long-running work returns a job id and progress events instead of blocking one request.

## 4. Components

### 4.1 Android Bridge Agent

Responsibilities:

- pairing and device identity
- outbound WSS session
- command validation
- capability allowlist
- project revision checks
- dispatch to Editor Command Bus
- event/progress publication
- sanitized diagnostics
- preview frame capture
- reconnect/backoff

Suggested package boundaries once implementation starts:

- `com.localcut.editor.bridge.protocol`
- `com.localcut.editor.bridge.security`
- `com.localcut.editor.bridge.transport`
- `com.localcut.editor.bridge.agent`
- `com.localcut.editor.bridge.diagnostics`
- `com.localcut.editor.commands`

### 4.2 Relay

Responsibilities:

- authenticated controller endpoint
- device registry and pairing state
- WSS routing to the correct paired device
- short-lived session issuance
- replay/idempotency metadata
- rate limiting
- audit metadata without source-media content

The relay does not become a project database in V1. Project state remains on the Android device.

### 4.3 LocalCut Controller

A private ChatGPT plugin/MCP-style controller maps tool calls to the relay API. It receives only the LocalCut tool surface and never receives Android Keystore material.

## 5. Device pairing and authentication

### 5.1 Device identity

On first enabling Bridge:

1. Generate an ECDSA P-256 key pair using Android Keystore.
2. Use alias `localcut.bridge.device.v1`.
3. Keep the private key non-exportable.
4. Send only the public key and device metadata required for pairing.
5. Relay returns a short pairing code/QR payload with a 10-minute TTL.
6. Controller account claims the pairing code.
7. Relay sends a random challenge.
8. Android signs the challenge with the Keystore private key.
9. Relay verifies the signature and binds the device id to the controller account.

P-256 is selected for broad Android Keystore compatibility. No OpenAI secret is stored on-device.

### 5.2 Session tokens

After pairing, the relay issues short-lived access/session credentials. If a longer-lived device refresh credential is required, it must be encrypted at rest with an AES-GCM key generated/stored by Android Keystore. Raw long-lived credentials must not be written to logs, SharedPreferences in plaintext, crash reports or project files.

### 5.3 Connection states

Canonical states:

- `OFFLINE`
- `DISABLED`
- `PAIRING`
- `CONNECTING`
- `ONLINE`
- `BUSY`
- `EXPORTING`
- `ERROR`

Android remains fully usable in `DISABLED` and `OFFLINE`.

## 6. Transport

### 6.1 HTTPS

Use HTTPS for:

- pairing start/claim/status
- controller-to-relay tool requests
- device/session bootstrap

### 6.2 WSS

Use one authenticated outbound WSS connection from Android for:

- command delivery
- command results
- project-state events
- export progress
- diagnostics events

Reconnect policy: exponential backoff with jitter, capped at 60 seconds. Network loss must never block the editor UI thread.

V1 does not queue destructive/write commands indefinitely while the device is offline. Expired commands are rejected instead of running unexpectedly hours later.

## 7. Protocol envelope

All protocol messages use an explicit schema version.

```json
{
  "protocolVersion": 1,
  "messageId": "uuid",
  "kind": "command",
  "deviceId": "opaque-device-id",
  "projectId": "project-id",
  "expectedRevision": 42,
  "issuedAt": "2026-10-19T21:00:00Z",
  "expiresAt": "2026-10-19T21:00:30Z",
  "nonce": "opaque-random-value",
  "capability": "split_clip",
  "payload": {}
}
```

Rules:

- `messageId` is the idempotency key.
- `expectedRevision` is mandatory for writes to project state.
- `expiresAt` is mandatory for writes.
- `nonce` plus message id prevents replay.
- Unknown protocol versions or capabilities are rejected.
- Repeating an already completed `messageId` returns the original result and does not re-run the mutation.

## 8. Project revision and transactions

Project revision is a monotonically increasing integer stored with project session state.

For each write command:

1. Verify `expectedRevision` equals the current revision.
2. Validate command and referenced ids.
3. Create an atomic edit transaction.
4. Apply mutation through the editor domain/session layer.
5. Run project/timeline validation.
6. Commit and increment revision if valid.
7. Roll back if validation fails.
8. Persist/autosave using existing project storage rules.

Revision mismatch returns `REVISION_CONFLICT` with current revision and no mutation.

For autonomous multi-command edits, one `EditScript` transaction may contain many commands but should create one user-visible checkpoint so the whole automated edit can be undone in one action.

## 9. Bridge V1 tool surface

### 9.1 Required read tools

- `get_device_status`
- `get_project_state`
- `list_clips`
- `get_export_status`
- `get_preview_frame`
- `get_errors`

Recommended read tools that reduce oversized state responses:

- `get_timeline_summary`
- `list_tracks`
- `get_selected_clip`
- `get_playhead`

### 9.2 Required write tools

- `seek`
- `split_clip`
- `trim_clip`
- `move_clip`
- `delete_clip`
- `import_media`
- `export_project`
- `undo`
- `redo`

Near-term additions already anticipated by the editor roadmap:

- `select_clip`
- `duplicate_clip`
- `set_transform`
- `set_volume`
- `set_speed`
- `add_text`
- `add_transition`
- `add_effect`
- `set_effect_param`
- `add_keyframe`
- `apply_edit_script`
- `cancel_export`

## 10. Tool contracts

### 10.1 `get_project_state`

Returns a bounded summary rather than complete binary/media data.

```json
{
  "projectId": "default",
  "revision": 42,
  "name": "Meu projeto",
  "width": 1080,
  "height": 1920,
  "fps": 30,
  "durationUs": 32000000,
  "trackCount": 3,
  "clipCount": 18,
  "selectedClipId": "clip-7",
  "playheadUs": 12500000,
  "dirty": false,
  "issues": []
}
```

### 10.2 `list_clips`

Input supports optional `trackId`, paging and a maximum result count. It returns stable ids, media type, timeline time, source trim information and user-editable properties. It must not return arbitrary filesystem paths.

### 10.3 `seek`

Input: `positionUs`.

Validation: `0 <= positionUs <= projectDurationUs`.

Seeking does not increment project revision because it changes editor/view state, not project content.

### 10.4 `split_clip`

Input: `clipId`, `positionUs`, `expectedRevision`.

Validation:

- clip exists
- split point is strictly inside clip bounds
- resulting clips satisfy project invariants

### 10.5 `trim_clip`

Input: `clipId`, either `newStartUs` or `newEndUs`, `expectedRevision`.

For exclusive/sequential tracks, V1 retains the Build 12 behavior: trim is clamped/rejected before it can overlap a neighboring clip.

### 10.6 `move_clip`

Input: `clipId`, `newStartUs`, `expectedRevision`.

Collision behavior is determined by the track policy. V1 legacy exclusive tracks preserve overlap-safe clamping. Future overlay tracks may intentionally overlap.

### 10.7 `delete_clip`

Deletes only the project clip reference. It never deletes the source media file. The mutation must be undoable.

### 10.8 `import_media`

Remote tools may import only a media reference already authorized by LocalCut, such as a persisted SAF URI/MediaStore item selected by the user, or a future LocalCut-managed inbox object. V1 must not accept arbitrary `file://` paths from the controller.

### 10.9 `get_preview_frame`

Input: optional `positionUs`, `maxLongEdgePx` up to 1280.

Output metadata includes project revision, frame time, width, height and MIME type. Default payload is JPEG/WebP quality suitable for inspection. V1 is frame-on-demand, not continuous video streaming.

Preview frames are rate-limited and never include hidden app UI, notifications or other Android apps.

### 10.10 `export_project`

Input profile:

```json
{
  "container": "mp4",
  "videoCodec": "h264",
  "width": 1080,
  "height": 1920,
  "fps": 30,
  "bitrate": 8000000,
  "audioCodec": "aac",
  "destination": "LOCALCUT_MEDIASTORE"
}
```

The controller cannot choose an arbitrary filesystem path. Unattended export uses a LocalCut-owned MediaStore destination. A user-selected SAF destination is allowed only after the user grants it locally.

Return immediately with `jobId` and state. Progress is delivered as events.

## 11. Long-running jobs

Canonical job states:

- `QUEUED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`

Progress is integer `0..100` when calculable.

A finished job stores bounded result metadata long enough for `get_export_status`. Repeating `export_project` with the same command/message id must not create duplicate exports.

## 12. Error model

All failures return a stable code plus a human-readable message.

Initial codes:

- `DEVICE_OFFLINE`
- `BRIDGE_DISABLED`
- `AUTH_REQUIRED`
- `PAIRING_REQUIRED`
- `PROTOCOL_UNSUPPORTED`
- `CAPABILITY_NOT_ALLOWED`
- `COMMAND_EXPIRED`
- `REPLAY_REJECTED`
- `REVISION_CONFLICT`
- `PROJECT_NOT_FOUND`
- `TRACK_NOT_FOUND`
- `CLIP_NOT_FOUND`
- `INVALID_ARGUMENT`
- `INVALID_TIMELINE`
- `OVERLAPPING_CLIPS`
- `ASSET_UNAVAILABLE`
- `IMPORT_PERMISSION_REQUIRED`
- `PREVIEW_FAILED`
- `EXPORT_FAILED`
- `EXPORT_BUSY`
- `RATE_LIMITED`
- `INTERNAL_ERROR`

Error objects include `retryable`, `projectRevision` when relevant and a sanitized `details` map. Tokens, raw Keystore data and unrestricted local paths are forbidden from error payloads.

## 13. Events

Suggested event names:

- `device.state_changed`
- `project.revision_changed`
- `project.selection_changed`
- `project.playhead_changed`
- `job.progress`
- `job.completed`
- `job.failed`
- `error.reported`

High-frequency events such as playhead movement are coalesced/throttled so normal playback does not flood the relay.

## 14. Capability policy and confirmations

V1 capability classes:

### Read-only

Always safe after pairing:

- inspect project/timeline
- preview frame
- diagnostics
- export status

### Reversible project edit

Allowed in controller/autopilot mode only when:

- revision precondition matches
- operation goes through transaction validation
- undo/checkpoint exists

Examples: split, trim, move, duplicate, delete clip reference, transform, keyframes.

### External/destructive side effect

Not exposed in V1:

- delete source media
- overwrite arbitrary existing files
- shell commands
- arbitrary filesystem mutation
- arbitrary app launching/intents

If a future capability enters this class, explicit local confirmation is required by default.

## 15. Audit log

Android stores a bounded local bridge audit log containing:

- timestamp
- command id
- capability
- project id/revision before and after
- success/failure code
- execution duration

Do not log media bytes, secrets or complete user instructions unless the user explicitly enables verbose development diagnostics.

## 16. Performance constraints

- Never block Android main thread with networking, rendering or serialization.
- Project-state responses are summaries; use dedicated tools for large collections.
- Preview frame default max long edge: 960 px; hard limit: 1280 px in V1.
- One active export per project in V1.
- Batch autonomous edits through `apply_edit_script` instead of hundreds of chat round trips.
- Backpressure/coalescing required for playback and progress events.

## 17. Offline behavior

Bridge disabled/offline must not change normal editing.

- local editing, preview and export continue locally
- no command is executed merely because connectivity later returns after its TTL
- pending read request may fail fast with `DEVICE_OFFLINE`
- autosave remains the existing local mechanism

## 18. Autonomous editing contract

The controller does not generate direct project JSON mutations. It generates an `EditScript` containing only allowlisted commands.

Pipeline:

`Instruction -> Planner -> EditScript -> Schema Validator -> Revision Validator -> Transaction Executor -> Timeline Validator -> Preview Check -> Commit/Rollback`

Example target instruction:

> Pegue essas 20 imagens, coloque 4 segundos cada, faça zoom suave, adicione áudio, transições e exporte.

The planner resolves this into deterministic imports/clip creation/keyframes/audio/transitions/export once those capabilities exist in the editor.

## 19. Security requirements

P0 requirements before any remote write command ships:

- Android Keystore device key
- TLS/WSS only
- short-lived sessions
- replay protection
- idempotency key
- revision preconditions
- capability allowlist
- sanitized diagnostics
- no embedded OpenAI/API provider key in APK
- no arbitrary filesystem or shell tool
- source media immutable
- user-visible Bridge enabled/connected indicator
- immediate local disconnect/revoke action

### Signing-key note

The current public repository workflow verifies that `localcut-debug.jks` exists inside the public Build 9 archive. That is acceptable only as a development/debug lineage. It must not become the trust root for a publicly distributed production APK. A separate private release signing lineage should be created before public distribution while retaining the current debug signature only for development/update-over-development-build continuity.

## 20. Compatibility and versioning

- `protocolVersion` starts at `1`.
- Tool payloads are forward-compatible by ignoring unknown optional response fields.
- Unknown required command fields/capabilities fail explicitly.
- Project model migration is separate from bridge protocol migration.
- Controller queries device capabilities before issuing optional/newer tools.

Example capability response:

```json
{
  "protocolVersion": 1,
  "capabilities": {
    "split_clip": 1,
    "trim_clip": 1,
    "get_preview_frame": 1,
    "apply_edit_script": 0
  }
}
```

## 21. Acceptance criteria — Bridge V1

Bridge V1 is complete only when all of the following are evidenced in CI/device tests:

1. Pairing succeeds without storing an OpenAI key in the APK.
2. Disabling Bridge leaves local editor behavior unchanged.
3. Controller reads project state from a paired device.
4. `seek`, `split_clip`, `trim_clip`, `move_clip`, `delete_clip`, `undo`, `redo` work through the same editor command path as local UI actions.
5. Duplicate command ids do not duplicate mutations.
6. Stale `expectedRevision` is rejected without modifying the project.
7. Offline/expired commands do not execute later.
8. `get_preview_frame` returns the requested project frame only.
9. Export starts asynchronously, reports progress/status and cannot overwrite arbitrary files.
10. Errors are structured and sanitized.
11. All bridge edits can be audited; reversible edits can be undone.
12. Existing import/preview/timeline/trim/split/move/delete/undo/redo/export regression tests remain green.

## 22. Research/reference findings used for this spec

Official CapCut documentation confirms current capability classes including multitrack editing, keyframes for transform/opacity/color-class properties, speed curves, chroma key, transitions, effects/filters, adaptive preview quality and configurable export resolution/frame rate/bitrate.

Official Alight Motion documentation confirms multiple video/audio/graphics layers, vector/bitmap layers, 100+ effect building blocks, keyframes broadly across settings, custom easing curves, grouping/masking, color tuning, motion blur, H.264/HEVC/GIF/PNG export, custom fonts, project packages, layer parenting/null objects and camera objects with Z-axis/focus/fog.

The supplied `209917.mp4` was also reviewed as a UI/capability reference. It demonstrates Alight Motion project/element setup, shapes/media/audio/object/text categories, color/effect/mask/transform workflows and presets; and CapCut multi-media import, timeline transitions, split/animation/effects/crop/delete/duplicate, effects/retouch/background tools, filters/adjustments and transform/mask controls. This video is a reference for capability grouping only; LocalCut must retain its own UI and implementation.

Reference URLs:

- https://www.capcut.com/tools/keyframe-animation
- https://www.capcut.com/tools/speed-ramp
- https://www.capcut.com/help/chroma-key-feature
- https://www.capcut.com/tools/free-video-transitions
- https://www.capcut.com/tools/video-effect-and-filter
- https://www.capcut.com/help/export-videos-in-capcut
- https://www.capcut.com/help/how-to-fix-blurry-videos-after-importing
- https://alightmotion.com/
- https://support.alightmotion.com/hc/en-us/sections/10536976447377-Feature-Guides
- https://support.alightmotion.com/hc/en-us/articles/10536934703889-Animation-Easing-Curves
- https://support.alightmotion.com/hc/en-us/articles/10536997444369-Layer-Parenting-and-Null-Objects
- https://support.alightmotion.com/hc/en-us/articles/10536993608977-Camera-Objects-Guide
- https://support.alightmotion.com/hc/en-us/articles/10537118778129-Motion-Blur
