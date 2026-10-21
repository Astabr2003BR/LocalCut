# LocalCut Keyframe Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Task 9 by turning LocalCut's existing keyframe engine into a usable mobile keyframe editor with correct preview/export timing and a validated Android APK.

**Architecture:** Preserve the existing `TransformKeyframe`/`TransformValue`, `SetKeyframesCommand`, `TimelineOps`, serialization and interpolation infrastructure. Add a pure UI policy for deterministic add/update/delete/navigation, correct Media3 sequence timestamps to clip-local time, add dynamic opacity parity, then expose controls in the inspector and visual markers in the timeline. Preview and export continue to share `ProjectCompositionCompiler`.

**Tech Stack:** Kotlin, Jetpack Compose, Android Media3 1.11.x, JUnit, GitHub Actions Android 35 emulator.

**Spec:** Existing LocalCut professional-editor roadmap / Task 9 keyframes, constrained by current main architecture and LocalCut project lock.

## Global Constraints

- Android/mobile-first; no AI dependency.
- Preserve existing import, preview, timeline, trim, split, delete, undo/redo, export and signing behavior.
- Preserve existing `overrides/` workflow and project serialization compatibility.
- No production behavior change without a failing test first.
- Keyframe time is clip-local; UI receives project playhead time and converts deterministically.
- Preview/export use the same temporal/transform model.
- Do not claim physical-device validation.

## Review Focus

- Later clips in a sequence must evaluate keyframes from clip-local time, not composition-global time.
- Keyframe opacity must combine predictably with clip-level opacity without breaking static transforms.
- Replacing a keyframe at the same offset must not create duplicates.
- Navigation at first/last keyframe and playhead outside the clip must be deterministic and safe.
- Timeline markers must remain aligned after existing trim/split keyframe remapping.

---

### Task 1: Keyframe editor policy

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/ui/editor/KeyframeEditorPolicy.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/ui/editor/KeyframeEditorPolicyTest.kt`

**Interfaces:**
- Consumes: `Clip`, `TransformKeyframe`, `TransformValue`, existing interpolation semantics.
- Produces: pure add/update/delete/navigation helpers used by Compose UI.

- [ ] **Step 1: Write failing tests** for project-playhead → local-offset conversion, insert/replace sorted behavior, delete exact keyframe, previous/next project timestamps, and interpolated draft value.
- [ ] **Step 2: Run CI/unit test and verify RED** because `KeyframeEditorPolicy` does not exist.
- [ ] **Step 3: Implement minimal pure policy** with no Android dependencies.
- [ ] **Step 4: Run full unit/build gate and verify GREEN.**
- [ ] **Step 5: Commit.**

### Task 2: Media3 keyframe timing and opacity parity

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/TransformEffectFactory.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/ProjectCompositionCompiler.kt`
- Create/modify tests under `overrides/app/src/test/java/com/localcut/editor/media/`
- Extend Android smoke under `overrides/app/src/androidTest/java/com/localcut/editor/media/`

**Interfaces:**
- Consumes: cumulative `presentationStartUs` already calculated for fade-to-black, `TransformInterpolator.valueAt`.
- Produces: transform/opacity effects that evaluate keyframes in clip-local time for both preview and export.

- [ ] **Step 1: Write failing tests** proving a second clip at global 1.25 s evaluates a local 0.25 s keyframe and proving effective opacity follows keyframe opacity × clip opacity.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Pass `presentationStartUs` into transform effects and evaluate local time; add timestamp-dependent alpha effect using current Media3-supported effect API.**
- [ ] **Step 4: Verify unit/build GREEN.**
- [ ] **Step 5: Extend real Android export smoke** to assert a transformed/opacity-keyframed second visual clip changes over time in actual MP4.
- [ ] **Step 6: Verify Android 35 smoke GREEN and commit.**

### Task 3: Mobile keyframe UI and timeline markers

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/components/InspectorPane.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorScreen.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/components/TimelinePane.kt`
- Add focused UI-policy/model tests as needed.

**Interfaces:**
- Consumes: `KeyframeEditorPolicy`, `EditorAction.SetSelectedKeyframes`, `EditorViewModel.seekToUs`.
- Produces: user-facing keyframe create/update/delete/previous/next controls and deterministic timeline markers.

- [ ] **Step 1: Write failing pure tests** for marker position ratios and editor action planning where possible.
- [ ] **Step 2: Verify RED.**
- [ ] **Step 3: Add inspector keyframe section** for non-audio clips: X/Y, scale, rotation, opacity, Add/Update at cursor, Previous, Next, Delete.
- [ ] **Step 4: Route all mutations through `SetSelectedKeyframes`** so existing undo/redo/autosave remain authoritative.
- [ ] **Step 5: Draw keyframe markers inside visual clip boxes** from local `offsetUs / durationUs`; markers are display-only and survive existing trim/split remapping.
- [ ] **Step 6: Run full build/regression GREEN and commit.**

### Task 4: Final regression, checkpoint and APK

**Files:**
- Create: `docs/checkpoints/2026-10-21-keyframes.md`

**Interfaces:**
- Consumes: all Task 9 work.
- Produces: validated Task 9 branch/PR and downloadable APK artifact.

- [ ] **Step 1: Run Android Debug APK gate** including all unit tests, overlap-safe timeline checks and legacy regression harnesses.
- [ ] **Step 2: Run Android 35 multitrack/export smoke** including text, fade and keyframe case.
- [ ] **Step 3: Audit regressions** for import, preview, timeline, trim/split/delete, undo/redo and MP4 export.
- [ ] **Step 4: Write checkpoint with exact runs/SHAs and known limitations.**
- [ ] **Step 5: Open/update Task 9 PR.**
- [ ] **Step 6: Download the final successful debug APK artifact and expose the APK to the user.**
