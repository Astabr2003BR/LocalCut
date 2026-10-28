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
- Stabilization strength: 10–100%.
- Tracking quality presets:
  - FAST: 120 frames / every 4 / 80 corners.
  - NORMAL: 180 / every 2 / 120.
  - PRECISE: 300 / every 1 / 200.
- Signed zero regression (`-0.0` versus `0.0`) was fixed by canonicalizing zero in production code, not by weakening the test.

### Base transform + automatic edge compensation
- Stabilization no longer assumes an artistic transform of `(x=0,y=0,scale=1,rotation=0)`.
- Correction keyframes are now relative to the selected clip's existing transform, so stabilizing a clip does not recenter or reset a transform intentionally applied by the user.
- The UI snapshots the clip transform and project canvas before tracking; if transform, asset/fingerprint, trim, selection or canvas changes while analysis runs, the stale result is discarded.
- `TransformEffectFactory` confirms domain x/y are project pixels before conversion to Media3 normalized device coordinates (NDC).
- Android Matrix documentation confirms the current renderer call order `postTranslate → postRotate → postScale` produces `M = S * R * T`.
- Media3 `MatrixTransformation` operates on NDC `[-1,1]` on x/y; edge coverage is therefore calculated in exactly that space.
- `StabilizationEdgeCompensation` computes the minimum relative scale that keeps all output NDC corners inside the transformed source after the actual `S*R*T` order.
- Translation at/beyond the NDC source boundary is unrecoverable by scaling and fails closed.
- Relative edge compensation above `2x` fails closed as `STABILIZATION_EDGE_COMPENSATION_TOO_LARGE` rather than silently applying an extreme crop/zoom.
- UI error text explains that excessive movement cannot be stabilized safely without excessive zoom.

Key commits:
- signed-zero fix: `a8ca720b70a50da45a1ad2391f0fbd0819d15d28`
- edge geometry tests: `5d0deea4950085e9a0a7c1ee41f3b65fd252252c`
- NDC edge geometry implementation: `abc7b1fa50aeb40a47b633e8a478681bb10a82ca`
- planner base-transform/edge integration: `abdbea074cded43c651049ea5ee22251823852f3`
- adapter forwarding: `74df0b7fadcc5ba8918f1beb755fd6b6e9663a12`
- UI snapshot/stale protection: `e9c25bf83fabcd885a76715e5ddb071b2cb3dbc9`

Local evidence:
- pure Kotlin harness passed: `PASS Media3 NDC S*R*T edge coverage`.

## MODERN PROPERTY ANIMATION RENDERING — CRITICAL GAP FIXED
A render-path audit found that `clip.propertyAnimations` were persisted/edited but the Media3 pipeline still carried only legacy `clip.keyframes`. This meant modern motion presets and stabilization could exist in the project without being applied to preview/export.

Current fixed path:
`Clip.propertyAnimations → ProjectRenderPlanCompiler → RenderSegment.Media → Media3BlueprintItem.Media → PropertyTransformRenderResolver → TransformEffectFactory / Media3SequenceOpacityResolver`.

Behavior:
- legacy `TransformKeyframe` remains supported;
- modern property animations override only properties they explicitly define;
- all other transform properties fall back to the legacy/base result;
- supported modern render properties are `transform.x`, `transform.y`, `transform.scale`, `transform.rotationDeg`, `transform.opacity`;
- duplicate modern animations for the same supported transform property fail closed;
- animation evaluation uses clip-local presentation time;
- modern opacity also triggers the custom compositor alpha path when needed;
- constructor compatibility was preserved by appending new defaulted fields/parameters rather than inserting them into old positional call order.

Tests cover:
- modern-vs-legacy precedence;
- clip-local clock;
- modern opacity and compositor requirement;
- `Clip → RenderPlan → Media3Blueprint` propagation.

