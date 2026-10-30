# Persistent Lasso Transform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make vector-lasso membership persistent and add atomic group-style scale/rotate actions for selected PATH elements.

**Architecture:** Capture selected element IDs once when the lasso closes, then keep those IDs until clear/new lasso/delete. Add a pure pivot policy based on renderer-compatible PATH geometry and a single batch transform command that scales/rotates every selected element around that pivot with one undo/redo entry.

**Tech Stack:** Kotlin, Jetpack Compose, existing LocalCut EditorCommand/history system, JUnit, DesignElementGeometryPolicy.

**Spec:** `docs/superpowers/specs/2026-10-28-persistent-lasso-transform-design.md`

## Global Constraints
- Repository: `Astabr2003BR/LocalCut`.
- Branch: `feature/localcut-bridge-v1`.
- PR #13 remains OPEN + DRAFT; no merge to `main`.
- Preserve `overrides/` and all validated editor functions.
- No fake grouping via `ReusableComponent`.
- One CI status query maximum per commit SHA.

## Review Focus
- Selection must not change when selected geometry moves outside the original lasso.
- Missing/deleted IDs must not mutate unrelated elements.
- Scale/rotate must preserve relative group geometry around a stable pivot.
- Overflow/non-finite/zero-or-negative scale must fail before mutation.
- Undo/redo must restore exact original transforms in original element order.

---

### Task 1: Persistent Selection Policy

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/ui/editor/VectorLassoPersistentSelectionPolicy.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/ui/editor/VectorLassoPersistentSelectionPolicyTest.kt`

**Interfaces:**
- Produces: `capture(ids: List<String>): List<String>` and `existing(selectedIds: List<String>, elements: List<DesignElement>): List<String>`.

- [ ] Write failing tests for stable ID order, duplicate rejection, and pruning missing IDs.
- [ ] Verify RED through CI/build evidence when available.
- [ ] Implement minimal policy.
- [ ] Keep semantics independent from playhead and polygon after capture.
- [ ] Commit.

### Task 2: Group Transform Domain

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/design/DesignSelectionTransformPolicy.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/history/TransformDesignElementsCommand.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/domain/design/DesignSelectionTransformPolicyTest.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/domain/history/TransformDesignElementsCommandTest.kt`

**Interfaces:**
- Produces: `DesignSelectionTransformPolicy.pathBoundsCenterPx(...)`.
- Produces: `TransformDesignElementsCommand(elementIds, pivotX, pivotY, scaleFactor, rotationDeltaDeg)`.

- [ ] Write failing pivot and batch-transform tests.
- [ ] Verify RED through CI/build evidence when available.
- [ ] Implement renderer-compatible PATH bounds pivot.
- [ ] Implement atomic scale/rotate command with overflow validation and exact undo/redo.
- [ ] Commit.

### Task 3: Mobile Lasso Controls

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/VectorLassoControl.kt`

**Interfaces:**
- Consumes persistent selection policy and batch transform command.

- [ ] Capture IDs at lasso completion instead of retaining polygon as the source of truth.
- [ ] Make move/delete operate on captured IDs after current-project existence validation.
- [ ] Add `Menor`, `Maior`, `↺15°`, `↻15°` controls.
- [ ] Preserve one-command undo/redo per action.
- [ ] Keep EditorScreen/DrawStrokePad untouched.
- [ ] Commit.

### Task 4: Regression and Checkpoint

**Files:**
- Modify: `docs/checkpoint-localcut-creative-2026-10-28-1822.md`

- [ ] Audit branch HEAD and PR #13 draft/open state.
- [ ] Query CI once for the final functional SHA only.
- [ ] If completed failure exists, inspect that failure; otherwise do not poll.
- [ ] Record persistent selection + scale/rotate state and grouping deferral in checkpoint.
- [ ] Commit checkpoint update.
