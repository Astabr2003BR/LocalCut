# LocalCut — Pesquisa mestre e arquitetura de evolução profissional
Data: 2026-11-10
Projeto: Astabr2003BR/LocalCut
Base auditada: feature/localcut-bridge-v1, commit 5cfb45ef14220d01d085fc33754de7fd2aa3eb4f, PR #14 OPEN/DRAFT
Status: PESQUISA + PLANO DE IMPLEMENTACAO; NAO E CODIGO IMPLEMENTADO NEM RELEASE.

## 1. Objetivo e limites
Evoluir o LocalCut existente, Android/mobile-first, para editor completo de video, motion design e animacao de personagens 2D/2.5D com visual consistente e exportacao local. Termux e auxiliar opcional. Core offline, gratuito, sem credito, servidor, assinatura ou IA obrigatorios. Preservar importacao, preview, timeline, exportacao, audio, piscadas, TALK, historico, projetos, pareamento, autorizacoes e assinatura do APK. Sem merge para main, distribuicao, exclusao de dados ou mudancas criticas sem autorizacao explicita. Atualizacoes consolidadas; alteracoes apenas de parametros podem ocorrer no app atual, mas NOVOS shaders/motores exigem nova build.

## 2. Referencias pesquisadas — funcionalidades, nao codigo nem APKs copiados
- CapCut: timeline, keyframes, transformacao, tracking, composicao, legendas e efeitos. https://www.capcut.com/tools/keyframe-animation
- Alight Motion: parenting, objetos nulos, easing, propriedades animadas. https://support.alightmotion.com/hc/en-us/articles/10536997444369-Layer-Parenting-and-Null-Objects
- Canva: timeline mobile multicamadas, titulos, design e templates. https://www.canva.com/pt_br/design-school/resources/video-timeline-mobile/
- FlipaClip: frames, camadas, onion skin, audio. https://link.flipaclip.com/4sjFhmjDJzjNcFLETdYH
- RoughAnimator: desenho frame-a-frame, audio para lip-sync, rotoscopia; nao oferece tweening automatico. https://roughanimator.com/faq/
- Krita: raster frame-by-frame, onion skin, curvas e storyboard. https://docs.krita.org/en/user_manual/animation.html
- OpenToonz: timeline/xsheet, Plastic mesh e rig deformavel; BSD; app de desktop, nao substituir o Android. https://opentoonz.github.io/e/ ; https://github.com/opentoonz/opentoonz_docs/blob/master/source/create_animations_using_plastic_tool.rst
- Blender: Grease Pencil, quadros, armature/ossos, 2D em 3D. https://docs.blender.org/manual/pt/5.2/grease_pencil/modifiers/deform/armature.html
- Rive: ossos, meshes, IK, constraints, graph editor; runtime MIT, editor/artefatos sujeitos a termos distintos. https://rive.app/use-cases/character-animation
- OpenCut: alternativa aberta de edicao, atualmente em reescrita, nao um modulo Android pronto. https://github.com/OpenCut-app/OpenCut
- Jitter: animacao baseada em operacoes, nao keyframes classicos; copiar PRINCIPIO dos presets operacionais, nao UI proprietaria. https://help.jitter.video/en/articles/16415500-understand-operations-vs-keyframes
- Photoshop e Clip Studio Paint: referencias de preparo de camadas, ilustracao e composicao, nao dependencias do runtime.
- Anime MP4 Studio: referencia de fluxo de anime, nao dependencia nem prova de capacidade.
- Termo "GGI" mencionado antes: identidade tecnica nao comprovada, nao usar como criterio de arquitetura.

Nao foi feita engenharia reversa/decompilacao de APKs proprietarios, nem incorporacao de bibliotecas/modelos sem auditoria de licenca.

