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
The existing authenticated PROJECT_EDIT bridge capability already requires matching project ID/revision. Its typed operations now accept `trimClip` with exactly one nonnegative project-timeline edge (`newStartUs` or `newEndUs`, microseconds), reusing the same local trim commands as the single-clip bridge. `patchClip`, `deleteClip`, `deleteByKind` clip targets and `trimClip` enforce track and direct composition-layer locks before mutation. Bulk deletion fails atomically when any target is locked; unchanged text/audio/design stay intact, and the existing batch snapshot supplies a single Undo/Redo. Example payload: `{"operations":[{"op":"trimClip","clipId":"clip-1","newEndUs":2000000},{"op":"patchText","overlayId":"text-1","text":"Novo"}]}`. This is not flattened MP4 layer reconstruction; only actual editable project items can be individually modified. Linked-item lock semantics and a complete audit of every command path remain pending; scoped relay, full effect/animation editing, preview QA and physical validation also remain pending. No production deployment.


### Insert-mode lock ancestry
`InsertClipAtTimelineCommand` shifts existing clips whose start is at or after the insertion point. Before building the new immutable project, it now applies the shared track/layer/ancestor lock guard to every shifted clip. A locked ancestor rejects the entire insert without changing the source; a locked clip outside the shifted range does not block the operation. Undo continues to use the captured track snapshot as a restoration command, separate from forward edit authorization. This closes the insert-mode path only; linked-item semantics and a complete audit of all mutation entry points remain pending.

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

### Rendered sample dark-frame warning
Technical review reports nearBlackSamplePositionsUs (requested sample times, not exact decoded PTS) and nearBlackHeuristicOnly. A 16x16 grid with at least 98% RGB channels <=16 flags needs_review, not export failure. Intentional darkness may trigger; sparse sampling cannot establish visual continuity or quality.


### Exported MP4 sample image handoff
Bridge export review encodes the existing sampled frames as up to three JPEGs (max 320px, 64 KiB each), before commit. Only successful publication attaches evidence to the job/source project/source revision/temp SHA-256. Retain one latest export's samples in RAM, expire after 10 minutes on access, clear on next export reservation, no image bytes in ordinary job status. `get_export_review_frames` payload {jobId,sourceRevision}, same active project, authenticated paired device, OAuth device match before and after, controller validates provenance, JPEG header dimensions and bounded canonical base64; MCP emits image parts once and metadata without bytes. Image responses are not retained in replay caches; duplicate IDs return retryable PREVIEW_FAILED, use a new read. Requested sample times are not exact decoded PTS; no guarantee of full continuity, audio sync, published byte readback, or visual approval. Browser generic command still uses existing paired browser authorization. No new endpoint, upload or persistent file. Real MP4 instrumentation decodes returned JPEGs and verifies color, dimensions and hash; unit tests cover expiry, stale jobs, revisions, malformed inputs. Physical and deployed MCP validation pending.

### Editor IA approval UI
The local studio displays Editor IA and an exact parsed-plan summary before choosing the output. A lost in-memory approval after Activity recreation requires revalidation; a SAF provider may already have created an empty destination. Flattened MP4 does not expose original layers. This is not the full existing-project editing UI.


### Existing-project Editor IA local approval
MainActivity exposes Editor IA beside Carrossel TikTok. Existing timeline remains underneath the dialog. Copy bounded non-URI project context to clipboard only on user action, paste GPT JSON {projectId,expectedRevision,operations}, validate typed visual-only operations (1–8 / 8 KiB), dry-run immutable ProjectEditBatchCommand, display exact draft and operation summary, explicit single-use approval. Recheck current session revision and full project snapshot on main-thread apply; use existing history and refresh snapshot. No timing/audio/deletion in this path, no persistent consent after rotation, no automatic remote calls. New-video button reuses AutomaticVideoActivity. Bridge review/export tools remain external; local approval does not gate existing authorized remote operations. JVM tests cover read-only validation, undo, stale, cancellation, duplicate, invalid, privacy. UI/emulator and actual device flow pending.


