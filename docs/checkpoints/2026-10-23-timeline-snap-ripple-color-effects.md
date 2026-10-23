# LocalCut checkpoint — timeline snap, ripple delete e color effects

Data: 2026-10-23
Branch: `feature/localcut-bridge-v1`
PR: #13 — deve permanecer Open, Draft e sem merge.

## Entregue neste bloco

### Timeline magnética
- Nova `TimelineSnapUiPolicy`.
- Tolerância de snap definida em espaço visual (8 dp) e convertida conforme o zoom.
- Movimento de clipe pode alinhar início ou fim.
- Alvos: bordas de outros clipes, playhead e beat markers.
- Trim esquerdo/direito usa o mesmo mecanismo.
- O resultado continua passando por `TimelineEditBounds`, preservando proteção contra overlap inválido.
- UI consolidada em um toggle `Ímã ON/OFF`.

### Ripple Delete
- Novo `RippleDeleteClipCommand` undoable/redoable.
- Restrito a tracks `EXCLUSIVE`.
- Remove transições ligadas ao clipe apagado por meio da semântica existente de delete.
- Fecha exatamente a duração removida nos clipes posteriores.
- Preserva gaps pré-existentes.
- Preserva relações entre clipes posteriores porque todos são deslocados pelo mesmo delta.
- Rejeita clipe bloqueado.
- Teste cobre delete, fechamento, transição posterior, undo/redo, gap e rejeição em track overlay.
- Backend pronto; integração explícita `Excluir + fechar` ainda é pendência de UI.

### Cor / efeitos
- `localcut.color.monochrome` com intensidade 0..1.
- `localcut.color.sepia` com intensidade 0..1.
- Renderização Media3 via `RgbMatrix`.
- Presets `P&B` e `Sépia` entram automaticamente no browser de efeitos.
- Teste focado verifica matriz identidade em intensidade 0 e matriz final em intensidade 1.

### Flip
- Branch concorrente já adicionou flip horizontal/vertical no renderer e UI.
- Não foi duplicada essa implementação.

## Gate observado nesta HEAD antes deste checkpoint
HEAD observada: `90f962bf7de7093bfb0f1de6ee721daffb6ab3f8`
- Bridge Controller CI: `success`.
- Android Debug APK: `pending` no único status consultado.
- Android Multitrack Export Smoke: `in_progress` no único status consultado.
- Não fazer polling repetido dessa HEAD.

## Próximas lacunas estruturais
1. Expor Ripple Delete como ação explícita `Excluir + fechar` sem substituir delete normal.
2. Group/Ungroup real para elementos de design, com hierarquia/transformação de grupo persistível.
3. Dissolve/Wipe reais exigem sobreposição/compositor de duas entradas; não anunciar como suportados antes do backend.
4. Reverse/Freeze continuam exigindo backend temporal dedicado.
5. Blend modes avançados continuam exigindo compositor de múltiplas entradas.

## Invariantes
- Sem IA no núcleo nesta fase.
- Não copiar código/assets proprietários de CapCut/Alight Motion/Canva.
- Preservar schema atual enquanto não houver necessidade real de migração.
- Preservar undo/redo/autosave/preview/export e `overrides/`.
- PR #13 não deve ser mesclada sem autorização explícita do usuário.
