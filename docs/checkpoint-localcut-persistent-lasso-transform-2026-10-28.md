# LocalCut checkpoint — persistent lasso transform — 2026-10-28

## Source of truth
- Repository: `Astabr2003BR/LocalCut`
- Branch: `feature/localcut-bridge-v1`
- PR #13 remains OPEN + DRAFT; never merge without explicit user authorization.
- Prior creative checkpoint remains valid: `docs/checkpoint-localcut-creative-2026-10-28-1822.md`.
- This file is the latest delta checkpoint for lasso/selection work.

## Persistent lasso selection — implemented
The old lasso behavior recalculated membership from the original polygon after every geometry/playhead change. That meant moving an element outside the polygon could silently drop it from the selection.

New behavior:
- `VectorLassoPersistentSelectionPolicy` captures stable element IDs when a valid lasso ends;
- selection membership no longer changes merely because selected geometry moves/scales/rotates or the playhead moves;
- missing/deleted IDs are filtered against the current project before mutation;
- starting a new lasso replaces the previous selection;
- Clear/Delete reset the persistent selection;
- highlighted PATHs use the persistent ID set.

Key commits:
- RED selection test: `bac1214c74ee43db49849920fd365dd5ba7dcebf`
- selection policy: `c5b70b3879fa2a7c1847d5f7a6cda126f5371915`

## Batch scale/rotate — implemented
`DesignSelectionTransformPolicy.pathBoundsCenterPx()` computes the shared project-space pivot from the actual transformed PATH geometry using `DesignElementGeometryPolicy`, keeping selection geometry aligned with renderer assumptions.

`TransformDesignElementsCommand` applies one atomic group-style transform:
- selected element centers orbit the shared pivot;
- scale multiplies element scale and distance from pivot;
- rotation rotates element centers and adds the same rotation delta to each element;
- IDs/pivot/factor/delta are validated;
- missing IDs, non-finite values, non-positive factor, no-op requests, overflow, or non-positive resulting scale fail closed;
- `TimelineValidator` remains the final project integrity gate;
- one undo restores the exact original element list/transforms; redo reapplies the result.

Mobile controls in `VectorLassoControl`:
- existing ← → ↑ ↓ retain the same selected IDs;
- `Menor` = 0.9x;
- `Maior` = 1.1x;
- `↺ 15°` and `↻ 15°` rotate the persistent selection;
- each action is one EditorCommand / one undo step.

Key commits:
- pivot RED test: `0c49207436a141b6a584ca80fbcd09f3ba4dbefa`
- transform RED test: `5df0289b86370ab85c27b136839314f1815842ef`
- pivot policy: `2a51052cf7f799674cefda3cd72410c587786063`
- transform command: `5d96b1a05439aff4c6379f501d2ec04fc6f9ac68`
- persistent-selection + scale/rotate UI: `d1eb9b24edb804dc4570a0f5d99ce568d7935327`

## Grouping decision
Do NOT use `ReusableComponent` as a fake editable group. Current `DesignElement` has no group membership field and `Project` has no `DesignGroup` collection.

A real grouping block must introduce an explicit model (for example `DesignGroup`) with saved-project compatibility, validation, commands, renderer/editor semantics, and undo/redo. That work was intentionally not mixed into this lasso patch.

## TDD / validation status
Tests were committed before production APIs for persistent selection, pivot geometry, and atomic transforms.

CI anti-loop status:
- `d1eb9b24edb804dc4570a0f5d99ce568d7935327` was queried exactly once.
- Android Debug APK: pending.
- Android Multitrack Export Smoke: pending.
- Bridge Controller CI: in progress.
- Termux Agent Engine CI: in progress.
- DO NOT query this SHA again.

Therefore this block is implemented and statically audited, but the final Android/unit-test CI result for this exact SHA was not observed completed.

## Next safe blocks
1. Real `DesignGroup` model + validation + atomic group/ungroup commands, only if done with saved-project compatibility and no fake ReusableComponent semantics.
2. Motion-path duration/range controls or per-point editing, continuing to use `PropertyAnimation`.
3. Expose BOTH_AXES drawing symmetry through a small UI patch; domain support already exists.
4. Continue parenting/camera as separate tested blocks.
5. Blend remains blocked until a real multi-texture compositor exists.
