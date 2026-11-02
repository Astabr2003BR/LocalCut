# LocalCut checkpoint — TimeMap overflow hardening + capability refresh — 2026-10-30

Branch: `feature/localcut-bridge-v1`
PR: #13 — keep OPEN + DRAFT; never merge into `main` without explicit authorization.
Code parent before this block: `d3846f049a64ac2e631fc7a588e7cd915ccfaa4d`.
TimeMap hardening commit: `38de7d8f1b5c91f68e010d0759ce2ead008c57a2`.

## Scope

This block has two goals:

1. harden temporal arithmetic used by Freeze/TimeMap so invalid/extreme data cannot wrap signed `Long` values and bypass validation;
2. reconcile stale capability documentation with current branch code and newer checkpoints without overstating Android/device validation.

No APK/AAB generation is authorized. Commits in this block use `[skip ci]` so the Android Debug APK workflow is not intentionally triggered.

## Bug 1 — Freeze overlap validation could fail open on Long overflow

### Symptom

`TimeMap.validationIssues()` checked overlap using:

`left.startOffsetUs + left.durationUs > right.startOffsetUs`

For an extreme positive interval, signed `Long` addition can wrap negative. Example reproduced locally:

- left start = `1`
- left duration = `Long.MAX_VALUE`
- right start = `10`
- wrapped left end = `Long.MIN_VALUE`
- pre-fix `validationIssues()` returned `[]`

The invalid overlapping pair therefore passed validation.

### Root cause

The overlap predicate computed an interval endpoint with unchecked signed addition.

### Correction

After sorting by non-negative `startOffsetUs`, overlap is now checked without addition:

`left.durationUs > right.startOffsetUs - left.startOffsetUs`

The subtraction is safe because the sorted right start is never below the left start. Adjacent intervals remain valid because equality is not considered overlap.

## Bug 2 — Freeze budget for ramp duration could wrap negative

### Symptom

The speed-ramp branch in `TimeMapEvaluator.presentationDurationUs(...)` used:

`timeMap.freezeSegments.sumOf { it.durationUs }`

A sufficiently large sum can wrap negative. The wrapped value was then used as the binary-search high bound and the evaluator failed with the unrelated error:

`presentationOffsetUs must be non-negative`

### Correction

Freeze budget now uses the evaluator's saturating `safeAdd(...)` fold, matching the existing no-ramp duration path:

`freezeSegments.fold(0L) { total, freeze -> safeAdd(total, freeze.durationUs) }`

This prevents a positive Freeze budget from becoming a negative presentation time.

## RED/GREEN evidence

Executed locally with the installed Kotlin/JDK toolchain; no Android package was built.

RED 1:
- current overlap logic reproduced the signed overflow;
- assertion failed with exit code 1;
- observed wrapped endpoint: `-9223372036854775808`;
- observed issues: `[]`.

RED 2:
- current `sumOf` freeze budget reproduced the negative high bound;
- observed exception: `IllegalArgumentException: presentationOffsetUs must be non-negative`;
- exit code 1.

GREEN:
- patched `TimeMap` + `TimeMapEvaluator` compiled with `kotlinc`;
- result: `GREEN: 8 temporal overflow/regression checks passed`.

Covered cases:
- ordinary non-overlap;
- adjacent freezes remain valid;
- normal overlap rejected;
- overflowed-end overlap rejected;
- duplicate start rejected;
- ordinary Freeze duration unchanged;
- ordinary speed-ramp duration unchanged;
- huge positive Freeze budget saturates instead of wrapping negative.

Repository test coverage was extended in `TimeMapEvaluatorTest` for both overflow regressions.

## Capability/documentation reconciliation

The original capability matrix dated 2026-10-23 is being refreshed against current branch code and newer checkpoints.

Statements superseded by newer evidence:

- Reverse is no longer only model/planner work: generated-media FFmpeg round trip, trim-safe replacement, stale-result protection, UI and history exist.
- Freeze is no longer only model/planner work: Media3 planner, playhead action UI, history and export compatibility guards exist.
- Blend is no longer rejected by the current backend: a real multi-texture compositor and mobile Blend UI exist; final Android gate remains pending.
- Motion Blur is no longer metadata-only: a real temporal GPU effect and mobile UI exist; Android/device visual validation remains pending.
- Object Tracking has a real VIDEO workflow with ROI, Termux/OpenCV analysis, apply coordinator and undo/redo; unsupported combinations remain fail-closed.
- Stabilization has global-motion analysis and the modern property-animation render path; physical Android validation remains pending.
- Parenting is no longer graph-only: planar parenting/camera transform resolution and mobile group/null parent controls exist; camera cuts/3D plane workflow remain incomplete.
- Both-axis drawing symmetry is now selectable in `DrawStrokePad` through the `Ambos` button; the older UI caveat is superseded.

Historical checkpoints are not treated as newer than current code. When a historical statement conflicts with a later dated checkpoint or verified current source, the later evidence wins.

## Explicit supersession notes

- The `BLEND MODES` section in `docs/checkpoint-localcut-tracking-reverse-2026-10-28.md` is historical and superseded by `docs/checkpoints/2026-10-30-blend-compositor-live-gate.md`.
- The old `BOTH_AXES is not yet selectable` caveat in `docs/checkpoints/2026-10-28-stabilization-removal-symmetry.md` is superseded by current `DrawStrokePad` and has been corrected in that addendum.
- The pending list in `docs/roadmaps/2026-10-23-localcut-capcut-alight-canva-full-matrix.md` must not continue listing Reverse, Freeze, temporal Motion Blur or advanced Blend as unimplemented.

## Validation limits

- Full Gradle/Android build was not executed in this block.
- No APK/AAB was generated.
- Motion Blur physical GPU quality on Android remains unproven.
- Tracking/Stabilization physical device behavior remains a separate gate.
- Blend final Android gate remains pending per its checkpoint.
- The overflow fix has executed Kotlin logic evidence but not a fresh full Android compilation.

## Next audit target

Continue from current code, not the stale roadmap. A candidate is `Extract audio from video`, but it must first be audited against the packaged base source and existing Termux/FFmpeg paths before any new implementation is added. Do not create duplicate infrastructure if a base implementation already exists.
