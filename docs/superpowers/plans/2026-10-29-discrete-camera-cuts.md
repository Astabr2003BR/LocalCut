# Discrete Camera Cuts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render LocalCut camera cuts as exact, non-interpolated camera switches in preview/export without physically splitting clips or disturbing speed ramps, Freeze, audio, or modern PropertyAnimation.

**Architecture:** Keep one media item per logical clip. `CompositionRenderResolver` will produce a reversible baseline composition container plus a time-indexed container timeline for camera cuts. The Media3 matrix effect will select the active container at the current presentation timestamp, evaluate clip/keyframe/PropertyAnimation in local space, and reapply the selected camera container. `rotationX/rotationY` remain fail-closed until a real 3D plane renderer exists.

**Tech Stack:** Kotlin, Android Media3 `MatrixTransformation`, existing LocalCut composition graph and PropertyAnimation pipeline.

**Spec:** `docs/checkpoints/2026-10-29-parenting-camera-modern-animation.md`

## Global Constraints

- PR #13 stays OPEN + DRAFT; no merge to `main`.
- Play Store/publication is out of scope.
- Do not generate APK/AAB without explicit authorization.
- CI is bounded: one status query per meaningful SHA; no polling.
- Preserve existing `overrides/`, timeline, preview, export, Freeze, Reverse, speed ramp, audio, undo/redo and project compatibility.
- Camera cuts are discrete: never interpolate between cameras.
- `rotationX/rotationY` stay rejected until a 3D plane renderer exists.

## Review Focus

- Cut exactly on a timestamp switches immediately, with no interpolation frame.
- A clip starting after earlier cuts uses the camera active at the clip start as its reversible baseline.
- Camera cuts do not change PropertyAnimation local values; only the world/camera container changes.
- Multiple cuts remain strictly ordered and invalid camera references remain rejected by `LayerGraph`.
- Freeze/speed-ramp media items keep one logical clip clock; no physical clip splitting is introduced.

---

### Task 1: Timed composition container contract

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/CompositionRenderResolver.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/media/CompositionRenderResolverTest.kt`

**Interfaces:**
- Produces: `CompositionTransformTimeline`, `TimedCompositionTransformContainer`, `containerAt(presentationTimeUs)`.

- [ ] Write failing tests for exact camera switch, clip-start baseline and planar-camera rejection.
- [ ] Verify RED.
- [ ] Implement the minimal timed-container resolver.
- [ ] Verify GREEN.
- [ ] Commit.

### Task 2: Dynamic container evaluation

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/PropertyTransformRenderResolver.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/TransformEffectFactory.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/media/PropertyTransformRenderResolverTest.kt`

**Interfaces:**
- Consumes: static baseline `compositionContainer` and optional `CompositionTransformTimeline`.
- Produces: world transform using the active camera container at each presentation timestamp.

- [ ] Write failing test proving 999999us uses camera A and 1000000us uses camera B with no interpolation.
- [ ] Verify RED.
- [ ] Apply/remove baseline container in local space and reapply active timed container.
- [ ] Verify GREEN.
- [ ] Commit.

### Task 3: RenderPlan/Media3 propagation

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/RenderPlan.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/ProjectRenderPlanCompiler.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/Media3CompositionBlueprint.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/ProjectCompositionCompiler.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/Media3SequenceOpacityResolver.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/media/Media3PropertyAnimationBlueprintTest.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/media/ProjectCompositionRenderPlanIntegrationTest.kt`

**Interfaces:**
- Propagates: `compositionContainerTimeline` unchanged from domain resolution through Media3 effect construction.

- [ ] Write failing propagation tests.
- [ ] Verify RED.
- [ ] Thread the timeline through RenderPlan and Media3 blueprint.
- [ ] Verify GREEN/regression.
- [ ] Commit.

### Task 4: Checkpoint

**Files:**
- Create/update: `docs/checkpoints/2026-10-29-discrete-camera-cuts.md`

- [ ] Audit final diff for unrelated changes.
- [ ] Make one CI status query for the final functional SHA only.
- [ ] Record exact validated/pending state without polling.
