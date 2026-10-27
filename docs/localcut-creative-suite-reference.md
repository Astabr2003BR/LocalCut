# LocalCut Creative Suite — referência técnica (2026-10-27)

Objetivo: ampliar o LocalCut como editor Android privado, gratuito e sem IA, preservando implementação própria e usando RedTerm/UserLAnd apenas como workers opcionais.

## Referências e licenças

- OpenToonz: código aberto sob Modified BSD para a base principal; revisar licença por arquivo antes de qualquer adaptação direta. Referências prioritárias: Xsheet/cels, onion skin, níveis/camadas e exposição de frames.
- Blender: GPL-2.0-or-later na maior parte do código. Usar conceitos e, se necessário, executar Blender separadamente como worker no Linux; não copiar/linkar código GPL ao APK atual sem decisão explícita de relicenciamento.
- Krita: GPLv3. Usar como referência de UX/arquitetura de pintura e opcionalmente como ferramenta separada em Linux; não copiar código para o APK atual.
- GIMP: GPL. Usar como referência de edição raster/camadas/filtros ou como processo separado em Linux.
- Photoshop, Clip Studio Paint, FlipaClip, RoughAnimator e Jitter: produtos proprietários/não há fonte aberta oficial apropriada para incorporação. Reproduzir apenas classes de funcionalidades com implementação própria; não copiar APK, assets, código ou interface idêntica.
- “GGI”: não foi identificado um editor moderno inequívoco com esse nome; tratar como categoria de edição gráfica até haver referência mais específica.

## Arquitetura escolhida

1. APK Android é a fonte de verdade do projeto e da timeline.
2. `project_edit` é o comando genérico versionado para edições rotineiras sem criar uma capability nova para cada frase do usuário.
3. O lote é tipado, limitado a 32 operações, atômico e reversível por um único Undo.
4. Operações iniciais: deleteByKind, deleteClip, deleteText, addText, patchText e patchClip.
5. Importação de mídia local continua exigindo interação/autorização Android; URLs públicas HTTPS usam o fluxo já existente.
6. RedTerm/UserLAnd podem executar FFmpeg/ffprobe e ferramentas Linux pesadas em jobs tipados. Não criar endpoint de shell genérico.
7. Se o worker Linux cair, o editor Android continua utilizável e o desenvolvimento pode usar GitHub/CI como fallback.

## Roadmap funcional

- Edit: vídeo, imagem, áudio, texto, timeline, keyframes, transições, exportação.
- Draw/Paint: camadas, brush/eraser, seleção, máscaras, crop/transform, fill/gradient, blend modes, filtros e ajustes.
- Animate 2D: cels/exposures, Xsheet, onion skin, frame holds, layers de desenho, áudio sync e referência/rotoscopia.
- Design/Motion: keyframes, graph/curve editor, easing, presets e composição orientada a objetos.
- FX/Compositor: máscaras, nós/effects graph e composição 2.5D.
- 3D opcional: jobs pesados via Blender headless no Linux, mantendo preview/resultados no Android.

## UX Android

Estruturar por modos/telas (Edit, Draw, Animate, Design, Audio, FX/Compositor e 3D opcional) em vez de colocar todas as ferramentas numa única tela. Priorizar preview adaptativo e caches limitados para o Poco X6 5G.