### Editor IA export review panel
Editor IA subscribes to latest Bridge export snapshot via StateFlow (no polling, no persisted media). Source project/revision captured at reservation; foreign project metadata hidden. Latest queued/running/completed/failed/cancelled state and strict parsed technical metadata, dimensions/duration/audio/decoded samples/dark warnings. Older revision visibly stale; no copy-review action for stale, incomplete, unavailable or mismatched provenance. Explicit clipboard action requests GPT to inspect existing ephemeral samples by job/source revision/hash; no automatic GPT action, images not locally rendered in this panel, no visual approval. Manual SAF exports not included. Terminal job callbacks immutable; unknown explicit status no longer falls back to unrelated active job; cancel rejects terminal/foreign/nonactive jobs and waits for renderer callback. Tests cover status/cancel and UI report policy; physical UI/MCP pending.


### Still-image replacement
Typed `replaceStillImage` takes `clipId`, `expectedAssetId`, `replacementAssetId` (existing image assets only). Local Editor IA shows explicit approval and bounded image IDs; existing batch history, revision and lock checks apply. No flattened-video separation, no new media import or visual render certification. CI pending.


### Rendered still-image replacement fixture
MultitrackExportSmokeTest.editorAiReplacesBackgroundInRenderedIntroWithoutChangingOtherLayers runs actual EditorAiApprovalGate prepare/apply against a synthetic layered 320x180 intro: red old background, green replacement, animated blue overlay, white title, PCM tone. Render before/after with existing Media3 H264/AAC compiler; exact expected model equality except asset reference, sampled background color, title bright-mask overlap, early/late overlay alpha, audio track MIME/packet count/duration, original MP4/source SHA256 and exact Undo. Synthetic fixture, not the user's actual opening; audio track evidence does not prove decoded waveform equality or full synchronization. CI pending; physical device and original media remain pending.

### Studio audio staged-byte validation
Audio duration and track presence are now inspected from the private bounded copy, not the provider URI before copying. Cancellation is checked before and after metadata inspection. This binds the validated duration to the bytes actually used by the renderer; decoder validation is not a full audio decode. Existing portable export instrumentation checks copied WAV hash/duration and corrupt audio rejection. CI pending.


### Studio interrupted export recovery
AutomaticVideoActivity records a unique private preparation job before building, using synchronous committed app-private preferences on IO. No destination URI, media, or approval persisted. Terminal/pre-submit cleanup removes only the exact flat automatic-UUID cache directory and its matching record. If collection ends after submit without terminal event, keep the record and files; a later process detects interruption, removes only its recorded private work directory, warns user to check destination, never retries/overwrites/deletes destination. Same-process records protected against live renderer cleanup; unexpected paths/nested directories fail closed; bounded 32 records. Recovery covers studio preparation artifacts, not controller-owned render temp files or SAF commit crash atomicity; no automatic resumption. Instrumentation simulates owner change and checks original/destination preservation, duplicate/invalid IDs, same-process protection and retry after unsafe contents. Physical process-kill test pending.


### Explicit portable package relay receiver (Android)
New HTTPS open_package handoff accepts only the fixed LocalCut relay origin, exact one-time /v1/mobile-media/<43-character token> path, SHA-256 and 1–20 MiB declared length. Reject unknown/duplicate fields, query/fragment/credentials/nondefault port, custom scheme. Existing local confirmation opens private Editor IA, second explicit confirmation downloads using pinned public DNS, TLS, no proxy/redirect/retry, bounded deadlines and exact HTTP 200, MIME, length and identity encoding. Verify whole archive digest before existing strict ZIP/media staging; failure retains prior selection and removes owned staging directory. Link not persisted in saved state or replayed after recreation. No public upload or server changes in this increment; controller must next produce authenticated package relay entries and matching handoff. Actual HTTP integration and device flow pending. SAF and existing media link behavior preserved except duplicate handoff fields now rejected.


