# LocalCut Pro Mobile Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans and TDD. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a professional Android-first LocalCut UI and progressively complete all non-generative editing capabilities researched from CapCut, Alight Motion and useful Canva video/design workflows.

**Architecture:** Preserve the existing LocalCut session/history/render architecture. Add a pure selection-aware tool catalog, then render one contextual mobile tool surface at a time. Existing working engines are exposed first; metadata-only capabilities receive renderer/codec/history/persistence work before being marked live.

**Tech Stack:** Kotlin, Jetpack Compose, Android, Media3 1.11.x, existing LocalCut domain/session/history/project JSON architecture.

**Spec:** `docs/superpowers/specs/2026-10-23-localcut-pro-mobile-editor-design.md`

## Global Constraints

- Repository: `Astabr2003BR/LocalCut`.
- Branch: `feature/localcut-bridge-v1`.
- PR #13 remains OPEN + DRAFT + NO MERGE.
- Preserve `overrides/`, stable APK signing and existing project/media behavior.
- Current project schema stays v6 until a persisted feature requires a complete codec/migration.
- Original implementation only; reference products provide capability/UX research, not code/UI/assets.
- Generative AI remains excluded.
- A visible enabled tool must have a real tested editor/backend path.
- Every editing mutation continues through history/session so undo/redo/autosave remain authoritative.

## Review Focus

1. Mobile portrait layout must not let Bridge configuration consume the editing workspace.
2. Bottom-tool navigation must not mutate project state by itself or bypass history.
3. Changing tool categories with no selected clip must remain safe.
4. Existing import/trim/split/delete/export interactions must stay reachable after layout changes.
5. Planned tools must not be accidentally enabled before renderer/persistence parity exists.

---

### Task 1: Tool catalog and selection-aware navigation model

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorToolCatalog.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/ui/editor/EditorToolCatalogTest.kt`

**Interfaces:**
- Produces `EditorToolId`, `EditorToolGroup`, `EditorSelectionKind`, `EditorToolState`, `EditorToolDefinition` and catalog filtering helpers.
- Consumed by mobile Compose tool rail in Task 3.

- [ ] Write RED tests for project/video/image/audio/text/design selection catalogs and AI exclusion.
- [ ] Implement catalog with LIVE/PARTIAL/PLANNED states.
- [ ] Verify no AI tool IDs exist in active catalog.
- [ ] Run focused tests GREEN.

### Task 2: Compact Bridge surface

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/BridgePanel.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/ui/editor/BridgePanelUiPolicyTest.kt`

**Interfaces:**
- Preserve existing `BridgePanel` callback API.
- Add pure summary policy for compact phase/status rendering.

- [ ] Write RED tests for offline/pairing/online/error summaries and expansion defaults.
- [ ] Make Bridge collapsed by default into a compact status row.
- [ ] Expand full Relay/pairing settings on explicit tap only.
- [ ] Preserve connect/disconnect/always-connected/revoke behavior.