## 3. Auditoria da base atual — evidencias no repositorio
CONFIRMADO POR INSPECAO DE CODIGO:
- CharacterSceneAnimationPlanner.kt: acoes IDLE, TALK, WALK, CROUCH, STAND, PICK_UP, PUT_DOWN, POINT, ENTER, EXIT; cenas PAN_ZOOM, PARALLAX, CROSSFADE, transform/property keyframes. Acoes planificadas em clipe plano NAO equivalem a locomocao humana fisicamente articulada.
- CharacterRigBinding referencia partes opcionais: corpo, cabeca, olhos, boca, bracos, pernas e objeto; controles dependem de clipes ja existentes.
- CharacterAnimationStudioPanel.kt: presets Discreto/Natural/Expressivo, piscada, breath/sway, calibracao de olhos, guardar/carregar preset e controle TALK.
- LocalCutMouthWarpEffect.kt: deformacao localizada da boca e abertura dirigida por audio; melhorada em 5cfb45e, mas nao produz visemas foneticos.
- LocalCutEyeBlinkEffect.kt: piscada parametrica.
- AudioDrivenTalkPlanner.kt + WaveformLipSyncCueCoordinator.kt: cues baseados em energia da fala, nao alinhamento de fonemas.
- ProjectCompositionCompiler/Media3: preview e export compartilham modelo de composicao e efeitos, com diferencas de runtime a validar.
- CanvasTools, AnimationCurvePanel, CameraRig, MotionPath, ObjectTracking e AudioTools ja existem em graus distintos. Nao declarar funcionalidades completas apenas por haver classes.
- Emulador e compilacao da versao 0.2.1999 passaram em CI; provas fisicas anteriores validaram voz, piscadas e MP4 do caso simples. Nao extrapolar para motor de rig futuro.

FALTAS IMPORTANTES:
- Rig 2D editavel e deformacao local de malhas com pesos.
- Poses de cabeca/olhar e expressoes independentes em retratos arbitrarios.
- Biblioteca de visemas + alinhamento em portugues brasileiro.
- Editor quadro a quadro/onion skin totalmente integrado e exportado.
- Varias personagens com rig, movimentos coordenados e objetos.
- Validacao real de desempenho, memoria, retomada, exportacao com todos os novos recursos.

## 4. Tres niveis honestos de animacao
A. RETRATO PLANO: uma imagem. Piscada, voz, micro-movimentos, olhos/cabeca sutis limitados pela textura. Nunca inventar pernas/parte de tras da cabeca.
B. PERSONAGEM EM CAMADAS: PNG transparentes de tronco/cabeca/cabelo/olhos/boca/bracos/pernas e props. Rig com pivots, hierarquia, keyframes, IK 2-bones e cycles permite walk/run/point/reach, com qualidade variavel.
C. RIG DE MALHA + DESENHO: deformacao de vertices (mesh/FFD), pesos de ossos, correcoes de pose, sprites e in-betweens; para animacao de alta qualidade.

Para retrato unico, permitir marcacao manual de partes e mascara somente quando confiavel. Detectores de rosto podem falhar em anime. Nunca afirmar que uma foto simples gera corpo completo ou movimento 3D real.

## 5. Inventario de movimentos alvo
ROSTO: blink separado E/D, olhar horizontal/vertical, sobrancelhas, sorriso, tristeza, surpresa, duvida, raiva estilizada, bochechas, micro-saccades, bocejo, respiracao, turn 2.5D com assets adequados.
BOCA: M/B/P fechado, A aberto, E/I largo, O arredondado, U projetado, F/V labiodental, L, consonantes curtas, repouso; transicoes suaves, intensidade moderada, maximo de deformacao e marcacao manual.
CABECA/PESCOCO: sim/não, inclinar, erguer/abaixar, virar parcialmente, acompanhar alvo, amortecimento/inercia.
CORPO: idle, respirar, deslocar peso, andar, correr, pular, agachar, levantar, sentar, apontar, cumprimentar, acenar, girar, pegar/soltar objeto; exigir rig/pernas quando necessario.
MATERIAIS: franja/cabelo por segmentos, roupas e acessorios com secundaria controlada, nunca fisica nao deterministica durante export.
CENA: camera pan/zoom/roll, parallax, travelling/seguimento, DOF simulado, corte, transicao, chuva/particulas, luz, profundidade por camadas, multiplos personagens e sincronizacao.

## 6. Sistema de lipsync recomendado
P0: preservar TALK atual como fallback, sem alterar audio. 
P1: editor manual de 6 a 9 sprites/poses de boca (visemas), keyframes em tempo absoluto do projeto, preview loop A/B, ajuste de centro e escala.
P2: pipeline opcional on-device para voz PT-BR: arquivo de audio local -> VAD -> transcricao/alinhamento quando modelos adequados estiverem disponiveis -> fonemas/visemas -> curvas suavizadas -> revisao manual. Texto fornecido pelo usuario melhora alinhamento, mas NUNCA confiar apenas no texto sem conferir audio real.
P3: deteccao da fala em outros idiomas e presets por estilo; voz sintetica/offline opcional, importacao MP3/WAV sempre disponivel.
Usar os 6-9 shapes de Rhubarb como REFERENCIA de conceitos. Rhubarb MIT, reconhecedor PocketSphinx e ingles; reconhecedor phonetic e independente de idioma, menos preciso. A portabilidade nativa no Android e os termos de modelos devem ser validados antes de integrar.
sherpa-onnx Apache 2.0 com Android/Kotlin e TTS/ASR offline; whisper.cpp tem exemplo Android. Verificar modelos, tamanho, privacidade e licenca de cada arquivo.
Recalcular cues quando audio asset/fingerprint/sourceIn/sourceOut/timeMap mudar. Remover cues obsoletos somente da camada TALK, sem destruir blink/efeitos externos. Audio fora da imagem: requer confirmacao e nao amplia clips de forma destrutiva.

