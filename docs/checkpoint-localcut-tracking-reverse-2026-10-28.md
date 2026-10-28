# LocalCut checkpoint — tracking, stabilization, freeze and reverse — 2026-10-28

## PROJECT
LocalCut

## BRANCH
`feature/localcut-bridge-v1`

## PR
#13 — OPEN + DRAFT. Do not merge without explicit authorization.

## FIXED CI POLICY — ANTI-LOOP
This rule is mandatory for continuation work:

1. A commit SHA gets at most ONE CI status query/checkpoint.
2. If that query returns queued/pending/in_progress, do NOT query the same SHA again.
3. Continue an independent implementation/test block instead.
4. If a completed checkpoint is red, inspect only the concrete failed workflow/job/log, fix the root cause and create a new SHA.
5. Only the new SHA may receive another status query.
6. Never poll GitHub Actions repeatedly waiting for completion.
7. A missing workflow list on the one permitted query is also considered consumed for that SHA.

This policy exists because repeated status polling previously caused conversation/tool loops.

## GOAL
Continue evolving LocalCut as the main Android editor, with UserLAnd/Termux as an optional heavy worker. No AI, no paid dependency, no Play Store publication goal.

## TRACKING / STABILIZATION
- OpenCV global-motion tracking uses the existing authenticated Android ↔ UserLAnd/Termux engine.
- Reuses `ANALYZE_MEDIA` with `mode=TRACKING`; no duplicate job database/protocol.
- Media is streamed from `content://`/InputStream; Android private paths are never sent to the worker.
- Upload size and SHA-256 are verified.
- Tracking options remain bounded (`maxFrames`, `sampleEvery`, `maxCorners`).
- Capabilities `ANALYZE_TRACKING` and `OPENCV_GLOBAL_MOTION` are advertised only when OpenCV is available.
- Tracking result is converted into existing `PropertyAnimation` transforms.
- Trim-aware stabilization rebases motion to clip-local time and conservatively rejects deltas crossing the trim-start boundary.
- Non-destructive merge preserves unrelated animations.
- Stale stabilization is rejected when the asset/fingerprint/trim changes during analysis.
- Stabilization strength: 10–100%.
- Tracking quality presets:
  - FAST: 120 frames / every 4 / 80 corners.
  - NORMAL: 180 / every 2 / 120.
  - PRECISE: 300 / every 1 / 200.
- Signed zero regression (`-0.0` versus `0.0`) in stabilization was fixed by canonicalizing zero in production code, not by weakening the test.

Important stabilization fix commit:
- `a8ca720b70a50da45a1ad2391f0fbd0819d15d28`

## LONG-RUNNING WORKER ARCHITECTURE
Reverse and OpenCV tracking must NOT block the `STAGE_MEDIA` socket while heavy work runs.

Current design:
- `STAGE_MEDIA` validates and stages bytes, marks the job `RUNNING`, and returns immediately.
- Heavy Reverse/OpenCV work runs in a background worker.
- FFmpeg and OpenCV share one heavy-worker lock, so only one resource-intensive creative job executes at a time on the phone/UserLAnd environment.
- Android waits through typed `GET_JOB` checks with bounded backoff:
  - starts at 250 ms,
  - max interval 1 s,
  - maximum 900 polls (~15 minutes worst case),
  - coroutine cancellation is preserved.
- A unit test explicitly proves a never-finishing job stops at the configured poll limit with `TERMUX_JOB_WAIT_TIMEOUT`; generated media download is not attempted.
- This bounded job wait is application behavior and is separate from the CI anti-loop rule above.

Key commits:
- Reverse background server: `b36234d6caba5d1f8d128265d5d3204e3f03c1bd`
- Reverse bounded Android await: `25602a6e54dcc9cbf327f70d9a0b048cc45f3007`
- Shared OpenCV/FFmpeg worker: `cb2e9eb9da7d82d0530361be69fe55537c052bb2`
- Tracking bounded Android await: `7f284afe7472025245887fc444cc4b0b8a573dda`
- Anti-loop wait test: `1aa51aee732008f58564fbec50a40e15555f8743`

## REVERSE — CURRENT ROUND TRIP
Reverse is no longer only a planner/foundation. The round trip now includes:

