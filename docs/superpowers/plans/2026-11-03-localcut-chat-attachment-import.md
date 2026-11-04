# LocalCut Chat Attachment Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import a current-conversation ChatGPT media attachment directly into LocalCut without Android Share or permanent public hosting.

**Architecture:** Add a bounded in-memory one-time media transfer endpoint to the existing LocalCut Relay, then teach the LocalCut Mobile skill to upload a mounted/materialized attachment and reuse the existing `import_url` handoff. No APK protocol change is required.

**Tech Stack:** Node.js/TypeScript Relay, existing LocalCut HTTPS handoff, ChatGPT plugin skill, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-03-localcut-chat-attachment-import.md`

## Global Constraints
- Branch `feature/localcut-bridge-v1`; PR #14 remains OPEN + DRAFT.
- No merge to `main` and no production Render deployment without explicit authorization.
- Maximum attachment 20 MiB; TTL 5 minutes; one-time download.
- MIME limited to image/audio/video.
- Existing Android import confirmation and `import_url` behavior remain unchanged.
- Never publish conversation attachments to third-party public hosts.

## Review Focus
- Oversized upload must return 413 and retain no partial media.
- Invalid MIME must fail closed.
- Expired and already-consumed tokens must return 404.
- Token format must reject weak/malformed values.
- Download response must preserve content type and use `Cache-Control: no-store`.

---

### Task 1: Relay one-time media transfer

**Files:**
- Create: `bridge/controller/src/transientMedia.ts`
- Modify: `bridge/controller/src/server.ts`
- Create: `bridge/controller/test/transientMedia.test.ts`
- Modify: `bridge/controller/test/server.test.ts`

**Interfaces:**
- Produces `TransientMediaStore.put(token, contentType, bytes, nowEpochMs)` and `consume(token, nowEpochMs)`.
- HTTP: `POST /v1/mobile-media/:token` with raw bytes and `Content-Type`; `GET /v1/mobile-media/:token` consumes once.

- [ ] Write RED tests for token validation, MIME validation, 20 MiB limit, TTL, one-time consume, and no-store headers.
- [ ] Run controller tests and confirm the new cases fail before implementation.
- [ ] Implement the bounded in-memory store and HTTP routes.
- [ ] Run controller tests and typecheck; require all new tests PASS.
- [ ] Commit only Relay/test changes.

### Task 2: LocalCut Mobile plugin attachment workflow

**Files:**
- Modify plugin `skills/localcut-mobile/SKILL.md`
- Modify plugin manifests/version only as required by Plugin Creator.

**Interfaces:**
- Consumes exact conversation attachment bytes plus Relay `POST /v1/mobile-media/:token`.
- Produces existing `https://localcut-bridge-v1.onrender.com/open?action=import_url&url=<temporary-url>&returnTo=chatgpt` handoff.

- [ ] Update the skill to prefer private temporary upload for current-conversation attachments.
- [ ] Require fresh 256-bit token, exact file preservation, no public-host fallback, and no completion claim before APK confirmation.
- [ ] Preserve Android Share as fallback when temporary upload is unavailable.
- [ ] Update the private `localcut-mobile` plugin release.

### Task 3: CI and live-gate preparation

**Files:**
- No production files beyond Tasks 1-2.

- [ ] Verify PR #14 remains OPEN + DRAFT and branch head contains only intended changes.
- [ ] Run/inspect Relay CI for the exact HEAD; fix routine failures.
- [ ] Do not deploy Render without explicit production authorization.
- [ ] After deployment approval, perform a live upload/download smoke test with a small non-sensitive media file, then import one user-provided attachment through LocalCut and confirm in the APK.
