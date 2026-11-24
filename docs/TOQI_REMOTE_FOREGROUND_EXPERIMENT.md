# Experimento: aprovação remota com retorno ao editor

Base: `feature/localcut-bridge-v1` em `cdf6885694016660eddfa0a29e82349fa7ab1338`. Esta alteração é experimental, não publicada nem instalada.

`MobileHandoffActivity` abria ChatGPT incondicionalmente quando a aprovação remota era bem-sucedida. O novo parâmetro opcional e restrito `returnTo=editor` permite que o fluxo Toqi retorne ao `MainActivity`. Ausência do parâmetro, ou `returnTo=chatgpt`, preserva o retorno legado ao ChatGPT (com fallback ao editor se o app não abrir). Erros continuam retornando ao editor. O fluxo OAuth e os outros handoffs não são alterados.

Para um cliente Toqi próprio, iniciar a autorização com `POST /remote/api/authorize/start?returnTo=editor`, guardar o cookie de binding e continuar a verificar o status no mesmo cliente. A URL de aprovação conterá o parâmetro; o relay experimental o repassa ao deep link. Alternativamente um deep link direto é `localcut://remote/approve?requestId=REQUEST_ID_VALIDO&returnTo=editor`. O parâmetro de navegação não é uma credencial, nem concede autorização; a aprovação segue autenticada e vinculada ao dispositivo e ao navegador. O usuário ainda precisa concluir a autorização do lado do cliente com o cookie original.

Valores diferentes de `editor`/`chatgpt`, valores vazios, parâmetros duplicados e URLs externas são recusados. Não são aceitas URLs arbitrárias de retorno.

A descoberta remota de contexto usa `__localcut_active_project__`, o marcador reconhecido pelo Android, no lugar de `__active__`. O dispositivo simulado no teste rejeita marcadores diferentes, para evitar regressão. A suíte do controller passou com 116 testes e o typecheck passou. Estes testes são simulados e não comprovam conexão nem controle no aparelho real.

IMPORTANTE: o app atualmente instalado, se for anterior a esta alteração, rejeita o parâmetro adicional. O servidor já publicado não é alterado por este experimento e pode descartar o parâmetro do link HTTPS. É necessário compilar, instalar e validar experimentalmente o Android; para o fluxo HTTPS também é necessário publicar separadamente a alteração do relay, somente com autorização. Não usar IDs reais em relatórios. Não foi verificado em um celular.
