# LocalCut Creative Suite — Design Spec

Date: 2026-10-23

## Intent

Expand LocalCut from a capable local Android editor into a broad mobile creative editor with original capability parity targets informed by CapCut, Alight Motion and the Canva video/design editor. This is not a clone. LocalCut must not copy proprietary code, APKs, private APIs, bundled assets, exact UI, trade dress, presets or branding from those products.

The user explicitly requested autonomous execution and large-scope parity. For this spec, the existing “other APK” reference is resolved to Alight Motion because the LocalCut roadmap already names CapCut + Alight Motion as the reference pair.

## Product boundary

LocalCut remains an Android-first video/motion/design editor. Canva capabilities are included when they map to a visual/video editing surface: layers, groups, element timing, canvas composition, reusable templates, text, captions, animations, background removal/cutout, transitions, design guides and export. Canva Docs, spreadsheets, websites, collaborative office workflows and unrelated SaaS surfaces are out of scope.

## Goals

1. Keep the existing timeline/import/preview/export/Bridge behavior working.
2. Make project state deterministic, serializable, migratable and recoverable.
3. Make every edit an undoable command or a deterministic model mutation through the existing session architecture.
4. Share the same temporal/compositing interpretation between preview and export.
5. Build reusable primitives instead of one-off feature buttons.
6. Keep the editor core offline-first. Network/AI features are optional adapters and must fail closed without breaking normal editing.
7. Preserve Android mobile performance by storing metadata/keyframes/effect parameters, not rendered frames, in project state.
8. Allow the ChatGPT Bridge to expose only explicit LocalCut capabilities; never generic device control.

## Non-goals

- No proprietary APK embedding, decompilation, code copying, asset extraction or exact UI cloning.
- No dependency on CapCut, Alight Motion or Canva runtimes.
- No licensed stock-media catalog mirroring.
- No credit system for core local editing.
- No mandatory cloud account for editing, project save, preview or export.

## Architecture

### A. Property animation engine

Introduce typed animatable properties instead of adding separate keyframe structures for every feature. Properties support:

- scalar, point, color and boolean/discrete values;
- ordered keyframes with stable IDs;
- interpolation: HOLD, LINEAR, CUBIC_BEZIER;
- easing presets: EASE_IN, EASE_OUT, EASE_IN_OUT;
- advanced timing modes represented deterministically: BOUNCE, ELASTIC, CYCLIC, RANDOM_SEEDED and STEPS;
- copy/pasteable curve descriptors;
- per-property evaluation at any project timestamp.

Legacy `TransformKeyframe` remains readable and migrates into the new property animation representation without changing the visual result.

### B. Layer/composition graph

Tracks/clips remain the media-timeline backbone. Add a composition graph for relationships and design behavior:

- parent/child layer relation;
- null/controller nodes;
- groups and nested compositions;
- z-depth metadata;
- camera nodes and camera cuts;
- lock/visibility inherited without changing existing track semantics;
- world-transform resolution with cycle detection.

The graph references stable LocalCut IDs; it never stores Android view references.

### C. Compositing and masks

Visual clips/layers gain serializable compositing state:

- blend mode: NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, ADD, SUBTRACT;
- crop/fit/fill metadata;
- geometric masks: rectangle, ellipse, linear/gradient and rounded rectangle;
- freeform path mask representation;
- invert, feather and expansion/erosion;
- animated mask properties;
- chroma key parameters: key color, tolerance/threshold, softness, spill suppression and edge feather;
- manual cutout mask as a path/bitmap reference when produced by an optional tool.

### D. Effect stack

Each eligible visual/audio object may own an ordered effect stack. Effects use an original LocalCut registry with stable IDs and typed parameter maps. Initial engine categories:

- brightness/contrast/exposure/saturation/temperature/tint;
- HSL and curves metadata;
- blur/sharpen/glow/vignette/noise/grain;
- RGB split, mosaic/halftone, ripple/warp/displacement;
- motion blur derived from transform deltas;
- move-along-path metadata;
- text progress/counter/timecode effects;
- audio EQ/compressor/limiter/pan/normalization metadata.

