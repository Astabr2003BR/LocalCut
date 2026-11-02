# Lasso and Motion Path UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the existing vector lasso/delete-batch foundation and motion-path planner through mobile-first controls without rewriting EditorScreen or DrawStrokePad.

**Architecture:** Add two isolated Compose controls mounted by `EditorBottomToolRail`. `VectorLassoControl` uses a mini-canvas mapped to project coordinates, calls `VectorLassoSelectionPolicy`, and dispatches one `DeleteDesignElementsCommand` for the current selection. `MotionPathActionControl` captures a mini-canvas gesture, maps it to project transform pixels, uses `MotionPathPlanner`, and replaces only `transform.x`/`transform.y` animations for the selected visual clip while preserving every other property animation.

**Tech Stack:** Kotlin, Jetpack Compose, existing LocalCut `LocalCutProcessRuntime`, `EditorAction`, property-animation pipeline, JUnit.

**Spec:** `docs/checkpoint-localcut-creative-2026-10-28-1822.md`

## Global Constraints

- Branch: `feature/localcut-bridge-v1`.
- PR #13 remains OPEN + DRAFT; no merge to `main`.
- Preserve `overrides/` and current editor features.
- No generic shell, AI dependency, or paid service.
- CI anti-loop: at most one status query per SHA/checkpoint.
- Lasso and motion-path changes must go through existing history/command infrastructure.

## Review Focus

- Empty/invalid gesture must not mutate the project.
- Lasso must use project dimensions and current playhead, not screen-space assumptions.
- Batch delete must remain atomic if a selected element is protected by a reusable component.
- Motion path must preserve non-X/Y property animations.
- Selection/clip changes between capture and apply must fail closed.

---

### Task 1: Vector Lasso UI policy and control

**Files:**
- Create: `overrides/app/src/test/java/com/localcut/editor/ui/editor/VectorLassoUiPolicyTest.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/ui/editor/VectorLassoUiPolicy.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/ui/editor/VectorLassoControl.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorBottomToolRail.kt`

**Interfaces:**
- Consumes: `VectorLassoSelectionPolicy.select(...)`, `DeleteDesignElementsCommand(ids)`.
- Produces: a DRAW-context control that captures polygon points and deletes the selected PATHs in one undoable command.

- [ ] Write failing mapping/gesture tests.
- [ ] Implement the minimal screen→project mapping policy.
- [ ] Implement `VectorLassoControl` with mini-canvas, clear, select, and delete actions.
- [ ] Mount only when `activeTool == DRAW`.
- [ ] Verify no project mutation for <3 lasso points or empty selection.

### Task 2: Motion Path UI policy and control

**Files:**
- Create: `overrides/app/src/test/java/com/localcut/editor/ui/editor/MotionPathUiPolicyTest.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/ui/editor/MotionPathUiPolicy.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/ui/editor/MotionPathActionControl.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorBottomToolRail.kt`

**Interfaces:**
- Consumes: `MotionPathPlanner.plan(points)`, selected clip transform, existing property animations.
- Produces: an ANIMATION-context control for VIDEO/IMAGE that captures a path and applies only X/Y property animations through the existing command/history path.

- [ ] Write failing mapping/merge tests.
- [ ] Implement gesture→timed motion-point mapping with bounded sampling.
- [ ] Implement X/Y merge preserving unrelated property animations.
- [ ] Implement stale selection/clip guard in the control.
- [ ] Mount only for visual clip selections under ANIMATION.

### Task 3: Regression checkpoint

- [ ] Run local/pure tests where possible.
- [ ] Commit each independent block.
- [ ] Query CI once for the new functional HEAD.
- [ ] Record the continuation checkpoint without polling the same SHA.
