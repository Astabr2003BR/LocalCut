# LocalCut Freeze / Frame Hold Backend v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Render `TimeMap.freezeSegments` correctly for safe video clips in LocalCut preview/export while preserving logical clip timing and refusing unsafe combinations.

**Architecture:** Keep the persisted model and Media3 blueprint unchanged. Add a Media3-only planner that decomposes a logical clip into moving and video-only hold pieces, then teach `ProjectCompositionCompiler` to emit those pieces while keeping keyframes/transitions on the original clip clock.

**Tech Stack:** Kotlin, Android, Jetpack Media3 Transformer 1.11.1, JUnit4, Android instrumentation.

**Spec:** `docs/superpowers/specs/2026-10-25-localcut-freeze-frame-backend-v1.md`

## Global Constraints
- Repo: `Astabr2003BR/LocalCut`.
- Branch: `feature/localcut-bridge-v1`.
- PR #13 remains OPEN + DRAFT; no merge.
- Preserve `overrides/` and stable APK signing.
- Reverse remains unsupported in this block.
- No large UI/redesign work.
- One consolidated CI/build review after the functional block.

## Review Focus
- Freeze at source start/end must never create zero-length clipping.
- Multiple non-overlapping freezes must preserve exact logical presentation duration.
- Hold pieces must contain video only; no duplicated/stretched audio.
- Transform keyframes must continue on the logical clip clock across internal pieces.
- Unsupported reverse/ramp/audio-fade combinations must fail explicitly rather than degrade.

---

### Task 1: Freeze piece planner

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/media/Media3FreezeFramePlanner.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/media/Media3FreezeFramePlannerTest.kt`

**Interfaces:**
- Produces `Media3FreezePiece` and `Media3FreezeFramePlanner.canRender/plan`.
- Consumes `Media3BlueprintItem.Media`, `TimeMapEvaluator`, `AssetType`, `Media3OutputTracks`.

- [x] Write failing unit contract first.
- [x] Verify RED in Android Debug #1049: unresolved `Media3FreezeFramePlanner`.
- [ ] Implement planner with moving/hold pieces and exact duration checks.
- [ ] Keep hold speed finite/positive and hold output `VIDEO_ONLY`.

### Task 2: Support policy and compiler integration

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/Media3CompositionSupportPolicy.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/ProjectCompositionCompiler.kt`

**Interfaces:**
- Consumes planner from Task 1.
- Produces a Media3 `Composition` that internally emits all freeze pieces.

- [ ] Accept only safe freeze combinations in support policy; reverse remains rejected.
- [ ] Expand visual items into planner pieces.
- [ ] Use piece source clipping and positive speed.
- [ ] Apply `setRemoveAudio(true)` for hold pieces.
- [ ] Preserve logical clip start for transform keyframes and transition envelopes.

### Task 3: Real export smoke

**Files:**
- Create: `overrides/app/src/androidTest/java/com/localcut/editor/media/FreezeFrameExportSmokeTest.kt`
- Modify: `.github/workflows/android-multitrack-smoke.yml`

**Interfaces:**
- Uses `ProjectCompositionCompiler` and real `Transformer` H.264 export.

- [ ] Build a three-color source video in the test.
- [ ] Export a project with one freeze segment.
- [ ] Verify output duration within tolerance.
- [ ] Verify representative frames before, during and after the hold.
- [ ] Add smoke class to the consolidated emulator workflow.

### Task 4: Regression gate and checkpoint

- [ ] Static review for clipping bounds, overflow, null/state regressions, audio track removal and logical timing.
- [ ] Run/inspect the single consolidated Android Debug build/unit gate on final HEAD.
- [ ] Run/inspect the consolidated Android instrumentation smoke on final HEAD.
- [ ] If red, inspect only the real failing job, patch root cause and rerun.
- [ ] If green, verify PR #13 remains Draft/unmerged and report implemented/compiled/tested separately from phone validation.
