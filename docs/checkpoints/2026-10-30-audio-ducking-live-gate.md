# LocalCut — Audio Auto Ducking live gate

Date: 2026-10-30
Branch: `feature/localcut-bridge-v1`
PR: #13 (must remain OPEN + DRAFT)
Baseline before this slice: `6026957672b1267b3dec861e55251ea68fe90b06`

## Goal

Add a real, deterministic sidechain ducking vertical slice for Android audio editing without fake voice detection, without changing clip timing, and with the same render semantics in preview and export.

## Implemented contract

- Ducking is configured on an AUDIO target track.
- The user selects another AUDIO track explicitly as the sidechain source.
- Settings are persistent and validated:
  - `sourceTrackId`
  - `reductionDb` in `-48..0 dB`
  - `attackUs` in `0..5,000,000 us`
  - `releaseUs` in `0..10,000,000 us`
- Source clip occupancy on the project timeline is compiled into target-clip-local ducking windows.
- Attack, hold and release are deterministic linear gain envelopes.
- Overlapping windows use the strongest attenuation (`min` gain), avoiding gain amplification.
- Muted source tracks and tracks excluded by solo policy do not trigger ducking.
- The target clip `durationUs`, `sourceInUs` and `sourceOutUs` are not changed.
- Target tracks with non-neutral `TimeMap` are rejected fail-closed until presentation-time/source-time mapping is implemented for ducking; this prevents timing drift with speed ramps/reverse/freeze.

## Render path

The envelope is carried through the existing shared pipeline:

`Track.ducking -> ProjectRenderPlanCompiler -> RenderSegment.Media.duckingEnvelope -> Media3CompositionBlueprint -> ProjectCompositionCompiler -> GainProcessor(DuckingGainProvider)`

The gain processor is inserted after the existing sample DSP and static clip volume and before the existing fade processor. Both FAST_PREVIEW and EXPORT compile through `ProjectCompositionCompiler`, so they share the same ducking semantics.

## Persistence / edit history

- `Track.ducking` is encoded/decoded by `ProjectJson`.
- Missing `ducking` in older project JSON decodes to `null`.
- `TimelineValidator` rejects missing/visual/self sidechain references and unsupported target time maps.
- `SetTrackDuckingCommand` uses the normal history command path, producing an exact inverse.
- Editor UI dispatches the command through `EditorAction.Execute`, therefore normal undo/redo, autosave, revision and preview rebuild behavior is preserved.

## UI

Audio > Básico exposes:

- Auto Ducking enable/disable
- explicit sidechain audio-track selection
- reduction dB
- attack ms
- release ms

The UI states that mute/solo of the source suspends ducking. It does not claim signal-aware voice detection.

## Tests added

- `AudioDuckingPlannerTest`
  - overlap to target-local mapping
  - release tail crossing target start
  - non-overlap unity
  - attack/hold/release continuity
- `DuckingGainProviderTest`
  - sample-position gain contract
  - unity ranges / Media3 sentinel values
  - invalid sample inputs fail closed
- `ProjectAudioDuckingParityTest`
  - FAST_PREVIEW vs EXPORT envelope parity
  - target duration unchanged
  - muted sidechain ignored
  - solo-excluded sidechain ignored
- `ProjectJsonDuckingPersistenceTest`
  - round-trip
  - legacy missing field
  - missing source rejection
- `SetTrackDuckingCommandTest`
  - history undo/redo exact restoration
  - time-mapped target rejection

## Verification state at checkpoint creation

- Static architecture audit: completed.
- Baseline-to-head compare: only ducking vertical-slice files changed; no unrelated subsystem rewrite.
- Local Gradle/JVM execution: NOT AVAILABLE in this environment.
- GitHub Actions: pending; query exactly once for this checkpoint SHA under the anti-loop rule.
- Physical Android runtime/APK test: NOT RUN; APK/AAB generation remains blocked by project rule unless explicitly authorized by the user.

## Known intentional limitation

This implementation is timeline/clip-occupancy sidechain ducking, not signal-level speech detection. It does not inspect speech loudness or automatically identify voice. Ducking on target clips with `TimeMap` speed/reverse/freeze is explicitly rejected rather than rendered with potentially incorrect timing.

## Next gate

1. Run the one permitted GitHub Actions status query for the checkpoint SHA.
2. If a workflow fails, inspect its job log, fix the root cause in a new SHA, and query that new SHA once.
3. Keep PR #13 OPEN + DRAFT.
4. Do not merge to `main`.
5. Do not generate APK/AAB without explicit authorization.
