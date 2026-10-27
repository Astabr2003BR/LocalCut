# LocalCut Creative Suite — referências técnicas e de licença

Data da pesquisa: 2026-10-27

## Regra do projeto

LocalCut permanece um editor Android independente, gratuito e sem IA. Referências externas servem para aprender arquitetura, fluxos e classes de ferramentas; o projeto não copia APK, assets, identidade visual nem implementação proprietária. Código externo só pode entrar quando a licença do arquivo/componente for compatível e a atribuição for preservada.

## Matriz de referências

| Referência | Código/licença | Capacidades úteis ao LocalCut | Política de integração |
| --- | --- | --- | --- |
| OpenToonz | Repositório oficial `opentoonz/opentoonz`; arquivos fora de `thirdparty` e `stuff/library/mypaint brushes` usam Modified BSD; terceiros têm licenças próprias | xsheet/exposure, onion skin, desenho frame a frame, níveis/camadas, câmera 2D, efeitos | Pode ser estudado e, por arquivo, reutilizado/adaptado somente após checagem da licença e atribuição. Não importar diretórios de terceiros sem auditoria. |
| Blender | GPL; código-fonte aberto | curvas, câmera, transformações, compositor, tracking, 3D, render headless | Não linkar/colar código GPL no núcleo independente do LocalCut. Usar conceitos; opcionalmente executar Blender separadamente no workstation UserLAnd/RedTerm quando disponível. |
| Krita | GPL; código-fonte aberto | brushes, raster/vector, camadas, máscaras, onion skin, animação | Estudar arquitetura e UX. Reimplementar no LocalCut; integração executável separada apenas se fizer sentido. |
| GIMP | GPL; código-fonte aberto | seleções, masks, layers, transforms, filtros, pintura/clonagem | Estudar algoritmos/UX em alto nível; não incorporar código GPL diretamente no APK. |
| Adobe Photoshop | Proprietário | camadas, máscaras, adjustment layers, edição não destrutiva, seleções, healing/clone, filtros | Referência funcional somente. |
| Jitter | Serviço/editor proprietário | motion design por operações, easing, presets, stagger, animação de texto, transforms | Reimplementar o conceito de `MotionOperation` sobre as propriedades já animáveis do LocalCut; não copiar UI/assets. |
| Clip Studio Paint | Proprietário | raster/vector, brushes, rulers, cels, timeline, onion skin, câmera, áudio | Referência funcional somente. |
| FlipaClip | Proprietário | frame viewer, onion skin, desenho frame a frame, layers, áudio/rotoscope | Referência funcional somente. |
| RoughAnimator | Proprietário | exposição de frames, onion skin, scrub, waveform/audio, rotoscope, brushes | Referência funcional somente. |
| RedTerm | `GlobalTechInfo/RedTerm`, GPL-3.0; Android/proot, foreground service, múltiplas distros/sessões | workstation Linux local e persistente | Usar como aplicativo externo/workstation. Não embutir o código GPL no LocalCut. |
| UserLAnd | Aplicativo Linux/proot externo | workstation Linux local para FFmpeg/ffprobe e ferramentas CLI | Usar como backend opcional através do LocalCut Agent Engine e jobs tipados. |

## Observação sobre “GGI”

A pesquisa não encontrou um editor moderno claramente identificável como “GGI” equivalente aos demais nomes. Nesta fase, a referência de edição de imagem correspondente é GIMP. Nenhum componente chamado “GGI” será inventado ou incorporado sem uma fonte identificável.

## Arquitetura escolhida

### Android — sempre disponível
- timeline multitrack e preview;
- edição de vídeo/imagem/áudio/texto;
- desenho e frame animation progressivamente;
- motion design, keyframes/operações, masks e compositing;
- histórico/undo e projeto serializável;
- exportação local;
- `PROJECT_EDIT` para comandos semânticos em lote.

### UserLAnd/RedTerm — workstation opcional
- `ffprobe`/FFmpeg;
- análise/transcode/cache/proxy media;
- futuros jobs de imagem e 3D quando houver ferramenta local instalada;
- execução somente por schemas de jobs permitidos; nunca shell arbitrário vindo do modelo.

O Android continua funcional se o workstation estiver desligado.

## Roadmap de módulos

1. **Command Bus** — `PROJECT_EDIT`, batch atômico, delete/add/patch e um Undo por comando.
2. **Draw** — canvas raster, brush engine próprio, eraser, color/size/opacity, layers.
3. **Animate** — frames/cels, exposure, onion skin, frame viewer, audio scrub.
4. **Motion** — `MotionOperation`, presets, easing, stagger, text animation e graph/curve editor.
5. **Image/Design** — masks, selections, blend modes, nondestructive adjustment/effect stack.
6. **Audio** — multitrack controls, fades, gain/pan, waveform, markers/beat tools sem IA.
7. **Compositor/3D** — camera/curves/compositor nativos onde leves; jobs headless opcionais no workstation para tarefas pesadas.
8. **UI mobile** — Home/Projetos, Editar, Desenhar, Animar, Motion, Áudio, Assets e Exportar, sem reproduzir interface de outro produto.

## Fontes primárias consultadas

- OpenToonz: https://github.com/opentoonz/opentoonz
- Blender: https://www.blender.org/about/license/ e https://projects.blender.org/blender/blender
- Krita: https://krita.org/ e https://invent.kde.org/graphics/krita
- GIMP: https://www.gimp.org/source/
- Jitter: https://jitter.video/
- Clip Studio Paint: https://www.clipstudio.net/
- FlipaClip: https://flipaclip.com/
- RoughAnimator: https://www.roughanimator.com/
- RedTerm: https://github.com/GlobalTechInfo/RedTerm
