# LocalCut — Extract Audio LIVE gate — 2026-10-30

## Fonte de verdade

- Repositório: `Astabr2003BR/LocalCut`
- Branch: `feature/localcut-bridge-v1`
- PR: #13 — manter OPEN + DRAFT; sem merge em `main` sem autorização explícita.
- Publicação/Play Store permanece fora do escopo.

## Estado funcional

O fluxo `Extrair áudio` está integrado de ponta a ponta:

`vídeo selecionado -> snapshot/stale guard -> Termux Agent Engine EXTRACT_AUDIO -> FFmpeg AAC/M4A -> download verificado -> inspeção Android -> ApplyExtractedAudioCommand -> nova track AUDIO -> history/autosave`

### Android/editor

- somente asset `VIDEO` é aceito;
- source range, duração, tamanho, MIME e fingerprint entram no guard;
- resultado stale é rejeitado antes da aplicação;
- o áudio extraído é adicionado em uma track `AUDIO` alinhada ao clip visual;
- volume/fades/timeMap/audioProcessing compatíveis são preservados;
- o clip visual original é mutado para `volume = 0.0` para evitar áudio duplicado;
- aplicação é um comando de histórico com undo/redo;
- timeline é validada antes de aceitar o novo projeto;
- generated media permanece protegida enquanto houver undo/redo possível.

### Agent Engine

O caminho de produção usa `localcut_supervisor.py`, que cria `create_audio_toolchain_server`; o engine base isolado não é o entrypoint de produção.

A extensão de áudio:

- anuncia `EXTRACT_AUDIO` e `DOWNLOAD_GENERATED_MEDIA` somente com FFmpeg/output spool disponíveis;
- valida que a entrada é vídeo e limita a duração a 10 minutos;
- executa worker fora da resposta de staging;
- gera `audio/mp4` em `output.m4a`;
- cancela publicação se o job for cancelado;
- remove mídia gerada em falha;
- não expõe filesystem path no protocolo público.

O executor FFmpeg usa argv com `shell=False`, `-nostdin`, `-map 0:a:0`, `-vn`, `-sn`, `-dn`, codec AAC, bitrate 192k e `+faststart`. Timeout/erro de processo remove o output.

### Integridade de transporte

O cliente Android baixa para arquivo `.part`, valida tamanho e SHA-256 antes de publicar o destino final. A camada específica de áudio valida ainda job id, MIME `audio/mp4` e canonical path.

## Bug corrigido neste fechamento

### Sintoma

Se o download já tivesse sido publicado no destination do LocalCut e a validação específica posterior falhasse — por exemplo MIME inválido, job id divergente ou returned path divergente — o `.m4a` local podia permanecer órfão.

### Causa raiz

`TermuxMediaAudioExtractCoordinator` chamava `downloadGeneratedMedia(...)` e depois executava os `require(...)` sem cleanup do destination em caso de rejeição posterior.

### Correção

A fase download + validações foi colocada dentro de um `try/catch` que remove **somente o `destination` criado pelo LocalCut** antes de propagar o erro. O arquivo apontado pelo backend nunca é apagado por confiança no caminho retornado.

Commits:

- `37bd24461e273462a3678b1e6190f912269650f4` — regression tests para cleanup de download rejeitado;
- `4fbf9eb84882a5428b93bf04799525b83c629901` — cleanup do destination em falha de download/validação.

## TDD / evidência executada

### RED real

Cenário:

1. fake download grava o destination `.m4a` do LocalCut;
2. backend devolve MIME `video/mp4`;
3. coordenador rejeita com `TERMUX_EXTRACT_AUDIO_DOWNLOAD_TYPE_INVALID`;
4. antes do patch, o arquivo permanecia no diretório.

Resultado observado: FAIL por arquivo órfão restante.

### GREEN real

O mesmo cenário após o patch termina com o diretório do LocalCut limpo.

Regressão adicional executada:

- backend retorna outro path;
- destination pertencente ao LocalCut é removido;
- arquivo externo/sentinel não é removido nem alterado.

Resultado observado: PASS para cleanup + proteção contra path spoofing.

### CI observado — regra anti-loop

Para `4fbf9eb84882a5428b93bf04799525b83c629901`, houve uma única consulta:

- Bridge Controller CI: SUCCESS;
- Termux Agent Engine CI: SUCCESS;
- Android Debug APK: não apareceu na leitura;
- Android Multitrack Export Smoke: não apareceu na leitura.

Não consultar novamente esse SHA apenas para polling.

## Limitações / gates restantes

- não houve teste físico do fluxo em Termux/Android nesta conversa;
- o container desta conversa não conseguiu clonar o GitHub por falha de resolução DNS, portanto a suíte Python completa não foi reexecutada localmente aqui;
- Android build/smoke do SHA acima não está confirmado;
- cancelamento durante FFmpeg impede publicação, mas o processo FFmpeg pode continuar até terminar/timeout antes de o worker observar `CANCELLED`; isso é uma limitação de responsividade, não uma corrupção conhecida de estado.

## Próximo alvo

Não reabrir `Extract Audio` sem regressão concreta. Continuar a auditoria do pipeline de áudio avançado (EQ/dynamics/pitch/time/noise/mixing) contra o código atual e só promover/adicionar controles que tenham backend real, persistência quando aplicável, undo/redo e equivalência preview/export.

## Status

`IMPLEMENTADO / BUG DE CLEANUP CORRIGIDO / TERMUX+BRIDGE CI VERDES / ANDROID GATE PENDENTE`
