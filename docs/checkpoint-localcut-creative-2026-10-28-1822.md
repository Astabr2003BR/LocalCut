# LocalCut creative checkpoint — 2026-10-28 18:22 BRT

## Fixed project rules
- Repository: `Astabr2003BR/LocalCut`
- Branch: `feature/localcut-bridge-v1`
- PR #13 must remain OPEN + DRAFT.
- Never merge into `main` without explicit user authorization.
- GitHub is source of truth.
- Preserve working import/preview/timeline/trim/split/delete/undo-redo/export/signing/overrides.
- LocalCut is the main Android editor. Termux/UserLAnd remains an optional typed heavy worker; no generic shell endpoint.

## Fixed CI anti-loop rule
- A commit SHA receives at most ONE workflow status query.
- queued/pending/in_progress means that SHA is consumed; do not query it again.
- Only inspect jobs/logs when the one permitted query reports a completed failure.
- Fix the concrete failure, create a new SHA, then only the new SHA may receive one new checkpoint.

## Modern property-animation rendering
`Clip.propertyAnimations` now travels through the real Media3 render/export path:
`Clip -> RenderPlan -> Media3CompositionBlueprint -> TransformEffectFactory / Media3SequenceOpacityResolver`.

`PropertyTransformRenderResolver` overlays modern `transform.x`, `transform.y`, `transform.scale`, `transform.rotationDeg`, and `transform.opacity` animations on the legacy transform/keyframe result. A modern property overrides only the property it defines; legacy values remain fallback for all others.

This closes the previous gap where stabilization/motion property animations could be saved in the project without reaching preview/export.

## Stabilization / edge compensation
- OpenCV global-motion corrections preserve the clip's existing base transform instead of recentering it.
- x/y use project pixels, matching `TransformEffectFactory`.
- Edge compensation mirrors the actual Android matrix order (`postTranslate -> postRotate -> postScale`, effective `S*R*T`) in Media3 NDC space.
- Safety zoom is bounded; compensation above 2x fails closed with `STABILIZATION_EDGE_COMPENSATION_TOO_LARGE`.
- UI explains excessive zoom instead of applying a destructive crop.
- Stale result guard now includes asset/fingerprint/trim/base transform/canvas.
- Signed zero remains canonicalized in production.

## Generated-media GC
A conservative Reverse-generated-media collector runs once per process after the project is loaded:
- only first-level `filesDir/remote-media` entries matching the strict `reverse-<uuid>-<uuid>.mp4` namespace are candidates;
- normal `shared-*` imports and unrelated files are untouched;
- active undo or redo blocks the entire scan;
- project-referenced files are preserved;
- recent files are protected by the grace period;
- symlinks, directories, empty files and unsafe paths fail closed;
- deletion failures are reported, not hidden.

## Reverse / Freeze
Existing completed state remains valid:
- chunked FFmpeg Reverse with bounded background job waiting, authenticated upload/download and SHA-256 verification;
- durable LocalCut-owned output, FileProvider URI, Asset inspection, stale guard and undoable replacement;
- Reverse available in the Speed context;
- Freeze 0.5s / 1s / 2s / remove uses current playhead and undo/redo.
- `FreezeFrameActionControl` selected-video guards were simplified to remove redundant Kotlin conditions without changing behavior.

## Blend modes
- `BlendModeMath` already defines/test canonical NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, ADD and SUBTRACT equations with source-over alpha.
- Rendering remains intentionally blocked for non-NORMAL blend modes. Do NOT remove the guard in `Media3CompositingEffectFactory.canRender`.
- Media3 1.11.1 public `VideoCompositorSettings` path exposes layout/alpha, not a blend equation; the standard multi-sequence Transformer path requires `DefaultVideoFrameProcessor.Factory`.
- `Media3SequenceBlendResolver` now defines the future real compositor contract: timed active blend mode, gap/outside=NORMAL, detection of compositions that require a pixel/multi-texture compositor, and used non-normal modes.
- No fake blend UI should be exposed until a real source+backdrop texture compositor exists.

Key blend contract commit: `a434c8a30a0bd02044227bb406fe775ab247de90`.

## Drawing
Existing drawing remains:
- freehand PATHs, straight-line assist, 15/45 degree snap;
- horizontal/vertical symmetry; domain already supports BOTH_AXES with dedupe;
- advanced onion skin;
- vector eraser;
- stroke stabilization;
- frame-by-frame exposure.

New brush presets automatically exposed by the existing preset UI:
- Fineliner: 2px / 100% opacity;
- Marca-texto: 28px / 32% opacity.

Brush commit: `071661bc354298003ce3204d412db4c953c94ce6`.

## Vector lasso foundation
`VectorLassoSelectionPolicy` now selects visible PATH centerlines using renderer-compatible geometry:
- same 25% short-edge base size through shared `DesignElementGeometryPolicy`;
- scale, rotation and x/y translation respected;
- path vertex inside polygon OR segment crossing polygon selects the stroke;
- only PATH elements active at the playhead are considered;
- stable result ordering by z-index then id;
- invalid/zero-area lasso fails closed.

`DesignOverlayRenderSpecCompiler` now uses the same shared base-size geometry policy to prevent renderer/lasso drift.

`DeleteDesignElementsCommand` provides atomic batch delete for future lasso UI:
- non-empty, unique, nonblank IDs;
- all requested elements must exist;
- elements referenced by reusable components block the entire operation;
- one undo restores exact original ordering;
- redo reapplies the batch.

Key lasso commits:
- RED test: `dfa47780340c2584aa499c2169cb0757d8368cfb`
- lasso geometry/policy: `80f1eaf7603cd0e4e817abcf07210ac82dd90ace`
- shared renderer geometry: `676a08f5e1e12b19abcc9d956844efb6f0f9145d`
- batch-delete RED: `bb058eaaac037c84febe3331af592a13e5b7ea76`
- batch-delete implementation: `b52c0651f95db000b17cc452b9ed35bf8945f33a`

## Concrete Android CI failure fixed this round
For SHA `ef02e607...`, Android `assembleDebug` succeeded but `compileDebugUnitTestKotlin` failed because trailing lambdas in two `TermuxMediaTrackingCoordinator` tests were being bound to the last `pollDelay` argument instead of `sourceResolver`.

Fixed by binding `sourceResolver = TermuxMediaSourceResolver { ... }` explicitly in commit `ad9b3bf1f159059996f32ff08c428f8c5520fad5`.

## CI SHAs consumed in this round — DO NOT QUERY AGAIN
- `ef02e6074a6873c8234ea895f76ec21e8475988e` — Android failure inspected and fixed.
- `ad9b3bf1f159059996f32ff08c428f8c5520fad5` — no workflows at its one query.
- `671ab4d18d3320f4746635ae5a93fdab746b85af` — no workflows at its one query.
- `071661bc354298003ce3204d412db4c953c94ce6` — queued/pending at its one query.
- `a434c8a30a0bd02044227bb406fe775ab247de90` — no workflows at its one query.
- `676a08f5e1e12b19abcc9d956844efb6f0f9145d` — workflows in progress/pending at its one query.
- `b52c0651f95db000b17cc452b9ed35bf8945f33a` — workflows queued/pending at its one query.

## Next safe blocks
1. Add lasso UI as a separate composable and integrate only through a small existing extension point; avoid whole-file `DrawStrokePad.kt` rewrites unless necessary.
2. Expose BOTH_AXES symmetry in UI when a safe patch point is available; domain/test support already exists.
3. For blend, research/implement an actual multi-texture compositor path; never fake it as a per-clip shader.
4. Continue motion-path / parenting / camera / advanced drawing in independent tested blocks.
