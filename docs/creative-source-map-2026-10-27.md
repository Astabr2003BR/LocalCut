# LocalCut Creative Source Map — 2026-10-27

Objetivo: registrar fontes concretas para implementar recursos no LocalCut sem copiar código proprietário e sem incorporar GPL ao APK atual por acidente.

## OpenToonz — candidato para estudo/adaptação permissiva

Licença principal Modified BSD/BSD-3; revisar cabeçalho/licença de cada arquivo e dependência antes de portar qualquer trecho.

Arquivos prioritários:
- `toonz/sources/include/toonz/onionskinmask.h` e `toonz/sources/toonzlib/onionskinmask.cpp`: modelo de máscara de onion skin.
- `toonz/sources/toonz/onionskinmaskgui.cpp`: comportamento/controles de onion skin.
- `toonz/sources/include/toonz/txsheet.h` e `toonz/sources/toonzlib/txsheet.cpp`: Xsheet/células/exposures.
- `toonz/sources/toonz/xshcellmover.h`: movimentação de células/exposures.

Uso planejado no LocalCut: estudar estruturas e adaptar conceitos para `Cel`, `Exposure`, `Hold` e `OnionSkinSettings` em Kotlin, mantendo implementação Android própria.

## Blender — worker/referência GPL

Não copiar/linkar no APK atual. Usar como processo separado no UserLAnd quando instalado ou apenas como referência de algoritmos/UX.

Arquivos relevantes do Video Sequencer:
- `source/blender/sequencer/intern/strip_transform.cc`
- `source/blender/sequencer/intern/strip_time.cc`
- `source/blender/sequencer/intern/strip_edit.cc`
- `source/blender/editors/transform/transform_convert_sequencer_retiming.cc`
- `source/blender/editors/space_sequencer/sequencer_timeline_draw.cc`

Uso planejado: snapping, retiming, multitrack e render/compositor opcional por job tipado no Linux.

## Krita — worker/referência GPLv3

Não copiar/linkar no APK atual. Referências para arquitetura de brush:
- `libs/brush/kis_brush.h` / `kis_brush.cpp`
- `plugins/paintops/libpaintop/kis_brush_based_paintop.cpp`
- `plugins/paintops/defaultpaintops/brush/kis_brushop.cpp`
- `libs/brush/kis_auto_brush.cpp`

Uso planejado: estudar separação entre stroke, brush preset, dab/mask e paint-op; implementar Draw Core próprio em Kotlin/Canvas/bitmap tiles.

## GIMP — worker/referência GPL

Não incorporar código GPL ao APK atual. Usar conceitos de layers, selection, masks, curves e filters; opcionalmente executar GIMP/GEGL como processo Linux separado somente por jobs allowlisted.

## Proprietários

Photoshop, Clip Studio Paint, Jitter, FlipaClip e RoughAnimator são referências de fluxo e funcionalidades. Não copiar APK, código, assets ou interface idêntica. Implementar equivalentes próprios: layers/masks/brushes, cels/onion skin, motion/easing/stagger, frame-by-frame e áudio sincronizado.

## Regra de arquitetura

- APK: fonte de verdade, UI, timeline, preview, projeto e operações interativas.
- `project_edit`: edição genérica remota de vídeo/imagem/áudio/texto/design sem capability nova por frase.
- UserLAnd: worker pesado opcional, com jobs tipados e paths validados.
- RedTerm: terminal/cliente opcional para manutenção, nunca requisito de execução do LocalCut.
- Sem IA e sem custo obrigatório.
