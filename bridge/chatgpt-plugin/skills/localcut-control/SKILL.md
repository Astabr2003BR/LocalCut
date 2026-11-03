---
name: localcut-control
description: Use when the user wants ChatGPT to inspect, control, edit, test, or audit a paired LocalCut Android editor through its authenticated Bridge.
---

# LocalCut direct control

Use the authenticated LocalCut MCP as the primary control path for a paired Android editor. Do not require Android handoff links when direct MCP tools are available.

## Bootstrap

1. If `deviceId` or `projectId` is unknown, call `discover_active_contexts` first.
2. If exactly one active context is returned, use its `deviceId`, `projectId`, and `projectRevision`.
3. If more than one context is returned, use a previously grounded device only when the conversation clearly identifies it. Otherwise report the ambiguity instead of guessing.
4. If no context is active, report that the paired APK is offline or the agent connection is not active. Do not retry in a loop.

## ChatGPT attachments

- For an attached image, audio file, or video/clip that the user wants to import into LocalCut, call `stage_attachment` with the top-level ChatGPT file parameter.
- Use the returned `handoffUrl` to open the existing Android `import_url` flow. Local confirmation remains mandatory before LocalCut downloads/imports the staged media.
- Never expose, copy into chat, or persist the raw ChatGPT `download_url`. The one-time LocalCut relay URL is bounded, short-lived, and consumed after download.
- After a successful Android import, rediscover/read the active project and verify the resulting timeline state before reporting success.
- TXT and other text documents are not media imports. They may be read by the model, but only turn their contents into LocalCut `add_text` edits when the user explicitly requests that semantic conversion and the timing/layout intent is known.
- Do not create public file links, weaken relay security, or bypass the Android confirmation dialog to make attachment import easier.

## Reads and writes

- Read `get_project_state` before any project write that depends on current timeline state.
- Pass the current `expectedRevision` on every revision-gated tool.
- On `REVISION_CONFLICT`, re-read project state once, recompute the requested edit against the new state, and retry at most once when the user's intent remains unambiguous.
- Never invent clip IDs, track IDs, project IDs, device IDs, positions, or revisions.
- Prefer reversible operations during testing. Use `undo` to clean up a temporary QA mutation when appropriate.
- Do not use destructive commands merely to test whether authentication works.

## Character and scene animation

- Read the current project/timeline first and use only real clip IDs. `animate_character` and `animate_scene` are revision-gated write tools and must receive the current `expectedRevision`.
- `animate_character` requires `bodyClipId` and supports `IDLE`, `TALK`, `WALK`, `CROUCH`, `STAND`, `PICK_UP`, `PUT_DOWN`, `POINT`, `ENTER`, and `EXIT`.
- Optional rig clip IDs may bind separate head, eyes, mouth, left/right arms, left/right legs, and a held object. Independent limb, eye, or mouth animation is only possible when those parts exist as separate LocalCut clips/layers; do not claim skeletal or facial articulation from one flat image.
- For `TALK`, use grounded `lipSyncCues` when exact timing is available. When cues are omitted but a `mouthClipId` exists, LocalCut uses a local rhythmic mouth open/close fallback. Do not describe that fallback as phoneme-accurate lip sync or audio-derived synchronization.
- `PICK_UP` and `PUT_DOWN` require `heldObjectClipId`. `targetX`/`targetY` may be supplied when the destination is grounded; otherwise use the planner defaults rather than inventing scene coordinates.
- `animate_scene` supports `PAN_ZOOM`, `PARALLAX`, and `CROSSFADE`. `CROSSFADE` requires a distinct `incomingClipId`; `PARALLAX` may use grounded `secondaryClipIds`.
- Animation writes merge into the requested time window and preserve existing keyframes outside that window. Sequential actions start from the already animated transform at the new action's start time.
- Every successful animation write advances project revision. Refresh project state/revision before issuing the next write unless the returned revision is explicitly available and authoritative for the same active project.
- Respect track/layer locks. Never bypass a lock to make an animation command succeed.

## Verification

- Treat a successful tool response as evidence for the specific action only; do not infer unrelated behavior.
- Use `get_playhead` after seek/playhead tests.
- Use `get_project_state`, `list_tracks`, `list_clips`, or `get_selected_clip` to verify timeline edits.
- After character or scene animation, verify affected clips/property animations and use `get_preview_frame` when visual confirmation is material. Do not call lip sync visually verified unless the mouth layer and timing are actually observable in the captured preview.
- Use `get_preview_frame` when visual verification materially helps, including preview regressions, transform/keyframe checks, layout/content checks that the captured editor frame can show, and final QA.
- For export, inspect `get_export_status`; do not report completion from job creation alone.

## Safety and integrity

- Preserve project/media unless the user explicitly requests an edit or a bounded reversible QA operation is necessary.
- Do not expose local asset URIs, credentials, pairing/session tokens, API keys, or signing material.
- Keep pairing, Android Keystore identity, TTL, replay protection, revision checks, track/layer locks, and controller authorization intact.
- If direct MCP is unavailable, fall back to the supported Android HTTPS handoff flow rather than weakening server or APK security.
