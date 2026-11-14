# LocalCut — Estúdio automático: primeiro incremento

## Uso
No carrossel, toque em **Estúdio automático • criar vídeo**. Selecione imagens locais, confira a numeração e opcionalmente selecione áudio. Cole um roteiro JSON v1, por exemplo:

```json
{"version":1,"format":"9:16","scenes":[{"image":1,"durationMs":5000},{"image":2,"durationMs":3500},{"image":1,"durationMs":2500}]}
```

Valide, autorize e escolha o destino MP4 pelo seletor Android. O vídeo é composto pelo motor Media3 já usado pelo editor. O áudio é cortado ao final do vídeo se for mais longo. O projeto aberto no editor não é modificado.

## Escopo
- Imagens estáticas, duração por cena, repetição/reordenação por índice, áudio opcional, MP4, 30 fps, proporções 9:16 / 4:5 / 16:9 / 1:1.
- Sem IA, serviço pago, créditos, rede obrigatória ou cotas comerciais. O limite técnico do JSON é 65536 caracteres; o aparelho e os codecs determinam os recursos de renderização. Não há limite de 35 cenas neste planejador.
- O primeiro incremento não implementa pacote ZIP .localcut, transporte automático de anexos do ChatGPT, fila persistente, preview editável, legenda ou animação automática. O roteiro deve ser colado e as mídias selecionadas pelo usuário. Renderização ocorre na tela; interrupções podem exigir nova exportação.
- Não publicar ou atualizar o APK instalado até aprovação do CI e teste de assinatura. O teste físico permanece necessário.

## QA
AutomaticVideoPlanTest (JVM): ordem, duração, referências inválidas, overflow, mais de 35 cenas. AutomaticVideoRecipeSmokeTest (Android): parsing estrito, versão, duplicidade, tipo, campo desconhecido, referências inválidas, conteúdo extra e limite de tamanho. O teste Android existente de multitrack cobre o motor compartilhado; o fluxo novo de UI/MP4 exige teste físico separado.
