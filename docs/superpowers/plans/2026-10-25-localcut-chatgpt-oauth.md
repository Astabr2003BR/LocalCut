# LocalCut ChatGPT OAuth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Connect the private LocalCut ChatGPT plugin to the authenticated Bridge using OAuth 2.1 approved by the already-paired Android device.

**Architecture:** Add a focused OAuth service to the controller, expose standards-compliant discovery and authorization endpoints, protect MCP with OAuth, add an Android deep-link approval path, then update the private plugin to OAuth.

**Tech Stack:** Node/TypeScript controller, MCP, Kotlin Android, OkHttp, Android Keystore, GitHub Actions, Render.

**Spec:** `docs/superpowers/designs/2026-10-25-localcut-chatgpt-oauth.md`

## Global Constraints
- PR #13 remains OPEN, DRAFT, NOT MERGED.
- Preserve pairing, recovery, signing, editor functions and existing Bridge behavior.
- Keep authentication material out of source, plugin files and logs.
- Require PKCE S256 and strict validation of OAuth requests.

## Tasks

### Task 1: OAuth domain service
Create `bridge/controller/src/oauth.ts` and `bridge/controller/test/oauth.test.ts`. Test expiry, replay, PKCE, scope/resource validation, refresh and device binding before implementation.

### Task 2: Controller HTTP integration
Modify `bridge/controller/src/server.ts` and `bridge/controller/test/server.test.ts` to add OAuth discovery/authorization/token/device-approval endpoints and OAuth protection for `/mcp`, preserving all existing `/v1` behavior.

### Task 3: MCP auth metadata
Modify `bridge/controller/src/mcp.ts` and its tests so tools advertise read/write OAuth scopes appropriately.

### Task 4: Android device approval
Add a small approval client and tests, then extend `MobileHandoffActivity` and the manifest for a dedicated OAuth approval deep link. Approval uses the existing encrypted paired-device session and only the configured HTTPS Relay.

### Task 5: Plugin manifest
Update the existing private LocalCut plugin from fixed-key auth to OAuth while preserving plugin identity and skill files.

### Task 6: Verification and deployment
Run controller CI, Android Debug and emulator smoke on one HEAD, deploy the controller, validate discovery and protected MCP behavior, then install the updated APK and perform a read-only MCP test followed by one reversible edit.