## 7. Modelo de dados sugerido (versionado, opcional e reversivel)
CharacterAssetSet { characterId, sourceAssetRefs, layers[], portraitFallback }
RigDefinition { rigId, version, parts[], bones[], meshes[], constraints[], anchors[], drawOrder[] }
RigPart { partId, sourceClipId, parentBoneId, pivotXY, restTransform, zIndex }
Bone { id, parentId?, restStart, restEnd, constraints, weightRefs }
Mesh { partId, boundedVertices, triangles, weightedBoneIds, maxDeformation }
Pose { id, boneTransforms, faceControls, spriteOverrides }
VisemeCue { timeUs, durationUs, shapeId, weight, sourceFingerprint }
AnimationAction { id, characterId, type, startUs, durationUs, intensity, presetVersion }
SceneGraph { nodes, camera, overlays, effects, lighting, audioRefs }
Campos opcionais para projetos legados. Nenhuma URL remota nem script executavel obrigatorio. Rigor de limites finitos, ordem hierarquica aciclica, IDs unicos e ownership no projeto.

## 8. Arquitetura de execucao
- UI: abas Editar / Animar (Rosto, Corpo, Cena) / Desenhar / Audio / Exportar, com painel inferior compacto mobile.
- CharacterRigEditor: escolher personagens e importar partes, calibrar pivots, validar proporcoes, mostrar previas neutras.
- PoseLibrary: pose neutra, sorriso, olhos fechados, head nod, gestos, walk-cycle 8/12 poses, run-cycle e variacoes.
- AnimationEvaluator: funcao deterministica evaluate(project, timeUs) -> transforms, poses, visemes, opacity, camera; a mesma origem para preview e export.
- Renderer: manter Media3 e cadeia GL validada; incluir rig compositor/mesh em implementacao dedicada, evitar passes GL por parte quando possivel, reuse textures/FBOs; fallback seguro quando nao suportado.
- AudioSyncService: decode local assíncrono, cache por hash da fonte + recorte + versao do algoritmo, cancelamento e progresso.
- History: comando atomico por aplicar preset/movimento, snapshots e undo/redo; nunca alterar assets origem.
- Bridge: capabilities tipadas e argumentos numericos/ids validados, revisao expectedRevision, confirmacao local; nem shaders/codigo remotos nem acao silenciosa.

## 9. Desempenho Android
Alvo inicial: preview adaptativo 540p/720p, exportacao 1080x1920/1080x1350/1280x720, 24/30 FPS; 60 FPS so apos medições. Sao METAS, nao resultados garantidos.
Usar leitura de bitmaps limitada, texturas reutilizaveis, caches com tamanho fixo, mediacao de GL memory, control de overdraw, processamento de audio fora da UI, vida curta para objetos grandes, cancelamento e progresso visivel; nao acumular quadros de video completos em memoria.
Manter buffer graph estavel em Media3, cuidar de codecs/rotacao e tratar 7001 com erro classificado, sem reload/try-catch que escondam origem. Testar black preview, pausa/retorno, LOW_MEMORY, fechamentos do Android, bateria e rede lenta.
Termux pode ajudar a gerar assets/testes, mas APK deve funcionar autonomamente.
Google docs: https://developer.android.com/media/media3/transformer/composition ; https://developer.android.com/topic/performance/memory/guide/bitmaps

## 10. Licencas e seguranca
Nao decompilar, redistribuir nem clonar codigo/assets/UI de CapCut, Alight Motion, Canva, FlipaClip, RoughAnimator, Clip Studio ou Photoshop.
OpenToonz tem BSD; Rhubarb tem MIT; sherpa-onnx Apache-2.0; Rive runtime MIT NAO significa editor/artefatos sem termos; Blender/Krita GPL demandam cuidado em distribuicao/derivacao; Live2D SDK possui regras proprias e pode exigir acordo para editor extensivel. Auditar dependencias e modelos caso a caso.
Evitar dynamic code loading de shaders/scripts remotos: https://developer.android.com/privacy-and-security/risks/dynamic-code-loading
Proibir SSRF, acesso arbitrario a arquivos, alteracao de projeto alheio e manipulacao por URL com IDs nao comprovados. Somente import HTTPS validado; token nunca exposto nos logs; relay exige pareamento/autorizacao.
Backup anterior, escrita atomica, migration dry-run, verificacao de checksum, rollback e preservacao de signature em toda release.

