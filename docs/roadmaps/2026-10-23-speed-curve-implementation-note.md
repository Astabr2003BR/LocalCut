# LocalCut custom speed curve implementation note

Date: 2026-10-23

## Goal

Complete the non-AI custom speed-curve workflow with LocalCut-owned semantics while preserving preview/export parity and the existing `TimeMap` persistence contract.

## Research evidence

- Current CapCut material exposes a manual/custom speed curve with multiple editable points and a documented working range around 0.1x to 10x.
- Android Media3 1.11.x exposes `EditedMediaItem.Builder.setSpeed(SpeedProvider)` for presentation-speed control. LocalCut already compiles positive variable-speed `TimeMap` data into a Media3 `SpeedProvider`.

## LocalCut decision

- Do not copy CapCut preset names, curve data, UI assets or proprietary implementation.
- Keep custom curve speeds strictly positive; reverse remains a separate backend task and must not be implemented with negative speed.
- Mobile custom-curve controls use LocalCut-owned points and clamp interactive values to 0.1x..10x.
- Convert UI control speeds into presentation-time `SpeedPoint`s so the trapezoidal integral of speed across presentation time maps the complete source range.
- The first and last control points anchor the presentation curve; intermediate points are evenly spaced initially and can be refined by later point-position editing.
- A custom curve may be exposed as LIVE only if `TimeMap.validationIssues()` is empty and `Media3TimeMapSupport.canRender()` returns true.
- Mutations continue through the existing selected-clip time-map action so undo/redo/autosave remain authoritative.
- No schema bump: the persisted representation remains the existing schema-v6 `TimeMap.speedPoints`.

## Acceptance

1. Two or more valid positive control speeds produce a strictly increasing `SpeedPoint` list.
2. The complete source duration is mapped by the resulting curve within rounding tolerance.
3. Invalid source duration or non-positive/non-finite speeds are rejected before mutation.
4. Media3 accepts the resulting `TimeMap`.
5. Preview/export use the same time-map semantics.