Key commits:
- resolver tests: `87cf2f42e6eb1df8b22af4885f4bd4c875ef5ca6`
- resolver: `a7cb17875ef32ca054acb8b6d5ec393fed4670d1`
- render-plan propagation: `9cc0870a17ee451a6d27cc4e450f4c3630baf24d`
- blueprint propagation: `b2affe881232336b825606d39eda3e6683b0f1bb`
- transform renderer: `288bbce1abbe4599d324f018607db47e411081eb`
- modern opacity: `f9976e905f27dfbfece16f19dc14d13892c32393`
- Media3 compiler wiring + audio-fade regression correction: `223da0575fe41f179808f5e70c5d4922d17634c4`
- end-to-end propagation tests: `f396abbb81073f42a386c77bb32477b3068e2d62`

## GENERATED REVERSE MEDIA RETENTION / GC
Generated Reverse files now have a bounded, history-safe retention policy.

Safety rules:
- GC is fully blocked while the session reports `canUndo` or `canRedo`;
- only exact `reverse-<uuid>-<uuid>.mp4` files are candidates;
- `shared-*`, imports and arbitrary filenames are never candidates;
- all current Project Asset URIs protect their generated backing files;
- exact FileProvider authority + `/remote-media/<file>` path is required before a URI protects a generated candidate;
- generated files younger than 24 hours are protected;
- impossible/future timestamps fail closed;
- only top-level `filesDir/remote-media` is scanned; there is no recursive traversal;
- root/child symlinks, directories, empty files and canonical-parent escapes are never deleted;
- deletion failure is counted/reported without widening the deletion set.

Lifecycle:
- cleanup runs once per app process after the project/session is loaded;
- no timer and no scan on each editor action;
- it runs on IO through the existing process scope;
- an async startup race remains safe because current-project Assets are protected and newly generated files are inside the 24h grace period.

The concrete base `HistoryManager` implementation lives in the packaged base source rather than a visible override, so history internals were deliberately not overwritten. Public `ProjectSessionState.canUndo/canRedo` is used as the fail-closed history gate.

Key commits:
- design: `383ca76108ebc52e70c8929152bd5fcb3bd4cb56`
- retention tests: `8192d43fb1015416402b48702389c545a1fd5507`
- retention policy: `41ec93e31f75d1059ff1b0e07216d01978cd378d`
- collector tests: `fcf9e98252988d9d6e2ee2c9b9eebde10752339f`
- collector: `d7b2ea0280a2b9ea0b6a38e7860a123ce660b703`
- runtime integration: `21c8a47f197220be890af0ccf6a0ca45e8b6fb64`
- one-per-process startup trigger: `60264c07bf012660cacd5db01faf7f52a5e8ffdf`

Local evidence:
- pure Kotlin harness passed: `PASS generated media retention/collector`.

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
Reverse is no longer only a planner/foundation. The round trip includes:

### Worker/server
- chunked FFmpeg reverse/areverse execution;
- short chunks to avoid whole-video reverse memory behavior;
- chunk 0.25–5 s, default 2 s, initial max source duration 10 min;
- private generated-media spool per job;
- fixed generated output path owned by the engine; no client-supplied worker path;
- sidecar metadata, size limit, SHA-256, traversal/symlink protections;
- authenticated generated-media download through `GET_JOB + includeGeneratedMedia=true`;
- server returns JSON header (`contentLength`, `sha256`, `binaryBody`) followed by exact MP4 bytes;
- internal worker paths are never returned to Android.

### Android transport/storage
- generated MP4 streams directly to a `.part` file; video is not loaded wholly into RAM;
- exact length and SHA-256 are checked before publish;
- `fsync` happens before rename;
- failure removes partial output;
- final generated files use `filesDir/remote-media`;
- existing FileProvider authority `${applicationId}.remote-media` is reused;
- generated `content://` is inspected by the existing Android Asset importer before timeline mutation.

### Clip replacement/history
- physical reverse trim mapping: original `[in,out]` over duration `D` becomes `[D-out,D-in]`;
- position, duration, transform, effects, property animations and compatible time-map settings are preserved;
- existing Freeze/logical reverse causes fail-closed behavior;
- `ApplyReversedAssetCommand` adds the generated Asset and swaps the clip source through normal undoable history;
- undo restores the exact original project state; redo can reapply the generated asset.