## 11. Roadmap em gates (nao prometer prazo sem medir)
FASE 0 — CONGELAR BASE/QA P0: snapshot e golden test do MP4 76909, regressao do 7001, source duration, orientacao e audio; testar PR HEAD, CI e POCO.
FASE 1 — ROSTO P1: shapes de boca editaveis, library de 9 visemas desenhados, multi-eye independent blink, eye gaze, eyebrow, emotion presets, easing. Inicialmente manual/offline e com voz existente. Teste visual e pixel regression.
FASE 2 — RIG P1: schema versionado, editor de partes com anchors/pivots, parenting sem salto, IK two-bone, mesh triangulation/weights/FFD, constraints e layer order.
FASE 3 — MOVIMENTO P1: idle, nod, talk, walk/run, crouch, point, pickup, seated gestures, secondary hair/clothes, foot plant e pose blending; exige rigs e assets; fallback plano explicitado.
FASE 4 — DESENHO/CENA P2: frame-by-frame, onion skin, dope sheet, graph editor, canvas, animatic/storyboard, camera, parallax, multi-personagem, particulas leves, compositing.
FASE 5 — VOZ OFFLINE P2: modelos opcionais PT-BR para ASR/alinhamento/TTS, download opt-in e com medicao de memoria; NUNCA colocar custo/rede no fluxo fundamental.
FASE 6 — REFINO P2/P3: packs de presets sem codigo executavel, templates, ergonomia, autoexpressoes por dados e export formatos adicionais sujeitos a medicao.

## 12. QA e criterios de aceite
Teste de unidade: vazio, null, invalidos/NaN, duplicados, loops hierarquicos, missing parts, limites de malha/pesos, source recorte, clips fora de quadro, reapply idempotent e conflito.
Teste grafico: frame em t=0, pico de fonema, silencio, blink esquerda/direita, cabeça, base neutra; pixels fora ROI sem mudanca; composicao camadas/oclusao; coerencia preview vs export.
Teste de audio: PT-BR com P/B/M e vogais, pausas, ruido, musica, silencio, ausencia transcript, voz substituida; fase correta dos cues, alinhamento opcional, preservacao da voz.
Teste locomocao: corpo flat -> movimento posicional explicitado; rig com pernas -> alternancia de membros, foot contact, sem desencontro de ombros/maos.
Teste de timeline: loops, duas vozes, undo/redo, clipes bloqueados, entrada concorrente, selecao trocada, cancelar analisador, fechamento e reabertura, import duplicado.
Teste de codec: MP4 com audio, 4:5/9:16/1:1, metadados de rotacao, 24/30 FPS, export completo e hashes de audio quando sem edicao sonora.
Teste mobile: POCO real com interacao touch/teclado/scroll, background/foreground, tela bloqueada, LOW_MEMORY, sessao expirada, sem internet, armazenamento cheio e uso prolongado.
P0: nenhuma perda de dados, crash, identidade/seguranca quebrada. P1: movimento, audio, export incorretos. P2: jank e UX. P3: refinamentos.
Gates: Android Debug Build/unit PASS; Android instrumentation/preview/export PASS; Bridge/Termux PASS; smoke fisico com videos/MP4, sem regressao; somente entao release candidato com certificado verificado. Nenhum resultado futuro deve ser declarado PASS antecipadamente.

## 13. Critério de conclusão do projeto
LocalCut continua editor de video normal, mas tem opcao de animacao de personagens. Permite criar roteiro/cenas no mesmo projeto, vincular personagens, construir/calibrar rigs, colocar poses e visemas no tempo, aplicar camera, ouvir audio, editar quadro a quadro e exportar um MP4 de qualidade verificada no Android. O conjunto deve ser progressivo e adaptativo, nao monolitico, e cada modulo necessita implementacao e QA proprios.

## 14. Repositorio e checkpoint
Branch: feature/localcut-bridge-v1
PR: https://github.com/Astabr2003BR/LocalCut/pull/14
Este arquivo so documenta pesquisa, planejamento e criterios; nao altera o motor Android e nao gera novo APK.