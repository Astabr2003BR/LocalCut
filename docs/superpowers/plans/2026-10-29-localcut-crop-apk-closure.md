# LocalCut — Crop + APK Closure Plan

## Scope

Finish the current checkpoint queue without reopening completed work. Keep PR #13 open/draft, do not merge `main`, and do not treat Play Store/publication as part of this closure.

## Batch A — Crop in the main editor flow

1. Extend existing UI-policy tests first so they require a dedicated `CROP` tool for VIDEO/IMAGE and a dedicated context panel.
2. Add `EditorToolId.CROP`, catalog ordering and workspace reachability.
3. Add `ContextPanelKind.CROP` and route it to a focused Crop panel using the existing `SetSelectedCompositing` history path.
4. Reuse `CropManualControls`; remove its embedded Chroma controls so Crop and Chroma remain separate tools.
5. Keep preview/export on the existing shared `ProjectCompositionCompiler` + `Media3CompositingEffectFactory` path.
6. Verify Crop backend, UI policy, undo/redo path, and regression-sensitive editor tests.

## Batch B — Hidden-tool and renderer audit

1. Audit LIVE catalog tools against real context/action controls and existing backend.
2. Expose only already-real, low-risk capabilities that are hidden; do not create fake buttons.
3. Re-audit `rotationX/rotationY`/2.5D. If no real plane renderer exists, keep them fail-closed and document as intentionally incomplete rather than exposing misleading UI.

## Batch C — Technical closure and APK

1. Run/inspect CI once per new SHA, following the anti-loop rule.
2. Fix only concrete failures and create a new SHA for each fix.
3. Write a final checkpoint with verified scope, intentional fail-closed items, and build evidence.
4. Confirm PR #13 remains OPEN + DRAFT.
5. Obtain the successful Android debug APK artifact, materialize it, inspect archive/APK integrity and SHA-256, then provide the APK to the user.

## Acceptance criteria

- Crop is reachable for VIDEO and IMAGE from the main editor flow.
- Crop and Chroma are not duplicated in the same focused panel.
- Crop mutations use the existing undoable `SetSelectedCompositing` path.
- Preview and export continue using the same compositor/compiler path.
- No new paid dependency, AI feature, proprietary copied UI/assets/code, merge to `main`, or Play Store work.
- Existing protected functionality remains intact.
- APK is only called validated after a successful build/test signal and artifact integrity check.
