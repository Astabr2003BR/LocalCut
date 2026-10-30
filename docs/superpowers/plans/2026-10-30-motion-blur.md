# Motion Blur Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the existing `localcut.motion_blur` effect as bounded temporal GPU accumulation with preview/export parity and mobile-safe memory use.

**Architecture:** Keep the existing `EffectRegistry` contract. Add pure policies for effective sample count and temporal weights, then a LocalCut `GlEffect`/shader program that owns a bounded history-texture ring. Wire it through `Media3EffectStackFactory` after transform/compositing, then expose it through the effect browser only after render support is real.

**Tech Stack:** Kotlin, Android OpenGL ES, Jetpack Media3 1.11.1, JUnit 4, Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-10-30-motion-blur.md`

## Global Constraints

- Branch: `feature/localcut-bridge-v1`.
- PR #13 remains OPEN + DRAFT; no merge to `main`.
- Preserve `localcut.motion_blur`, `shutterAngle 0..360`, `samples 1..64`.
- No paid dependency, no schema migration, no Gaussian fallback.
- Preview and export must share the same render path.
- No fake UI control before backend support is real.
- Android/mobile-first; bound GPU memory and release all history resources.

## Review Focus

- 4K input with requested `samples=64`: effective history must stay within mobile cap.
- First frames after load/seek/configure: no stale history from an earlier stream.
- `shutterAngle=0` or `samples=1`: exact passthrough semantics.
- Constant-color input: normalized weights must not alter brightness/alpha.
- GL allocation/release errors: fail explicitly and do not leak textures/FBOs.

---

### Task 1: Pure temporal and memory policies

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/media/MotionBlurPolicy.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/media/MotionBlurPolicyTest.kt`

**Interfaces:**
- Produces: `MotionBlurPolicy.effectiveSamples(requestedSamples: Int, width: Int, height: Int): Int`
- Produces: `MotionBlurPolicy.weights(shutterAngle: Double, availableSamples: Int): List<Float>`

- [ ] Write failing tests for 720p/1080p/4K caps, requested 1/64, zero shutter, normalized weights, monotonic historical contribution and invalid dimensions/settings.
- [ ] Run `MotionBlurPolicyTest` and verify RED because the policy does not exist.
- [ ] Implement the minimal pure policy with explicit hard texture/memory caps.
- [ ] Run the policy test and verify PASS.
- [ ] Commit policy + tests.

### Task 2: Temporal GPU effect contract

**Files:**
- Create: `overrides/app/src/main/java/com/localcut/editor/media/LocalCutMotionBlurEffect.kt`
- Create: `overrides/app/src/test/java/com/localcut/editor/media/LocalCutMotionBlurShaderContractTest.kt`

**Interfaces:**
- Consumes: `MotionBlurPolicy`
- Produces: `LocalCutMotionBlurEffect(shutterAngle: Double, requestedSamples: Int) : GlEffect`
- Produces: bounded shader-program/history resource lifecycle.

- [ ] Write failing shader/resource contract tests for current/history samplers, normalized weight uniforms, reset-on-configure and explicit release.
- [ ] Run the test and verify RED.
- [ ] Implement `GlEffect` and shader program with owned history textures/FBOs; copy current frame into the history ring after rendering.
- [ ] Ensure high-precision texture allocation follows Media3 `useHdr`/working precision.
- [ ] Run focused tests and verify PASS.
- [ ] Commit effect + tests.

### Task 3: Media3 effect-stack wiring

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/media/Media3EffectStackFactory.kt`
- Modify/Create test under: `overrides/app/src/test/java/com/localcut/editor/media/Media3EffectStackFactoryTest.kt`

**Interfaces:**
- Consumes: `LocalCutMotionBlurEffect`
- Produces: render support for valid `localcut.motion_blur` effects.

- [ ] Write failing tests requiring `canRender()` to accept valid static Motion Blur, reject invalid parameters/unsupported animation, and create a `LocalCutMotionBlurEffect` with evaluated parameters.
- [ ] Verify RED.
- [ ] Add `localcut.motion_blur` to supported type IDs and instantiate the custom effect from `shutterAngle`/`samples`.
- [ ] Verify focused tests PASS.
- [ ] Run existing effect-stack/composition tests for regression.
- [ ] Commit wiring + tests.

### Task 4: UI reachability after real backend

**Files:**
- Modify: `overrides/app/src/main/java/com/localcut/editor/ui/editor/EffectBrowserUiPolicy.kt`
- Modify/Create: `overrides/app/src/test/java/com/localcut/editor/ui/editor/EffectBrowserUiPolicyTest.kt`

**Interfaces:**
- Produces: `EffectBrowserCategory.MOTION`, label `Movimento`, preset `Motion Blur` for `localcut.motion_blur`.

- [ ] Write failing tests requiring the category/preset and numeric controls for `shutterAngle` and `samples`.
- [ ] Verify RED.
- [ ] Add Motion category/preset and Portuguese parameter labels without changing existing categories.
- [ ] Verify focused UI policy tests PASS.
- [ ] Confirm edits still use the existing `SetSelectedEffects` history/autosave path.
- [ ] Commit UI + tests.

### Task 5: Regression gate and checkpoint

**Files:**
- Create: `docs/checkpoints/2026-10-30-motion-blur-live-gate.md`

**Interfaces:** none.

- [ ] Audit resource lifetime, stream reset, Blend interaction, transform ordering, Tracking compatibility and project compatibility.
- [ ] Run GitHub CI gates on the final meaningful SHA once: Bridge, Termux, Android Debug APK and Android Multitrack Export Smoke.
- [ ] Do not poll a pending SHA; continue independent audit if CI is not complete.
- [ ] Record exact tested SHA/results and remaining physical-device limitation in the checkpoint.
- [ ] Keep Motion Blur unadvertised as fully validated until Android CI evidence is green.
