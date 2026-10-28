# LocalCut checkpoint — tracking, stabilization, freeze and reverse foundation — 2026-10-28

## PROJECT
LocalCut

## BRANCH
`feature/localcut-bridge-v1`

## PR
#13 — OPEN + DRAFT. Do not merge without explicit authorization.

## GOAL
Continue evolving LocalCut as the main Android editor, with UserLAnd/Termux as an optional heavy worker. No AI, no paid dependency, no Play Store publication goal.

## COMPLETED IN THIS BLOCK

### UserLAnd / Termux tracking
- Remote OpenCV global-motion tracking is exposed through the existing creative server.
- Reuses `ANALYZE_MEDIA` with `mode=TRACKING`; no second job database/type.
- Reuses private media spool, idempotency, cancellation and authentication.
- Tracking options are bounded (`maxFrames`, `sampleEvery`, `maxCorners`).
- Capabilities `ANALYZE_TRACKING` and `OPENCV_GLOBAL_MOTION` are advertised only when the analyzer is available.
- Stable base engine remains untouched.

Key commit: `3111fee0cf115f5dba78be427c5236a349ea8fa5`.

### Android tracking protocol
- Existing secure Android → UserLAnd runtime was reused; no duplicate socket/token protocol.
- Token remains in Android Keystore.
- Media remains streamed from `content://`/InputStream; Android private paths are never sent.
- Upload size and SHA-256 are verified.
- `TermuxAnalyzeMediaMode.TRACKING`, `TermuxTrackingOptions`, global-motion typed models and parsing are implemented.
- `TermuxMediaTrackingCoordinator` uses the existing `SocketTermuxEngineJobClient` and `STAGE_MEDIA` path.

Key commits:
- `1182cbaf35c1c0c83a9267ab67323178c89e47bf`
- `a48c10df5555afbf5ad8a86c101c75e8655bf4ee`
- `84266fb5a88b83332ef790c3f239b89194422ba6`
- `6b499bba17baedd66fbae7141587b57237840612`

### Stabilization
- Global motion maps into existing `PropertyAnimation` transform properties.
- Trim-aware: samples outside `sourceInUs/sourceOutUs` are removed and clip-local timing is rebased.
- A delta that crosses the trim-start boundary is discarded conservatively.
- Non-destructive merge preserves unrelated property animations and replaces only stabilization transform properties.
- Animation panel can run tracking through UserLAnd/Termux, apply stabilization and retain undo/autosave through the existing editor command path.
- Stale analysis is rejected if selection, asset fingerprint, sourceIn or sourceOut changes while analysis is running.
- Stabilization strength control: 10–100%.
- Tracking quality presets:
  - FAST: 120 frames, sample every 4, 80 corners.
  - NORMAL: 180 frames, sample every 2, 120 corners.
  - PRECISE: 300 frames, sample every 1, 200 corners.

Key commits:
- `72888cfbdc894d9b4af0d99ef1e0d2dbdfb40994`
- `d195a555b6693893f342773899b04d4f84606bc7`
- `3effd129223e28560464a65c93072d62086e0a73`
- `f935b72879447f1002c0795bb82d1f9a09fb4d66`

### Freeze audit
- Freeze is NOT missing from the backend: `Media3FreezeFramePlanner` already renders freeze holds by splitting a clip into moving/frozen pieces.
- `SetClipTimeMapCommand` recalculates logical duration and timeline validation protects overlaps.
- Added mobile freeze policy for current-frame freeze durations and clear operation.
- Policy supports one freeze at a time, forward constant-speed time maps only. Reverse and speed ramps are deliberately rejected in this first UI policy.
- UI wiring to the focused speed panel is not completed yet because the connector only replaces whole files and `EditorScreen.kt`/`FocusedCreativeToolsPanels.kt` are large. Avoid risky whole-file rewrite for two-line playhead plumbing.

Key commits:
- test: `ba69e285f3b8e03774eee23f9826433bd8292205`
- implementation: `d6d009c9a4f3c8e663a31b60f695dc7f1503aa7b`

### Blend modes audit
- BlendMode enum exists, but Media3 backend explicitly rejects non-NORMAL modes.
- `VideoCompositorSettings` path currently provides layer alpha/layout, not Multiply/Screen/Overlay equations.
- Do NOT expose blend buttons until a custom compositor/OpenGL backend exists.

### Reverse audit and foundation
- Media3 time-map backend rejects `reverse=true`; reverse is genuinely missing.
- FFmpeg official docs warn that `reverse` and `areverse` buffer the full clip in memory; whole-clip reverse is inappropriate for mobile.
- Added private generated-media spool:
  - per-job private directory,
  - fixed `output.mp4` name,
  - no client-supplied filesystem path,
  - size limit,
  - SHA-256,
  - finalized sidecar metadata,
  - bounded chunk streaming,
  - symlink and traversal rejection.
- Added bounded reverse chunk planner:
  - source split into short chunks,
  - output order reversed,
  - each chunk later processed independently with FFmpeg reverse/areverse,
  - chunk size 0.25–5s,
  - default 2s,
  - initial max clip duration 10 min.
- FFmpeg execution, secure download protocol and Android import are NOT implemented yet. Reverse must not be called functional yet.

Key commits:
- output spool test `c697755d578120e5fce6f4f04280e615c1bbeeb0`
- output spool `11481f1cfa39f05e69fa4cfade8b9cf861c88039`
- reverse planner test `6dafa3aab29261e973ecd78fb63610c0a4cb7535`
- reverse planner `c258e22cd10060342a978c5b061c3639b047fa0c`

## TEST / CI EVIDENCE
- Remote tracking Termux CI #111: SUCCESS.
- Later Termux CI checkpoints continued passing (#119, #130, #131, #134, #136).
- Bridge checkpoints continued passing (#978, #989, #990, #993, #995).
- At the last bounded check for the newest commits, Android Debug / Multitrack jobs were still queued or in progress. Do not claim the newest Android integration is fully CI-validated until a completed success run is observed.
- Do not poll CI in a loop.

## KNOWN RISKS / OPEN ITEMS
1. Confirm latest Android Debug + Multitrack smoke after they finish; one bounded check only.
2. Freeze policy backend exists but focused speed UI still needs safe playhead plumbing.
3. Stabilization edge/crop compensation is not implemented. Do not guess transform coordinate units.
4. Blend modes require a dedicated custom compositor backend.
5. Reverse requires:
   - FFmpeg chunk processor,
   - output download operation,
   - Android streamed download with SHA-256 verification,
   - import/generated asset handling,
   - clip replacement/history behavior.
6. Do not expose Reverse before the full round trip is complete.

## NEXT ACTION
Prefer small blocks:
1. Run one bounded CI check. Fix any real failure first.
2. Implement/test FFmpeg chunk reverse processor using `shell=False`, fixed generated paths and short chunk windows.
3. Then implement generated-media fetch protocol and Android download verification.
4. Only after full round trip, expose Reverse in the Speed panel.

## LAST VALID CHECKPOINT
This file plus branch HEAD are the source of truth. Keep PR #13 draft/open and do not merge.