### Task 3: Professional portrait editor chrome

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorBottomToolRail.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorScreen.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/ui/editor/EditorChromeUiPolicyTest.kt`

**Interfaces:**
- Consumes Task 1 tool catalog.
- Preserves `EditorScreen` external callback signature.
- Produces compact top bar, larger preview, expanded timeline and bottom contextual tool rail.

- [ ] RED tests for default selected tool/group and selection changes.
- [ ] Replace always-visible properties stack with one contextual tool surface.
- [ ] Keep import/audio/overlay/text/export reachable from bottom/top controls.
- [ ] Keep undo/redo/export in compact top bar.
- [ ] Verify portrait and landscape compilation.

### Task 4: Wire existing LIVE creative tools into contextual panels

**Files:**
- Modify: `CreativeToolsPanel.kt`, `DesignToolsPanel.kt`, `EditorScreen.kt`.
- Extend: `CreativeToolsRendererContractTest.kt`.

**Interfaces:**
- Existing `EditorAction.SetSelected*` remains mutation boundary.

- [ ] RED test that every enabled visual/time/audio preset has an enabled backend/command.
- [ ] Expose transform, effects/color, mask/chroma/crop, speed, audio and Anime recipes from bottom navigation.
- [ ] Expose design elements/canvas/components only in relevant tool groups.
- [ ] Keep metadata-only tools disabled/not-live.

### Task 5: Layer panel and Canva-style arrange/group workflow

**Files:**
- Add layer UI/policy under `ui/editor/`.
- Extend composition/design history commands and tests only where missing.

- [ ] RED tests for front/back ordering, selection, lock/hide and group-safe operations.
- [ ] Implement deterministic layer list over media/text/design graph.
- [ ] Add align/distribute and guide controls through history commands.
- [ ] Verify save/reopen and undo/redo.

### Task 6: Keyframe and curve editor

**Files:**
- Add curve/keyframe UI under `ui/editor/animation/`.
- Extend animation history/UI policy tests.

- [ ] RED tests for add/move/delete keyframe, easing change and curve copy/paste.
- [ ] Expose existing property animation evaluator with timeline-aware controls.
- [ ] Add curve editor for cubic bezier and supported easing modes.
- [ ] Verify preview/export evaluator parity.

### Task 7: Color and filter suite

**Files:**
- Extend effect registry/backend/UI and instrumentation tests.

- [ ] Add HSL controls and renderer support.
- [ ] Add RGB/curve correction pipeline.
- [ ] Add local LUT import/application with bounded validation.
- [ ] Build filter presets as parameter data, not proprietary copied presets.
- [ ] Run shader/export regression.

### Task 8: Complete time tools

**Files:**
- Extend `Media3TimeMapSupport`, renderer compiler and timing UI.

- [ ] RED reverse export test.
- [ ] Implement a renderer-safe reverse strategy without unsupported negative-speed hacks.
- [ ] RED freeze/frame-hold test and implement real frame-hold semantics.
- [ ] Build custom speed curve UI on existing TimeMap model.
- [ ] Verify audio/timeline duration behavior.

### Task 9: Complete compositing/blend workflow

- [ ] Add tested renderer support for targeted blend modes.
- [ ] Add mask feather/expand/invert UI and animated-mask support where backend can match preview/export.
- [ ] Add mirror/flip transform.
- [ ] Do not enable a blend/mask mode without renderer support.

### Task 10: Text/caption professional workflow

- [ ] Build text style inspector, custom font picker metadata, stroke/shadow/background controls.
- [ ] Build manual caption timeline editor and split/merge/restyle controls.
- [ ] Add entrance/exit/loop animation UI over normal property animations.
- [ ] Keep automatic transcription/TTS excluded.

### Task 11: Audio professional workflow

- [ ] Wire pan/normalization/EQ/compressor/limiter controls to real render pipeline.
- [ ] Add extract-audio command.
- [ ] Add voice-over recording/import workflow.
- [ ] Add beat-marker UI over local beat analysis.
- [ ] Preserve multitrack audio regressions.

### Task 12: Motion design graph/camera/parenting

- [ ] Complete persisted parent/group/null/camera graph behavior.
- [ ] Add layer parenting UI and cycle prevention.
- [ ] Add camera object/cut controls and 2.5D depth/parallax.
- [ ] Implement velocity-derived motion blur before enabling it in UI.

### Task 13: Tracking, stabilization and local reframe analysis

- [ ] Keep analyzers local/deterministic and cancellable.
- [ ] Persist generated tracking/stabilization/reframe metadata through v6-compatible fields only if already encoded; otherwise plan a complete next-schema migration.
- [ ] Wire tracking to ordinary transform/parenting commands.
- [ ] Never label metadata-only analysis as complete.

### Task 14: Templates/components and replace-media workflows

- [ ] Build local template/component browser.
- [ ] Implement replace-media while preserving timing/layout when valid.
- [ ] Add canvas/aspect/background/social presets.
- [ ] Keep templates local and user-created; do not copy Canva/CapCut bundled templates.

### Task 15: Export/package expansion and full regression

- [ ] Add GIF/image-sequence profiles where the Android pipeline supports them reliably.
- [ ] Complete editable project package round-trip and missing-asset safety.
- [ ] Run Gates A-F and Android instrumentation smoke.
- [ ] Generate APK only after mandatory gates green.
- [ ] Keep PR #13 Draft and unmerged.
