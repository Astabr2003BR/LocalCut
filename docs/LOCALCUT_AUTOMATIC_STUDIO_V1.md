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


## Portable package v1 — preflight increment
A local .localcut ZIP contains manifest.json and exactly one media/<id> entry per manifest asset. IDs are lowercase ASCII [a-z0-9][a-z0-9_-]{0,63}; image order determines the existing recipe's 1-based indices. Manifest example (hash and size must match the actual file):

```json
{
  "version": 1,
  "recipe": "{\"version\":1,\"format\":\"9:16\",\"scenes\":[{\"image\":1,\"durationMs\":5000}]}",
  "images": [
    {
      "id": "frame",
      "mime": "image/png",
      "bytes": 123,
      "sha256": "0000000000000000000000000000000000000000000000000000000000000000"
    }
  ]
}
```
Optional audio is one object with the same fields (omit if absent). This version only supports still images and optional audio. No external paths or URLs. No archive comments, ZIP64, multi-disk, directories, or extra entries; maximum 128 assets, 64 KiB manifest, 128 MiB per asset, 256 MiB compressed archive and aggregate uncompressed media, 1 MiB central directory. These are technical preflight limits, not production quotas. Read-only validation verifies strict UTF-8 JSON, exact entry set, CRC, lengths, SHA-256, existing recipe and cancellation checkpoints; it does not extract files, decode media, or alter projects. Caller must supply a private immutable local copy and later perform Android media decoding and storage preflight before import. SAF picker, streaming copy/extraction, project integration, physical QA, video assets and return/export are not implemented by this increment. Never treat a successful preflight as full package import.


### Portable package Android staging increment
The Estúdio automático now has an Abrir pacote .localcut SAF picker; selecting a file replaces only the current draft after confirmation if a selection exists. The app streams a bounded copy into its private cache, verifies the package, checks usable space (16 MiB reserve, no guarantee against concurrent disk use), extracts using canonical generated filenames, rechecks SHA-256, verifies image bounds/MIME or audio track/duration, deletes the archive, and exposes local FileProvider URIs to the existing recipe/export flow. Failed/cancelled staging cleans its new directory, previous selection remains. Cache can be evicted; no persistent project or job recovery, video assets, automatic Share entry point, direct GPT return or physical device validation yet. Full image decoding and export are still performed by the existing engine. Cache cleanup on normal finish/replacement; interruption during export may leave cache for OS cleanup. CI required before release.


### Portable package to MP4 — shared preparation and smoke
AutomaticVideoProjectBuilder is now shared by the studio activity and the instrumentation test. The smoke builds a package containing an original green image and a 2-second PCM tone, stages it through the private FileProvider, prepares the existing editor project for a 1-second 16:9 recipe, exports using the existing Media3 composition compiler and Transformer, and checks actual output duration, resolution, audio track, visible green frame and unchanged source hashes. It does not test the SAF picker interaction, production export destination/controller, physical device or persistent project recovery. CI pending until observed.

### Local MP4 preparation
The SAF visual picker also accepts MP4. Recipe v1 `image` indexes the visual list; video scenes start at source time zero and must fit the source duration. The private copy is bounded to 128 MiB per video. Portable ZIP v1 remains image/audio only. Existing project editing, embedded audio behavior and video export verification remain pending.


### Non-destructive MP4 source trimming
Recipe v1 accepts optional integer sourceInMs (default 0) on each scene; image remains the existing 1-based visual-source index, durationMs the output length. Example: `{"version":1,"format":"16:9","scenes":[{"image":1,"sourceInMs":200,"durationMs":500}]}` selects [200,700) ms of an existing local MP4. The planner rejects negative/overflowing offsets, and the builder rejects offsets for still images and intervals beyond video duration. Timeline positions remain contiguous; the existing compiler retains embedded video audio by default. New instrumentation reexports the actual fixture MP4, checks output audio track, approximate duration, decoded pixel and unchanged source SHA-256; CI pending. No claim of preserving editable text/layers from flattened MP4, or full editor operation protocol. Portable package v1 still accepts images/audio only; MP4 via local picker.


### Existing editable projects — typed trim batch and lock enforcement
The existing authenticated PROJECT_EDIT bridge capability already requires matching project ID/revision. Its typed operations now accept `trimClip` with exactly one nonnegative project-timeline edge (`newStartUs` or `newEndUs`, microseconds), reusing the same local trim commands as the single-clip bridge. `patchClip`, `deleteClip`, `deleteByKind` clip targets and `trimClip` enforce track and direct composition-layer locks before mutation. Bulk deletion fails atomically when any target is locked; unchanged text/audio/design stay intact, and the existing batch snapshot supplies a single Undo/Redo. Example payload: `{"operations":[{"op":"trimClip","clipId":"clip-1","newEndUs":2000000},{"op":"patchText","overlayId":"text-1","text":"Novo"}]}`. This is not flattened MP4 layer reconstruction; only actual editable project items can be individually modified. Group/linked-item lock semantics, scoped relay, full effect/animation editing, preview QA and physical validation remain pending. No production deployment.


