# LocalCut — Object Tracking Foundation Checkpoint — 2026-10-29

## Estado

O `EditorToolId.TRACKING` continua **PLANNED**. Não promover para LIVE neste checkpoint.

## Implementado

### Agent Engine
- novo `termux-engine/localcut_object_tracking.py`;
- ROI normalizada e validada fail-closed;
- limites mobile para frames/amostragem/corners;
- tracking de posição via optical flow dentro da ROI;
- mediana robusta de deslocamento para reduzir efeito de outliers;
- saída normalizada `dxNorm` / `dyNorm`, centro da ROI e confiança;
- falha explícita para mídia ausente, frame/FPS inválido, poucas features e ausência de amostras válidas;
- nenhuma dependência OpenCV/Numpy é importada até o backend ser realmente usado.

### Android/domain
- `ObjectTrackingPathPlanner` converte deslocamento normalizado para `transform.x/y` usando dimensões efetivas de render;
- amostras de baixa confiança mantêm a última posição confiável;
- nenhuma trajetória é produzida quando todas as amostras ficam abaixo do limiar;
- keyframes gerados usam prefixos `object-track-x-` e `object-track-y-`.

### Conflitos
- `ObjectTrackingUiPolicy` rejeita merge com Stabilization gerada;
- rejeita merge com Motion Path gerado;
- remoção só elimina canais X/Y quando ambos pertencem ao Object Tracking gerado;
- animações não relacionadas são preservadas.

## Evidência local

Protótipo OpenCV real executado com vídeo sintético 320x240 e ROI texturizada:
- movimento esperado: +98 px X, +49 px Y;
- movimento recuperado: aproximadamente +98.02 px X, +48.96 px Y;
- confiança final: 1.0.

Esta evidência valida o núcleo matemático/visual local, mas não substitui CI nem teste físico do fluxo completo Termux -> socket -> Android.

## Ainda obrigatório antes de LIVE

1. adicionar modo de job específico para object tracking ao protocolo Termux;
2. serializar ROI/opções no Android;
3. executar o novo tracker no creative server;
4. parsear resultado no Android com validação estrita;
5. mapear ROI do preview para coordenadas da mídia, inclusive crop/fit/transform;
6. respeitar trim/sourceIn/sourceOut;
7. aplicar por comando undo/redo com stale guard;
8. UI de seleção de ROI e qualidade;
9. preview/export equivalentes;
10. CI verde e teste físico no Android.

## Travas preservadas

- PR #13 permanece OPEN + DRAFT;
- nenhum merge para `main`;
- nenhum botão placebo;
- global motion existente continua separado e destinado a Stabilization;
- Object Tracking não reutiliza global motion fingindo seguir objeto.
