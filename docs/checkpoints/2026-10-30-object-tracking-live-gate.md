# LocalCut checkpoint — Object Tracking LIVE gate — 2026-10-30

## Fonte de verdade

- Repositório: `Astabr2003BR/LocalCut`
- Branch: `feature/localcut-bridge-v1`
- PR: `#13` — deve permanecer OPEN + DRAFT; sem merge em `main` sem autorização explícita.
- Publicação/Play Store continua fora do escopo.

## Incremento fechado neste lote

Object Tracking foi ligado ao editor como ferramenta real de vídeo, sem reutilizar o tracking global da estabilização.

Fluxo implementado:

`ROI no PreviewPane -> geometria fonte Android -> transform canônico preview/export -> ROI normalizada na mídia -> Termux/OpenCV OBJECT_TRACKING -> source time -> presentation time -> transform.x/y -> comando único no histórico -> undo/redo`

### Preview/ROI

- `PlayerView` permanece explicitamente `FIT`.
- `ObjectTrackingPreviewOverlay` permite selecionar retângulo no preview.
- `ObjectTrackingPreviewSession` é transitória e não altera o schema do projeto.
- mudança do viewport invalida ROI antiga;
- sair de Tracking desativa e limpa a seleção;
- overlay desativado não inicia `detectDragGestures`.
- ao abrir Tracking, o editor leva o playhead ao início do clipe;
- `ObjectTrackingSelectionAnchorPolicy` só permite capturar ROI no frame-âncora do tracker, com tolerância máxima de um frame;
- se o usuário sair da janela de âncora, a seleção é desativada e limpa, evitando usar uma ROI desenhada em outro frame sobre `sourceInUs`.

### Geometria e continuidade visual

- dimensões fonte são obtidas sob demanda via `MediaMetadataRetriever`, fora da thread principal;
- rotação da mídia é normalizada e validada contra o frame realmente decodificado pelo OpenCV;
- ROI usa `CompositionRenderResolver` + `PropertyTransformRenderResolver`, preservando parenting/câmera planar e animação compatível;
- seleção em letterbox/pillarbox ou fora de pixels reais falha fechada;
- `ObjectTrackingVisualSnapshot` impede aplicar resultado se canvas, compositionGraph, asset ou estado visual do clip mudar durante o job.

### Tempo e conflitos

- `sourceInUs/sourceOutUs` fazem round-trip e mismatch é rejeitado;
- samples do tracker são convertidos de source time para presentation time;
- velocidade normal e curvas são suportadas;
- Reverse e Freeze continuam fail-closed nesta versão;
- legacy transform keyframes são rejeitados antes do job porque `transform.x/y` moderno teria precedência e alteraria a semântica do movimento;
- estabilização, Motion Path e posição manual conflitante continuam bloqueados antes do job;
- stale guard inclui keyframes legados além de trim, transform, propertyAnimations, compositing e timeMap.

### Aplicação e segurança de job

- `ObjectTrackingActionCoordinator` faz preflight antes de OpenCV;
- uma análise Termux por ação, sem retry cego;
- clip é relido após o job;
- aplicação é um único `SetClipPropertyAnimationsCommand`, portanto undo/redo permanece atômico;
- UI consulta capability `ANALYZE_OBJECT_TRACKING` antes de disparar;
- botão fica desabilitado durante execução para impedir duplo disparo;
- sucesso só é exibido depois de confirmar que as animações geradas estão de fato no clip.

## Reachability

- `EditorToolId.TRACKING` promovido de `PLANNED` para `LIVE`;
- seleção suportada: `VIDEO` apenas;
- action target permanece `TRACKING`;
- ferramenta exposta no workspace `Animar` (`EditorWorkspace.MOTION`);
- não aparece para IMAGE, AUDIO ou PROJECT;
- execução real é montada no `EditorBottomToolRail`, seguindo o padrão já usado por Reverse/Freeze e evitando reescrever o `EditorScreen` grande.

## Testes adicionados/estendidos

- `ObjectTrackingApplyGuardTest`
- `ObjectTrackingActionCoordinatorTest`
- `ObjectTrackingWorkflowCoordinatorTest`
- `ObjectTrackingSelectionAnchorPolicyTest`
- `ObjectTrackingPreviewSessionTest`
- `EditorToolCatalogTest`
- `EditorWorkspacePolicyTest`
- `EditorChromeUiPolicyTest`
- `LiveToolWorkspaceReachabilityTest` já cobre genericamente toda ferramenta LIVE primária e passa a exigir reachability de Tracking quando o catálogo é promovido.

## Limitações deliberadas

Object Tracking nesta versão não deve fingir suporte para:

- imagem estática;
- seed de ROI fora do primeiro frame do trecho rastreado;
- Reverse ativo;
- Freeze ativo;
- legacy transform keyframes;
- animação manual moderna de X/Y;
- Stabilization/Motion Path conflitantes;
- rotação 3D `rotationX/rotationY` sem renderer real de plano.

Todos esses casos devem falhar fechados/preservar o estado existente.

## Gate de verificação

Build Android local continua indisponível neste ambiente por ausência de Gradle/Android SDK. O gate real é GitHub Actions no último SHA de código do lote.

Código do gate após correção da âncora: `858bc5d030073bcfb3ca6ac74929cbb8d0a194f8`.

Regra anti-loop: consultar o status desse SHA no máximo uma vez enquanto estiver pending/in_progress. Só um SHA novo recebe novo checkpoint de CI após correção concreta.

## Próximo passo após gate verde

- teste físico no Android/Poco X6 5G: selecionar vídeo -> Animar -> Tracking -> confirmar seek ao início -> marcar ROI -> executar -> validar trajetória no preview/export -> undo/redo;
- depois continuar roadmap: compositor blend multi-texture real / motion blur / áudio avançado, sem reabrir Object Tracking salvo regressão concreta.
