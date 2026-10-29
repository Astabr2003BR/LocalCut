# LocalCut checkpoint — parenting/camera + modern animation — 2026-10-29

Branch: `feature/localcut-bridge-v1`
PR: #13 — manter OPEN + DRAFT; não fazer merge em `main` sem autorização explícita.

## Escopo fixo

- Play Store/publicação está fora do escopo do projeto.
- Foco: desenvolver, testar e manter o APK LocalCut para uso do usuário.
- Não gerar APK/AAB sem autorização explícita.
- CI: no máximo uma consulta por SHA; não fazer polling de SHA pendente.

## Estado concluído nesta rodada

### Parenting/câmera + PropertyAnimation

Foi corrigido o gap em que `CompositionRenderResolver` achatava pai/câmera nos transforms legados, enquanto `propertyAnimations` modernas (`transform.x/y/scale/rotation/opacity`) permaneciam no espaço local e podiam sobrescrever o resultado mundial no renderer.

A solução mantém o RenderPlan estático compatível e transporta um `CompositionTransformContainer` reversível:

1. resolve transform legado em world/camera space;
2. antes de aplicar PropertyAnimation, remove o container para recuperar local space;
3. avalia a animação moderna local;
4. reaplica o container por frame.

Isso preserva parenting/câmera para Motion Path, estabilização e demais animações modernas sem amostragem aproximada.

Arquivos principais:
- `media/CompositionRenderResolver.kt`
- `media/PropertyTransformRenderResolver.kt`
- `media/RenderPlan.kt`
- `media/Media3CompositionBlueprint.kt`
- `media/ProjectRenderPlanCompiler.kt`
- `media/TransformEffectFactory.kt`
- `media/Media3SequenceOpacityResolver.kt`
- `media/ProjectCompositionCompiler.kt`

Testes adicionados/atualizados:
- `PropertyTransformRenderResolverTest`
- `Media3PropertyAnimationBlueprintTest`

### Camadas / parenting móvel

- `+ Grupo` agora cria `CompositionNodeKind.GROUP` real no painel Camadas.
- Null e Grupo podem ter parent Null/Grupo, com rejeição de ciclos.
- Câmera não é parentável por esse fluxo.
- Controladores mostram `Sem parent`, `Desvincular` e alvos de parent compatíveis.
- Camada selecionada ganhou atalhos `+ Null pai` e `+ Grupo pai`, que criam e vinculam atomicamente em uma ação.

Testes:
- `LayerPanelGroupUiPolicyTest`
- `LayerControllerParentUiPolicyTest`
- `LayerQuickParentUiPolicyTest`

## CI desta rodada

- SHA `df1f1a791bf663248bd563399705366ccee0e5a6`: consulta única; Termux e Bridge passaram, Android Debug e Multitrack ainda estavam executando. Não consultar novamente.
- SHA `72d8834cbcacc2e2dd555117728478392ba37054`: consulta única; workflows ainda não registrados. Não consultar novamente.
- SHA `50f375b2b1e99acaae99066084cdddb337187769`: consulta única; workflows ainda não registrados. Não consultar novamente.

## Próximo bloco sugerido

Continuar a evolução do layer graph/câmera sem UI falsa:
- avaliar suporte seguro a camera cuts discretos;
- manter rotationX/rotationY bloqueados até existir renderer de plano 3D real;
- depois avançar para controles de rig/parenting e câmera com preview/export equivalentes.
