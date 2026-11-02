# LocalCut Creative Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand LocalCut with original professional editing/design primitives that cover the relevant capability classes of CapCut, Alight Motion and Canva while preserving the existing Android editor and Bridge.

**Architecture:** Extend the serializable domain model first, then layer deterministic evaluators/validators, then wire preview/export/history/UI/Bridge to those primitives. Optional analysis/AI always produces ordinary LocalCut metadata/assets and never becomes a dependency of the local editor core.

**Tech Stack:** Kotlin, Android, Media3, existing LocalCut project/session/history/Bridge architecture, deterministic JSON persistence.

**Spec:** `docs/superpowers/specs/2026-10-23-localcut-creative-suite-design.md`

## Global Constraints

- Repository: `Astabr2003BR/LocalCut`; branch: `feature/localcut-bridge-v1`; PR #13 remains OPEN + DRAFT + NO MERGE.
- Preserve `overrides/` and all existing validated import/preview/timeline/trim/split/delete/undo/redo/export behavior.
- Original implementation only; no proprietary APK/code/assets/UI copying.
- Android/mobile-first and offline-first.
- Preview/export must use the same temporal/compositing semantics.
- Persisted model changes must remain backward compatible with project schema v1-v3.
- TDD: every task starts with a failing focused test and ends with regression tests.

## Review Focus

1. Legacy v1-v3 project decoding must produce the same current visuals/timing after schema migration.
2. Non-finite/invalid effect, mask, animation or time-map values must be rejected before save/render.
3. Parent/group cycles must never enter saved project state.
4. Preview degradation may lower quality but must not alter timing/layer order/effect order compared with export.
5. Large media projects must store metadata rather than materialized frame data and must remain cancellable.

---

### Task 1: Creative Suite capability contract and schema-v4 model foundation

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/model/Animation.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/model/Compositing.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/model/Effect.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/model/TimeMap.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/domain/model/Clip.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/domain/model/Project.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/BuildContract.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/domain/model/CreativeSuiteModelTest.kt`

**Interfaces:**
- Produces stable serializable types for animation, masks/blend, effects, hierarchy metadata and time mapping.
- All new persisted fields default to legacy behavior.

- [ ] Write failing model tests for defaults, validation helpers and deterministic ordering.
- [ ] Run focused model tests and verify RED.
- [ ] Add minimal data types/defaults and increment schema to v4.
- [ ] Run focused tests and verify GREEN.
- [ ] Run timeline/domain regression tests.

### Task 2: Project JSON v4 round-trip and v1-v3 migration

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/data/project/ProjectJson.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/domain/timeline/TimelineValidator.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/data/project/CreativeSuiteProjectJsonTest.kt`

**Interfaces:**
- Consumes Task 1 model.
- Produces v4 JSON persistence and deterministic legacy defaults.

- [ ] Write RED tests for v4 round-trip plus v1/v2/v3 migration.
- [ ] Add bounded encode/decode for new fields and legacy defaults.
- [ ] Add validation for graph references, masks/effects/time maps and finite numbers.
- [ ] Run RED→GREEN and existing project store/corruption tests.

### Task 3: Property animation and easing evaluator

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/animation/PropertyAnimationEvaluator.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/domain/animation/PropertyAnimationEvaluatorTest.kt`

**Interfaces:**
- Consumes Task 1 animation types.
- Produces deterministic evaluation for scalar/point/color properties and easing modes.

- [ ] RED tests for hold, linear, cubic Bézier, ease presets, steps, seeded random and bounds.
- [ ] Implement evaluator with deterministic seeded behavior.
- [ ] Add legacy TransformKeyframe adapter preserving existing output.
- [ ] Run animation/timeline regression suite.

### Task 4: Compositing, masks, blend and chroma evaluator

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/render/CompositingEvaluator.kt`
- Modify relevant render-plan/compiler model after inspection.
- Test: `overrides/app/src/test/java/com/localcut/editor/render/CompositingEvaluatorTest.kt`

**Interfaces:**
- Consumes Task 1 compositing types and Task 3 animated properties.
- Produces render-ready compositing state shared by preview/export.

- [ ] RED tests for normal/blend ordering, mask inversion/feather/expansion and chroma parameters.
- [ ] Implement pure evaluator and wire it into shared render planning.
- [ ] Verify unsupported hardware path degrades explicitly, never silently changes timing.
- [ ] Run preview/export parity regression.

### Task 5: Layer graph, groups, parenting, nulls and camera/2.5D

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/composition/LayerGraph.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/composition/WorldTransformResolver.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/domain/composition/LayerGraphTest.kt`

**Interfaces:**
- Produces cycle-safe parenting/group/camera graph and world-transform resolution.

- [ ] RED tests for parent inheritance, null nodes, nested groups, cycle rejection and camera cuts.
- [ ] Implement graph validator/world transform resolver.
- [ ] Add z-depth/parallax projection metadata.
- [ ] Wire shared evaluated transform into preview/export render planning.
- [ ] Regression test layer ordering and legacy projects.

### Task 6: Effect registry and core color/motion effect stack

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/effects/EffectRegistry.kt`
- Create effect evaluators under `overrides/app/src/main/java/com/localcut/editor/effects/`
- Test: `overrides/app/src/test/java/com/localcut/editor/effects/EffectRegistryTest.kt`

