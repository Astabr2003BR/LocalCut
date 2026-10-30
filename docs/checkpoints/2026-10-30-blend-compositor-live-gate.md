# LocalCut — Blend compositor LIVE gate — 2026-10-30

## Escopo

Fechamento do compositor de Blend multi-texture real no branch `feature/localcut-bridge-v1`, preservando preview/export, histórico, autosave, timeline e compatibilidade dos projetos existentes.

## Estado funcional

### Backend real

O LocalCut possui compositor multi-texture próprio para Blend, compartilhado pelo pipeline Media3 usado por preview e exportação.

Modos suportados:

- NORMAL
- MULTIPLY
- SCREEN
- OVERLAY
- DARKEN
- LIGHTEN
- ADD
- SUBTRACT

O shader dedicado implementa os oito modos. `OVERLAY` usa a fórmula por canal com ramificação em 0.5 e não é aproximação de `SCREEN`.

A composição usa Porter-Duff source-over para alpha e valida `alphaScale` em `0..1`.

### Roteamento

- composição somente NORMAL mantém o caminho Media3 padrão;
- qualquer Blend não-NORMAL ativa o caminho `LOCALCUT_MULTI_TEXTURE`;
- o efeito de compositing por item continua responsável por crop, máscara e chroma;
- Blend não é aplicado no efeito por item para evitar dupla composição;
- Blend é resolvido no compositor que recebe source + backdrop.

### UI

O painel visual de Máscara/Compositing agora contém a seção `Mesclagem` com todos os oito modos.

Labels mobile/PT-BR:

- Normal
- Multiplicar
- Tela
- Sobrepor
- Escurecer
- Clarear
- Adicionar
- Subtrair

O modo ativo fica marcado e a lista usa rolagem horizontal.

### Persistência e histórico

Fluxo real:

`FocusedMaskPanel -> EditorAction.SetSelectedCompositing -> SetClipCompositingCommand -> HistoryManager -> preview refresh + autosave`

Consequências:

- alteração de Blend é uma edição real do projeto;
- undo/redo usa o histórico central;
- preview é recarregado depois da edição;
- autosave recebe o projeto atualizado;
- não existe estado paralelo/fake exclusivo da UI.

## Testes e regressão

Cobertura existente relevante:

- `BlendModeMathTest`
- `LocalCutBlendShaderContractTest`
- `BlendCompositorRoutePolicyTest`
- `BlendExecutionPlannerTest`
- `BlendFrameSelectionPolicyTest`
- `BlendInputTimelineTest`
- `BlendOutputLeasePolicyTest`
- `BlendPassPlannerTest`
- `BlendRenderTargetPlannerTest`
- `BlendTextureResourcesTest`
- `KeyframeVideoCompositorSettingsBlendTest`
- `Media3CompositorSettingsPolicyTest`
- `Media3SequenceBlendModeResolverTest`
- `Media3SequenceBlendResolverTest`

Cobertura adicionada neste fechamento:

- `CompositingControlsUiPolicyTest.blendModeSelectionPreservesOtherCompositingAndAllowsEveryImplementedMode`
- `BlendModeUiPolicyTest.optionsExposeEverySupportedBlendModeExactlyOnce`
- `BlendModeUiPolicyTest.normalModeIsFirstAndLabelsAreMobileFriendly`
- `BlendModeSessionHistoryTest.blendModeChangeIsOneUndoableRedoableSessionEdit`

O teste de sessão exige:

`NORMAL -> MULTIPLY -> Undo -> NORMAL -> Redo -> MULTIPLY`

## Commits deste fechamento

- `b1feec2ba78e1192fa509ca1a003fe281454d4c8` — teste de policy de seleção
- `990d4c130f76abc01013d6961d07bbe12630c92e` — setter de Blend no compositing
- `25bfb4adc671b6aa0fec408f4b76678ded273e46` — teste do catálogo mobile
- `255112296596e158b8f173a2788ab27f0af964cd` — catálogo/labels mobile
- `d442ecc57d6b73b072eb61f026e277ba3f57bca4` — UI `Mesclagem`
- `7f71df9704e827716da1b664a9099d13e5111737` — regressão de undo/redo de Blend

## CI observado antes deste checkpoint

Para `d442ecc57d6b73b072eb61f026e277ba3f57bca4`, na única consulta permitida:

- Bridge Controller CI #1436: SUCCESS
- Termux Agent Engine CI #577: SUCCESS
- Android Debug APK #1728: IN_PROGRESS
- Android Multitrack Export Smoke #1582: IN_PROGRESS

Não houve polling repetido desse SHA.

## Gate de encerramento

O código e a cobertura de regressão do Blend estão fechados no branch, mas este checkpoint só pode ser classificado como totalmente validado após os gates Android do HEAD final passarem.

Até essa evidência existir:

`STATUS = IMPLEMENTADO / REGRESSÃO ADICIONADA / CI FINAL PENDENTE`

## Regras preservadas

- PR #13 continua OPEN + DRAFT;
- nenhum merge na `main`;
- nenhuma assinatura alterada;
- nenhum APK/AAB intermediário exigido por este bloco;
- nenhum controle fake;
- nenhuma dependência paga;
- preview/export continuam no pipeline compartilhado;
- GitHub permanece fonte de verdade.