### Pointwise effect editing
PROJECT_EDIT now supports `addEffect` (clipId/effectId/typeId/numbers), `patchEffect` (clipId/effectId/enabled/numbers), and `setEffectKeyframe` (clipId/effectId/parameter/offsetUs/value). Numeric parameters only, known EffectRegistry types only, same revision and Undo batch; no arbitrary shaders or replacement of the entire stack. Animated parameters must be changed via keyframes, preserving other keyframes and existing easing at a replaced timestamp. LIST_CLIPS returns bounded effect ID/type/enabled/count metadata. Direct and ancestor composition locks enforced in batch and existing animation-plan command. No remote effect removal or general nonnumeric parameter edits added; renderer coverage and physical tests pending.


### Revision-aware preview capture
Existing GET_PREVIEW_FRAME returns MCP image content via the paired bridge. Android capture now invalidates cached frames before project load, seek/step, final-preview load, release and surface changes. In-flight callbacks carry generation/position/time and cannot republish invalidated data; only ready player frames within 2 seconds and 250 ms playhead drift are accepted. Bound 720 px/1 MiB. Optional expectedRevision is checked before capture; response includes projectId/revision; project change during capture fails closed. This is bounded best-effort surface capture, not frame-accurate export QA; a ready surface may still show a previous decoded frame after seek. No new upload endpoint. Multi-frame contact sheet, controller validation, closed-loop review and actual-device validation pending.

Bridge runtime additionally refuses captures unless the editor preview successfully loaded the current project. Failed loads, release/background suspension, and unversioned final-rendered-file previews are excluded until the matching editable project is loaded again. This guard is intentionally conservative; ordinary local preview playback is unchanged.


### Bounded multi-frame GPT review (MCP)
`review_preview_frames` collects 2–4 ascending, distinct timeline positions from the currently loaded editable project. Request deviceId/projectId/expectedRevision/positionsUs; the OAuth token's approved device must match and is revalidated at each step. Uses existing paired commands (get_project_state, revision-bound seek, get_preview_frame, final get_project_state), at most three capture attempts per position, validates returned revision, position drift <=250ms, increasing actual timestamps, dimensions <=720 and canonical image bytes <=1 MiB/frame, with a 25-second admission budget and existing router timeout. A failed capture, auth loss, revision change or timeout discards all images. Output MCP image parts are not duplicated in JSON metadata. One review per device concurrently; pause playback before use, visible playhead moves and is not restored to avoid overriding a local user's cursor. No project edits or new storage/upload/API fees. Approximate editor surfaces, not exact final MP4 frames; individual local/remote seeks can still interfere, and a pending command can finish after the admission deadline. Full closed-loop review, physical and exported-file QA pending.


### Review-guided visual correction
The authenticated MCP review tool now returns a single-use reviewId bound to the device, project, revision and sampled positions; latest evidence only, 5-minute TTL, max 64 in-memory metadata receipts. `apply_review_correction` accepts at most 8 visual operations / 8 KiB: patchClip transform/opacity, patchText visual style/content, addEffect, patchEffect, setEffectKeyframe. No deletes, timing, audio or media changes. Existing Android atomic PROJECT_EDIT and revision/lock validation remain authoritative. On confirmed commit, automatically collect the same positions at the new revision; return images and next reviewId, capped at 3 corrections per chain. The agent must inspect before/after and stop on acceptance or uncertainty. A new independent review starts a new chain. No automatic rollback; a failed verification does not undo a committed edit; transport ambiguity is applied=unknown, never retry automatically. No image storage in receipts, no model/API calls. MCP tool availability and physical preview quality still require deployed integration/device QA; no actual visual acceptance claimed.


### Rendered MP4 technical review
Bridge exports inspect the private Media3 temporary MP4 on the existing destination commit worker, before publishing; metadata is attached only after successful commit. SHA-256/bytes, display resolution, duration vs project (150ms or two frames tolerance), video/audio track counts/codecs/durations, audio sample rate/channels and up to three bounded 320px sampled decodes (API 27+) are returned in get_export_status technicalReview; sourceProjectId/sourceRevision bind output to the export snapshot, not the current live revision. Inspection failure is explicitly unavailable, does not suppress or delete a successful export. No image bytes, URI or path retained in the job report; no new upload. The digest describes rendered temp bytes prior to copy, not read-back verification of the published file. This is NOT complete decode, visual continuity or audio-sync certification; no automated visual approval. Tests reuse real portable MP4 render and validate metadata/hash/sample decode, corrupt file, wrapper failure and provenance. Device and final sampled-image transport pending.
