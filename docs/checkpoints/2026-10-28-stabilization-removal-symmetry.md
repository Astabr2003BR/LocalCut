# LocalCut checkpoint addendum — stabilization removal + both-axis symmetry — 2026-10-28

This addendum is newer than `docs/checkpoint-localcut-tracking-reverse-2026-10-28.md` and must be read together with it.

## Fixed project rules
- Repo: `Astabr2003BR/LocalCut`
- Branch: `feature/localcut-bridge-v1`
- PR #13 remains OPEN + DRAFT.
- NEVER merge into `main` without explicit user authorization.
- Keep the one-CI-query-per-SHA anti-loop rule.

## Stabilization removal
`StabilizationApplyPolicy` can now identify generated stabilization channels using the internal keyframe prefixes emitted by `GlobalMotionStabilizationPlanner`:
- `transform.x` → `stabilize-x-`
- `transform.y` → `stabilize-y-`
- `transform.rotationDeg` → `stabilize-rotation-`
- `transform.scale` → `stabilize-scale-`

Safety behavior:
- `hasGeneratedStabilization(...)` requires the complete four-channel generated signature.
- `removeGeneratedStabilization(...)` removes only generated channels when the complete signature is present.
- manual transform animations are not reported as generated stabilization and are never removed by this control.
- partial generated signatures fail closed and are left untouched.
- Animation panel shows `Remover estabilização` only when the full generated signature exists.
- removal goes through the existing `onSetPropertyAnimations` / editor command path, so undo/redo remains available.

Key commits:
- policy tests: `7ed8f785aa3b1f6dab08aca6fa90dd46f782f119`
- policy: `061011bba47368fbf6e8357f18616fbcea8337c9`
- UI: `6c7369e8549d3056198b05b046647625e7cf66ed`

Local evidence:
- `PASS selective stabilization removal`

## Media3 stabilization edge correction — corrected matrix model
The initial edge formula was audited against Android/Media3 matrix semantics before being finalized.

Current model matches the real renderer:
- `TransformEffectFactory` calls `postTranslate → postRotate → postScale`.
- Android Matrix post operations produce `M = S * R * T`.
- Media3 `MatrixTransformation` works in normalized device coordinates (NDC) `[-1,1]` for x/y.
- domain translation is converted from project pixels to NDC before applying the Matrix.
- edge coverage is therefore computed in NDC and in the exact `S*R*T` order.

For pre-scale translation fraction `t`, pure translation needs reciprocal coverage `1/(1-|t|)`, not `1+|t|`.
A translation reaching the NDC boundary cannot be recovered by zoom and is converted to the high-level fail-closed error `STABILIZATION_EDGE_COMPENSATION_TOO_LARGE`.

Key corrected commits:
- NDC/order tests: `5d0deea4950085e9a0a7c1ee41f3b65fd252252c`
- NDC/order implementation: `abc7b1fa50aeb40a47b633e8a478681bb10a82ca`
- planner normalized failure: `abdbea074cded43c651049ea5ee22251823852f3`
- updated planner expected zoom: `237c48fb8cd058d343c85b19792904e3d9f1f87b`
- updated adapter expected zoom: `b5830225e3fc2f6ea1cc155cff7c48abab4979ea`

Local evidence:
- `PASS Media3 NDC S*R*T edge coverage`

## Both-axis drawing symmetry
`StrokeSymmetryMode.BOTH_AXES` and domain support are implemented.

Behavior:
- normal case expands one gesture into up to four editable PATH strokes:
  1. original;
  2. vertical mirror;
  3. horizontal mirror;
  4. vertical + horizontal mirror.
- identical full strokes are deduplicated in stable order.
- a stroke exactly on one symmetry axis therefore does not create duplicate overlapping PATHs.
- canvas/point validation remains fail closed.
- the existing `DrawStrokePad` preview and commit paths already call `StrokeSymmetryPolicy.expand(...)`, so the new mode is supported by those paths once selected.

Key commits:
- tests: `6c028e2f9488c7c329b7b9ae90f778c7bf171d8e`
- implementation: `d8306e089208207a8dc622e5de305d3c487cb1be`

Local evidence:
- `PASS both-axis symmetry dedupe`

### UI caveat
The `DrawStrokePad` currently exposes buttons only for OFF / Vertical / Horizontal. `BOTH_AXES` is not yet selectable from the UI.
The GitHub connector only supports whole-file replacement for writes, and `DrawStrokePad.kt` is a large Compose file. Do not risk a full-file rewrite solely for this one button unless the complete current file has been safely reconstructed/verified. Backend support is ready; UI exposure is a small pending integration task.

## CI anti-loop state
Consumed SHAs from the main checkpoint remain consumed and must not be queried again, including:
- `60264c07...`
- `f396abbb...`
- `fa11cdb...` (its one query returned no workflow runs yet, so it is still consumed by rule).

This addendum commit is a new SHA and may receive exactly one CI status query.
