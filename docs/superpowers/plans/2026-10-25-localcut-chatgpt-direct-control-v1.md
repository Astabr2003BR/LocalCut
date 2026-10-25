# LocalCut ChatGPT Direct Control V1 Implementation Plan

**Goal:** let ChatGPT discover the currently connected LocalCut device/project and invoke existing MCP tools directly without an Android handoff for every command.

**Architecture:** `discover_active_contexts` enumerates live devices only and probes them through the existing authenticated `CommandRouter`. Android handles the reserved discovery project ID at `BridgeMessageProcessor`, after device auth and replay/TTL validation; `BridgeCommandExecutor` keeps exact project binding for all normal commands. The private ChatGPT plugin connects to the existing bearer-authenticated `/mcp` endpoint.

## Constraints

- Keep PR #13 OPEN, DRAFT and NOT MERGED.
- Preserve `overrides/`, stable signing, pairing/Keystore/session security and all working editor functions.
- `/mcp` stays bearer-authenticated; no secret goes into source or plugin package.
- Writes continue to require current `expectedRevision`.
- Android handoff remains available as fallback.
- One consolidated candidate APK after the block is complete.

## TDD evidence

- RED controller: management list lacked `discover_active_contexts` and helper export was absent; existing controller tests otherwise passed.
- RED Android: `BridgeActiveContextProbeTest.activeProjectProbeReturnsCurrentRoutingWithoutMutation` failed while the sentinel-bypass rejection test passed; 677/678 existing+new tests passed.
- Design ruling after RED: keep the sentinel exception at the authenticated message boundary rather than weakening `BridgeCommandExecutor` project binding. The acceptance behavior is unchanged externally.

## Tasks

- [x] Define failing controller discovery tests.
- [x] Define failing Android active-context tests and verify RED.
- [ ] Add deterministic live-device enumeration to `DeviceRegistry`/`CommandRouter`.
- [ ] Add and register `discover_active_contexts` in MCP.
- [ ] Add read-only active-context handling to `BridgeMessageProcessor` and provider wiring in `LocalCutProcessRuntime`.
- [ ] Add private plugin source (`plugin.json`, `mcp.json`, `skills/localcut-control/SKILL.md`) with no embedded credential.
- [ ] Run Bridge Controller CI, Android Debug APK and Android emulator smoke on the exact final HEAD.
- [ ] Verify PR #13 remains OPEN/DRAFT/not merged.
- [ ] Create private plugin package and require the user's one-time install/connect action.
- [ ] After connection, use direct read-only discovery/state/preview and then a reversible command to prove end-to-end control.
