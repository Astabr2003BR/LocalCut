# Blend Compositor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render LocalCut `BlendMode` values with real multi-layer pixel blending, identically in preview and export, without exposing fake controls.

**Architecture:** Keep per-clip effects responsible only for single-input processing. Resolve the active blend mode for each visual input at composition time and apply the blend equation only where multiple decoded textures are composed. Preserve the existing Media3 path for `NORMAL`; non-normal modes remain fail-closed until the multi-input GL compositor is fully wired into both CompositionPlayer and Transformer.

**Tech Stack:** Kotlin, Android/OpenGL ES, AndroidX Media3 1.11.1, JUnit 4.

**Spec:** Existing `BlendMode`/`CompositingSettings` domain model plus LocalCut project lock and current Media3 render architecture.

## Global Constraints

- Branch: `feature/localcut-bridge-v1`; PR #13 remains OPEN + DRAFT.
- No merge to `main` without explicit authorization.
- No paid dependencies and no proprietary code/assets/UI copying.
- Preserve preview/export parity, signing, timeline, import, undo/redo and current render behavior.
- Keep non-normal blend modes unavailable/fail-closed until a real renderer exists.
- No schema migration is required: `BlendMode` already persists inside `CompositingSettings`.

## Review Focus

- Alpha 0/1 and partially transparent source/backdrop must remain bounded and deterministic.
- Gaps/inactive sequences must resolve to `NORMAL`, never inherit a previous clip's mode.
- Layer/input order must match the existing `Media3VisualLayerOrder` inversion.
- Single-layer projects must not pay custom-compositor cost or change pixels.
- Preview and export must use the same compositor implementation and equations.

---

### Task 1: Blend equation contract

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/media/BlendModeMath.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/media/BlendModeMathTest.kt`

**Interfaces:**
- Produces: `BlendModeMath.blend(backdrop: Rgba, source: Rgba, mode: BlendMode): Rgba`.

- [ ] Write RED tests for NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, ADD, SUBTRACT and alpha edge cases.
- [ ] Implement bounded source-over blend math with explicit source/backdrop semantics.
- [ ] Run unit/regression gate.

### Task 2: Time-resolved blend mode per visual input

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/media/Media3SequenceBlendModeResolver.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/media/Media3SequenceBlendModeResolverTest.kt`

**Interfaces:**
- Produces: `blendModeAt(sequence, presentationTimeUs): BlendMode` and `requiresCustomBlend(sequences): Boolean`.

- [ ] RED tests for media/gap/out-of-range/time boundaries and non-normal detection.
- [ ] Implement resolver without changing rendering support gates.
- [ ] Run unit/regression gate.

### Task 3: Multi-input GL compositor backend

**Files:**
- Create focused compositor/shader classes under `overrides/app/src/main/java/com/localcut/editor/media/`.
- Modify preview/export factory wiring only after API contract is proven by compile tests.

**Interfaces:**
- Consumes Task 1 equations/spec and Task 2 mode resolver.
- Produces one compositor path shared by CompositionPlayer and Transformer.

- [ ] RED compile/behavior tests for factory wiring and layer order.
- [ ] Implement texture composition with explicit premultiplied-alpha handling.
- [ ] Preserve default Media3 compositor for projects requiring only NORMAL.
- [ ] Verify preview/export parity and fail closed for unsupported HDR/color paths.

### Task 4: Support policy and UI reachability

**Files:**
- Modify `Media3CompositionSupportPolicy.kt`, compositing UI policy/panel, and reachability tests only after Task 3 is green.

- [ ] RED tests showing non-normal modes become renderable only when custom compositor is selected.
- [ ] Expose blend selector for visual clips.
- [ ] Verify undo/redo, project persistence, preview, export and existing crop/mask/chroma regressions.
- [ ] Update checkpoint and keep PR #13 draft.
