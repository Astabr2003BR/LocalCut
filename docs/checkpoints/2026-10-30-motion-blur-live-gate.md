# LocalCut — Motion Blur LIVE gate — 2026-10-30

## Escopo

Implementação do efeito já registrado `localcut.motion_blur` como blur temporal real no branch `feature/localcut-bridge-v1`, preservando o contrato salvo existente:

- `shutterAngle`: 0..360, default 180
- `samples`: 1..64, default 8

Sem schema migration, dependência paga ou fallback para Gaussian Blur.

## Arquitetura

Fluxo por clip:

`TransformEffectFactory -> Media3CompositingEffectFactory -> Media3EffectStackFactory -> LocalCutMotionBlurEffect`

O Motion Blur recebe pixels já transformados, então movimento criado por keyframes/transform do LocalCut participa do blur. Crop/máscara/chroma permanecem no compositing por item. O compositor Blend multi-layer continua depois do processamento por item, evitando confundir blur temporal com blend entre camadas.

## Backend temporal

`LocalCutMotionBlurEffect` usa um `GlEffect`/`BaseGlShaderProgram` próprio.

Características:

- frame atual + histórico próprio de frames anteriores;
- máximo efetivo de 8 samples no shader: 1 atual + até 7 históricos;
- shader desenrolado, sem array dinâmico de samplers;
- pesos calculados e normalizados por `MotionBlurPolicy`;
- `shutterAngle = 0` ou `samples = 1` = passthrough;
- sem deslocamento espacial/Gaussian Blur;
- histórico copiado para texturas próprias — texture ID de entrada do Media3 não é retido entre frames;
- copy pass nunca liga a textura-FBO alvo como sampler;
- FBO e viewport do Media3 são salvos/restaurados após a cópia;
- histórico é resetado em configure, release, timestamp não crescente e salto temporal >250 ms;
- ring buffer determinístico newest-first;
- resources de history são liberados mesmo após falha parcial.

## Política mobile de memória

Orçamento exclusivo do histórico: 80 MiB.

A quantidade efetiva respeita simultaneamente:

- request salvo 1..64;
- hard cap de shader 8;
- resolução;
- precisão estimada 4 Bpp SDR / 8 Bpp high precision.

Exemplos cobertos:

- 720p high precision, request 64 -> 8
- 1080p high precision, request 64 -> 6
- 4K SDR, request 64 -> 3
- 4K high precision, request 64 -> 2

## UI

Nova categoria no browser profissional:

- `Movimento`
- preset `Motion Blur`
- `Obturador`
- `Amostras`

A UI só foi exposta depois do backend temporal existir.

## Histórico/autosave

O fluxo permanece o central do editor:

`FocusedEffectsPanel -> EditorAction.SetSelectedEffects -> SetClipEffectsCommand -> HistoryManager -> preview refresh + autosave`

Nenhum estado temporário exclusivo de Motion Blur foi criado.

## TDD / evidência local

### MotionBlurPolicy

RED observado por compilação local: referência ausente para `MotionBlurPolicy`.

GREEN observado:

`PASS MotionBlurPolicyHarness`

Depois do edge case de seek:

RED observado: `unresolved reference: shouldResetHistory`

GREEN observado:

`PASS MotionBlurPolicySeekHarness`

### Shader contract

RED observado:

`unresolved reference: LocalCutMotionBlurShaderContract`

GREEN observado:

`PASS LocalCutMotionBlurShaderContractHarness`

### History ring

RED observado:

`unresolved reference: MotionBlurHistoryRing`

GREEN observado:

`PASS MotionBlurHistoryRingHarness`

Um erro de sintaxe ocorreu somente no harness temporário local (`List<Int>=` sem espaço), foi corrigido no harness e não existia no arquivo do repositório.

## Testes adicionados

- `MotionBlurPolicyTest`
- `LocalCutMotionBlurShaderContractTest`
- `LocalCutMotionBlurEffectTest`
- `MotionBlurHistoryRingTest`
- `Media3EffectStackFactoryMotionBlurTest`
- cobertura Motion Blur adicionada a `EffectBrowserUiPolicyTest`

## Commits principais do bloco

- `0caefa810b66e8edffdd8ce19556c8c59b9736bc` — RED policy
- `f29b4bea158534534bf7dab6e37ddbda265f3802` — bounded policy
- `4d0b962a9ffa8fe0a4b6269eb93d255300299ced` — RED shader contract
- `435d7da48ff7b6e300b5a4a2b4a4492a14cc8f2d` — RED effect gate
- `f43f63bbf8e9fb33a18b3e78bc81569e8c70f84b` — shader contract
- `d3927eaa6bf4ca66f53cc553d2387535189bfba4` — RED seek reset
- `b1f98d6ea3ed1781b81f59b342de607465775412` — seek reset policy
- `baa047fd8565b5548999d76419889de119267898` — initial temporal GPU effect
- `1f9f91b984e01269753310e9d044bab0d1d92134` — RED history ring
- `4e81a263b5e9f178b44cd599c2cc3efe9ee3f295` — history ring
- `53648e2efd7dc610e3a2f36bdb9b6cd3f18cc7cf` — hardened GPU history lifecycle
- `39e1658500ab2eba215dd03f082beac4f51fc247` — RED Media3 wiring
- `35eec424f235af6fb389146a1f6005865d6f3127` — Media3 wiring
- `af74206e9b6a33dc5dc0c536b3a3df8d888248c4` — RED UI
- `5f7223eb0eb6d55617aa72d1916337d0b783ee0c` — Motion browser UI

## CI discipline

Para o RED `39e1658500ab2eba215dd03f082beac4f51fc247`, houve uma única consulta:

- Android Multitrack Export Smoke #1598: pending
- Android Debug APK #1744: pending
- Bridge Controller CI #1452: in_progress
- Termux Agent Engine CI #593: in_progress

Esse SHA não será consultado novamente.

## Gate final

A implementação só será marcada como Android-validada após o CI do HEAD final deste checkpoint compilar e executar os testes Android/Unit relevantes.

Teste físico de GPU no Poco/Android ainda continua sendo gate separado: CI/assemble não prova compilação real do shader nem qualidade visual do temporal blur no dispositivo.

## Regras preservadas

- PR #13 OPEN + DRAFT;
- sem merge na `main`;
- assinatura intacta;
- sem APK intermediário obrigatório;
- sem dependência paga;
- sem Gaussian Blur disfarçado de Motion Blur;
- preview/export compartilham o mesmo efeito Media3;
- GitHub continua fonte de verdade.