**Interfaces:**
- Consumes Task 1 `EffectInstance` and Task 3 animation evaluator.
- Produces ordered validated effect parameters.

- [ ] RED tests for registry lookup, stack order, unknown effect preservation, parameter bounds.
- [ ] Implement first-party IDs for brightness/contrast/exposure/saturation/temp/tint, blur/sharpen/glow/vignette/grain, RGB split, mosaic, ripple/displacement and motion blur metadata.
- [ ] Wire render-supported effects through shared compiler; unsupported effects fail controlled or preserve metadata according to spec.
- [ ] Add copy/paste effect-stack history commands.

### Task 7: Speed/time remap, reverse and freeze

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/time/TimeMapEvaluator.kt`
- Modify timeline duration/source mapping helpers.
- Test: `overrides/app/src/test/java/com/localcut/editor/domain/time/TimeMapEvaluatorTest.kt`

**Interfaces:**
- Consumes Task 1 `TimeMap`.
- Produces project-time → source-time mapping.

- [ ] RED tests for constant speed, ramp, reverse, freeze and invalid maps.
- [ ] Implement deterministic mapping and duration calculation.
- [ ] Wire preview/export compiler to the same mapper.
- [ ] Regression test trim/split/transition behavior around remapped clips.

### Task 8: Text, captions, custom fonts, vector shapes and Canva-style design tools

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/domain/model/TextOverlay.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/model/DesignElement.kt`
- Create history commands for caption split/merge/restyle and element/group operations.
- Test: `overrides/app/src/test/java/com/localcut/editor/domain/model/DesignElementTest.kt`

**Interfaces:**
- Produces timed design elements, shapes/paths, caption blocks, alignment/snapping metadata and reusable component instances.

- [ ] RED tests for text style migration, caption split/merge timing, vector bounds and group operations.
- [ ] Implement local font reference metadata and fallback policy.
- [ ] Implement shape/path model, gradients/strokes, guides/snapping/distribution.
- [ ] Add reusable local component/template model with exposed properties.
- [ ] Wire supported elements into preview/export.

### Task 9: Audio professional toolchain

**Files:**
- Add audio processing models/evaluators under `overrides/app/src/main/java/com/localcut/editor/audio/`
- Modify shared audio render compiler.
- Test corresponding unit/instrumentation suites.

**Interfaces:**
- Produces EQ, compressor/limiter, pan, normalization, beat markers and extraction metadata.

- [ ] RED tests for audio parameter bounds and deterministic chain order.
- [ ] Implement local normalization/EQ/compressor/limiter/pan primitives.
- [ ] Add extract-audio and beat-marker commands.
- [ ] Preserve mute/solo/fades/waveform behavior.
- [ ] Run audio export regression.

### Task 10: Tracking, stabilization and auto-reframe metadata pipeline

**Files:**
- Create analysis contracts under `overrides/app/src/main/java/com/localcut/editor/analysis/`.
- Test pure metadata and cancellation semantics.

**Interfaces:**
- Produces serializable tracking/stabilization/reframe data independent from the analyzer implementation.

- [ ] RED tests for bounded tracking samples, transform application, cancellation and missing analyzer.
- [ ] Implement local metadata contracts and no-provider graceful behavior.
- [ ] Wire tracking output to normal animation/parenting primitives.
- [ ] Add optional analyzer hooks without making network a core dependency.

### Task 11: Anime Mode scene recipes and reusable motion presets

**Files:**
- Create recipe/preset model and applicator under `overrides/app/src/main/java/com/localcut/editor/recipes/`.
- Test preset expansion into ordinary LocalCut commands.

**Interfaces:**
- Consumes Tasks 3-8 primitives.
- Produces reversible commands for parallax, camera pan/zoom/shake, impact frames, speed lines, eye/mouth helpers and motion presets.

- [ ] RED tests that recipes expand deterministically and undo cleanly.
- [ ] Implement recipes as data + command expansion, not hard-coded render exceptions.
- [ ] Verify preview/export parity using representative anime scene fixtures.

### Task 12: Bridge/mobile exposure of new safe capabilities

**Files:**
- Modify Bridge protocol/executor/mobile handoff only after local editor primitives are validated.
- Add focused Bridge tests for revision preconditions and safe argument bounds.

**Interfaces:**
- Exposes explicit LocalCut operations only; no generic shell/device commands.

- [ ] Add read/state capability extensions for effects/masks/groups when needed.
- [ ] Add explicit edit commands for supported new primitives.
- [ ] Preserve local confirmation for mobile handoff destructive operations.
- [ ] Run Bridge controller and Android handoff regressions.

### Task 13: Project packages, export formats and final regression/audit

**Files:**
- Add project package/export contracts and tests after renderer features are stable.
- Update docs/checkpoint/capability matrix.

**Interfaces:**
- Produces package import/export plus GIF/image-sequence/alpha-capable profiles where platform codec support exists.

- [ ] RED tests for package manifest integrity/path traversal rejection and missing asset handling.
- [ ] Implement bounded package export/import.
- [ ] Add supported additional render profiles without breaking MP4 H.264/HEVC.
- [ ] Run Gates A-F, source audit and Android smoke.
- [ ] Update capability matrix statuses only from executed evidence.
- [ ] Keep PR #13 Draft and do not merge.
