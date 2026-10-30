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

## Reads and writes

- Read `get_project_state` before any project write that depends on current timeline state.
- Pass the current `expectedRevision` on every revision-gated tool.
- On `REVISION_CONFLICT`, re-read project state once, recompute the requested edit against the new state, and retry at most once when the user's intent remains unambiguous.
- Never invent clip IDs, track IDs, project IDs, device IDs, positions, or revisions.
- Prefer reversible operations during testing. Use `undo` to clean up a temporary QA mutation when appropriate.
- Do not use destructive commands merely to test whether authentication works.

## Verification

- Treat a successful tool response as evidence for the specific action only; do not infer unrelated behavior.
- Use `get_playhead` after seek/playhead tests.
- Use `get_project_state`, `list_tracks`, `list_clips`, or `get_selected_clip` to verify timeline edits.
- Use `get_preview_frame` when visual verification materially helps, including preview regressions, transform/keyframe checks, layout/content checks that the captured editor frame can show, and final QA.
- For export, inspect `get_export_status`; do not report completion from job creation alone.

## Safety and integrity

- Preserve project/media unless the user explicitly requests an edit or a bounded reversible QA operation is necessary.
- Do not expose local asset URIs, credentials, pairing/session tokens, API keys, or signing material.
- Keep pairing, Android Keystore identity, TTL, replay protection, revision checks, and controller authorization intact.
- If direct MCP is unavailable, fall back to the supported Android HTTPS handoff flow rather than weakening server or APK security.