### Stale-result guard and reusable apply coordinator
- source signature includes clip id, asset id/fingerprint/source duration/sourceIn/sourceOut;
- stale result is discarded and generated bytes deleted if source/trim becomes incompatible;
- the sensitive apply flow is centralized in `ReverseMediaApplyCoordinator`.

### UI exposure
- `ReverseMediaActionControl` is the single reusable Compose entrypoint;
- Agent Engine media panel reuses it;
- when **Velocidade** is active for a selected VIDEO, the same real Reverse control appears in the speed context;
- one capability preflight occurs per user click; no engine polling loop.

Key later commits:
- coordinator: `a0c0c0fe189d07f948976c23973617200aa31a33`
- reusable Reverse control: `f1a49b2e69c15a6288895eec507e616621e9509a`
- Agent panel reuse: `b00d18ce711a8a409c3b7f087ec4ef8bf506f341`
- Speed-context Reverse: `0d417258e6840c5bbfd2581c5f2f5cfedc3c7392`

## FREEZE
- Freeze backend exists through `Media3FreezeFramePlanner`.
- `CreativeToolsUiPolicy.freezeCurrentFrame(...)` maps current presentation frame to source offset through `TimeMapEvaluator`.
- `SetClipTimeMapCommand` recalculates logical duration and timeline validation protects overlaps.
- initial policy supports one freeze at a time and rejects incompatible logical Reverse/speed-ramp combinations.
- `FreezeFrameActionControl` reads the actual editor playhead/selected VIDEO.
- Speed context exposes Freeze 0.5 s / 1 s / 2 s and Remove Freeze.
- applies through `SetClipTimeMapCommand`, preserving undo/redo.

Key commits:
- policy: `d6d009c9a4f3c8e663a31b60f695dc7f1503aa7b`
- playhead-aware control: `6ab51c9eca226ad26078fb9b3b2e36ab0ac3fd1c`
- Speed-context Freeze: `cd940e919a86839addb71025b04445f1ce62649c`

## REVERSE FFMPEG AUDIT
- current algorithm reverses short chunks individually, reverses chunk order globally and concatenates uniformly re-encoded MP4 chunks;
- FFmpeg docs warn `reverse`/`areverse` buffer their clip and recommend trimming first; chunking follows that guidance;
- no speculative timestamp rewrite was added without a reproducible A/V-sync failure.

## BLEND MODES
- BlendMode enum exists.
- Current Media3 backend rejects non-NORMAL blend modes.
- Do not expose Multiply/Screen/Overlay as fake buttons.
- Correct implementation requires a dedicated compositor/OpenGL blend backend.

## CI EVIDENCE / CONSUMED SHAS
Do not query these SHAs again:

- `cd940e919a86839addb71025b04445f1ce62649c`
  - Bridge #1053 queued; Termux #194 queued; Android #1345 queued; Multitrack #1199 pending at its single checkpoint.
- `60264c07bf012660cacd5db01faf7f52a5e8ffdf`
  - Termux Agent Engine CI #203 SUCCESS.
  - Bridge Controller CI #1062 SUCCESS.
  - Android Debug #1354 and Multitrack #1208 were still in progress at the single checkpoint.
- `f396abbb81073f42a386c77bb32477b3068e2d62`
  - Android Debug #1369 queued.
  - Termux #218 and Bridge #1077 in progress.
  - Multitrack #1223 queued.

A NEW final code/checkpoint SHA after the stabilization-edge work may receive exactly one fresh CI query.

## OPEN ITEMS — PRIORITY
1. Follow the fixed one-query-per-SHA CI rule; inspect a workflow only when a permitted new-SHA checkpoint reports a completed failure.
2. Validate the now-rendered modern property animations and stabilization on a real Android device when an APK candidate is intentionally built/tested; do not claim physical behavior before that.
3. Blend modes require a dedicated compositor/OpenGL backend before any UI buttons are exposed.
4. Continue creative-suite features in small independent blocks; do not reimplement Reverse/Freeze/GC/property-animation render paths now completed.

## LAST VALID CHECKPOINT
This file plus the current branch HEAD are the source of truth. Keep PR #13 open + draft. Never merge into `main` without explicit user authorization.