### Worker/server
- Chunked FFmpeg reverse/areverse execution.
- Short chunks to avoid whole-video reverse memory behavior.
- Initial limits:
  - chunk 0.25–5 s,
  - default 2 s,
  - max source duration 10 min.
- Private generated-media spool per job.
- Fixed generated output path owned by the engine; no client-supplied worker path.
- Sidecar metadata, size limit, SHA-256, traversal/symlink protections.
- Authenticated generated-media download through `GET_JOB + includeGeneratedMedia=true`.
- Server returns JSON header (`contentLength`, `sha256`, `binaryBody`) followed by exact MP4 bytes.
- Internal worker paths are never returned to Android.

### Android transport/storage
- Generated MP4 streams directly to a `.part` file; video is not loaded wholly into RAM.
- Exact length and SHA-256 are checked before publish.
- `fsync` happens before rename.
- Failure removes partial output.
- Final generated files use LocalCut-owned durable storage: `filesDir/remote-media`, not cache.
- Existing `FileProvider` authority `${applicationId}.remote-media` is reused.
- `OwnedMediaFilePolicy` rejects empty files and anything outside the exact LocalCut-owned directory.
- Existing `AndroidAssetImporter` inspects the generated `content://` URI and validates video type, MIME, size and duration before timeline mutation.
- Preparation failure removes the generated output file.

### Clip replacement/history
- Trim mapping for whole-source physical reverse is implemented:
  - original source `[in, out]` over source duration `D`
  - reversed source range becomes `[D-out, D-in]`.
- Position, duration, transform, effects, property animations and compatible time-map settings are preserved.
- Existing Freeze or logical `timeMap.reverse` causes fail-closed behavior for this first implementation.
- `ApplyReversedAssetCommand` adds the generated Asset and swaps the selected clip source through the normal `EditorCommand` history path.
- Undo restores the exact original project state; redo can reapply the generated asset.
- Original/generated bytes are intentionally not aggressively deleted while history may still reference them.

### Stale-result guard and reusable apply coordinator
Reverse captures a source signature before processing:
- clip id,
- asset id,
- asset fingerprint,
- source duration,
- sourceIn/sourceOut.

When the worker returns, the result is discarded and its file deleted if the source media/trim changed, the clip disappeared, or Freeze/logical Reverse became active. Moving the clip or changing compatible visual properties does not unnecessarily invalidate the result.

The sensitive generated-media apply flow is centralized in `ReverseMediaApplyCoordinator`:
- capture source snapshot;
- prepare Reverse through the existing runtime;
- stale-check immediately before apply;
- execute the normal undoable editor command;
- verify that the clip actually switched to the generated asset;
- delete generated bytes if stale or rejected.

Key commits:
- coordinator test: `8dd76069abbcceecfc9c6c14dbe40662adba031a`
- coordinator: `a0c0c0fe189d07f948976c23973617200aa31a33`

### Current UI exposure
- `ReverseMediaActionControl` is the single reusable Compose entrypoint; socket/filesystem/FFmpeg logic remains outside Compose.
- Agent Engine media panel reuses that control instead of maintaining duplicate Reverse logic.
- When **Velocidade** is active for a selected VIDEO, the bottom tool context now shows the same real Reverse control beside the speed tools.
- The control makes one capability preflight per user click; it does not poll the engine.
- It only applies after the full verified worker/download/Asset/guard/undoable-command path succeeds.

Key UI commits:
- reusable Reverse control: `f1a49b2e69c15a6288895eec507e616621e9509a`
- Agent panel reuse: `b00d18ce711a8a409c3b7f087ec4ef8bf506f341`
- Speed-context Reverse: `0d417258e6840c5bbfd2581c5f2f5cfedc3c7392`

Earlier Reverse commits include:
- executor: `e4bb818...`
- server job: `f124219...`
- generated download server: `31d5d6b...`
- Android streamed verified download: `6e13f1c...`
- reverse coordinator/runtime: `1221817...`
- durable generated storage: `2e00ff1bb59485ea125762c3699499bec6baf492`
- LocalCut-owned media boundary: `c831a851d9971cbea399f7d9d33d3df674446eda`
- FileProvider exposure without copy: `4ea8ee01fec783d32f3fc6fd3787a188eaf285da`
- generated Asset inspector: `c79cd30303c27b3c009ce03bee38b43f5c6335b0`
- prepared Reverse Asset: `62964dbce44d9702a56877f212249cc422570b08`
- trim replacement planner: `22d3c294e774956cfb95d387a3da861bd6105b9e`
- undoable replacement command: `2a80df9832e5f26d907a5acde1d1c4500415858a`
- stale guard: `e7624f45e343e58d452a268824d1c1de1d235f3a`
- original Agent Engine Reverse UI: `2a40861bf1228b0c12879ca7cb9a844d5c6cafd7`

