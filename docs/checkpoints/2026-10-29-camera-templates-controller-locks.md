# LocalCut checkpoint — câmera, modelos e locks de controladores

Data: 2026-10-29
Branch: `feature/localcut-bridge-v1`
PR: #13 — manter OPEN + DRAFT, sem merge em `main` sem autorização explícita.

## Escopo permanente

- Publicação/Play Store está fora do escopo do projeto.
- LocalCut é desenvolvido para uso Android do usuário.
- Não embutir/copiar APK, código ou assets proprietários; implementar capacidades equivalentes de forma própria.
- CI: no máximo uma consulta de status por SHA significativo; se estiver pendente, não fazer polling.

## Câmera

- `CAMERA` agora é ferramenta `LIVE`.
- Câmera aparece no workspace `Animar`.
- `CameraCutActionControl` é montado tanto em `Camadas` quanto em `Câmera`.
- Camera cuts continuam discretos por timestamp e usam o pipeline de render já existente.
- Painel Câmera permite:
  - criar câmera;
  - duplicar câmera ativa no playhead;
  - criar/substituir cut no playhead;
  - navegar aos cuts;
  - remover cuts não-base;
  - editar câmera ativa: X/Y/Z, escala, rotação Z, focal e ortográfica;
  - bloquear/desbloquear câmera.
- `duplicateActiveCameraAt(...)` foi implementado porque o teste existente já exigia a API e a produção não a continha.
- `rotationX/rotationY` permanecem bloqueadas até existir renderer real de plano 3D.

## Estabilização

- `STABILIZE` agora é atalho `LIVE` apenas para vídeo.
- No workspace `Animar`, `Estabilizar` encaminha para o painel real de `Animação` (`actionTarget -> ANIMATION`).
- Não existe backend duplicado; reutiliza OpenCV/Termux, quality presets, stale checks, overscan e undo/redo existentes.
- `TRACKING` permanece `PLANNED`, pois ainda não existe uma operação de tracking de objeto aplicada ao editor independente da estabilização.

## Parenting / controladores

- Null/Group suportam visibilidade real que afeta descendentes no renderer.
- Null/Group/Camera suportam lock.
- Lock bloqueia transform, parent, settings de câmera e remoção.
- Visibilidade de Null/Group pode ser alterada mesmo se o controlador estiver bloqueado.
- `CompositionControllerControls` desabilita sliders, parent, reset e remover quando bloqueado.
- `LayerPanel` e `CameraCutActionControl` agora passam callbacks reais de lock/visibilidade; não usam defaults no-op.
- Camada visual bloqueada não pode ser reparentada.
- UI desabilita `Desvincular`, `Vincular`, `+ Null pai` e `+ Grupo pai` enquanto a camada estiver bloqueada.

### Correção de lacuna quick-parent

- `LayerPanel` e testes já chamavam `LayerPanelUiPolicy.addNullParentForClip` / `addGroupParentForClip`, porém esses métodos estavam ausentes da policy atual.
- Métodos foram restaurados como operações atômicas: cria controlador + atribui parent.
- Respeitam lock da camada.

## Modelos reutilizáveis

- `TEMPLATES` agora é ferramenta `LIVE` para `PROJECT` e `DESIGN`.
- `Modelos` aparece nos workspaces `Projeto` e `Design`.
- Novo `TemplatesActionControl` usa exclusivamente comandos de histórico existentes:
  - `UpsertReusableComponentCommand` para salvar formas atuais como modelo;
  - `InstantiateReusableComponentCommand` para aplicar modelo no playhead;
  - `DeleteReusableComponentCommand` para remover.
- Aplicação usa prefixo de instância seguro fornecido por `DesignToolsUiPolicy.nextInstancePrefix`.
- Nenhuma cópia manual fora do sistema de undo/redo.

## CI do checkpoint funcional anterior

SHA consultado uma única vez: `c61ad4c94701acff887b6ec149da1d3afc7d83bc`

- Bridge Controller CI: SUCCESS
- Termux Agent Engine CI: SUCCESS
- Android Debug APK: IN_PROGRESS no único checkpoint
- Android Multitrack Export Smoke: IN_PROGRESS no único checkpoint

Não consultar novamente esse SHA.

## Próximos blocos seguros

1. Animação persistente de Null/Group/Camera — exige modelo próprio, persistência compatível e provável schema 8; não improvisar com animação de clip.
2. Tracking aplicado a objeto/camada, separado da estabilização global.
3. Renderer de plano 3D antes de liberar rotationX/rotationY.
4. Melhorias no browser de Modelos (nome/seleção/preview) sem quebrar ReusableComponent atual.
5. Continuar expondo capacidades reais já implementadas antes de criar backend paralelo.
