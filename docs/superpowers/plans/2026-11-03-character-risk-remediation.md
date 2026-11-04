# Character Animation Risk Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the known flat-image TALK and audio-cue risks, then prove the path through Android Bridge, preview and export without adding unrelated character features.

**Architecture:** Extend the existing animation plan with an optional effect patch so TALK can drive a localized mouth-warp effect on the body clip when no separate mouth clip exists. Keep waveform decoding behind the existing asynchronous `WaveformRepository`; add a pure waveform-to-cue planner rather than blocking the synchronous Bridge executor. Render the mouth warp with a dedicated dynamic Media3 GL effect and cover the full path with unit and emulator smoke tests.

**Tech Stack:** Kotlin/Android, Media3/OpenGL ES, JUnit, Android instrumentation, TypeScript controller tests.

**Spec:** `docs/superpowers/specs/2026-11-03-character-risk-remediation.md`

## Global Constraints

- Work only on `feature/localcut-bridge-v1`; PR #14 stays OPEN + DRAFT.
- Never merge to `main`, force-push, deploy production, alter signing/keystore, or publish a release.
- Preserve existing rigged-mouth behavior and all validated editor/Bridge/export behavior.
- Audio media decoding must not run synchronously in `BridgeCommandExecutor` or Android main-thread command dispatch.
- Every mutation remains revision-gated, lock-safe, atomic and undoable.

## Review Focus

- `mouthRegion` missing/invalid/out-of-range: reject invalid data and preserve the existing TALK fallback.
- Existing unrelated effects on the body clip: preserve order/content while upserting only the LocalCut mouth effect.
- Existing mouth-warp animation outside a new TALK window: preserve keyframes outside the new action interval.
- Silent/flat waveform and one-bucket/very-short durations: return valid bounded cues or empty without divide-by-zero/overlap.
- Renderer at openness 0/1 and texture edges: no coordinate overflow, invalid GL state or unsupported-effect regression.

---

### Task 1: Flat-image TALK planning and atomic history

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/domain/animation/CharacterSceneAnimationPlanner.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/domain/history/ApplyAnimationPlanCommand.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/domain/animation/CharacterSceneAnimationPlannerTest.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/domain/history/ApplyAnimationPlanCommandTest.kt`

**Interfaces:**
- Produces `MouthRegion`, `ClipEffectPatch`, and optional effect patches on `CharacterAnimationPlan`.
- `ApplyAnimationPlanCommand` consumes effect patches and atomically merges/restores `Clip.effects` with property animations.

- [ ] Write RED planner tests for flat TALK using `mouthRegion`, rigged-mouth precedence, region validation and fallback cue generation.
- [ ] Write RED history tests proving unrelated effects survive, only the mouth effect is upserted, lock failure is pre-mutation, and Undo/Redo restores exact state.
- [ ] Run Android unit CI and verify the new tests fail for the intended missing interfaces/behavior.
- [ ] Implement the minimal plan/effect-patch model and atomic history merge/restore.
- [ ] Re-run Android unit/build CI and verify the task tests and full unit suite pass.

### Task 2: Dynamic mouth-warp effect renderer

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/effects/EffectRegistry.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/Media3EffectStackFactory.kt`
- Create: `overrides/app/src/main/java/com/localcut/editor/media/LocalCutMouthWarpEffect.kt`
- Test: `overrides/app/src/test/java/com/localcut/editor/effects/EffectRegistryTest.kt`
- Create/Test: `overrides/app/src/test/java/com/localcut/editor/media/LocalCutMouthWarpEffectTest.kt`

**Interfaces:**
- Consumes Task 1 mouth effect type/parameter names.
- Produces a Media3 `GlEffect` that evaluates the effect's `openness` animation at presentation time.

- [ ] Write RED registry/render-policy/shader tests for parameter bounds, only-mouth-effect animated support, zero-openness identity and coordinate clamping.
- [ ] Run Android unit CI and verify intended RED failures.
- [ ] Register `localcut.character.mouth_warp`, special-case animated render support only for this effect, and implement the dynamic GL effect.
- [ ] Re-run Android unit/build CI and verify GREEN.

### Task 3: Bridge payload support

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeAnimationPayloadParser.kt`
- Modify: `overrides/app/src/main/java/com/localcut/editor/bridge/agent/BridgeAnimationCommandHandler.kt`
- Test: existing Bridge animation parser/command tests (locate exact files before edit)
- Modify/Test only if needed: `bridge/controller/test/mcp.test.ts`

**Interfaces:**
- Consumes Task 1 `MouthRegion`.
- Produces validated optional `mouthRegion` parsing and response metadata indicating flat-mouth use.

- [ ] Write RED Android Bridge tests for valid region, malformed region, rigged-mouth precedence and revision safety.
- [ ] Implement strict payload parsing without changing authentication/routing.
- [ ] Re-run Android and Bridge Controller CI; require zero regressions.

### Task 4: Audio-energy lip cue planner

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/domain/animation/WaveformLipSyncCuePlanner.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/domain/animation/WaveformLipSyncCuePlannerTest.kt`

**Interfaces:**
- Consumes `WaveformData` and action duration.
- Produces `List<LipSyncCue>` sorted, non-overlapping and bounded to the action duration.

- [ ] Write RED tests for speech-like peaks, silence, one bucket, short duration, normalization, determinism and bounds.
- [ ] Implement the pure planner with a stable noise floor and adjacent-active-bucket merge.
- [ ] Run Android unit/build CI and verify GREEN.
- [ ] Audit that no synchronous media decoding was added to Bridge/main-thread code.

### Task 5: Automated Bridge → preview → export risk gate

**Files:**
- Create or extend an Android instrumentation smoke under `overrides/app/src/androidTest/java/com/localcut/editor/media/`.
- Modify workflow only if the existing multitrack smoke does not discover the new instrumentation test.

**Interfaces:**
- Consumes Tasks 1–3 renderer and Bridge path.
- Produces automated evidence that a flat image TALK plan reaches render/export.

- [ ] Write the instrumentation test for image project + `animate_character` TALK/mouthRegion + project verification + preview + export.
- [ ] Run `Android Debug APK` and `Android Multitrack Export Smoke` on the same code HEAD.
- [ ] Require Bridge Controller and Termux Agent Engine CI green on the same code HEAD.
- [ ] Inspect failure logs rather than retry-looping; patch only proven code failures.

### Task 6: Operational documentation and checkpoint

**Files:**
- Modify: `bridge/chatgpt-plugin/skills/localcut-control/SKILL.md`
- Add/update PR #14 checkpoint comment.

**Interfaces:**
- Documents when to use `mouthRegion`, separate mouth clip precedence, waveform-derived cues versus fallback, and the physical-device evidence boundary.

- [ ] Update plugin guidance only after code gates are green.
- [ ] Compare documentation-only HEAD to the validated code SHA to prove no motor change.
- [ ] Record exact CI run numbers, counts, remaining physical-device limitation, HEAD and no-merge/no-deploy state in PR #14.
