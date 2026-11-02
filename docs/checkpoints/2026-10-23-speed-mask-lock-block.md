PROJECT
LocalCut

BRANCH
feature/localcut-bridge-v1

HEAD_SHA
Will be the commit that adds this checkpoint; use branch HEAD as source of truth.

GOAL
Advance non-AI professional mobile parity with real backend-gated controls: custom speed curves, mask/chroma refinement and practical layer locking.

CHANGES
- custom five-point speed curve controls, 0.1x..10x, compiled into existing TimeMap speed points
- custom curve mutation commits only when slider gesture finishes
- renderer contract requires Media3TimeMapSupport for custom curve output
- mask feather, expansion, opacity and invert controls over existing Media3 compositing shader
- chroma threshold, softness and spill-suppression controls over existing shader parameters
- layer panel exposes lock/unlock state using CompositionNode.locked
- normal selected-clip session mutations reject locked clips without project/history/revision changes
- reverse/freeze remain disabled because Media3 positive SpeedProvider does not provide a safe native reverse/frame-hold backend
- advanced blend modes remain disabled because current per-clip shader has no access to the underlying layer required for correct blending

FILES_TOUCHED
- docs/roadmaps/2026-10-23-speed-curve-implementation-note.md
- overrides/app/src/main/java/com/localcut/editor/ui/editor/CreativeToolsUiPolicy.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/CreativeToolsPanel.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/LayerPanelUiPolicy.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/LayerPanel.kt
- overrides/app/src/main/java/com/localcut/editor/session/ProjectSession.kt
- overrides/app/src/test/java/com/localcut/editor/ui/editor/CreativeToolsUiPolicyTest.kt
- overrides/app/src/test/java/com/localcut/editor/ui/editor/CreativeToolsRendererContractTest.kt
- overrides/app/src/test/java/com/localcut/editor/ui/editor/MaskControlsUiPolicyTest.kt
- overrides/app/src/test/java/com/localcut/editor/ui/editor/CompositingControlsUiPolicyTest.kt
- overrides/app/src/test/java/com/localcut/editor/ui/editor/LayerPanelUiPolicyTest.kt
- overrides/app/src/test/java/com/localcut/editor/session/LockedLayerSessionTest.kt

TESTS_RUN
- tests were authored before corresponding production changes for speed curve, mask/chroma refinement and lock enforcement
- intermediate GitHub Actions queries were intentionally performed at most once per HEAD; many were observed before workflows registered or while pending
- no intermediate HEAD is being polled again

CI_STATUS
Checkpoint HEAD gate not queried yet.

KNOWN_RISKS
- generic EditorAction.Execute remains an audit target; selected-clip lock enforcement is real, but universal direct-command/Bridge lock enforcement is not yet claimed
- current Media3 compositing backend supports one mask per clip; multi-mask composition remains unsupported
- reverse and freeze require dedicated renderer/materialization support
- blend modes require multi-input layer compositing, not the current single-input clip shader
- latest image+audio preview regression test was added earlier, but its final current-head result has not yet been observed without violating the one-query-per-HEAD rule

LAST_VALID_CHECKPOINT
2f5c7f989cba561168dda1fcb0cdfbeb676feecc — earlier fully green build/test/smoke baseline. LocalCut v0.2.697 remains the last structurally downloaded/verified APK artifact.

NEXT_ACTION
Run the single consolidated CI query for this checkpoint HEAD. If red, open only the red job and fix the root cause. If mandatory gates are green and a new APK artifact exists, download and verify it before continuing the next parity block.
