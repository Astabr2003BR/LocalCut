PROJECT: LocalCut
BRANCH: feature/audio-waveform-fade-foundation
IMPLEMENTATION_HEAD: 888a782536ae9d316432c973167b5b3a83e13ad5
GOAL: Concluir Task 6 — waveform e controles de áudio.
CHANGES: cache LRU limitado; geração de waveform em background com deduplicação; decoder Android MediaExtractor/MediaCodec; waveform na timeline; fade-in/fade-out persistidos e validados; mute/solo por track; comandos com undo/redo; múltiplas tracks de áudio EXCLUSIVE no render plan/Media3; envelope linear aplicado no preview/export; slicing de prévia final preserva apenas fades de borda compatíveis.
FILES_TOUCHED: overrides/app/src/main/java/com/localcut/editor/audio/**; domain/model/Clip.kt; domain/model/Track.kt; domain/timeline/TimelineOps.kt; domain/timeline/TimelineValidator.kt; domain/timeline/TimelineSlicer.kt; domain/history/AudioControlCommands.kt; data/project/ProjectJson.kt; media/RenderPlan.kt; media/ProjectRenderPlanCompiler.kt; media/Media3CompositionBlueprint.kt; media/Media3CompositionSupportPolicy.kt; media/ProjectCompositionCompiler.kt; session/ProjectSession.kt; ui/editor/EditorScreen.kt; ui/editor/components/TimelinePane.kt; ui/editor/components/InspectorPane.kt; testes de audio/render/slicing.
TESTS_RUN: TDD RED comprovado para cache, envelope e contratos de waveform/modelo; CI run #88 executou unit tests, overlap-safe tests, 23 regression harnesses e assembleDebug com sucesso.
CI_STATUS: PASS — GitHub Actions run 39469114066 (#88); APK debug verificado e artefatos enviados.
KNOWN_RISKS: não houve teste auditivo em dispositivo físico; corte de prévia que começa/termina no meio de um fade descarta esse fade parcial porque o modelo atual só representa fades ancorados nas bordas; AUDIO OVERLAY dentro da mesma track continua bloqueado; preview visual multitrack continua bloqueado fora de EXPORT pelo gate de confiabilidade existente.
LAST_VALID_CHECKPOINT: main e62426916626f8be5c9d0c41458d044ec0424e64 — Media3 visual composition layers.
NEXT_ACTION: integrar PR #8 ao main após CI final do checkpoint e iniciar Task 7 — export profiles/adaptive preview.