### Authenticated portable package sender (Bridge)
Explicit stage_package MCP tool uses existing fileParams and OAuth device request context, rechecks authorization before download and before staging, requires .localcut filename and custom declared MIME, accepts custom/ZIP/octet-stream upstream, reuses pinned HTTPS fetch and bounded 20 MiB transport. Central directory preflight rejects traversal, duplicates, unsupported compression, encryption, oversized expansion, invalid local references and missing manifest without decompressing. Android remains authoritative for manifest/CRC/asset hashes and actual decoding. Returns open_package handoff with exact bytes/SHA256; existing one-time 5-minute GET relay, no public POST. Token is a temporary bearer link, not bound to device at download; do not share it. Store capped at 8 pending entries (technical resource bound); existing media behavior maintained. No production deployment or live Android transfer yet.


### Relay browser fallback and redemption authorization
The actual GET /open endpoint redirects browser fallback to localcut://command. Android open_package now accepts this exact route in addition to the verified HTTPS route, while retaining the fixed HTTPS relay host, descriptor, size and hash checks and local confirmation. Package staging retains an authorization callback in the bounded temporary entry; the existing OAuth/device check runs again at GET redemption. Denied entries are consumed without serving bytes or affecting other entries. This is still a bearer link, not proof of the downloading device; no new credentials or public upload. HTTP regression verifies the redirect descriptor and post-staging revocation; Android parser regression verifies both routes and rejects path/userinfo/port/fragment variants. CI pending; actual device/production flow pending.


### Revision-bound external MP4 review correction
The existing local Editor IA copy/paste workflow now includes the bounded editable project context and an exact rendered MP4 identity in its review request. Optional review object (jobId, sha256, findings 1–1000 chars) in the local visual plan must match the current successful export's project, revision, job and digest; incomplete/stale evidence offers no identity. Local approval displays user-supplied external findings explicitly as unverified, dry-runs existing operations and rechecks the latest job at confirmation. Changing export even at the same revision invalidates the review approval. Ordinary local plans unchanged; no operation or export without approval, no automatic visual-quality claim. After applying a correction a new export/review is required. Findings and approval are ephemeral; not authenticated GPT output, no automatic transport or durable history. CI pending, physical flow pending.


### Direct export review proposal inbox (Bridge)
New submit_export_review_proposal capability, exact OAuth device scope, expectedRevision, 8 KiB planJson, visual-only envelope. Android requires actual fresh rendered evidence plus current exact job/project/revision/hash, dry-runs existing Editor IA gate, queues one ephemeral proposal per export without editing. Identical pending proposal returns same ID; different/consumed proposals cannot silently replace it. UI explicitly loads or discards, validates again and requires local approval; manual paste remains. Fresh export/expired evidence invalidate queue, source/project/revision checked on load. Auth expiry after delivery may leave unknown delivery; no auto-retry. No proof external findings were actually reviewed, no persistence, no production/device flow yet. No automatic application/export or paid AI.


### Local proposal history
Private bounded metadata history (64 records globally; 8 current-project rows displayed), tracks received, loaded, applied in editor, discarded, rejected, superseded and interrupted. Stores only proposal/project/export identifiers, source revision, MP4 hash, state and resulting revision. No plan/findings/media/URI/consent stored. Restart marks pending/loaded records interrupted, does not restore inbox or apply. UI records applied only after existing local gate succeeds; this does not certify project autosave, visual quality or current project contents. Storage failures visible; corrupt journal preserved. No production/deployment/device QA yet.


