# LocalCut Master Capability Expansion Plan — 2026-10-29

## Objetivo

Evoluir o LocalCut como editor Android independente, reunindo capacidades úteis encontradas em editores como CapCut, Alight Motion, Canva, OpenCut, OpenToonz, Blender, Krita, Clip Studio Paint, FlipaClip, RoughAnimator, Photoshop e Jitter/Jitter Video, sem copiar código, APKs, assets ou interface proprietária.

## Regras de implementação

1. GitHub e `overrides/app` continuam como fonte de verdade.
2. Não expor controles sem backend real.
3. Preview e export devem compartilhar a mesma semântica de renderização.
4. Patches pequenos, reversíveis e testáveis.
5. Cada bloco entra com teste/regressão e CI antes de ser marcado LIVE.
6. PR #13 permanece OPEN + DRAFT; nenhum merge para `main` sem autorização explícita.
7. Sem IA nesta versão do LocalCut.

## Matriz mestre

| Família | Capacidades | Estado atual / próximo gate |
| --- | --- | --- |
| Edição base | importação, timeline multitrack, trim, split, delete, undo/redo, overlay, crop, flip, speed, reverse, freeze | LIVE; preservar regressões |
| Motion | keyframes, easing/Bezier, motion path, parenting, câmera, estabilização | LIVE; ampliar sem quebrar compatibilidade |
| Tracking | análise de movimento + aplicação segura em propriedades | PLANNED/PARCIAL; fechar provider + applier + UI antes de LIVE |
| Composição | máscaras, chroma, transformações, hierarquia, z-depth | LIVE/PARCIAL conforme recurso |
| Blend modes | Multiply, Screen, Overlay e equivalentes | BLOQUEADO por compositor multi-textura real; não expor botão falso |
| 2.5D/3D plano | profundidade e projeção de câmera | z-depth LIVE; rotationX/rotationY BLOQUEADO até renderer de plano real |
| Motion blur | blur dependente de movimento/keyframes | PRÓXIMO; requer integração render/preview/export |
| Design | camadas, grupos, formas, canvas, templates, transformações | LIVE; auditar alinhamento, distribuição, guias, snapping e estilos avançados |
| Texto | texto, estilos, animações, captions | LIVE; auditar presets, tipografia e controles avançados |
| Desenho/animação | path editável, brushes, onion skin, exposição, frame-by-frame, eraser, lasso, simetria, snap | LIVE; auditar fill, seleção avançada e transformações adicionais |
| Áudio | importação, volume/fades, ferramentas e marcadores | LIVE/PARCIAL; auditar EQ, dynamics, pitch/time, noise tools e mixing |
| Efeitos/cor | efeitos, ajustes e presets | LIVE; expandir apenas quando renderer suportar preview/export equivalentes |
| Workflow | projeto serializável, backup de estado, cache/mídia gerada, export | LIVE; continuar performance, recuperação e diagnósticos |
| Agent Engine | análise/reverse/tracking auxiliares via Termux | BLOQUEIO FÍSICO IDENTIFICADO: engine externo não iniciado; adicionar recuperação mobile-first |

## Ordem de execução

### Bloco 0 — Recuperação mobile do Agent Engine
- detectar Termux no Android;
- abrir Termux pelo LocalCut;
- copiar comando curto de start/pair;
- copiar comando seguro de setup/reparo em diretório gerenciado;
- nunca executar shell externo silenciosamente;
- nunca copiar/expor token;
- manter `TERMUX_ENGINE_UNAVAILABLE` como erro real até o worker responder.

### Bloco 1 — Tracking real
- fechar contrato de análise e retorno;
- stale guards e idempotência;
- aplicar tracking a transform/motion path sem conflito com Stabilize;
- undo/redo;
- preview/export equivalentes;
- só então mover `EditorToolId.TRACKING` de PLANNED para LIVE.

### Bloco 2 — Compositor avançado
- multi-textura;
- blend modes reais;
- matte/clipping quando suportados;
- regressões visual/export;
- memória GPU previsível no Android.

### Bloco 3 — Motion avançado
- motion blur;
- curvas/keyframes adicionais;
- renderer de plano necessário para rotationX/rotationY;
- câmera e parenting sem drift.

### Bloco 4 — Design + desenho
- auditar gaps contra Canva/Photoshop/Krita/Clip Studio/FlipaClip/RoughAnimator;
- completar alinhamento/distribuição/guias/seleção/fill/presets somente com persistência e undo/redo.

### Bloco 5 — Áudio
- auditar pipeline real antes de adicionar UI;
- completar EQ/dynamics/pitch/time/noise/mixing conforme suporte do render/export.

### Bloco 6 — Performance e encerramento
- stress tests de projetos grandes;
- memória, cache, cancelamento e retomada explícita;
- validação física no Android;
- novo APK somente após gates verdes.

## Definição de pronto por ferramenta

Uma ferramenta só pode ser chamada LIVE quando tiver: modelo persistente quando necessário, ação real, undo/redo quando editável, validação/stale guard quando assíncrona, preview, export equivalente, teste de unidade/regressão, CI verde e ausência de botão placebo.
