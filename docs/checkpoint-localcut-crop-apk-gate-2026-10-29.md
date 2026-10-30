# LocalCut — Checkpoint final do lote Crop + Gate de APK — 2026-10-29

## Estado desta continuidade

Este checkpoint continua a branch `feature/localcut-bridge-v1` sem reabrir blocos já concluídos.

### Crop / Recorte manual

- `CROP` é ferramenta LIVE para VIDEO e IMAGE.
- Está acessível pelos workspaces Editar e FX.
- O controle usa `CropManualControls` dedicado no bottom rail.
- Chroma permanece separado; controles de Chroma foram removidos do painel de Crop.
- Alterações de Crop usam `EditorAction.SetSelectedCompositing`, mantendo o caminho de `SetClipCompositingCommand`/HistoryManager.
- O backend não foi refeito: Crop continua usando o compositor Media3 compartilhado por preview/export através de `ProjectCompositionCompiler`.

### Segurança de estado e histórico

- `CropApplyGuard` bloqueia resultado stale quando a seleção mudou, o compositing foi alterado concorrencialmente ou o clip atual não existe mais.
- `CropApplyGuardTest` cobre aplicação válida e rejeições de stale state.
- `CropCompositingHistoryTest` cobre Crop com bounds + mode através de apply -> undo -> redo, sem perda dos valores.

### Auditoria de ferramentas

- `LiveToolWorkspaceReachabilityTest` impede ferramenta `LIVE + primary` sem alcance por nenhum workspace para uma seleção suportada.
- `TRACKING` permanece `PLANNED`: há análise/coordenadores, mas não existe provider/aplicador completo conectado ao runtime principal que justifique UI LIVE.
- Não existe botão falso para Tracking.

### Parenting / Câmera / 2.5D

- Parenting, camera rig, camera cuts e profundidade Z já estavam concluídos no checkpoint anterior e foram preservados.
- `rotationX/rotationY` continuam fail-closed. O resolver exige plane renderer real antes de aceitar tilt 3D; teste existente cobre essa trava.
- Não expor `rotationX/rotationY` até existir render real consistente em preview/export.

### Travas preservadas

- `overrides/` permanece a fonte Android ativa.
- Timeline, importação, preview, exportação, undo/redo e assinatura foram preservados.
- Sem dependências pagas.
- Sem IA nesta versão.
- Sem copiar código/assets/interface proprietária de terceiros.
- PR #13 deve permanecer OPEN + DRAFT.
- Não fazer merge na `main`.
- Play Store/publicação permanece fora de escopo.

## Gate final de APK

O commit que contém este checkpoint é o alvo definitivo do workflow Android Debug APK deste lote.

Critérios para considerar o lote encerrado:

1. unit tests do workflow passam;
2. `assembleDebug` passa;
3. regressions check passa;
4. APK debug é produzido;
5. artifact é baixado e sua integridade/SHA-256 é verificada;
6. PR #13 continua OPEN + DRAFT e sem merge.

A regra anti-loop continua obrigatória: uma única consulta de CI para este SHA. Não fazer polling repetido.
