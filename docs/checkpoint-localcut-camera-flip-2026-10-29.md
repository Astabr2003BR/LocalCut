# LocalCut checkpoint — câmera, rig e flip — 2026-10-29

Branch: `feature/localcut-bridge-v1`
PR: #13 — manter OPEN + DRAFT; não fazer merge em `main` sem autorização explícita.

## Escopo atualizado

- Publicação/Play Store foi removida do escopo por decisão do usuário.
- Objetivo: desenvolvimento, testes e APK para uso pessoal.
- Não copiar/embutir APK, código, assets ou UI proprietária; reproduzir capacidades desejadas com implementação própria.
- CI anti-loop: uma consulta de status por SHA; se estiver pendente/em execução, não consultar de novo.

## Câmera / parenting

A base já existente foi auditada e confirmada de ponta a ponta:

- `WorldTransformResolver` resolve hierarquia/parenting.
- `CameraProjection` suporta perspectiva/ortográfica e parallax por Z.
- `CompositionRenderResolver` converte grafo/câmera para o contrato 2D seguro.
- `ProjectRenderPlanCompiler` preserva container/timeline de composição.
- `ProjectCompositionCompiler` passa isso ao Media3.
- `TransformEffectFactory` aplica por frame.
- rotationX/rotationY continuam fail-closed até existir renderer de plano 3D real.

Novidades desta rodada:

- `CameraPresetUiPolicy`: Perspectiva forte (750), Normal (1000), Perspectiva suave (1800), Ortográfica.
- `CameraRigUiPolicy`: câmera pode ser parentada a Null/Grupo com ciclo/lock/target validation.
- `CameraRigActionControl`: presets + parent da câmera ativa na ferramenta Câmera.
- `CameraCutFrameSnapPolicy`: novos cuts são alinhados ao frame do FPS do projeto.
- `CameraCutActionControl`: criação/duplicação/cut usa timestamp snapado ao frame.
- `CameraCutUiPolicy.duplicateActiveCameraAt`: duplicação agora preserva `parentId`, evitando salto de enquadramento em câmera riggada.

Commits funcionais relevantes:
- `f23a7218c2c4cf742894eb7b554020f23bc4d645` camera presets
- `39c8ca8b51f358580a4161c311b0b086479250db` camera rig parenting
- `961649fe1b07de15bdf272fa82d234c6228c4e7c` camera rig UI
- `b86c4d6257af40d59fe154409c966dd3e810379b` expose camera rig
- `5af5aa6129140abba689ceb11202707784756b29` frame snap policy
- `8648180683e306b93c765c96e2bf2e8df2e06965` frame-snapped cuts UI
- `335cf42a2239fee918b29034d096f989d0826628` preserve rig parent on duplicate

CI de `335cf42...` foi consultado uma única vez e estava pending/in_progress; não consultar novamente.

## Flip

Backend confirmado real:
- `EffectRegistry` registra `localcut.flip.horizontal` e `localcut.flip.vertical`.
- `Media3EffectStackFactory` renderiza ambos por `MatrixTransformation`.

Novidades:
- `FlipToolsUiPolicy.clear()` remove só os flips e preserva FX não relacionados.
- `FlipActionControl`: Espelhar H / Espelhar V / Reset Flip.
- controle aparece em `Editar` para vídeo/imagem.

Commits relevantes:
- `da4385e6f2c2677f1e98d704f4f4a01ac629b8c2` reset flip
- `1762f5a7cc9689742eeb714b74f684f5a0bc486f` flip UI
- `b0d69eeef84b10109ded6737adf1c246940c30a9` expose flip in Edit

CI de `b0d69ee...` foi consultado uma única vez:
- Termux Agent Engine CI: SUCCESS
- Bridge Controller CI: SUCCESS
- Android Debug APK: IN_PROGRESS
- Android Multitrack Export Smoke: IN_PROGRESS
Não consultar `b0d69ee...` novamente.

## Tracking / Stabilize audit

- `STABILIZE` é LIVE e redireciona para `ANIMATION`; não está morto.
- `TRACKING` permanece PLANNED. Não marcar LIVE até existir tracking de objeto aplicável ao usuário; o OpenCV atual de estabilização não deve ser apresentado como object tracking completo.

## Próximo bloco

1. Expor Crop/Recorte manual no fluxo principal atual sem duplicar Chroma UI.
2. Auditar ferramentas existentes porém escondidas antes de criar novos backends.
3. Depois avançar em 2.5D/plane renderer, sem liberar rotationX/Y antes do backend real.
