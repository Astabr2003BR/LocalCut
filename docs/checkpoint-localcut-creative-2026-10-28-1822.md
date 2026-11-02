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
- Stale result guard includes asset/fingerprint/trim/base transform/canvas.
- Signed zero is canonicalized in production.
- `StabilizationApplyPolicy.hasAnyGeneratedStabilization()` now detects even a partial generated stabilization signature. Motion path uses this to fail closed instead of leaving stale stabilize rotation/scale combined with new X/Y motion.

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
- `BlendModeMath` defines/test canonical NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, ADD and SUBTRACT equations with source-over alpha.
- Rendering remains intentionally blocked for non-NORMAL blend modes. Do NOT remove the guard in `Media3CompositingEffectFactory.canRender`.
- Media3 1.11.1 public `VideoCompositorSettings` path exposes layout/alpha, not a blend equation; the standard multi-sequence Transformer path requires `DefaultVideoFrameProcessor.Factory`.
- `Media3SequenceBlendResolver` defines the future real compositor contract: timed active blend mode, gap/outside=NORMAL, detection of compositions that require a pixel/multi-texture compositor, and used non-normal modes.
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

## Vector lasso — UI live
The lasso is no longer only a domain foundation.

Domain:
- `DesignElementGeometryPolicy` shares the same 25% short-edge base geometry with the renderer;
- scale, rotation and x/y translation are respected;
- vertex containment or segment/polygon crossing selects a PATH;
- only PATHs active at the playhead are considered;
- invalid/zero-area lassos fail closed.

UI:
- `VectorLassoControl` is mounted when `EditorToolId.DRAW` is active; `EditorScreen.kt` and `DrawStrokePad.kt` were not rewritten;
- mini-canvas renders current transformed PATH centerlines;
- lasso gesture maps safely from mini-canvas coordinates to project pixels and is bounded to 128 policy points;
- selected strokes are highlighted;
- project/playhead changes recalculate the selected IDs from the stored project-space polygon;
- delete recomputes selection immediately before mutation and uses one `DeleteDesignElementsCommand`, preserving atomic undo/redo;
- ← → ↑ ↓ move the current lasso selection by 40 project pixels using one `MoveDesignElementsCommand` per nudge;
- batch move validates unique/existing IDs, finite/nonzero delta and transform overflow, and undo/redo restores exact transforms.

Key lasso commits:
- domain lasso: `80f1eaf7603cd0e4e817abcf07210ac82dd90ace`
- shared renderer geometry: `676a08f5e1e12b19abcc9d956844efb6f0f9145d`
- batch delete: `b52c0651f95db000b17cc452b9ed35bf8945f33a`
- UI mapping policy: `c721fb98848cbd7c7099c7a4a2dd9609a07795a0`
- lasso control initial: `830853ad24c139a5780526deb0ad1449dd586f18`
- current-state recompute: `ff5fefe89bafd0cdbfbc80d3bf2932c49dfea5b5`
- atomic batch move: `e9c320a4a3df6b92f5e1c96dfe3b3fcadfeaac27`
- move UI: `b63d70400b80f67a4354c0beb68dd2ec42c721a1`

## Motion path — UI live
Domain:
- `MotionPathPlanner` supports 2..256 timed points, strict increasing offsets, finite project-pixel X/Y, easing validation and a 10-minute max offset;
- planner emits the existing `PropertyAnimation` channels `transform.x` + `transform.y`, so preview/export use the same modern animation pipeline.

UI/policy:
- `MotionPathActionControl` is mounted under `EditorToolId.ANIMATION` for VIDEO/IMAGE selections;
- mini-canvas captures a relative trajectory; the first point preserves the clip's current x/y and the last point maps to clip end;
- mobile capture is bounded to 64 sampled points;
- apply replaces only transform.x/y and preserves unrelated property animations;
- generated motion path can be removed without deleting manual X/Y unless both generated signatures are present;
- selection/clip checks are inside `runCatching`, so stale state does not throw through the Compose click handler;
- any generated stabilization channel, including a partial signature, blocks motion-path merge with `MOTION_PATH_CONFLICTS_WITH_STABILIZATION`;
- UI exposes Linear, Ease In, Ease Out and Suave (`EASE_IN_OUT`) presets through the native LocalCut `Easing` model;
- apply/remove use `SetClipPropertyAnimationsCommand`, preserving undo/redo.

Key motion commits:
- planner: `035c5b2741097f3437d82ec0aa879430c63f2e4c`
- UI policy: `2196f34c47a82e99e26c700c2ffe751212b9acaf`
- control initial: `b9e06c7479fdda91334c0985cfa81797ea29c0b9`
- stale/error hardening: `e95a89330d05eddc1208f011bc618052c102aeb8`
- partial-stabilization guard: `70e02ad88f576955e22f781da2042a6053d526ba`
- easing UI: `d5a2ef0fb716620b46d2a1530c37a2176d9e302f`
- easing regression test: `600c15cdb39ff50e02b978a9438dbfe4f316abfb`

## Concrete Android CI failure fixed earlier
For SHA `ef02e607...`, Android `assembleDebug` succeeded but `compileDebugUnitTestKotlin` failed because trailing lambdas in two `TermuxMediaTrackingCoordinator` tests were being bound to the last `pollDelay` argument instead of `sourceResolver`.

Fixed by binding `sourceResolver = TermuxMediaSourceResolver { ... }` explicitly in commit `ad9b3bf1f159059996f32ff08c428f8c5520fad5`.

## CI SHAs consumed — DO NOT QUERY AGAIN
Earlier consumed SHAs remain consumed, including:
- `ef02e6074a6873c8234ea895f76ec21e8475988e` — Android failure inspected and fixed.
- `ad9b3bf1f159059996f32ff08c428f8c5520fad5` — no workflows at its one query.
- `671ab4d18d3320f4746635ae5a93fdab746b85af` — no workflows at its one query.
- `071661bc354298003ce3204d412db4c953c94ce6` — queued/pending at its one query.
- `a434c8a30a0bd02044227bb406fe775ab247de90` — no workflows at its one query.
- `676a08f5e1e12b19abcc9d956844efb6f0f9145d` — workflows in progress/pending at its one query.
- `b52c0651f95db000b17cc452b9ed35bf8945f33a` — workflows queued/pending at its one query.

Newly consumed UI SHAs:
- `155cab45a304b4d55d5b3e0a8a5fe46fe89cffdb` — Android/Multitrack pending; Bridge/Termux in progress at its one query.
- `46bcc3a8422323af3af216d672e74ff11f0dc2a4` — no workflows at its one query.
- `ff5fefe89bafd0cdbfbc80d3bf2932c49dfea5b5` — no workflows at its one query.
- `b63d70400b80f67a4354c0beb68dd2ec42c721a1` — Android/Multitrack pending; Bridge/Termux in progress at its one query.
- `600c15cdb39ff50e02b978a9438dbfe4f316abfb` — no workflows at its one query.

Never re-query any SHA listed above.

## Next safe blocks
1. Add persistent lasso-selection semantics before adding scale/rotate/group operations, so a nudge cannot unexpectedly change membership merely because geometry crossed the lasso edge.
2. Add motion-path duration/range controls or per-segment point editing; keep using PropertyAnimation, never a parallel animation system.
3. Expose BOTH_AXES symmetry in UI only through a safe small patch point; domain/test support already exists.
4. For blend, implement/research an actual multi-texture compositor path; never fake blend as a per-clip shader.
5. Continue parenting/camera/advanced drawing in independent tested blocks.