## FREEZE
- Freeze backend already exists through `Media3FreezeFramePlanner`.
- `CreativeToolsUiPolicy.freezeCurrentFrame(...)` converts the current presentation frame to source offset using `TimeMapEvaluator`.
- `SetClipTimeMapCommand` recalculates logical duration and timeline validation protects overlaps.
- Initial policy supports one freeze at a time and deliberately rejects logical Reverse and speed-ramp combinations.
- A real `FreezeFrameActionControl` now reads the actual editor playhead and selected VIDEO from `LocalCutProcessRuntime`.
- Speed context exposes:
  - Freeze 0.5 s;
  - Freeze 1 s;
  - Freeze 2 s;
  - Remove Freeze when a hold already exists.
- Freeze applies through `SetClipTimeMapCommand`, preserving undo/redo and the existing Media3 render backend.
- No `EditorScreen.kt` rewrite was needed; the control is attached through the existing bottom tool rail only while Speed is active.

Key commits:
- policy test: `ba69e285f3b8e03774eee23f9826433bd8292205`
- policy implementation: `d6d009c9a4f3c8e663a31b60f695dc7f1503aa7b`
- playhead-aware Freeze control: `6ab51c9eca226ad26078fb9b3b2e36ab0ac3fd1c`
- Speed-context Freeze: `cd940e919a86839addb71025b04445f1ce62649c`

## REVERSE FFMPEG AUDIT
- Current algorithm reverses short chunks individually, reverses chunk order globally and concatenates uniformly re-encoded MP4 chunks.
- FFmpeg official documentation explicitly warns that `reverse` / `areverse` buffer their clip and recommends trimming first; the chunk architecture follows that guidance.
- FFmpeg FAQ confirms concat demuxer is appropriate when already-compatible encoded segments can be concatenated without another full re-encode.
- No speculative timestamp rewrite was added in this audit because no failing A/V-sync or timestamp test currently demonstrates the need. Add such a change only with reproducible evidence.

## BLEND MODES
- BlendMode enum exists.
- Current Media3 backend rejects non-NORMAL blend modes.
- Do not expose Multiply/Screen/Overlay as fake buttons.
- Correct implementation requires a dedicated compositor/OpenGL blend backend.

## CI EVIDENCE / CURRENT RULE
Historical validated checkpoints include multiple successful Termux and Bridge runs.
A previous Android failure was diagnosed precisely: 879 tests ran and one stabilization test failed due to signed zero. Production was fixed in `a8ca720...`.

Recent code SHAs have each received at most one status query; several were still pending/in-progress at that single checkpoint. Do NOT query those same SHAs again merely to see whether they finished.

Latest code checkpoint consumed under the one-query rule:
- `cd940e919a86839addb71025b04445f1ce62649c`
  - Bridge Controller CI #1053: queued at the single checkpoint.
  - Termux Agent Engine CI #194: queued.
  - Android Debug APK #1345: queued.
  - Android Multitrack Export Smoke #1199: pending.
- Do NOT query `cd940e...` again.
- A future NEW code SHA may receive one fresh CI checkpoint.

## OPEN ITEMS — PRIORITY
1. Follow the fixed CI anti-loop rule; inspect a workflow only if a permitted checkpoint on a new SHA reports a completed failure.
2. Add generated-media orphan garbage collection only after defining history-safe retention; never delete media still reachable by undo/redo.
3. Add stabilization edge/crop compensation only after transform coordinate units are confirmed.
4. Blend modes require a dedicated compositor backend.
5. Continue creative-suite features in small independent blocks; do not reimplement Reverse/Freeze backends that are now wired.

## LAST VALID CHECKPOINT
This file plus the current branch HEAD are the source of truth. Keep PR #13 open + draft. Never merge into `main` without explicit user authorization.
