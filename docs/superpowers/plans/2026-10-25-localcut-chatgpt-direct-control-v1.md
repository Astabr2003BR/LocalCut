# LocalCut ChatGPT Direct Control V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let ChatGPT discover the currently connected LocalCut device/project and invoke the existing MCP tools directly without an Android handoff for each command.

**Architecture:** Add a read-only MCP management discovery tool that probes only live devices through the existing authenticated router. Android accepts one reserved project sentinel only for `GET_DEVICE_STATUS`, returning the real active project routing metadata; all other capabilities retain exact project binding and existing revision checks.

**Tech Stack:** Kotlin/JUnit/Android Media3 app, Node.js 24/TypeScript 7, MCP SDK 2.x, WebSocket controller, Agent Plugins 1.0.

**Spec:** `docs/superpowers/specs/2026-10-25-localcut-chatgpt-direct-control-v1.md`

## Global Constraints

- Keep PR #13 OPEN, DRAFT and NOT MERGED.
- Preserve `overrides/`, stable signing, existing pairing/Keystore/session security and all currently working editor functions.
- `/mcp` stays bearer-authenticated; no secret goes into source or plugin package.
- Project writes continue to require the current `expectedRevision`.
- Android handoff remains available as a fallback.
- One consolidated candidate APK only after the block is complete.

## Review Focus

- Multiple connected devices: discovery must return separate deterministic contexts rather than guessing.
- Device disconnect during discovery: a race must not weaken authorization or mutate state.
- Sentinel misuse on non-status capabilities: must return project-not-found behavior.
- Stale project revision after discovery: write tools must still reject with revision conflict.
- Plugin credential handling: package declares bearer API-key auth but contains no API key.

---

### Task 1: Active-context contract tests

**Files:**
- Modify: `bridge/controller/test/mcp.test.ts`
- Create: `overrides/app/src/test/java/com/localcut/editor/bridge/agent/BridgeActiveContextProbeTest.kt`

**Interfaces:**
- Consumes: existing `DeviceRegistry`, `CommandRouter`, `BridgeCommandExecutor`, `GET_DEVICE_STATUS`.
- Produces: executable expectations for `discover_active_contexts` and the reserved active-project sentinel.

- [ ] **Step 1: Write the failing controller tests**

Require `controllerManagementToolNames()` to include `discover_active_contexts`. Require an exported `discoverActiveContexts(router, registry, controllerId, nowEpochMs)` helper to probe a live device with `projectId="__localcut_active_project__"` and return the real routing context.

- [ ] **Step 2: Write the failing Android tests**

Require `GET_DEVICE_STATUS` with the sentinel to succeed, return `projectId="project-1"` and revision `5`, and leave revision unchanged. Require the same sentinel with `GET_PROJECT_STATE` to remain rejected.

- [ ] **Step 3: Run RED**

Run the controller test suite and Android unit gate. Expected: failures specifically because discovery is absent and the sentinel is not yet accepted.

### Task 2: Controller discovery implementation

**Files:**
- Modify: `bridge/controller/src/commandRouter.ts`
- Modify: `bridge/controller/src/mcp.ts`
- Modify: `bridge/controller/src/server.ts`

**Interfaces:**
- Produces: `DeviceRegistry.listDeviceIds(): string[]` and `discoverActiveContexts(...)` plus MCP tool `discover_active_contexts`.
- Consumes: existing router authorization and `get_device_status` capability.

- [ ] **Step 1: Add deterministic live-device enumeration**

Add `DeviceRegistry.listDeviceIds(): string[]`, returning currently registered device IDs in stable sorted order.

- [ ] **Step 2: Add bounded discovery helper**

Implement `discoverActiveContexts(router, registry, controllerId, nowEpochMs = Date.now)` in `mcp.ts`. Probe each live device through `CommandRouter.execute` using `get_device_status`, the reserved sentinel and a 30-second command TTL. Return only successful, structurally valid contexts.

- [ ] **Step 3: Register the management tool**

Expose `discover_active_contexts` with an empty strict input schema and route it through the server's existing authenticated MCP handler.

- [ ] **Step 4: Run controller tests and typecheck**

Run `npm test` and `npm run typecheck` in `bridge/controller`. Expected: PASS.

### Task 3: Android active-project status probe

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeCommandExecutor.kt`

**Interfaces:**
- Consumes: reserved project ID `__localcut_active_project__` from controller discovery.
- Produces: read-only status JSON containing `state`, current `projectId`, and current `revision`.

- [ ] **Step 1: Permit the sentinel only for `GET_DEVICE_STATUS`**

Skip the exact project-ID check only for that one capability/sentinel pair. All other capabilities keep current project binding.

- [ ] **Step 2: Expand device status payload**

Return `state=READY`, the active project ID, and current revision without mutating the project.

- [ ] **Step 3: Run Android unit/regression gate**

Expected: `BridgeActiveContextProbeTest` and all existing Android unit tests PASS.

### Task 4: Private ChatGPT plugin source

**Files:**
- Create: `bridge/chatgpt-plugin/plugin.json`
- Create: `bridge/chatgpt-plugin/mcp.json`
- Create: `bridge/chatgpt-plugin/skills/localcut-control/SKILL.md`

**Interfaces:**
- Connects to: `https://localcut-bridge-v1.onrender.com/mcp` via streamable HTTP.
- Auth: API key using Bearer scheme; credential is stored by ChatGPT connection UI, never committed.

- [ ] **Step 1: Define portable plugin manifest**

Create a private LocalCut control plugin with ChatGPT-facing metadata and no embedded credentials.

- [ ] **Step 2: Define MCP connection**

Use Agent Plugins 1.0 `mcp.json`, `type="streamable-http"`, verified endpoint URL, and `extensions.com.openai.auth` with `type="api_key"`, `headerScheme="bearer"`.

- [ ] **Step 3: Define operating skill**

Instruct the agent to discover active context first, read current revision before writes, prefer reversible actions, verify results, and use preview capture for visual QA.

- [ ] **Step 4: Validate JSON/YAML/package layout and save the private plugin**

Package only the plugin directory, create the private plugin, then verify its MCP connection after the user completes the required one-time install/authentication action.

### Task 5: Final gates and delivery

**Files:**
- No additional production files unless a failing gate identifies a real defect.

**Interfaces:**
- Verifies all outputs from Tasks 2–4 together.

- [ ] **Step 1: Run final GitHub CI on the exact final HEAD**

Require Bridge Controller CI, Android Debug APK and Android Multitrack Export Smoke to PASS.

- [ ] **Step 2: Verify PR lock**

Require PR #13 state OPEN, DRAFT, merged=false.

- [ ] **Step 3: Download and verify the candidate APK**

Record version, artifact digest and actual APK SHA-256.

- [ ] **Step 4: Connect plugin and set automatic permission**

After explicit user install/connect action, set the LocalCut plugin permission to `full_access` because the user explicitly requested commands without per-action confirmation. Then perform read-only discovery/status/preview before any destructive QA action.
