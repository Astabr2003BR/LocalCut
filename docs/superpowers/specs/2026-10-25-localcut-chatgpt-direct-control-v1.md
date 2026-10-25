# LocalCut ChatGPT Direct Control V1 — Design

## Intent

Allow a paired LocalCut APK to be controlled directly from ChatGPT through the existing authenticated MCP endpoint, without requiring an Android handoff link for every edit command.

## Current problem

The APK already keeps an authenticated WebSocket session with the controller and the controller already exposes the LocalCut edit/read capabilities through `/mcp`. However, every normal MCP tool currently requires both `deviceId` and `projectId`. A newly connected ChatGPT plugin has no safe way to discover those routing identifiers, so it cannot bootstrap direct control by itself.

## Goals

- Add a read-only MCP management tool named `discover_active_contexts`.
- Discover only devices that are currently connected to the authenticated controller.
- Return enough routing metadata for subsequent MCP calls: `deviceId`, `projectId`, `projectRevision`, and device state.
- Keep all project writes revision-gated exactly as they are today.
- Keep the existing Android handoff flow as a fallback.
- Package a private ChatGPT plugin that uses the existing `https://localcut-bridge-v1.onrender.com/mcp` endpoint with bearer API-key authentication.

## Non-goals

- Do not remove pairing, Android Keystore identity, session authentication, replay protection, TTLs, or revision checks.
- Do not make `/mcp` public or unauthenticated.
- Do not auto-approve Android handoff links; direct MCP control replaces repeated handoffs when available.
- Do not merge PR #13.
- Do not create another LocalCut project or permanent development branch.

## Architecture

The controller will expose `discover_active_contexts` as a management tool with no routing arguments. The tool enumerates only live device IDs from `DeviceRegistry`, then sends a bounded `get_device_status` probe through the existing `CommandRouter`. The probe uses the reserved project identifier `__localcut_active_project__`.

On Android, `BridgeCommandExecutor` accepts that reserved identifier only when the capability is `GET_DEVICE_STATUS`. That response includes the real current `projectId` and `revision`. All other capabilities continue to require an exact project ID, so the sentinel cannot bypass project binding for reads or writes.

The plugin workflow is:

1. `discover_active_contexts`
2. choose the single live context, or disambiguate if several are live
3. `get_project_state` using the discovered IDs
4. for a write, pass the returned `expectedRevision`
5. inspect the command result and optionally `get_preview_frame`

## Security invariants

- `/mcp` remains protected by `CONTROLLER_API_TOKEN` bearer authentication.
- Discovery never reads persistent devices that are offline; it starts from the live registry.
- The router still verifies that every device is bound to the current `controllerId`.
- The active-project sentinel is valid only for `GET_DEVICE_STATUS`.
- Discovery is read-only and must not change project revision.
- Secrets are never stored in the plugin package. The plugin declares API-key authentication and lets ChatGPT store the credential through its connection UI.

## Acceptance criteria

- Controller tests prove `discover_active_contexts` is present and returns routing metadata for a live paired device.
- Android tests prove the sentinel returns current routing metadata without mutation.
- Android tests prove the sentinel is rejected for another capability.
- Existing Bridge/controller, Android unit, emulator smoke, signing, preview, timeline and export gates remain green.
- PR #13 remains OPEN, DRAFT and NOT MERGED.
