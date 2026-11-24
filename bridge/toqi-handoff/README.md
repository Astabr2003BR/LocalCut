# LocalCut ↔ Toqi: gerador local de link

Protótipo local em Python, sem dependências externas. Recebe uma URL HTTPS de arquivo e monta o handoff já testado: `https://localcut-bridge-v1.onrender.com/open?action=import_url&url=URL_ENCODED&returnTo=editor`. O endereço de entrada é codificado como um único parâmetro, preservando sua query e fragmento. O parâmetro de retorno solicita o editor; a aplicação de destino decide o que fazer.

**Não é um MCP, não está instalado ou registrado no Toqi e não integra automaticamente o chat.** É uma função/CLI que um futuro conector pode chamar. Não envia nem hospeda arquivos, não acessa o celular ou o armazenamento local, não faz upload, não valida o tipo ou existência de mídia e não garante que a importação aconteça. É preciso fornecer uma URL de arquivo que o LocalCut consiga acessar; abrir o link gerado dependerá do ambiente e do serviço. `example.com` abaixo é apenas ilustrativo, não uma mídia real.

## Usar

Requer Python 3.10 ou superior. No diretório dos arquivos:

```sh
python3 localcut_toqi.py 'https://example.com/video.mp4'
python3 -m unittest -v
```

A CLI imprime o link no sucesso (código 0) ou uma mensagem de erro no stderr (código 2). Em Python:

```python
from localcut_toqi import criar_link
link = criar_link('https://example.com/video.mp4')
```

Para integrar com Toqi no futuro, uma ferramenta ou adaptador autorizado teria de chamar `criar_link` e apresentar o resultado; esse registro e essa conexão não foram feitos aqui.

## Segurança e limites

- Verificação **sintática e conservadora**, sem DNS e sem rede: aceita HTTPS absoluto com domínio completo ASCII válido ou IP literal global; rejeita credenciais, endereços IP não globais, IPs mapeados, nomes locais comuns, endereços numéricos ambíguos, espaços, controles e barras invertidas. Domínios internacionalizados precisam estar em ASCII/punycode. Há rejeições intencionais de algumas URLs válidas, como domínios com ponto final.
- Isso **não comprova** que um domínio é público: pode resolver para IP privado, mudar de IP, ou redirecionar. Não é proteção SSRF suficiente para um servidor que busque URLs. Qualquer serviço que faça a requisição precisa validar DNS/IP e cada redirecionamento no momento da conexão, proteger contra DNS rebinding e limitar destinos, tamanho e tempo. Este gerador não faz requisições e não controla a segurança do LocalCut/bridge.
- A URL original fica dentro do link enviado ao bridge. Dados sensíveis, tokens de URLs assinadas e parâmetros podem ser expostos ao bridge, registros, histórico e a quem receber o link. Evite URLs privadas ou sensíveis. O código não grava nem envia o link, mas a saída e os serviços usados depois podem fazê-lo.
- Entrada limitada a 2048 bytes UTF-8; link final limitado a 8192 bytes. O limite efetivo da plataforma ou navegador pode ser menor. O gerador não verifica se o arquivo existe, seu tamanho ou formato, nem se o serviço está disponível.
- Os testes usam strings fictícias e nenhum acesso à rede. O handoff foi baseado no formato informado; o protótipo não testa a integração ao vivo.
