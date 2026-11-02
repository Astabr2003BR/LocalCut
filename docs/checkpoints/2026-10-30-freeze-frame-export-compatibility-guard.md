# LocalCut checkpoint — Freeze Frame export compatibility guard

Date: 2026-10-30
Parent checkpoint: `26a3dd91e2ce04fff45d2450dadaaad71168c9f7`
Branch: `feature/localcut-bridge-v1`
PR: #13 (must remain OPEN + DRAFT)

## Symptom

The editor allowed creating a Freeze Frame on a video clip that already had `fadeInUs` or `fadeOutUs`, while the real Media3 Freeze backend explicitly rejects Freeze items with either audio fade enabled.

That mismatch could create a project state that looked valid in the editor but was rejected by Media3 composition/export preparation.

## Root cause

`FreezeFrameActionControl` gated Freeze using existing freeze state, logical reverse, speed ramps and playhead bounds, but it did not include the Media3 backend restriction for clip audio fades.

`Media3FreezeFramePlanner.canRender(...)` rejects `fadeInUs != 0L || fadeOutUs != 0L` because Freeze is expanded into multiple Media3 pieces and the current whole-clip audio envelope is not safely preserved across those pieces.

## Correction

- Added `FreezeFrameActionPolicy` as the single UI-side compatibility policy for creating a Freeze.
- The policy rejects invalid time maps, existing Freeze, logical Reverse, speed ramps, audio fades, invalid clip duration and an out-of-bounds playhead.
- `FreezeFrameActionControl` uses the policy when rendering button availability.
- The click handler re-reads current clip/playhead state and re-validates the same policy before dispatching `SetClipTimeMapCommand`, preventing stale-state application.
- The UI gives a specific recovery message for the audio-fade case instead of a generic unavailable state.
- Added `FreezeFrameActionPolicyTest` covering valid creation, fade-in/fade-out rejection, Reverse, speed ramp, existing Freeze, invalid TimeMap, playhead bounds and recovery text.

## Evidence in this session

### RED — isolated Kotlin reproduction

The pre-fix UI predicate was mirrored in a small Kotlin harness. With `fadeInUs=500000`, the predicate still allowed Freeze and the assertion failed with exit code 1, reproducing the compatibility mismatch.

### GREEN — isolated Kotlin policy verification

The corrected policy was compiled and executed with `kotlinc`; 8 compatibility checks passed, including fade-in, fade-out, Reverse, speed ramp and playhead bounds.

## Validation limitation

Full repository Gradle/Android CI was intentionally not triggered in this checkpoint because PR #13's `Android Debug APK` workflow executes `assembleDebug` and uploads an APK. The active project lock says not to generate APK/AAB without explicit authorization.

The containing commit uses GitHub's supported `[skip ci]` instruction so `push`/`pull_request` workflows do not generate an APK for this change. This means the change has logic-level executed evidence and source audit, but not a fresh full Android compile in this checkpoint.

## Regression / preservation

- No change to `main`.
- No merge.
- No APK/AAB generation requested.
- No dependency changes.
- No signing changes.
- No change to `overrides/` architecture beyond the targeted UI policy/control/test files.
- Existing Media3 Freeze planner and export semantics are preserved.
- Reverse pipeline is untouched.
- Motion Blur checkpoint remains preserved.

## Remaining limitation

This checkpoint prevents new unsupported Freeze + audio-fade combinations from the editor. It does not add true global audio-envelope support across Media3 Freeze pieces, and it does not rewrite already persisted projects that may already contain that unsupported combination.
