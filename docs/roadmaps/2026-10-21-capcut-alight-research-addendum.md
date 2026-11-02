# LocalCut capability research addendum — CapCut + Alight Motion

Date: 2026-10-21

This addendum supplements `2026-10-21-capcut-alight-capability-matrix.md`. It records additional current official-product findings without copying proprietary implementation, UI, presets, branding, APKs, or assets.

## CapCut capabilities to add to LocalCut backlog

### Smart editing / timing
- Auto Cut / Smart Edit: analyze footage, speech/music/beat structure and propose cuts/synchronization.
- Beat-aware edit planning and transition placement.
- Keep this optional automation above the deterministic timeline core; generated edits must become normal reversible LocalCut commands.

### Captions
- Auto caption generation.
- Manual text/timing correction.
- Caption block splitting / re-segmentation.
- Re-recognition when source transcription is poor.
- Style controls and timeline-native caption blocks.
- LocalCut target: captions remain ordinary editable timed text objects after generation.

### Chroma / background
- Chroma color picker.
- Strength/intensity threshold.
- Shadow/spill refinement.
- Edge feathering/refinement.
- Automatic background removal remains an optional ML module; chroma key should be available offline without AI.

### Stabilization
- Automatic stabilization / shake analysis.
- LocalCut implementation should separate analysis from render so stabilization data is serializable and preview/export deterministic.

## Alight Motion capabilities to add to LocalCut backlog

### Parenting / null controllers
- One parent per layer, many children per parent.
- Hierarchical parent chains.
- Parent transform inheritance for position/rotation/scale.
- Parenting helper modes: normal, locked and weighted rotation/scale inheritance.
- Auto-rotate based on parent motion.
- Null/controller objects that render invisibly but can own transform/keyframes/effects.
- Preserve world transform when parenting is created at the playhead so layers do not visually jump.

### Camera system
- Camera position, scale/size and rotation with keyframes.
- Multiple cameras and timeline camera cuts.
- Parent overlays to camera for HUD/captions.
- Parent camera to another layer to follow motion.
- Z-depth simulation.
- Focus distance / depth-of-field blur.
- Near/far fog with color.
- Camera-level motion effects such as shake/oscillation.

### Reusable Elements
- Reusable grouped components stored separately from ordinary projects.
- Link instances to source element.
- Expose element properties such as text/color without modifying the source.
- Convert linked element to ordinary editable group.
- Package/export/import reusable elements.
- LocalCut target: implement as versioned reusable compositions/presets with explicit linking semantics.

### Effects/effect graph
Official help material exposes more than 100 effects/building blocks. Important engine categories to support rather than copying individual presets:
- color/brightness/contrast
- blur/motion blur
- ripples/distortion/warp
- displacement mapping
- mosaic/halftone
- RGB split/channel effects
- move along path
- offset/polar coordinate transforms
- stroke/text progress
- procedural counters/timecode
- background sampling/copying
- effect copy/paste and reusable effect stacks

### Motion blur
- Velocity-derived blur based on position, scale and rotation deltas.
- Independent enable/disable contribution for position, scale and angle.
- Tune/multiplier control.
- Must account for easing and effect-order semantics.

## LocalCut implementation rule

Reference products define **capability targets only**. LocalCut implementation must remain original and follow:

1. Serializable project state.
2. Undoable command/history operations.
3. Preview/export parity.
4. Mobile performance budgets.
5. Offline-first editor core.
6. Optional AI/analysis modules that degrade safely when unavailable.
7. Bridge commands operate only on LocalCut capabilities and never expose generic device control.

## Sources reviewed

- https://www.capcut.com/help/auto-cut-in-capcut
- https://www.capcut.com/help/auto-captions-in-capcut
- https://www.capcut.com/help/chroma-key-feature
- https://www.capcut.com/tools/video-stabilization
- https://support.alightmotion.com/hc/en-us/articles/10536997444369-Layer-Parenting-and-Null-Objects
- https://support.alightmotion.com/hc/en-us/articles/10536993608977-Camera-Objects-Guide
- https://support.alightmotion.com/hc/en-us/articles/10536791122449-Elements-The-Complete-Guide
- https://support.alightmotion.com/hc/en-us/articles/10537118778129-Motion-Blur
- https://support.alightmotion.com/hc/en-us/sections/10537020203665-Effects
