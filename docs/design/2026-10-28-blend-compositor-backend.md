# LocalCut blend compositor backend — 2026-10-28

## Goal
Implement real NORMAL / MULTIPLY / SCREEN / OVERLAY / DARKEN / LIGHTEN / ADD / SUBTRACT with preview/export parity. Do not expose blend UI until the same equations are used by both paths.

## Current Media3 version
LocalCut currently uses AndroidX Media3 `1.11.1`.

## Current LocalCut render paths
- Preview: `CompositionPlayer` + `MultipleInputVideoGraph.Factory()`.
- Export: `Transformer.Builder` → internal `VideoSampleExporter` → internal `MultipleInputVideoGraph.Factory(videoFrameProcessorFactory)` for multi-input compositions.
- `KeyframeVideoCompositorSettings` currently controls per-layer alpha only.
- `Media3CompositingEffectFactory` is a single-input shader path and cannot see the backdrop texture, so it cannot implement layer blend equations.

## Media3 source audit
AndroidX Media3's `MultipleInputVideoGraph.initialize()` constructs `DefaultVideoCompositor` internally from `VideoCompositorSettings`. The public factory accepts a `VideoFrameProcessor.Factory`, not a custom `VideoCompositor.Factory`.

The Media3 source contains a TODO to allow injecting an arbitrary `VideoCompositor.Factory` in the future.

Preview can replace the whole `VideoGraph.Factory` through `CompositionPlayer.Builder.setVideoGraphFactory(...)`, but `Transformer.Builder` does not expose an equivalent public setter. `VideoSampleExporter` selects `MultipleInputVideoGraph.Factory` internally for multi-input export.

Therefore implementing a custom compositor only for preview would violate LocalCut's preview/export parity rule.

## Canonical blend contract
`BlendModeMath` is the CPU/reference implementation. It defines:
- source = upper layer;
- backdrop = already-composited lower content;
- RGB values use straight-alpha normalized channels in `[0,1]`;
- blend equation is applied first;
- Porter-Duff source-over performs alpha composition;
- `SUBTRACT = max(0, source - backdrop)`, matching FFmpeg top-minus-bottom semantics.

Equations:
- NORMAL: `Cs`
- MULTIPLY: `Cb * Cs`
- SCREEN: `Cb + Cs - Cb*Cs`
- OVERLAY: multiply for backdrop <= 0.5, screen otherwise
- DARKEN: `min(Cb, Cs)`
- LIGHTEN: `max(Cb, Cs)`
- ADD: `clamp(Cb + Cs)`
- SUBTRACT: `clamp(Cs - Cb)`

## Safety lock
Until a shared compositor is implemented:
- `Media3CompositionSupportPolicy` must reject every non-NORMAL blend mode;
- no blend-mode buttons should be exposed;
- no preview-only implementation is acceptable.

## Viable future routes
### Route A — shared LocalCut GL compositor pipeline
Build a LocalCut-owned multi-input OpenGL compositor and use the same shader contract for preview and export. Export would require replacing/bypassing the Transformer's hard-coded multi-input graph path or owning the encode pipeline around the compositor output.

Pros: full control, exact preview/export parity, future masks/motion blur/advanced blend support.
Cons: substantial implementation and testing surface.

### Route B — upstream-compatible Media3 fork/patch
Maintain a narrowly scoped Media3 patch that allows injecting a `VideoCompositor.Factory`, then use the same LocalCut custom compositor in CompositionPlayer and Transformer.

Pros: preserves much of Media3's timing/encoding stack.
Cons: maintenance burden across Media3 updates; fork must retain Apache-2.0 notices and be audited each upgrade.

### Route C — separate export renderer
Keep custom GL preview and implement an independent export renderer with exactly the same blend equations/shader fixtures.

Pros: does not fork Media3 internals.
Cons: larger parity/test burden and likely more custom codec/timestamp work.

## Recommended direction
Do not fork Media3 just for the first blend feature. Continue building the pure blend/shader contract while other end-to-end features advance. Revisit a shared custom compositor when masks, true layer blend, temporal motion blur and other multi-input effects justify the dedicated backend together.

## Evidence / tests
- `BlendModeMathTest` covers all eight modes and alpha source-over cases.
- `Media3CompositingSupportPolicyTest` iterates every non-NORMAL mode and requires explicit `UNSUPPORTED_COMPOSITING` until the shared backend exists.
- local Kotlin harness: `PASS blend mode math`.