### Integrated rendered review regression
PortableStudioPackageSmokeTest now drives an actual rendered package through sampled MP4 evidence, BridgeCodec/BridgeMessageProcessor/BridgeCommandExecutor, exact proposal inbox and local Editor IA approval, re-render through production composition compiler and Media3, decoded color/border, audio/duration/resolution, source hash, new evidence and Undo checks. Private preference history is reopened using an isolated test key. This is an emulator integration with synthetic image/audio and in-process Bridge wire messages; no actual external GPT, OAuth/WebSocket transport or physical device is claimed. Loaded remote proposals must still have live evidence at approval. CI pending.


### Editor IA lifecycle audit: explicit discard versus interruption
A loaded remote proposal previously became DISCARDED when the Compose dialog was disposed for any reason, including configuration changes. The dialog now retains a stable state holder for lifecycle cleanup and records INTERRUPTED only on implicit disposal. Explicit Close/back, creation navigation, clearing or replacing the plan still record DISCARDED; successful approval remains APPLIED_IN_EDITOR and failures remain REJECTED. Added terminal-state and persisted-reopen tests; no proposal text or authorization is restored after recreation, no edit is performed by cleanup. UI/device lifecycle validation remains pending.


### Approved Editor IA edits and durable saving
The local UI distinguishes batch apply (editor memory) from atomic project-store flush. After the single-use approval, it immediately requests a save and reports success only on Result.success. Failure or thrown callback leaves the already-applied undoable edit in memory, keeps the history at APPLIED_IN_EDITOR (not saved), displays a prominent warning and offers a guarded retry for the same revision. A successful save upgrades the metadata-only history to SAVED_TO_DISK; no remote approval, no media copy and no unexpected revert. Debounced saver retains failed changes, while the local retry never marks a later revision as proof for the old edit. This does not guarantee visual correctness, physical device behavior, crash survivability if disk writes fail, or an external GPT judgment. CI pending.


### Storage exception during scheduled or manual autosave
The debounced saver now normalizes ordinary exceptions thrown directly by a ProjectStore into Result.failure, so a scheduled save can record its failure, retain pending edits and use its existing one-shot automatic retry. Manual flush and Editor IA save confirmation receive a failure result instead of an escaped exception. Fatal Error subclasses are not swallowed. Existing serialization and newest-edit behavior remain; scheduled, manual and newer-edit tests were added. This only addresses the exception path, not irrecoverable power loss, unavailable storage or unsaved data after an OS process kill.


### Persistent recovery project selection
A corrupt/unsupported primary project no longer causes a brand-new untracked recovery directory on each process launch. Startup checks existing private recovery-* folders, including legacy timestamped folders, selects the most recently modified valid atomic snapshot and continues editing/saving there; failed snapshots remain untouched. New recovery directories are allocated only when no valid candidate exists; once a project is saved, later launches locate it. Valid primary projects remain preferred. This does not reconstruct edits lost before first durable save, merge divergent recovery projects, or constitute physical device kill testing.


### Bulk and ripple deletion lock ancestry
Bulk visual deletion now checks every target clip with the shared track/layer/ancestor guard before constructing the replacement project. Ripple deletion applies the same guard to the removed clip and every following clip it will shift; a locked GROUP ancestor or invalid hierarchy fails closed before returning an edited project. Added regressions for a bulk-delete target under a locked GROUP and a ripple-shifted clip under a locked GROUP, both asserting the source project remains unchanged. Existing Undo/Redo snapshots and unlocked operations are unchanged. CI is required for this commit; no physical-device test is implied.


### Confirmed TALK duration coverage respects composition locks
The local TALK coverage planner can extend a still-image body clip and can repair a five-second imported voice duration before the later animation-plan command runs. Track-level lock checks alone did not cover a locked composition LAYER or ancestor GROUP on either clip. The planner now calls the shared `TrackEditGuard.requireClipTrackUnlocked` only for the clips it actually mutates: the voice clip when recovering its duration and the image clip when extending its coverage. Read-only/no-op coverage calculations remain unaffected. Regression tests reject both locked-ancestor cases and assert the original immutable project is unchanged. This is domain/JVM coverage; CI and physical-device validation are separate gates.
