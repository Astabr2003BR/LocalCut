# LocalCut Live Agent Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a paired ChatGPT controller visually inspect the LocalCut preview, edit the project through existing Bridge commands, inspect the result again, and iterate safely.

**Architecture:** Reuse Bridge protocol v1 and the already-declared `get_preview_frame` capability. Attach a bounded Android preview capture port to the current `PlayerView`, return JPEG base64 + metadata through the existing authenticated command route, and make the MCP adapter emit actual image content for that result. Existing project revision checks remain the concurrency guard.

**Tech Stack:** Kotlin/Jetpack Compose/Media3 1.11.1/Android PixelCopy, TypeScript/MCP server, JUnit + Node tests.

**Spec:** `docs/design/2026-10-24-live-agent-mode.md`

## Global Constraints
- Keep PR #13 open, draft, and unmerged.
- Preserve current Bridge pairing/authentication/revision protections.
- Do not expose arbitrary Android screenshots or files.
- Keep preview capture bounded and read-only.
- Preserve import, preview, timeline, edit, undo/redo and export regressions.

## Review Focus
- Preview surface unavailable: return controlled `PREVIEW_FAILED`, never crash.
- Manual edit races controller: existing `REVISION_CONFLICT` must still win.
- Oversized preview: capture must clamp dimensions and JPEG quality.
- Empty/no-media project: preview command returns controlled failure/metadata, not invalid image bytes.
- MCP image payload: only successful validated `image/jpeg`/`image/png` data is emitted as image content.

---

### Task 1: Bridge preview-frame contract

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgePreviewFramePort.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeCommandExecutor.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/bridge/agent/BridgePreviewFrameCommandTest.kt`

**Interfaces:**
- Produces: `BridgePreviewFrame`, `BridgePreviewFramePort.capture(): Result<BridgePreviewFrame>`.
- `BridgeCommandExecutor` accepts an optional preview port and serves `GET_PREVIEW_FRAME`.

- [ ] Write a failing test where a fake port returns JPEG bytes and assert `GET_PREVIEW_FRAME` succeeds with mime/base64/size/position metadata.
- [ ] Write a failing test where the port fails and assert `PREVIEW_FAILED` is returned as retryable.
- [ ] Implement the minimal preview port contract and executor branch.
- [ ] Run Android unit tests for bridge command executor.
- [ ] Commit.

### Task 2: Android PlayerView capture implementation

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/AndroidPlayerPreviewFramePort.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/components/PreviewPane.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/LocalCutProcessRuntime.kt`

**Interfaces:**
- Consumes: `BridgePreviewFramePort` from Task 1.
- Produces: a runtime-owned preview port whose current `PlayerView` can be attached/detached by `PreviewPane`.

- [ ] Add pure tests for clamp/output metadata helper if needed.
- [ ] Implement capture using `PlayerView.videoSurfaceView`; use `PixelCopy` for `SurfaceView`, `TextureView.bitmap` for `TextureView`.
- [ ] Scale output to a bounded maximum edge and JPEG-compress at bounded quality.
- [ ] Attach/detach the PlayerView from `PreviewPane` lifecycle.
- [ ] Inject the port into `BridgeCommandExecutor` in `LocalCutProcessRuntime`.
- [ ] Run Android unit tests + assemble debug.
- [ ] Commit.

### Task 3: MCP image response

**Files:**
- Modify: `bridge/controller/src/mcp.ts`
- Test: `bridge/controller/test/mcp.test.ts`

**Interfaces:**
- Consumes: successful Bridge result shaped as `{ result: { mimeType, base64, ... } }`.
- Produces: MCP tool result containing normal structured content plus an `image` content item for `get_preview_frame`.

- [ ] Write a failing Node test for image content mapping.
- [ ] Implement strict preview-image extraction for supported image MIME types only.
- [ ] Preserve existing text/structured response for all other tools and errors.
- [ ] Run controller tests.
- [ ] Commit.

### Task 4: Regression and checkpoint

**Files:**
- Create/update: `docs/checkpoints/2026-10-24-live-agent-mode.md`

- [ ] Run Android Debug APK workflow once on consolidated HEAD.
- [ ] Run Bridge Controller CI and multitrack smoke on consolidated HEAD.
- [ ] Verify PR #13 remains open/draft/unmerged.
- [ ] Record PASS/FAIL evidence, known limitation that the assistant can only iterate while an active tool session is executing, and next Bridge mutation targets for Motion/Design.
- [ ] Download and validate APK artifact if Android build passes.