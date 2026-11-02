# LocalCut Creative Source Map — atualizado 2026-10-28

Objetivo: registrar fontes concretas para implementar o máximo possível de recursos úteis no LocalCut sem copiar código proprietário e sem incorporar código com licença incompatível ao APK por acidente.

## OpenCut — código aberto MIT

Fonte oficial estudada: `OpenCut-app/OpenCut`.

A reescrita atual usa um núcleo Rust e declara como direção: Editor API, arquitetura plugin-first, web/desktop/mobile a partir do mesmo core, MCP, modo headless e scripting. A licença do repositório é MIT.

Uso no LocalCut:
- estudar separação entre core de edição e UI;
- contratos de Editor API;
- arquitetura plugin-first;
- execução headless e automação;
- composição de operações sem acoplar o APK a uma ferramenta externa.

Regra: mesmo com licença permissiva, portar apenas trechos realmente úteis e manter avisos/licença quando exigido. Não trocar a arquitetura Android atual só para imitar o OpenCut.

## OpenToonz — candidato para estudo/adaptação permissiva

Licença principal Modified BSD/BSD-3; revisar cabeçalho/licença de cada arquivo e dependência antes de portar qualquer trecho.

Arquivos prioritários:
- `toonz/sources/include/toonz/onionskinmask.h` e `toonz/sources/toonzlib/onionskinmask.cpp`: modelo de máscara de onion skin;
- `toonz/sources/toonz/onionskinmaskgui.cpp`: comportamento/controles de onion skin;
- `toonz/sources/include/toonz/txsheet.h` e `toonz/sources/toonzlib/txsheet.cpp`: Xsheet/células/exposures;
- `toonz/sources/toonz/xshcellmover.h`: movimentação de células/exposures.

Uso no LocalCut: `Cel`, `Exposure`, `Hold`, onion skin configurável, timeline/Xsheet, níveis/camadas, desenho raster/vetor e áudio sincronizado, com implementação Android própria.

## Blender — worker/referência GPL

Não copiar/linkar o código GPL ao APK atual sem decisão explícita de licenciamento. Preferir conceitos ou executar Blender separadamente no UserLAnd quando instalado.

Arquivos relevantes do Video Sequencer e animação:
- `source/blender/sequencer/intern/strip_transform.cc`;
- `source/blender/sequencer/intern/strip_time.cc`;
- `source/blender/sequencer/intern/strip_edit.cc`;
- `source/blender/editors/transform/transform_convert_sequencer_retiming.cc`;
- `source/blender/editors/space_sequencer/sequencer_timeline_draw.cc`;
- código de F-Curves/easing e compositor como referência para keyframes, curvas, masks, blur, displacement, keying e motion blur.

Uso no LocalCut: snapping, retiming, multitrack, compositor/2.5D, câmera, scopes e render pesado opcional por job tipado no Linux.

## Krita — worker/referência GPLv3

Não copiar/linkar ao APK atual. Referências de arquitetura de brush:
- `libs/brush/kis_brush.h` / `kis_brush.cpp`;
- `plugins/paintops/libpaintop/kis_brush_based_paintop.cpp`;
- `plugins/paintops/defaultpaintops/brush/kis_brushop.cpp`;
- `libs/brush/kis_auto_brush.cpp`.

Uso no LocalCut: separar stroke, preset, dab/mask e paint-op; estabilização, pressão quando o modelo suportar largura por amostra, brushes, layers, masks e animação 2D. O Draw Core continua próprio em Kotlin/Canvas/bitmap tiles.

## GIMP / GEGL — workers/referências GPL

Não incorporar código GPL ao APK atual. Usar conceitos de layers, selection/lasso, masks, curves, levels, fill, gradients e filtros. GIMP/GEGL podem rodar como processos Linux separados por jobs allowlisted quando instalados no UserLAnd.

## CapCut — referência proprietária

Sem código oficial aberto apropriado para incorporação. Estudar comportamento e implementar equivalentes próprios para:
- timeline multitrack, cortes, trim/split, speed ramp, reverse/freeze;
- transições, filtros, color grading e efeitos;
- textos, captions, áudio, keyframes, tracking, estabilização e templates;
- UX mobile rápida, preview/proxy/cache e exportação.

Não extrair/copiar APK, código, assets ou interface idêntica.

## Alight Motion — referência proprietária

