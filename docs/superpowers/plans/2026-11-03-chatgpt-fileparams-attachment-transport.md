# ChatGPT fileParams Attachment Transport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Receive ChatGPT media attachments in the authenticated LocalCut MCP and stage them into the existing one-time relay for the Android `import_url` flow.

**Architecture:** Keep the transient relay as the single byte store. Refactor it so one store instance is shared by HTTP GET/POST compatibility and MCP staging, avoiding a self-HTTP upload. Add a `stage_attachment` MCP management tool that uses the official ChatGPT file-param shape and delegates bounded HTTPS download/staging to the controller server.

**Tech Stack:** TypeScript, Node 24, `@modelcontextprotocol/server` 2.3.0, Zod 4.6.7, Node test runner.

**Spec:** `docs/superpowers/specs/2026-11-03-chatgpt-fileparams-attachment-transport.md`

## Global Constraints
- Branch `feature/localcut-bridge-v1`; PR #14 stays OPEN + DRAFT.
- No merge to `main`, no force-push.
- Preserve `overrides/`, Android signing, pairing/OAuth, revision guards, existing relay semantics, and validated editor behavior.
- Media staging limit remains 20 MiB and TTL remains 5 minutes.
- Android local confirmation for `import_url` remains mandatory.

## Review Focus
- Payload exceeds 20 MiB while streaming: abort without staging partial bytes.
- Missing/incorrect upstream Content-Type: use declared file MIME only when supported; otherwise reject.
- Non-HTTPS `download_url`: reject before network access.
- Upstream non-2xx response or empty body: stable tool error and no relay entry.
- One-time relay consumption and TTL behavior remain unchanged for existing HTTP users.

---

### Task 1: RED — pin MCP file-param contract

**Files:**
- Modify: `bridge/controller/test/mcp.test.ts`

**Interfaces:**
- Produces expected `stage_attachment` management tool name, file schema validation, and metadata helper contract.

- [ ] Add tests asserting `stage_attachment` is exposed, `download_url`/`file_id` are required, optional MIME/name are accepted, non-HTTPS URLs are rejected, and metadata contains `openai/fileParams: ["file"]`.
- [ ] Commit test-only change.
- [ ] Run Bridge Controller CI for that SHA and confirm expected RED due missing implementation.

### Task 2: GREEN — shared transient-media store

**Files:**
- Modify: `bridge/controller/src/transientMediaHttp.ts`
- Modify: `bridge/controller/src/browserControlHttp.ts`
- Modify: `bridge/controller/test/transientMediaHttp.test.ts`

**Interfaces:**
- Produces `TransientMediaStore`/factory with direct `stage(...)` and existing HTTP handler sharing the same entries.

- [ ] Add store-level test proving direct staging is downloadable once through the HTTP handler.
- [ ] Implement minimal shared store while preserving current route/status/header behavior.
- [ ] Keep existing HTTP tests green.

### Task 3: GREEN — bounded attachment download and MCP tool

**Files:**
- Modify: `bridge/controller/src/mcp.ts`
- Modify: `bridge/controller/src/server.ts`
- Modify: `bridge/controller/test/mcp.test.ts`
- Modify: `bridge/controller/test/server.test.ts`

**Interfaces:**
- Consumes shared transient store from Task 2.
- Produces `stage_attachment(file)` tool result containing relay URL, handoff URL, MIME, file name/id, expiry; never raw OpenAI `download_url`.

- [ ] Implement file-param schema and metadata.
- [ ] Stream HTTPS download with 20 MiB cap and stable error codes.
- [ ] Stage bytes directly into shared relay store using a random 32-byte base64url token.
- [ ] Return `https://.../v1/mobile-media/<token>` and `/open?action=import_url...&returnTo=chatgpt` handoff URL.
- [ ] Add server/MCP tests for success, non-HTTPS, unsupported MIME, upstream failure, empty payload, and oversize stream.

### Task 4: Plugin guidance + regression

**Files:**
- Modify: `bridge/chatgpt-plugin/skills/localcut-control/SKILL.md`

- [ ] Document that attached image/audio/video should use `stage_attachment`; TXT remains model-readable and must only become timeline text through explicit `add_text` intent.
- [ ] Run Bridge Controller CI, Android Debug APK, Android Multitrack Export Smoke, and Termux Agent Engine CI once for the final SHA.
- [ ] Record final checkpoint with HEAD, tests, known risks, and next action.
