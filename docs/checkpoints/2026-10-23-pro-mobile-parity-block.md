PROJECT
LocalCut

BRANCH
feature/localcut-bridge-v1

HEAD_SHA
99e2d2579333043280aba94b3907d71e973ae483

GOAL
Advance non-AI CapCut + Alight Motion + useful Canva parity with an original LocalCut mobile UX and real backend-gated tools.

CHANGES
- compact expandable ChatGPT Bridge instead of permanent large editor card
- professional mobile editor chrome: compact top bar, larger preview, dominant timeline, contextual bottom rail
- selection-aware tool catalog with LIVE/PARTIAL/PLANNED states and no generative-AI entries
- real visual layer show/hide through CompositionGraph + shared renderer semantics + undo/autosave
- advanced easing/steps/cyclic/random and cubic-Bezier mobile editor
- HSL Media3 backend, continuous color controls and original LocalCut filter presets
- professional audio controls over real LocalCutAudioProcessor DSP: pan, gain/normalization, 3-band EQ, compressor, limiter, noise gate
- design bring-to-front/send-to-back through persisted zIndex
- manual captions through existing TextOverlay + CaptionMetadata pipeline
- text styling/motion controls for renderer-supported background, shadow, letter spacing, entrance/exit/loop/typewriter
- Android instrumentation smoke for still image + WAV audio -> H.264/AAC export to cover screenshot frame-processing failure
- WAV smoke header compile fix
- capability matrix refreshed from implemented evidence

FILES_TOUCHED
- docs/roadmaps/2026-10-23-localcut-capcut-alight-canva-full-matrix.md
- docs/superpowers/specs/2026-10-23-localcut-pro-mobile-editor-design.md
- docs/superpowers/plans/2026-10-23-localcut-pro-mobile-editor.md
- overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorToolCatalog.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorChromeUiPolicy.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorBottomToolRail.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/EditorScreen.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/BridgePanel.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/BridgePanelUiPolicy.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/LayerPanel.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/LayerPanelUiPolicy.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/AnimationCurvePanel.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/AnimationCurveUiPolicy.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/ColorToolsPanel.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/ColorToolsUiPolicy.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/ColorFilterUiPolicy.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/AudioToolsPanel.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/AudioToolsUiPolicy.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/DesignToolsPanel.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/DesignToolsUiPolicy.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/TextOverlayEditorModel.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/TextOverlayDialogs.kt
- overrides/app/src/main/java/com/localcut/editor/domain/history/CompositionGraphCommands.kt
- overrides/app/src/main/java/com/localcut/editor/media/CompositionRenderResolver.kt
- overrides/app/src/main/java/com/localcut/editor/effects/EffectRegistry.kt
- overrides/app/src/main/java/com/localcut/editor/media/Media3EffectStackFactory.kt
- overrides/app/src/main/java/com/localcut/editor/ui/editor/CreativeToolsPanel.kt
- overrides/app/src/androidTest/java/com/localcut/editor/media/MultitrackExportSmokeTest.kt
- focused unit tests for all new policies/commands and renderer contracts

TESTS_RUN
- TDD RED states observed for tool catalog, Bridge UI policy, editor chrome, composition graph history, hidden-layer render semantics, layer policy, animation curves, HSL registry/backend, color policy, audio policy, design arrangement, captions/text controls and filters
- corresponding minimal production implementations committed after RED
- local full Gradle execution unavailable because this environment cannot resolve github.com
- final GitHub Actions gate to run on checkpoint HEAD

CI_STATUS
Final checkpoint HEAD not queried yet. Previous intermediate HEADs were intentionally queried at most once; no polling loops were used. Last fully validated APK checkpoint remains v0.2.697 / code697.

KNOWN_RISKS
- final compile/instrumentation status for this expanded block still requires the checkpoint HEAD gate
- screenshot preview error is covered by new real image+audio export smoke, but root cause is not declared fixed until that smoke passes
- reverse and freeze remain model-only/renderer pending
- temporal motion blur and advanced blend remain renderer pending
- lock exists in model but is not enforced across all edit paths, so it is not exposed as complete
- tracking/stabilization/reframe analyzers are not complete
- RGB curves/LUT, effect/transition browser depth, voice-over, extract-audio, replace-media, social presets, GIF/image-sequence export and template browser remain pending

LAST_VALID_CHECKPOINT
1420fa6a7878f941dc7e638200d3a8577f428497 — LocalCut v0.2.697 artifact validated structurally; earlier full build/test/smoke baseline 2f5c7f989cba561168dda1fcb0cdfbeb676feecc.

NEXT_ACTION
Run the single CI query for this checkpoint HEAD. If red, inspect only the red job and fix root cause. Once mandatory gates are green, download/verify the new APK; then continue non-AI parity with reverse/freeze backend, advanced blend, camera/parenting/groups, RGB curves/LUT, extract-audio/voice-over and local tracking/stabilization/reframe.