Estudar e reimplementar:
- múltiplas camadas gráficas/vídeo/áudio;
- keyframes em propriedades e easing/curvas;
- grouping, masking, parent/child e rigs;
- câmera, pan/zoom, depth/focus/fog quando fizer sentido;
- motion blur baseado em variação de posição/escala/ângulo;
- vetores, fills, strokes, gradients, shadows, fontes e elementos reutilizáveis.

O LocalCut já possui base de keyframes/easing/layers e deve evoluir sobre ela sem copiar implementação proprietária.

## Canva — referência proprietária

Estudar e reimplementar:
- canvas/design, elementos, formas, alinhamento, guides e snapping;
- templates/componentes reutilizáveis;
- custom motion paths e animação de elementos;
- organização visual simples para mobile.

## Photoshop — referência proprietária

Estudar e reimplementar:
- layers e masks;
- adjustment layers não destrutivas;
- Curves, Levels, Hue/Saturation, Color Balance;
- blend modes, selections, transform/crop e filtros.

## Clip Studio Paint — referência proprietária

Estudar e reimplementar fluxos de desenho/animação:
- brushes e estabilização;
- camadas raster/vetor;
- cels, timeline, onion skin e light-table/rotoscopia;
- seleção, transformações e organização de animação.

## FlipaClip — referência proprietária

Estudar e reimplementar:
- frame-by-frame simplificado para toque;
- onion skin antes/depois, quantidade, transparência, cores distintas e frame skipping;
- holds/exposures, layers, áudio e waveform;
- timeline mobile com seleção/movimentação de frames.

Implementado no LocalCut nesta fase: onion skin configurável com janela anterior/próxima, frame-step, opacidade e modo tradicional vermelho/verde.

## RoughAnimator — referência proprietária

Estudar e reimplementar:
- timeline tradicional, holds e áudio sincronizado;
- transform onion skin / shift-and-trace;
- multi-select de frames;
- lasso/feather, bucket fill, pressure controls e interpolation/smoothing;
- onion tint e waveform.

## Jitter / Jitter Video — referência proprietária

O fluxo atual do Jitter é uma referência importante para motion: operações aplicadas a layers, combinando move/scale/rotate/color/opacity em animações maiores, além de easing, stagger e animação de texto/geometria.

Uso planejado no LocalCut: criar operações/presets de motion que expandem para os keyframes normais do LocalCut, mantendo o projeto serializável e reversível por Undo.

## “GGI”

Ainda não foi identificado de forma inequívoca qual app/programa foi referido por “GGI”. Não inventar uma correspondência. Até existir referência exata, recursos de edição gráfica avançada ficam cobertos pelas referências GIMP/Photoshop/Krita.

## RedTerm / UserLAnd / Termux — papel correto

- **LocalCut APK:** fonte de verdade do projeto, UI, timeline, preview e edição interativa.
- **UserLAnd/Termux:** motor/worker Linux opcional para tarefas pesadas e ferramentas externas, sem ser requisito para abrir ou editar o projeto.
- **RedTerm:** terminal/cliente SSH opcional para controle e manutenção das sessões Linux; não é o motor de mídia.

O repositório já possui `termux-engine/`, `start_userland_private.sh`, supervisor e detecção de ferramentas. O toolchain atual reconhece FFmpeg/ffprobe e pode reconhecer Blender, OpenToonz, Krita, GIMP e GEGL quando instalados.

Regras do worker:
- jobs tipados e allowlisted;
- caminhos e argumentos validados;
- sem endpoint de shell genérico;
- serviços privados/autenticados;
- falha do worker nunca pode impedir o LocalCut de abrir/editar;
- resultados pesados retornam como mídia/metadados comuns do LocalCut.

## Ordem incremental de incorporação

1. Draw/Animate 2D: brushes, onion skin, cels/exposures, holds, lasso/fill e layers.
2. Motion: operações, presets, stagger, text motion e curvas sobre o motor de keyframes atual.
3. Compositor/FX: masks, blend, adjustment layers, color e efeitos.
4. Vídeo profissional: retiming/speed ramp, reverse/freeze, tracking/stabilization e scopes.
5. Áudio profissional: waveform, EQ/dynamics, beat markers e sincronização.
6. Worker Linux: ampliar jobs FFmpeg/OpenCV/Blender/OpenToonz/Krita/GIMP somente onde agregarem valor real.
7. 2.5D/3D opcional: câmera/parallax/compositor no APK e Blender headless para tarefas pesadas.

Sempre executar em microblocos: teste RED → implementação mínima → GREEN → regressão → próximo bloco, sem lote gigante e sem polling de CI em loop.
