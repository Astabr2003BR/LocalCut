# LocalCut — Plano de release consolidado: Estúdio de Animação

**Projeto:** `Astabr2003BR/LocalCut`  
**Alvo:** branch `feature/localcut-bridge-v1`, PR #14 **draft**, sem merge.  
**Decisão:** **não entregar APK ao usuário a cada gesto/efeito**. Agrupar em um único candidato de versão, com QA de regressão e testes no POCO X6 5G antes da aprovação final.

## Base confirmada pelo projeto

- Timeline V/A, mídia via importação HTTPS/autorizada ou compartilhamento nativo, preview Media3, exportação MP4.
- `TALK` local com forma de onda, pose da boca validada, comando reversível, idempotência.
- Piscadas em retrato estático por `localcut.character.eye_blink`, shader GL e handoff Android confirmado.
- `AudioSourceDurationProbe` para distinguir MP3 de 12,54s de clipe padrão 5s.
- Histórico com Undo/Redo; pareamento e serviço de conexão independentes.
- Falhas vistas no aparelho: `Error processing video frames`, retomada da prévia, falha LOW_MEMORY histórica. **Não classificar essas falhas como corrigidas sem novo diagnóstico no dispositivo**.

## Arquitetura para não atualizar o APK a cada animação

1. **Motor estável embutido:** shaders do TALK e retrato, uma cadeia determinística usada na prévia e na exportação. Novo código nativo/GL continua precisando de APK **quando realmente necessário**.
2. **Movimentos parametrizáveis como dados:** `EffectInstance` persistido no projeto: localização dos olhos, intervalo/duração/intensidade da piscada, oscilação lateral e respiração discreta. Sem download de código, sem scripts ou shaders arbitrários remotos.
3. **Presets prontos e editáveis:** Discreto, Natural, Expressivo; usuário altera sliders no painel **Animar** e usa **Aplicar animação**. Salvar/carregar preset pessoal num registro local `portrait-motion-v1`; números estritamente limitados. Não contém mídia, credenciais ou identificadores de conta.
4. **TALK acessível no mesmo painel:** permite marcar boca/voz no mesmo fluxo; preservar a animação existente. Não modificar automaticamente trilhas bloqueadas, voz intencionalmente cortada ou outro clipe.
5. **Comandos móveis coexistem:** `action=lip_sync`, `action=blink` continuam compatíveis. Mudanças em parâmetros não exigem novo endpoint nem reinício do serviço.
6. **Uma atualização consolidada:** não liberar build de desenvolvimento como versão final. Commit após commit apenas dentro da mesma PR draft; uma instalação quando os gates forem aprovados.

## Escopo implementável em uma versão

- [x] TALK já existente; boca com movimento visível em gravação do aparelho (qualidade natural ainda é assunto de evolução)
- [x] Piscadas locais no shader e emulador
- [x] Modos de piscada/calibração parametrizáveis com persistência por projeto
- [x] Um único shader para piscar + oscilação e respiração discretas (evitar mais passes GL)
- [x] Presets numéricos salvos localmente, sem execução de código remoto
- [x] Painel de animação mobile-first no editor, com ajuste das regiões dos olhos
- [ ] Resolver de forma comprovada `Error processing video frames` (exige código/causa no POCO)
- [ ] Medir duração completa de MP3 no dispositivo e validar sem duplicação
- [ ] Validação física e visual de todos os presets, Undo/Redo, preview/export, cold restart, LOW_MEMORY e reconexão

**Limitações deliberadas:** uma única imagem não permite inferir cabeça 3D, fonemas precisos, boca lateral ou expressões complexas com qualidade profissional. Não anunciar esses itens como prontos sem sprites/rig/modelo específico. Não usar upload público anônimo, código remoto nem burlar política de instalação do Android.

## Critérios obrigatórios antes de uma única instalação final

| Caso | Esperado |
|---|---|
| Vazio/sem seleção | Não aplicar efeito ou editar outro clipe |
| Projeto com 1 retrato e áudio | Aplicação reversível; não duplica voz |
| Falta de mídia/fora da internet | Presets locais funcionam; TALK falha com mensagem útil |
| Faixa visual bloqueada | Recusar, sem liberar silenciosamente |
| Reaplicar o mesmo preset | Idempotente, sem duplicar `EffectInstance` |
| Configuração inválida, NaN, grande, malformada | Rejeitar, manter projeto |
| Duas imagens elegíveis | Não escolher uma por adivinhação |
| Presets Discreto/Natural/Expressivo | Diferem nos valores sem código novo |
| Personalizado salvo/carregado | Recupera valores válidos, sem mídia nem conta |
| TALK + piscada + movimento | Prévia e exportação têm os mesmos quadros animados |
| Silêncio | Boca quieta; olhos podem piscar |
| Undo/Redo | Preserva imagem, áudio, TALK e demais efeitos |
| 12,54s de voz | Confere duração do mesmo áudio, sem cópia sobreposta |
| 9:16, 4:5, retrato/landscape | Efeito ajustado ao quadro, sem distorção fora das áreas |
| Rodar em background, retornar, low memory | Sem perda do projeto, sem crashes no fluxo de recuperação |
| Exportar MP4 | Arquivo decodificável, áudio presente, duração coerente, piscada visível |
| Pareamento e autorização | Não alterar credenciais e confirmar ações com escrita |
| APK sobre o atual | Mesmo package/certificado e migração reversível |

## Gates de CI

- **Android Debug APK**: testes unitários Kotlin, compile, assemble; compilação não comprova renderização.
- **Android Multitrack Export Smoke**: emulador com teste de diferença de pixels nos olhos, boca aberta/fechada e preservação de fundo/áudio; regressão total.
- **Bridge Controller CI** e **Termux Agent CI** para não quebrar conexão/importação.
- Verificar SHA-256, certificado e integridade de APK; registrar commit e `run_id`.
- Somente fornecer link de uma versão final depois de todos os gates e da revisão de QA.
- Teste no POCO depende de execução física do usuário; não marcar PASS sem vídeo/log reais.

## Referências técnicas (Android oficiais)

- https://developer.android.com/media/media3/transformer/transformations
- https://developer.android.com/media/media3/transformer/composition
- https://developer.android.com/media/implement/editing-app
- https://developer.android.com/privacy-and-security/risks/dynamic-code-loading

**Política:** configurações numéricas validadas podem evoluir sem recompilar; novo código shader/função Android requer APK e regressão. Não prometer instalação permanente sem upgrades de segurança.
