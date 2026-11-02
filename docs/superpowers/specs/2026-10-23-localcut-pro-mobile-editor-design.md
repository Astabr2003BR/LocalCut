# LocalCut Professional Mobile Editor — Design Addendum

Date: 2026-10-23

## Intent

Evolve LocalCut into an Android-first professional local editor with capability coverage informed by CapCut, Alight Motion and the useful video/design portions of Canva, while keeping an original LocalCut implementation, information architecture, visual identity, icons and assets.

The user has given full authorization to implement the non-generative editing surface autonomously. Generative AI features remain out of scope for this release train.

## Product rule

Reference products are capability and UX research only. Do not copy proprietary code, APKs, private APIs, exact layouts, iconography, branded presets, bundled assets, names or trade dress. Build equivalent workflows with LocalCut primitives and an original dark mobile UI.

## UX target

The current persistent Bridge card and vertically stacked property panels consume too much mobile space. Replace the editor hierarchy with:

1. Compact top bar: LocalCut/project context, undo, redo and export.
2. Bridge as a compact status/control entry; full Relay/pairing controls expand only on demand.
3. Large central preview with non-blocking error/status overlays.
4. Timeline as the primary lower workspace with multiple visual/audio/design/text lanes and a fixed/playhead-oriented editing model.
5. Bottom contextual tool rail inspired by standard mobile editor ergonomics, not copied from any product.
6. A single contextual tool sheet/panel at a time instead of stacking every inspector simultaneously.
7. Tools filtered by current selection: video/image/audio/text/design/canvas/project.
8. Advanced controls use secondary panels: keyframes, easing/curves, masks, color, effects, speed remap, audio, layer graph and camera.

## Non-AI capability families

### Editing and timeline
- import video/image/audio;
- multitrack timeline;
- overlays/PiP;
- trim/split/move/duplicate/delete;
- insert/overwrite/ripple modes;
- markers/ranges/snapping;
- track mute/solo/lock/hide;
- audio waveforms;
- transitions;
- undo/redo and autosave.

### Transform and compositing
- position/scale/rotation/opacity;
- crop/fit/fill/stretch;
- mirror/flip;
- blend modes;
- masks including freeform;
- chroma key;
- feather/expansion/invert;
- clipping/group masks where renderer support is real.

### Animation and motion design
- per-property keyframes;
- hold/linear/bezier/easing presets/custom timing curves;
- steps/cyclic/bounce/elastic/seeded random;
- parenting/groups/null controllers;
- 2D/2.5D depth and camera objects;
- camera cuts;
- motion path;
- velocity-derived motion blur once renderer support exists.

### Color, effects and filters
- brightness/contrast/exposure/saturation/temperature/tint;
- HSL;
- RGB/curves;
- LUT/filter pipeline;
- blur/sharpen/glow/vignette/grain;
- RGB split/mosaic/ripple/displacement;
- reusable effect stacks and presets;
- motion/warp/distortion building blocks with deterministic validation.

### Time tools
- constant speed;
- speed ramps/curve editor;
- reverse;
- freeze/frame hold;
- source-time remap;
- preserve audio/timeline semantics where applicable.

### Text and captions
- timed text overlays;
- local/manual captions as timeline objects;
- font selection and custom font references;
- size/weight/alignment/spacing;
- fill/gradient/stroke/shadow/background/rounded box;
- entrance/exit/loop animations;
- per-character animation metadata;
- caption split/merge/restyle.

### Audio
- multiple audio tracks;
- extract audio from video;
- volume/fades/pan;
- normalization;
- EQ/compressor/limiter;
- beat markers and local beat analysis;
- voice-over record/import workflow;
- waveform and sync tools.

### Design / Canva-style local tools
- vector/basic shapes and editable paths;
- fill/gradient/stroke/shadow/multi-border;
- groups and layer panel;
- bring forward/back/front/back;
- align/distribute;
- guides and snapping;
- reusable local components/templates;
- replace media while preserving timing/transform where compatible;
- canvas size/aspect/background and social presets.

### Tracking / stabilization / reframing
These are allowed only as local deterministic analysis features, not generative AI dependencies. Metadata must serialize before the UI calls a feature complete.

- motion tracking;
- stabilization;
- auto-reframe/crop path when implemented with a local analyzer;
- tracked parenting/attachment.

### Export and project portability
- H.264 and HEVC MP4;
- supported GIF/image sequence/alpha-capable profiles when Android codecs/render pipeline allow them;
- local project packages;
- stable APK signing and upgrade compatibility;
- preview/export semantic parity.

## Explicitly excluded for now

- generative video/image;
- prompt-based editing;
- AI-generated stickers;
- cloud-only generative tools;
- mandatory AI transcription/TTS/background removal providers.

Manual captions, chroma key, masks, local DSP and deterministic local analysis remain allowed.

## UI architecture

Introduce a pure tool catalog that describes tool ID, group, label, compatible selection types and implementation state. Compose renders the catalog; the catalog does not execute edits. Existing `EditorAction`/history/session commands remain the mutation boundary.

A tool may be marked `LIVE` only when its mutation path and preview/export/backend requirements pass relevant tests. `PARTIAL` means the model exists but the complete user-visible workflow/backend is not proven. `PLANNED` tools may exist in the research matrix but should not masquerade as working editor commands.

## Compatibility

- Current project schema remains v6 unless a persisted feature truly requires a new codec/migration.
- No schema bump for UI navigation/chrome.
- Preserve v1-v6 decode compatibility.
- Preserve `overrides/`, stable debug signing, Bridge protocol, imports, timeline, preview, export and project persistence.

## Acceptance gates

1. Mobile editor no longer requires the full Bridge configuration card to stay visible while editing.
2. Core editing is reachable from a bottom contextual tool rail.
3. One contextual panel is visible at a time.
4. Existing actions continue through `EditorAction` and history/autosave.
5. Every `LIVE` creative tool has a tested backend or deterministic editor operation.
6. Import/preview/timeline/trim/split/delete/undo/redo/export/Bridge/persistence regressions remain green.
7. PR #13 remains Open + Draft and unmerged.
