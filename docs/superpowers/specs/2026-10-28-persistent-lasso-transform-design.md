# Persistent Lasso Transform Design — 2026-10-28

## Goal
Turn the existing vector lasso from a live polygon query into a persistent selection that remains stable while selected PATH elements move, scale, or rotate.

## Decisions
- Selection membership is captured as element IDs when a valid lasso gesture ends.
- Membership does not change merely because geometry crosses the original polygon or because the playhead moves.
- Missing/deleted IDs are ignored by the UI and all mutations revalidate current project existence before applying.
- Batch scale/rotate is one EditorCommand per action so undo/redo is atomic.
- Scale/rotate behaves like a group transform: element centers orbit a shared project-space pivot, element scale multiplies by the factor, and element rotation receives the same delta.
- Pivot is the center of the selected PATH geometry bounds using renderer-compatible DesignElementGeometryPolicy.
- ReusableComponent is not a group. No grouping UI/model is added in this block.

## Mobile controls
- Existing nudge arrows remain.
- Add smaller/larger actions using factors 0.9 and 1.1.
- Add rotate left/right actions using -15 and +15 degrees.
- Clear and Delete keep their existing semantics.

## Safety
- IDs must be nonblank, unique, and exist at execution time.
- Pivot, factor, and rotation must be finite.
- Scale factor must be positive.
- At least scale or rotation must change.
- Any transform overflow or non-positive resulting scale fails before project mutation.
- TimelineValidator remains the final project integrity gate.

## Non-goals
- No DesignGroup model yet.
- No parent/child transform graph.
- No arbitrary gesture handles or pinch/rotate gesture in this block.
- No changes to EditorScreen or DrawStrokePad.
