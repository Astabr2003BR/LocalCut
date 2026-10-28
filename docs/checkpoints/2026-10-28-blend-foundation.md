# LocalCut checkpoint addendum — blend foundation — 2026-10-28

Read together with the main 2026-10-28 checkpoint and the stabilization/symmetry addendum.

## Fixed project rules
- repo `Astabr2003BR/LocalCut`
- branch `feature/localcut-bridge-v1`
- PR #13 remains OPEN + DRAFT
- never merge into `main` without explicit authorization
- one CI status query per SHA; never poll the same SHA

## Media3 blend audit
LocalCut uses AndroidX Media3 `1.11.1`.

Current paths:
- preview: `CompositionPlayer` with `MultipleInputVideoGraph.Factory()`;
- export: `Transformer` → internal `VideoSampleExporter` → internal `MultipleInputVideoGraph.Factory(videoFrameProcessorFactory)` for multi-input video;
- `KeyframeVideoCompositorSettings` controls alpha/layout but not RGB blend equations;
- `Media3CompositingEffectFactory` is a single-input shader and cannot sample the already-composited backdrop.

AndroidX Media source confirms `MultipleInputVideoGraph.initialize()` directly creates `DefaultVideoCompositor` from `VideoCompositorSettings`. The source contains a TODO to allow an arbitrary `VideoCompositor.Factory` in the future.

`CompositionPlayer.Builder` can replace the whole `VideoGraph.Factory`, but `Transformer.Builder` does not expose the equivalent export hook. Therefore a custom preview-only compositor would violate LocalCut preview/export parity.

## Canonical blend math
Added CPU/reference `BlendModeMath` for the model's existing modes:
- NORMAL
- MULTIPLY
- SCREEN
- OVERLAY
- DARKEN
- LIGHTEN
- ADD
- SUBTRACT

Contract:
- source = top layer;
- backdrop = already-composited lower content;
- normalized straight-alpha RGBA in `[0,1]`;
- blend equation first, then Porter-Duff source-over;
- SUBTRACT uses `max(0, source - backdrop)`, matching FFmpeg top-minus-bottom convention.

Key commits:
- tests: `7e7bd16b9e13bfba4437ea2ac74b6dec51f11557`
- reference math: `ae97fdbb51d4d894cdd9a55d0a494d45e0889c36`
- fail-closed test expanded to every non-NORMAL mode: `82f1a4495e0d10da205b89814baa0c24e905003a`
- design/backend audit: `cf2facf511c29b3624b487b6075b1176cc6f9311`

Local evidence:
- `PASS blend mode math`

## Safety lock
Until a shared preview/export compositor exists:
- every non-NORMAL blend mode must remain rejected as `UNSUPPORTED_COMPOSITING`;
- do not expose blend buttons;
- do not implement preview-only blend behavior.

## Future backend direction
Preferred long-term direction is a LocalCut-owned shared GL compositor when several multi-input features justify it together (true blend modes, more advanced masks, temporal effects/motion blur, etc.). A narrowly scoped Media3 fork/patch is an alternative but should not be introduced only for one blend feature because it adds upgrade/maintenance burden.
