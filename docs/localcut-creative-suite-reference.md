# LocalCut Creative Suite — referência técnica (2026-10-27)

Objetivo: ampliar o LocalCut como editor Android privado, gratuito e sem IA, preservando implementação própria e usando RedTerm/UserLAnd apenas como workers opcionais.

## Referências e licenças

- OpenToonz: código aberto sob Modified BSD para a base principal; revisar licença por arquivo antes de qualquer adaptação direta. Referências prioritárias: Xsheet/cels, onion skin, níveis/camadas, pintura digital, raster+vetor e exposição de frames.
- Blender: GPL-2.0-or-later na maior parte do código. Usar conceitos e, se necessário, executar Blender separadamente como worker no Linux; não copiar/linkar código GPL ao APK atual sem decisão explícita de relicenciamento. Referências: VSE, Grease Pencil, compositor e render headless.
- Krita: GPLv3. Usar como referência de UX/arquitetura de pintura, brushes, camadas e animação 2D; opcionalmente como ferramenta separada no Linux; não copiar código GPL para o APK atual.
- GIMP: GPL. Usar como referência de edição raster, camadas, máscaras, seleção, brushes, curvas e filtros ou como processo separado no Linux.
- Photoshop, Clip Studio Paint, FlipaClip, RoughAnimator e Jitter: produtos proprietários/não há fonte aberta oficial apropriada para incorporação. Reproduzir apenas classes de funcionalidades com implementação própria; não copiar APK, assets, código ou interface idêntica. O fato de o APK ser privado não altera essas licenças.
- “GGI”: a busca não identificou um editor inequívoco com esse nome. Até existir referência exata, o LocalCut trata isso como edição gráfica; GIMP é a referência aberta mais próxima para raster avançado.

## Arquitetura escolhida

1. APK Android é a fonte de verdade do projeto e da timeline.
2. `project_edit` é o comando genérico versionado para edições rotineiras sem criar uma capability nova para cada frase do usuário.
3. O lote é tipado, limitado a 32 operações, atômico e reversível por um único Undo.
4. Operações atuais: `deleteByKind`, `deleteClip`, `deleteText`, `addText`, `addClip`, `patchText` e `patchClip`. Isso cobre adicionar/editar/excluir vídeo, imagem, áudio e texto quando o asset já foi autorizado/importado.
5. `get_project_state` inclui inventário sanitizado e limitado de assets e textos, com IDs e metadados editáveis sem expor URI privada/fingerprint. Isso evita pedir IDs ao usuário e reduz a necessidade de novos APKs para comandos rotineiros, sem criar novas capabilities no Controller.
6. Importação de mídia local continua exigindo interação/autorização Android; URLs públicas HTTPS usam o fluxo existente. Não contornar o sandbox do Android.
7. RedTerm/UserLAnd são workers opcionais para FFmpeg/ffprobe e tarefas pesadas. Blender/GIMP/Krita podem ser usados como processos Linux separados somente quando instalados e quando a tarefa justificar; o APK nunca depende deles para abrir/editar.
8. Não criar endpoint de shell genérico. Jobs pesados continuam tipados, com caminhos/argumentos validados.
9. Se o worker Linux cair, o editor Android continua utilizável e o desenvolvimento usa GitHub/CI como fallback.

## O que incorporar por implementação própria

- OpenToonz / Clip Studio / FlipaClip / RoughAnimator: timeline de cels, exposures/holds, onion skin configurável, camadas de desenho, scrubbing, áudio sincronizado e rotoscopia por referência.
- Krita / GIMP / Photoshop: brush/eraser, seleção/lasso, fill/gradient, layers, masks, blend modes, transform/crop, curvas/níveis, filtros e histórico não destrutivo.
- Jitter: separação entre estado estático de Design e operações de Animate, segmentos de animação na timeline, stagger, duração, easing e animação de texto.
- Blender: edição multifaixa, scopes, adjustment layers, compositor/2.5D e, opcionalmente, 3D/render pesado em worker Linux.

## UX Android — POCO X6 5G

A barra inferior fica dividida em espaços de trabalho focados, reutilizando ferramentas reais já existentes:

- `Editar`: corte/timeline/mídia/texto/áudio/velocidade.
- `Animar`: keyframes/curvas, layers, máscaras, texto, efeitos, velocidade e áudio.
- `Design`: canvas, texto, overlays, componentes e motion.
- `Áudio`: edição, volume, EQ/dynamics, velocidade e mídia.
- `FX`: efeitos, cor, máscaras, chroma, animação e layers.
- `Projeto`: mídia, formato/canvas, texto, áudio, overlays, legendas e design.

A edição de imagem/raster usa hoje as ferramentas reais de Editar/FX/Design. Draw/Paint frame-a-frame e 3D permanecem fases separadas até terem motor real; não exibir ferramenta falsa como `LIVE`. Para o POCO X6 5G, manter preview adaptativo, cache limitado, proxies e processamento pesado opcional fora do processo principal.

## Próximas fases sem IA e sem custo obrigatório

1. Raster Draw Core próprio: bitmap tiles, brush/eraser, lasso, fill, undo por stroke e camadas.
2. Animate 2D: cels/exposures, onion skin, holds e áudio sync sobre o Draw Core.
3. Motion: operações/presets/stagger inspirados no fluxo do Jitter sobre o sistema de keyframes existente.
4. FX/Compositor: ampliar composition graph e máscaras; usar FFmpeg/Blender headless apenas como workers opcionais.
5. 3D opcional: import/export e jobs Blender no RedTerm/UserLAnd; sem tentar embutir Blender no APK.
