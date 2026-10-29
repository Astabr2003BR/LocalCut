# LocalCut — Checkpoint Crop + Gate de APK — 2026-10-29

## Estado desta continuidade

Este checkpoint continua a branch `feature/localcut-bridge-v1` sem reabrir blocos já concluídos.

### Crop / Recorte manual

- `CROP` agora é ferramenta LIVE para VIDEO e IMAGE.
- Está acessível pelos workspaces Editar e FX.
- O controle usa `CropManualControls` dedicado no bottom rail.
- Chroma permanece separado; controles de Chroma foram removidos do painel de Crop.
- A alteração de Crop usa `EditorAction.SetSelectedCompositing`, portanto permanece no mesmo caminho de histórico/undo-redo já existente.
- O controle possui guarda contra aplicação stale após troca de seleção/estado.
- O backend não foi refeito: Crop continua usando o compositor Media3 existente compartilhado por preview/export através de `ProjectCompositionCompiler`.

### Auditoria de ferramentas

- Foi adicionado gate para impedir ferramenta `LIVE + primary` sem alcance por nenhum workspace para uma seleção suportada.
- `TRACKING` permanece `PLANNED`: há análise/coordenadores, mas não existe ainda um provider/aplicador completo conectado ao runtime principal que justifique UI LIVE.
- Não foi criado botão falso para Tracking.

### Parenting / Câmera / 2.5D

- Parenting, camera rig, camera cuts e profundidade Z já estavam concluídos no checkpoint anterior e foram preservados.
- `rotationX/rotationY` continuam fail-closed. O resolver de render exige plane renderer real antes de aceitar tilt 3D; teste existente cobre essa trava.
- Não expor `rotationX/rotationY` na UI até existir render real consistente em preview/export.

### Travas preservadas

- `overrides/` preservado como fonte Android ativa.
- Timeline, importação, preview, exportação, undo/redo e assinatura não foram substituídos.
- Sem dependências pagas.
- Sem IA nesta versão.
- Sem copiar código/assets/interface proprietária de terceiros.
- PR #13 deve permanecer OPEN + DRAFT.
- Não fazer merge na `main`.
- Play Store/publicação permanece fora de escopo.

## Gate de fechamento

Este checkpoint é o alvo para validação do workflow Android Debug APK da PR.

Critérios antes de chamar o lote de fechado:

1. unit tests do workflow passam;
2. `assembleDebug` passa;
3. regressions check passa;
4. APK debug é produzido pelo workflow;
5. artifact é baixado e sua integridade/SHA-256 é verificada;
6. PR #13 continua OPEN + DRAFT.

Se o CI falhar, corrigir somente a falha concreta, gerar novo SHA e validar apenas o novo SHA conforme a regra anti-loop.
