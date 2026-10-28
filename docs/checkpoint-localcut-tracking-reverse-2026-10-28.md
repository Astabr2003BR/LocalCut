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

### Stale-result guard
Reverse captures a source signature before processing:
- clip id,
- asset id,
- asset fingerprint,
- source duration,
- sourceIn/sourceOut.

When the worker returns, the result is discarded and its file deleted if the source media/trim changed, the clip disappeared, or Freeze/logical Reverse became active. Moving the clip or changing compatible visual properties does not unnecessarily invalidate the result.

### Current UI exposure
- A real Reverse action is currently exposed in the existing Agent Engine media panel.
- It only enables for a compatible selected VIDEO and when the engine advertises `REVERSE_MEDIA`.
- UI state shows running/success/error.
- The action calls the existing runtime; no socket/filesystem logic lives directly in Compose.
- It verifies that the normal editor command actually replaced the clip; rejected timeline changes cause the generated file to be deleted.
- The CapCut-style placement inside the focused Speed panel is still a UI placement task; backend functionality must not be reimplemented for that move.

Key Reverse commits in the later round trip include:
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
- Agent Engine Reverse UI: `2a40861bf1228b0c12879ca7cb9a844d5c6cafd7`

## FREEZE
- Freeze backend already exists through `Media3FreezeFramePlanner`.
- A mobile policy exists for current-frame freeze durations and clearing freeze.
- `SetClipTimeMapCommand` recalculates logical duration and timeline validation protects overlaps.
- Initial policy supports one freeze at a time and deliberately rejects incompatible Reverse/speed-ramp combinations.
- Focused Speed UI wiring is still pending.

Key commits:
- test: `ba69e285f3b8e03774eee23f9826433bd8292205`
- implementation: `d6d009c9a4f3c8e663a31b60f695dc7f1503aa7b`

## BLEND MODES
- BlendMode enum exists.
- Current Media3 backend rejects non-NORMAL blend modes.
- Do not expose Multiply/Screen/Overlay as fake buttons.
- Correct implementation requires a dedicated compositor/OpenGL blend backend.

## CI EVIDENCE / CURRENT RULE
Historical validated checkpoints include multiple successful Termux and Bridge runs.
A previous Android failure was diagnosed precisely: 879 tests ran and one stabilization test failed due to signed zero. Production was fixed in `a8ca720...`.

Recent code SHAs have each received at most one status query; several were still pending/in-progress at that single checkpoint. Do NOT query those same SHAs again merely to see whether they finished. A later new code SHA may receive one new checkpoint.

## OPEN ITEMS — PRIORITY
1. Let CI validation occur only through the anti-loop checkpoint policy above; fix a failure only when a new permitted checkpoint reports a completed red run.
2. Move the already-functional Reverse action into the focused Speed panel without duplicating backend logic.
3. Wire the existing Freeze policy into the focused Speed panel using the actual playhead.
4. Add generated-media orphan garbage collection only after defining history-safe retention; never delete media still reachable by undo/redo.
5. Add stabilization edge/crop compensation only after transform coordinate units are confirmed.
6. Blend modes require a dedicated compositor backend.

## LAST VALID CHECKPOINT
This file plus the current branch HEAD are the source of truth. Keep PR #13 open + draft. Never merge into `main` without explicit user authorization.