Unknown future effect IDs must survive project round-trip as opaque data when possible, rather than corrupting the project.

### E. Time mapping

Media clips gain a serializable time map:

- constant speed;
- speed ramps with ordered points;
- reverse;
- freeze/frame-hold segments;
- future interpolation metadata.

Timeline duration and source-time mapping are calculated from the time map. Invalid/non-monotonic maps are rejected before save/render.

### F. Text, captions and design elements

Expand text/design objects with:

- custom font reference metadata;
- letter spacing, line spacing, background, rounded box and multiple shadows/strokes where supported;
- entrance/exit/loop animation descriptors;
- per-character animation metadata;
- caption blocks as ordinary timed text objects with source transcript metadata;
- caption split/merge/restyle operations;
- shape/vector layers with fill, gradient, stroke and editable path;
- alignment guides, snapping and distribution metadata;
- reusable local templates/components with exposed properties.

### G. Audio

Retain current multitrack audio and add:

- extraction from video;
- local loudness normalization;
- EQ, compressor/limiter and stereo pan metadata/render path;
- beat markers and optional beat analysis;
- voice-over recording/import path;
- optional noise reduction/vocal isolation/voice enhancement adapters.

### H. Analysis / optional AI

These are adapters above the deterministic core:

- background removal;
- captions/transcription;
- text-to-speech;
- tracking targets;
- stabilization analysis;
- auto-reframe;
- beat-aware edit suggestions;
- optional generative image/video connectors.

Generated output must be converted into normal LocalCut assets, keyframes, masks, captions, tracking data or commands so projects remain editable without the provider.

## Current capability target categories

The Creative Suite release train covers:

1. Timeline editing modes: snapping, markers, range, ripple/insert/overwrite.
2. Per-property keyframes and curve editor.
3. Masks, blend modes, crop and chroma key.
4. Parenting, groups, nulls, cameras and 2.5D depth.
5. Effects/color stack and motion blur.
6. Speed ramp, reverse and freeze.
7. Text, captions, fonts, vector shapes and paths.
8. Audio processing and beat tools.
9. Tracking, stabilization and reframing.
10. Canva-style reusable elements/templates and design alignment tools.
11. Anime Mode recipes built only from ordinary primitives.
12. Project packages and additional export formats.
13. Optional AI/analysis adapters.

## Compatibility and schema

- Current project schema is v3.
- Creative Suite model additions increment the schema only when persisted fields are introduced.
- Decoder must continue accepting v1-v3 projects and migrate defaults deterministically.
- New fields must use defaults that reproduce current behavior: normal blend, no mask/effects, normal forward speed, no parent/camera.
- A migration may add metadata but must never silently discard unknown assets or clips.

## Performance rules

- Never serialize full decoded frames/bitmaps into project JSON.
- Mask paths and keyframes must be bounded/validated.
- Effect stacks have deterministic order.
- Expensive analysis is cancellable and produces reusable metadata.
- Preview may lower quality but cannot change timing/compositing semantics relative to export.
- Renderer must release Media3/codec/GPU resources on lifecycle/cancellation.

## Safety / integrity rules

- No project save succeeds with invalid graph cycles, invalid time maps, non-finite numeric parameters or references to nonexistent parent nodes.
- Remote Bridge commands use explicit capabilities and revision preconditions.
- Destructive commands remain locally confirmable through mobile handoff when invoked from ChatGPT.
- App-data clearing is treated as destructive Android behavior; normal update/restart must preserve projects and private staged media.

## Acceptance criteria

A feature category is only marked complete when:

1. its model round-trips through project JSON;
2. legacy projects still decode;
3. invalid state has a typed/controlled rejection path;
4. its history operation is undoable where it mutates the project;
5. preview/export share the same evaluator/compiler semantics;
6. relevant Android/unit smoke tests pass;
7. existing import/player/timeline/trim/split/delete/undo/redo/export regressions remain green.

## Research basis

Capability targets were refreshed on 2026-10-23 from current official CapCut editing material, Alight Motion help material and Canva video/design help material. Reference products are evidence for capability categories only; LocalCut implementation remains original.